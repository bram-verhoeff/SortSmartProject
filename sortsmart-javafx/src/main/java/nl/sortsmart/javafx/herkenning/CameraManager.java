package nl.sortsmart.javafx.herkenning;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_videoio;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Beheert de webcam via OpenCV met de Media Foundation (MSMF) backend voor Windows.
 * Verwerkt videoframes in het geheugen voor weergave en bewegingsdetectie.
 * Slaat NOOIT frames op schijf op.
 */
public class CameraManager {

    private final HerkenningConfig config;
    private final SceneDetector sceneDetector = new SceneDetector();

    private VideoCapture capture;
    private ExecutorService captureExecutor;
    private final AtomicBoolean loopt = new AtomicBoolean(false);
    private volatile boolean cameraBeschikbaar = false;
    private volatile String foutMelding = null;

    private Consumer<Image> frameListener;
    private Consumer<byte[]> scanTriggerListener;

    private volatile byte[] laatsteJpegBytes = null;

    private static final int SAMPLE_W = 48;
    private static final int SAMPLE_H = 36;

    public CameraManager() {
        this(HerkenningConfig.getInstantie());
    }

    public CameraManager(HerkenningConfig config) {
        this.config = config;
    }

    public boolean isCameraBeschikbaar() {
        return cameraBeschikbaar;
    }

    public String getFoutMelding() {
        return foutMelding;
    }

    public boolean isActief() {
        return loopt.get();
    }

    /**
     * Start de camera-opname op een achtergrondthread.
     */
    public synchronized void start(Consumer<Image> frameListener, Consumer<byte[]> scanTriggerListener) {
        if (loopt.get()) {
            return;
        }

        this.frameListener = frameListener;
        this.scanTriggerListener = scanTriggerListener;
        this.foutMelding = null;

        captureExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Camera-Capture-Thread");
            t.setDaemon(true);
            return t;
        });

        loopt.set(true);
        captureExecutor.submit(this::captureLoop);
    }

    /**
     * Stopt de camera en geeft hardwarebronnen direct vrij.
     */
    public synchronized void stop() {
        loopt.set(false);
        if (captureExecutor != null) {
            captureExecutor.shutdownNow();
            try {
                captureExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            captureExecutor = null;
        }

        if (capture != null) {
            try {
                if (capture.isOpened()) {
                    capture.release();
                }
            } catch (Throwable ignored) {}
            capture = null;
        }

        sceneDetector.reset();
        cameraBeschikbaar = false;
    }

    private void captureLoop() {
        int cameraIndex = config.getCameraIndex();
        try {
            // Probeer VideoCapture te openen met MSMF-backend
            capture = new VideoCapture();
            boolean geopend = capture.open(cameraIndex, opencv_videoio.CAP_MSMF);
            if (!geopend) {
                // Fallback naar standaard backend
                geopend = capture.open(cameraIndex, opencv_videoio.CAP_ANY);
            }

            if (!geopend || !capture.isOpened()) {
                foutMelding = "Kan camera " + cameraIndex + " niet openen via MSMF.";
                cameraBeschikbaar = false;
                loopt.set(false);
                return;
            }

            // Stel gewenste resolutie in
            capture.set(opencv_videoio.CAP_PROP_FRAME_WIDTH, 1280);
            capture.set(opencv_videoio.CAP_PROP_FRAME_HEIGHT, 720);

            cameraBeschikbaar = true;
            foutMelding = null;
            sceneDetector.reset();

            Mat frameMat = new Mat();
            Mat grayMat = new Mat();
            Mat smallMat = new Mat();
            BytePointer jpegPointer = new BytePointer();
            byte[] grayBytes = new byte[SAMPLE_W * SAMPLE_H];

            long lastFrameTime = 0;
            final long minIntervalMs = 40; // Max ~25 FPS om CPU te ontlasten
            int opeenvolgendeFouten = 0;

            while (loopt.get() && !Thread.currentThread().isInterrupted()) {
                long now = System.currentTimeMillis();
                long elapsed = now - lastFrameTime;
                if (elapsed < minIntervalMs) {
                    try {
                        Thread.sleep(minIntervalMs - elapsed);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                lastFrameTime = System.currentTimeMillis();

                if (!capture.read(frameMat) || frameMat.empty()) {
                    opeenvolgendeFouten++;
                    if (opeenvolgendeFouten > 25) {
                        cameraBeschikbaar = false;
                        foutMelding = "Camerastream onderbroken";
                        try {
                            Thread.sleep(400); // Voorkom log flooding bij slaapstand
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    continue;
                }
                opeenvolgendeFouten = 0;
                cameraBeschikbaar = true;

                // 1. Converteer naar JPEG-bytes in geheugen (geen bestand op schijf)
                boolean encoded = opencv_imgcodecs.imencode(".jpg", frameMat, jpegPointer);
                if (encoded && jpegPointer.limit() > 0) {
                    byte[] jpegBytes = new byte[(int) jpegPointer.limit()];
                    jpegPointer.get(jpegBytes);
                    laatsteJpegBytes = jpegBytes;

                    // Update live UI preview
                    if (frameListener != null) {
                        Platform.runLater(() -> {
                            try {
                                Image fxImg = new Image(new ByteArrayInputStream(jpegBytes));
                                frameListener.accept(fxImg);
                            } catch (Exception ignored) {}
                        });
                    }

                    // 2. Bewegingsdetectie (indien autoScan aan staat)
                    if (config.isAutoScan()) {
                        opencv_imgproc.cvtColor(frameMat, grayMat, opencv_imgproc.COLOR_BGR2GRAY);
                        opencv_imgproc.resize(grayMat, smallMat, new Size(SAMPLE_W, SAMPLE_H));
                        smallMat.data().get(grayBytes);

                        SceneDetector.Update update = sceneDetector.verwerkFrame(grayBytes, System.currentTimeMillis());
                        if (update.isScan() && scanTriggerListener != null) {
                            final byte[] scanSnapshot = jpegBytes.clone();
                            Platform.runLater(() -> {
                                if (scanTriggerListener != null) {
                                    scanTriggerListener.accept(scanSnapshot);
                                }
                            });
                        }
                    }
                }
            }

            // Opruimen
            try {
                if (capture.isOpened()) {
                    capture.release();
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            foutMelding = "Camerafout: " + t.getMessage();
            cameraBeschikbaar = false;
        } finally {
            loopt.set(false);
        }
    }

    /**
     * Vangt het meest recente stilstaande frame op als JPEG-bytes (in het geheugen).
     */
    public byte[] getLaatsteJpegBytes() {
        return laatsteJpegBytes;
    }

    /**
     * Reset de bewegingsdetector (bijv. na een voltooide scan).
     */
    public void resetBeweegDetectie() {
        sceneDetector.reset();
    }
}

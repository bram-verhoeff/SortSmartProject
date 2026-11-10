import {
  ApplicationSettingsSchema,
  ClassificationEngine,
  HISTORY_VERSION,
  MockSortingDevice,
  createLogger,
  createProvider,
  defaultSettings,
  loadHistory,
  loadSettings,
  type AIProvider,
  type ApplicationSettings,
  type ClassificationRecord,
  type DeviceStatus,
  type LogEntry,
  type MockDeviceScenario,
  type SortingDevice,
} from "@sorte/core";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { exampleStore, readStore, transport, writeStore } from "../platform";
import { browserEmbedder } from "../platform/clip";

export type Screen = "live" | "overview" | "providers" | "local" | "devices" | "history" | "settings";

interface AppState {
  ready: boolean;
  screen: Screen;
  go(screen: Screen): void;
  settings: ApplicationSettings;
  updateSettings(fn: (s: ApplicationSettings) => ApplicationSettings): void;
  notices: string[];
  dismissNotice(n: string): void;
  providers: Map<string, AIProvider>;
  providerErrors: Map<string, string>;
  engine: ClassificationEngine;
  history: ClassificationRecord[];
  addRecord(r: ClassificationRecord): void;
  updateRecord(id: string, patch: Partial<ClassificationRecord>): void;
  clearHistory(): void;
  logs: LogEntry[];
  device: SortingDevice | null;
  deviceStatus: DeviceStatus | null;
  connectSimulator(scenario: MockDeviceScenario): Promise<void>;
  disconnectDevice(): Promise<void>;
  refreshDevice(): Promise<void>;
  cloudRequests: number;
  bumpStats(): void;
  /** First-run setup is showing instead of the app. */
  setupOpen: boolean;
  openSetup(): void;
  closeSetup(): void;
}

const Ctx = createContext<AppState | null>(null);

export function useApp(): AppState {
  const v = useContext(Ctx);
  if (!v) throw new Error("useApp outside provider");
  return v;
}

/**
 * A mode whose provider is gone (e.g. Cloud after removing the only cloud
 * provider) can never classify. Fall back to the provider that is set.
 */
function usableMode(s: ApplicationSettings): ApplicationSettings {
  const has = (id: string | null) => !!id && s.providers.some((p) => p.id === id);
  const local = has(s.localProviderId);
  const cloud = has(s.cloudProviderId);
  if (s.mode === "cloud" && !cloud && local) return { ...s, mode: "local" };
  if ((s.mode === "local" || s.mode === "hybrid") && !local && cloud) return { ...s, mode: "cloud" };
  return s;
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [screen, setScreen] = useState<Screen>("live");
  const [settings, setSettings] = useState<ApplicationSettings>(defaultSettings);
  const [history, setHistory] = useState<ClassificationRecord[]>([]);
  const [notices, setNotices] = useState<string[]>([]);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [device, setDevice] = useState<SortingDevice | null>(null);
  const [deviceStatus, setDeviceStatus] = useState<DeviceStatus | null>(null);
  const [, setTick] = useState(0);
  const [setupOpen, setSetupOpen] = useState(false);
  /** False when the database couldn't be read at launch: then nothing is written, so saved data is never replaced by defaults. */
  const persist = useRef(true);

  // Load persisted state once.
  useEffect(() => {
    (async () => {
      let rawSettings: string | null = null;
      let rawHistory: string | null = null;
      let loadError: string | null = null;
      try {
        rawSettings = await readStore("settings");
        rawHistory = await readStore("history");
      } catch (e) {
        persist.current = false;
        loadError = `${(e as Error).message} Settings and history won't be saved until you reload.`;
      }
      const s = loadSettings(rawSettings);
      const h = loadHistory(rawHistory, s.value.history.retentionDays);
      setSettings(usableMode(s.value));
      // Anyone who already connected an AI before setup existed skips it.
      setSetupOpen(s.value.onboarding.completedAt === null && !s.value.localProviderId && !s.value.cloudProviderId);
      setHistory(s.value.history.enabled ? h.value.records : []);
      setNotices([loadError, s.message, h.message].filter((m): m is string => !!m));
      setReady(true);
    })();
  }, []);

  const saveFailed = useCallback((e: unknown) => {
    const msg = `Couldn't save: ${(e as Error).message}`;
    setNotices((ns) => (ns.includes(msg) ? ns : [...ns, msg]));
  }, []);

  // Persist settings (debounced). Secrets are never part of settings.
  useEffect(() => {
    if (!ready || !persist.current) return;
    const t = setTimeout(() => void writeStore("settings", JSON.stringify(settings)).catch(saveFailed), 250);
    return () => clearTimeout(t);
  }, [settings, ready, saveFailed]);

  // History is debounced too: it goes over the network to MySQL now.
  useEffect(() => {
    if (!ready || !persist.current) return;
    const records = settings.history.enabled ? history : [];
    const t = setTimeout(() => void writeStore("history", JSON.stringify({ version: HISTORY_VERSION, records })).catch(saveFailed), 400);
    return () => clearTimeout(t);
  }, [history, settings.history.enabled, ready, saveFailed]);

  const { providers, providerErrors } = useMemo(() => {
    const providers = new Map<string, AIProvider>();
    const providerErrors = new Map<string, string>();
    for (const cfg of settings.providers) {
      try {
        providers.set(cfg.id, createProvider(cfg, { transport, embedder: browserEmbedder, examples: exampleStore }));
      } catch (e) {
        providerErrors.set(cfg.id, (e as Error).message);
      }
    }
    return { providers, providerErrors };
  }, [settings.providers]);

  const settingsRef = useRef(settings);
  settingsRef.current = settings;
  const providersRef = useRef(providers);
  providersRef.current = providers;

  const engine = useMemo(
    () =>
      new ClassificationEngine({
        getSettings: () => settingsRef.current,
        getProvider: (id) => providersRef.current.get(id) ?? null,
        // Request metadata only — the logger redacts secrets, and images are never logged.
        logger: createLogger((e) => setLogs((l) => [e, ...l].slice(0, 200))),
      }),
    [],
  );

  const updateSettings = useCallback((fn: (s: ApplicationSettings) => ApplicationSettings) => {
    setSettings((prev) => {
      const parsed = ApplicationSettingsSchema.safeParse(usableMode(fn(prev)));
      if (!parsed.success) {
        console.warn("[sorte] rejected invalid settings update", parsed.error.issues[0]);
        return prev;
      }
      return parsed.data;
    });
  }, []);

  const addRecord = useCallback(
    (r: ClassificationRecord) => {
      if (!settingsRef.current.history.enabled) return;
      setHistory((h) => [r, ...h].slice(0, 5000));
    },
    [],
  );
  const updateRecord = useCallback((id: string, patch: Partial<ClassificationRecord>) => setHistory((h) => h.map((r) => (r.id === id ? { ...r, ...patch } : r))), []);
  const clearHistory = useCallback(() => setHistory([]), []);

  const connectSimulator = useCallback(async (scenario: MockDeviceScenario) => {
    await device?.disconnect();
    const d = new MockSortingDevice(scenario);
    setDevice(d);
    setDeviceStatus(await d.getStatus());
    setDeviceStatus(await d.connect());
  }, [device]);

  const disconnectDevice = useCallback(async () => {
    await device?.disconnect();
    setDevice(null);
    setDeviceStatus(null);
  }, [device]);

  const refreshDevice = useCallback(async () => {
    if (device) setDeviceStatus(await device.getStatus());
  }, [device]);

  const value: AppState = {
    ready,
    screen,
    go: setScreen,
    settings,
    updateSettings,
    notices,
    dismissNotice: (n) => setNotices((ns) => ns.filter((x) => x !== n)),
    providers,
    providerErrors,
    engine,
    history,
    addRecord,
    updateRecord,
    clearHistory,
    logs,
    device,
    deviceStatus,
    connectSimulator,
    disconnectDevice,
    refreshDevice,
    cloudRequests: engine.cloudRequestCount,
    bumpStats: () => setTick((t) => t + 1),
    setupOpen,
    openSetup: () => setSetupOpen(true),
    closeSetup: () => setSetupOpen(false),
  };

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export const MODE_META = {
  local: { label: "Local", tone: "sage", description: "Inference on this computer. Nothing leaves it." },
  cloud: { label: "Cloud", tone: "lavender", description: "Images are sent to your selected cloud provider." },
  hybrid: { label: "Hybrid", tone: "amber", description: "Local first; cloud only if you've authorised fallback." },
  demo: { label: "Demo", tone: "neutral", description: "Simulated results from fixtures. Not AI inference." },
} as const;

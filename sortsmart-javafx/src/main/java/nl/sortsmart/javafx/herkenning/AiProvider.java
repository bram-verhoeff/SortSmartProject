package nl.sortsmart.javafx.herkenning;

import java.util.List;

/**
 * Interface voor afvalherkennings-providers (bijv. lokale Ollama, cloud-modellen of hardware-sensoren).
 */
public interface AiProvider {

    /**
     * Naam van de provider voor weergave in de UI.
     */
    String getNaam();

    /**
     * Analyseert een afbeelding in JPEG-formaat en retourneert de classificatie.
     *
     * @param jpegAfbeelding Bytematrix van de afbeelding in JPEG-compressie
     * @param minBetrouwbaarheid Drempelwaarde voor betrouwbaarheid (0-100)
     * @return Het herkenningsresultaat
     * @throws Exception bij communicatiefouten of time-outs
     */
    HerkenningResultaat herkenAfval(byte[] jpegAfbeelding, int minBetrouwbaarheid) throws Exception;

    /**
     * Test of de achterliggende service bereikbaar is.
     */
    boolean testVerbinding() throws Exception;

    /**
     * Haalt een lijst op van beschikbare modellen.
     */
    List<String> getBeschikbareModellen() throws Exception;

    /**
     * Verwarmt het model voor op de achtergrond zodat de eerste scan sneller verloopt.
     */
    void warmUp() throws Exception;
}

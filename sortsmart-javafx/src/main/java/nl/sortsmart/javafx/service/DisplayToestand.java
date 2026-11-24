package nl.sortsmart.javafx.service;

import nl.sortsmart.javafx.model.Afvalfractie;

/**
 * Representeert de actieve toestand van het display op het sorteerstation.
 * Sluit 1-op-1 aan op de mockups (1 t/m 6).
 */
public enum DisplayToestand {
    KLAAR,            // Mockup 1: Gooi je afval in de bak
    SCANNEN,          // Mockup 2: Even kijken...
    HERKEND,          // Mockup 3: Dit is [Fractie] - Het bakje kantelt nu
    GELUKT,           // Mockup 4: Gelukt! [Fractie] is goed gescheiden (volledig groen)
    MEERDERE_ITEMS,   // Mockup 5: Eén item tegelijk, graag (Let op)
    VLOEISTOF,        // Mockup 6: Eerst even leegmaken (Let op)
    NIET_HERKEND      // Afval niet herkend / lage betrouwbaarheid
}

# SortSmart JavaFX – Beheerpaneel

Desktopapplicatie voor het beheer van SortSmart sorteerstations, gebouwd met JavaFX 21 en Maven.

---

## Vereisten

| Software       | Versie         |
|----------------|----------------|
| Java JDK       | 21 (x64 vereist voor JavaFX & OpenCV natives) |
| Apache Maven   | 3.9+ (of de meegeleverde `mvnw.cmd`) |
| MySQL          | 8.0+ of MariaDB 10.4+ |
| CLIP model     | `Xenova/clip-vit-base-patch32` (standaard, lokaal via ONNX) |
| Ollama         | 0.3+ lokaal (`http://127.0.0.1:11434`, model `gemma3:4b`, optioneel) |
| Scene Builder  | 21+ (optioneel, voor FXML bewerken) |

> **Opmerking voor Windows ARM64 (Qualcomm Snapdragon):**
> Omdat JavaFX en OpenCV native bibliotheken uitsluitend als x64 beschikbaar zijn op Maven Central, draait het project met de x64 JDK 21 (`C:\Users\bramv\AppData\Local\Programs\jdk-21-x64\jdk-21.0.13.1+2`) via de ingebouwde Windows Prism x64-emulatie. De meegeleverde `mvnw.cmd` selecteert deze JDK automatisch.

---

## CLIP Model downloaden (indien niet aanwezig)

De applicatie gebruikt standaard het snelle lokale CLIP-model (`clip-vit-base-patch32`). De bestanden staan al in de repository cache (`Sortsmart-main/.cache/models/Xenova/clip-vit-base-patch32`), maar kunnen ook handmatig worden gedownload vanaf Hugging Face:
- Downloadbron: [huggingface.co/Xenova/clip-vit-base-patch32](https://huggingface.co/Xenova/clip-vit-base-patch32)
- Benodigde bestanden:
  - `onnx/vision_model_quantized.onnx`
  - `onnx/text_model_quantized.onnx`
  - `tokenizer.json`
- Plaats ze in `~/.sortsmart/models/clip-vit-base-patch32/` of stel het pad in via het **Instellingen**-scherm.
- `.onnx`-bestanden staan in `.gitignore` en worden nooit gecommit naar versiebeheer.

---

## Database instellen

1. Zorg dat MySQL actief is.
2. Voer het schema en de voorbeelddata in:

```bash
mysql -u root < ../Sortsmart-main/database/schema.sql
mysql -u root < ../Sortsmart-main/database/seed.sql
```

---

## Configuratie

Kopieer het voorbeeldbestand en vul je eigen gegevens in:

```bash
cp src/main/resources/db.properties.example src/main/resources/db.properties
```

Bewerk daarna `src/main/resources/db.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/sortsmart?useSSL=false&serverTimezone=Europe/Amsterdam&allowPublicKeyRetrieval=true
db.gebruiker=root
db.wachtwoord=jouwwachtwoord
```

> **Let op:** `db.properties` staat in `.gitignore` en wordt nooit meegeleverd in versiebeheer.

---

## Starten

Met de meegeleverde Maven wrapper (Windows PowerShell / Command Prompt):

```bash
.\mvnw.cmd javafx:run
```

Of met een geïnstalleerde Maven:

```bash
mvn javafx:run
```

Of eerst compileren en daarna starten:

```bash
.\mvnw.cmd compile
.\mvnw.cmd javafx:run
```

---

## Project openen in Scene Builder

Scene Builder kan elk FXML-bestand direct openen. De FXML-bestanden staan in:

```
src/main/resources/nl/sortsmart/javafx/fxml/
```

| Bestand            | Scherm                   |
|--------------------|--------------------------|
| `MainView.fxml`    | Hoofdvenster + navigatie |
| `StationsdisplayView.fxml` | Stationsdisplay (schermen 1 t/m 6) |
| `DashboardView.fxml` | Dashboard (vakken per station) |
| `StationsView.fxml` | Stationsbeheer          |
| `MeldingenView.fxml` | Meldingenoverzicht      |
| `LegingView.fxml`  | Leging registreren       |
| `ScansView.fxml`   | Scanoverzicht            |
| `InstellingenView.fxml` | Instellingen (AI, model, camera) |
| `DbFoutView.fxml`  | Verbindingsfoutscherm    |

**Stap voor stap:**

1. Open Scene Builder.
2. Kies *File → Open…* (of sleep het `.fxml`-bestand erin).
3. Het `fx:controller`-attribuut verwijst naar de bijbehorende controllerklasse.
4. Zorg dat het Maven-project als bibliotheek is toegevoegd in Scene Builder (*Settings → JAR/FXML Manager*), anders geeft Scene Builder een waarschuwing over onbekende controllerklassen.

---

## Schermen

### 0 – Stationsdisplay
Display op het sorteerstation dat exact aansluit bij de mockups (1 t/m 6):
- Donker navy thema (`#111827`) met subtiele afgeronde kaarten en status-pills.
- **Live camera & bewegingsdetectie**: toont een live camerabeeld via OpenCV (MSMF-backend). Gebruikt de bewegingsdetector gebaseerd op SORTE (`live.ts`): analyseert kleine grijswaardenframes, leert de achtergrond en activeert de scan automatisch zodra een item stil ligt.
- **Handmatige bediening & annuleren**: bevat knoppen "Nu scannen" en "Foto kiezen" (`FileChooser`) als de camera niet actief is of voor handmatig testen, plus een knop "Annuleren" tijdens het wachten.
- **Klaar (1)**: toont "Gooi je afval in de bak", status-pill "Klaar" (groen) en pijl/bakje-icoon.
- **Scannen (2)**: toont "Even kijken..." met een **lopende teller** (bv. `Even kijken... 23 s`), geanimeerde voortgangsbalk en scan-icoon. Het model wordt op de achtergrond voorverwarmd met `keep_alive: '30m'` en beelden worden voor verzending automatisch geschaald naar max. 512 px (JPEG 0.8) voor snellere verwerking.
- **Herkend (3)**: toont "Dit is [Fractie]" in de fractiekleur (gehaald uit `afvalfractie`), bakje kantelmelding, icoon van de fractie en "Hierheen" markering met fractierand bij het betreffende vak onderaan.
- **Gelukt (4)**: volledig diepgroene achtergrond (`#15803D`), wit vinkje en onderaan de balk "Nog iets weg te gooien? / Leg het volgende item in het bakje". Keert na 3 seconden automatisch terug naar Klaar.
- **Meerdere items (5) & Vloeistof (6)**: gele "Let op" status-pill met duidelijke instructie en bijbehorend icoon.
- **Niet herkend**: nette status wanneer de betrouwbaarheid onder de drempelwaarde ligt.
- **Hardware-simulatie**: bovenaan bevindt zich een inklapbaar paneel met knoppen om scans per fractie, meerdere items of vloeistof te simuleren. Elke scan wordt met betrouwbaarheid en itemtype opgeslagen in de tabel `scan` en verhoogt bij een gesorteerd item direct de vulgraad in `vak`. De schermlogica is ondergebracht in `StationDisplayService.java` zodat hardware zoals een micro:bit hier direct op kan worden aangesloten.

### 1 – Dashboard
Per sorteerstation een kaart met alle vakken. Elk vak toont de afvalfractie, kleurcode en vulgraad als balk. Bij ≥ 80 % wordt de balk en het percentage rood weergegeven.

### 2 – Stations
Tabel met alle sorteerstations, campus en status. Voeg nieuwe stations toe via het formulier, of wijzig de status van een geselecteerd station.

### 3 – Meldingen
Overzicht van open meldingen. Handelt een geselecteerde melding af: vult `afgehandeld_op` automatisch in en koppelt de gekozen medewerker. Vinkje om ook afgehandelde meldingen te tonen. **Bedrijfsregel:** `VakVol`-meldingen hebben altijd een vaknummer.

### 4 – Leging registreren
Kies station, vak en medewerker. Vul de vulgraad vóór de leging en het gewicht in. Na opslaan wordt de vulgraad van het gekozen vak automatisch op 0 % gezet (via één databasetransactie).

### 5 – Scans (Geschiedenis)
Tabel met de meest recente scans inclusief het herkende itemtype en de betrouwbaarheid in procenten. Filter op station en/of resultaat (`Gesorteerd`, `MeerdereItems`, `VloeistofGedetecteerd`, `NietHerkend`). Stel het maximum aantal rijen in met de spinner.

### 6 – Instellingen
Configuratiescherm voor de herkenning:
- **Herkenningsmodus**: keuze tussen **"Snel (CLIP, lokaal)"** (standaard, herkent binnen 0,05 - 0,2 s) of **"Nauwkeurig (Ollama)"**.
- **CLIP-modelmap**: configureerbaar pad naar de modelbestanden (met fallback naar `~/.sortsmart/models/clip-vit-base-patch32`).
- **Minimale zekerheid**: apart instelbaar voor CLIP (standaard 40%) en Ollama (standaard 60%).
- **Ollama Server URL**: standaard `http://127.0.0.1:11434` en knop "Verbinding testen".
- **Camera-apparaat**: selecteren van webcam-index.
- **Automatisch scannen**: in-/uitschakelen bij bewegingsdetectie.
- Instellingen worden bewaard in `~/.sortsmart/config.properties` (buiten git).

---

## Projectstructuur

```
sortsmart-javafx/
├── pom.xml
├── mvnw.cmd                           ← Maven wrapper voor Windows
├── .mvn/                              ← Maven wrapper configuratie
├── src/main/
│   ├── java/nl/sortsmart/javafx/
│   │   ├── MainApp.java               ← Application entry point
│   │   ├── controller/                ← JavaFX controllers (één per scherm)
│   │   ├── dao/                       ← Data Access Objects (alleen PreparedStatements)
│   │   ├── herkenning/                ← Camera, SceneDetector, OllamaProvider en configuratie
│   │   ├── model/                     ← POJO-modelklassen
│   │   ├── service/                   ← Scherm- en hardwarelogica (StationDisplayService)
│   │   └── util/                      ← DatabaseConnector, FxmlLader, WaarschuwingUtil
│   └── resources/
│       ├── db.properties              ← Lokale config (staat in .gitignore)
│       ├── db.properties.example      ← Template voor nieuwe installaties
│       └── nl/sortsmart/javafx/
│           ├── fxml/                  ← FXML-schermen (te openen in Scene Builder)
│           └── css/style.css          ← Stylesheet (donker navy thema)
└── .gitignore
```

---

## Privacymaatregelen

- Databasegegevens staan uitsluitend in `db.properties` (buiten versiebeheer).
- Alle SQL-queries gebruiken **uitsluitend `PreparedStatement`** – SQL-injectie is daardoor niet mogelijk.
- Wachtwoorden worden nergens in de applicatie gelogd of weergegeven.
- **Privacy van camerabeelden**: videoframes worden **nooit** op schijf opgeslagen. Beelden blijven strikt in het werkgeheugen tijdens de analyse; alleen een SHA-256 hash van het beeld wordt gebruikt voor lokale caching.

-- =====================================================================
-- SortSmart – databaseschema (MySQL 8+ / MariaDB 10.4+)
-- Gebaseerd op het UML-klassendiagram.
--
-- Uitvoeren:  mysql -u root -p < database/schema.sql
-- LET OP: dit script verwijdert bestaande tabellen en maakt ze opnieuw aan.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS sortsmart
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE sortsmart;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS leervoorbeeld;
DROP TABLE IF EXISTS api_sleutel;
DROP TABLE IF EXISTS app_opslag;
DROP TABLE IF EXISTS leging;
DROP TABLE IF EXISTS melding;
DROP TABLE IF EXISTS medewerker;
DROP TABLE IF EXISTS scan;
DROP TABLE IF EXISTS vak;
DROP TABLE IF EXISTS itemtype;
DROP TABLE IF EXISTS afvalfractie;
DROP TABLE IF EXISTS sorteerstation;
DROP TABLE IF EXISTS campus;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Campus
-- ---------------------------------------------------------------------
CREATE TABLE campus (
  id        INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  naam      VARCHAR(100)  NOT NULL,
  adres     VARCHAR(150)  NOT NULL,
  postcode  VARCHAR(10)   NOT NULL,
  plaats    VARCHAR(100)  NOT NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- SorteerStation   (Campus 1 —heeft— 0..* SorteerStation)
-- ---------------------------------------------------------------------
CREATE TABLE sorteerstation (
  id                    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  campus_id             INT UNSIGNED  NOT NULL,
  serienummer           VARCHAR(50)   NOT NULL,
  locatie_omschrijving  VARCHAR(255)  NOT NULL,
  verdieping            INT           NULL,
  installatiedatum      DATE          NOT NULL,
  firmware_versie       VARCHAR(30)   NOT NULL,
  status                ENUM('Actief', 'Onderhoud', 'Storing', 'Inactief')
                                      NOT NULL DEFAULT 'Actief',
  PRIMARY KEY (id),
  UNIQUE KEY uq_sorteerstation_serienummer (serienummer),
  KEY idx_sorteerstation_campus (campus_id),
  CONSTRAINT fk_sorteerstation_campus
    FOREIGN KEY (campus_id) REFERENCES campus (id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Afvalfractie
-- ---------------------------------------------------------------------
CREATE TABLE afvalfractie (
  id           INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  naam         VARCHAR(50)   NOT NULL,
  kleurcode    VARCHAR(20)   NOT NULL,
  omschrijving VARCHAR(255)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_afvalfractie_naam (naam)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- ItemType   (ItemType 0..* —hoort bij— 1 Afvalfractie)
-- ---------------------------------------------------------------------
CREATE TABLE itemtype (
  id               INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  afvalfractie_id  INT UNSIGNED  NOT NULL,
  naam             VARCHAR(100)  NOT NULL,
  materiaal        VARCHAR(100)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_itemtype_naam (naam),
  KEY idx_itemtype_afvalfractie (afvalfractie_id),
  CONSTRAINT fk_itemtype_afvalfractie
    FOREIGN KEY (afvalfractie_id) REFERENCES afvalfractie (id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Vak   (SorteerStation 1 ◆—bestaat uit— 1..4 Vak, compositie)
--       (Vak 0..* —is voor— 1 Afvalfractie)
-- Compositie: een vak verdwijnt mee als het station verwijderd wordt.
-- Max. 4 vakken per station wordt afgedwongen door vaknummer 1–4 + uniek.
-- ---------------------------------------------------------------------
CREATE TABLE vak (
  id                  INT UNSIGNED      NOT NULL AUTO_INCREMENT,
  sorteerstation_id   INT UNSIGNED      NOT NULL,
  afvalfractie_id     INT UNSIGNED      NOT NULL,
  vaknummer           TINYINT UNSIGNED  NOT NULL,
  capaciteit_liter    INT UNSIGNED      NOT NULL,
  vulgraad_procent    TINYINT UNSIGNED  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_vak_station_nummer (sorteerstation_id, vaknummer),
  KEY idx_vak_afvalfractie (afvalfractie_id),
  CONSTRAINT chk_vak_vaknummer        CHECK (vaknummer BETWEEN 1 AND 4),
  CONSTRAINT chk_vak_capaciteit       CHECK (capaciteit_liter > 0),
  CONSTRAINT chk_vak_vulgraad         CHECK (vulgraad_procent BETWEEN 0 AND 100),
  CONSTRAINT fk_vak_sorteerstation
    FOREIGN KEY (sorteerstation_id) REFERENCES sorteerstation (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_vak_afvalfractie
    FOREIGN KEY (afvalfractie_id) REFERENCES afvalfractie (id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Scan   (SorteerStation 1 —registreert— 0..* Scan)
--        (Scan 0..* —herkent als— 0..1 ItemType)
-- ---------------------------------------------------------------------
CREATE TABLE scan (
  id                 BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  sorteerstation_id  INT UNSIGNED     NOT NULL,
  itemtype_id        INT UNSIGNED     NULL,
  tijdstip           DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  betrouwbaarheid    DECIMAL(5,4)     NULL,      -- 0.0000 – 1.0000
  resultaat          ENUM('Gesorteerd', 'MeerdereItems', 'VloeistofGedetecteerd', 'NietHerkend')
                                      NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scan_station_tijdstip (sorteerstation_id, tijdstip),
  KEY idx_scan_itemtype (itemtype_id),
  CONSTRAINT chk_scan_betrouwbaarheid
    CHECK (betrouwbaarheid IS NULL OR betrouwbaarheid BETWEEN 0 AND 1),
  CONSTRAINT fk_scan_sorteerstation
    FOREIGN KEY (sorteerstation_id) REFERENCES sorteerstation (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_scan_itemtype
    FOREIGN KEY (itemtype_id) REFERENCES itemtype (id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Medewerker
-- ---------------------------------------------------------------------
CREATE TABLE medewerker (
  id               INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  voornaam         VARCHAR(50)   NOT NULL,
  tussenvoegsel    VARCHAR(20)   NULL,
  achternaam       VARCHAR(80)   NOT NULL,
  email            VARCHAR(255)  NOT NULL,
  rol              ENUM('Facilitair', 'Schoonmaak', 'Beheerder') NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_medewerker_email (email)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Melding   (SorteerStation 1 —genereert— 0..* Melding)
--           (Melding 0..* —betreft— 0..1 Vak)
--           (Melding 0..* —handelt af— 0..1 Medewerker)
-- ---------------------------------------------------------------------
CREATE TABLE melding (
  id                 BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  sorteerstation_id  INT UNSIGNED     NOT NULL,
  vak_id             INT UNSIGNED     NULL,
  medewerker_id      INT UNSIGNED     NULL,
  tijdstip_gemeld    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  type               ENUM('VakVol', 'SensorStoring', 'ServoStoring', 'Offline') NOT NULL,
  omschrijving       VARCHAR(500)     NULL,
  status             ENUM('Open', 'InBehandeling', 'Afgehandeld') NOT NULL DEFAULT 'Open',
  afgehandeld_op     DATETIME         NULL,
  PRIMARY KEY (id),
  KEY idx_melding_station (sorteerstation_id),
  KEY idx_melding_vak (vak_id),
  KEY idx_melding_medewerker (medewerker_id),
  KEY idx_melding_status (status),
  CONSTRAINT chk_melding_afgehandeld
    CHECK (afgehandeld_op IS NULL OR afgehandeld_op >= tijdstip_gemeld),
  CONSTRAINT fk_melding_sorteerstation
    FOREIGN KEY (sorteerstation_id) REFERENCES sorteerstation (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_melding_vak
    FOREIGN KEY (vak_id) REFERENCES vak (id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_melding_medewerker
    FOREIGN KEY (medewerker_id) REFERENCES medewerker (id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Leging   (Vak 1 —wordt geleegd— 0..* Leging)
--          (Medewerker 1 —voert uit— 0..* Leging)
-- ---------------------------------------------------------------------
CREATE TABLE leging (
  id                       BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  vak_id                   INT UNSIGNED      NOT NULL,
  medewerker_id            INT UNSIGNED      NOT NULL,
  tijdstip                 DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
  vulgraad_voor_leging     TINYINT UNSIGNED  NOT NULL,
  gewicht_kg               DECIMAL(6,2)      NULL,
  PRIMARY KEY (id),
  KEY idx_leging_vak_tijdstip (vak_id, tijdstip),
  KEY idx_leging_medewerker (medewerker_id),
  CONSTRAINT chk_leging_vulgraad CHECK (vulgraad_voor_leging BETWEEN 0 AND 100),
  CONSTRAINT chk_leging_gewicht  CHECK (gewicht_kg IS NULL OR gewicht_kg >= 0),
  CONSTRAINT fk_leging_vak
    FOREIGN KEY (vak_id) REFERENCES vak (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_leging_medewerker
    FOREIGN KEY (medewerker_id) REFERENCES medewerker (id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

-- =====================================================================
-- Tabellen van de webapp zelf (de server maakt ze ook aan als ze ontbreken)
-- =====================================================================

-- Instellingen en classificatiegeschiedenis van de app, als gzip-JSON.
CREATE TABLE app_opslag (
  naam           VARCHAR(20)  NOT NULL,          -- 'settings' of 'history'
  inhoud         LONGBLOB     NOT NULL,
  bijgewerkt_op  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (naam)
) ENGINE = InnoDB;

-- API-sleutels van AI-providers. Alleen de server leest ze; de browser krijgt ze nooit terug.
CREATE TABLE api_sleutel (
  provider_id    VARCHAR(64)  NOT NULL,
  sleutel        TEXT         NOT NULL,
  origin         VARCHAR(255) NOT NULL,          -- sleutel mag alleen naar deze host
  opgeslagen_op  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (provider_id)
) ENGINE = InnoDB;

-- Wat de snelle modus leerde van correcties: een beeld-embedding (float32-reeks), nooit de foto zelf.
CREATE TABLE leervoorbeeld (
  id             INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  model          VARCHAR(100)  NOT NULL,
  categorie      VARCHAR(20)   NOT NULL,
  embedding      BLOB          NOT NULL,
  aangemaakt_op  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_leervoorbeeld_model (model, id)
) ENGINE = InnoDB;

-- =============================================================
-- SortSmart – relationeel implementatiemodel (MySQL 8.0+ / MariaDB 10.4+)
-- Team: Bram, Houssam, Mohmad, Diese, Daniyal
-- Ontwerp (klassendiagram, representatiemodel, implementatiemodel):
-- zie database/ontwerp.html
--
-- Uitvoeren:  mysql -u root < database/schema.sql
-- LET OP: de domeintabellen (campus t/m melding) worden verwijderd en
-- opnieuw aangemaakt. De tabellen van de webapp zelf (app_opslag,
-- api_sleutel, leervoorbeeld) blijven staan, dus instellingen,
-- geschiedenis en API-sleutels gaan niet verloren.
-- =============================================================

CREATE DATABASE IF NOT EXISTS sortsmart CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sortsmart;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS melding;
DROP TABLE IF EXISTS leging;
DROP TABLE IF EXISTS medewerker;
DROP TABLE IF EXISTS scan;
DROP TABLE IF EXISTS itemtype;
DROP TABLE IF EXISTS vak;
DROP TABLE IF EXISTS afvalfractie;
DROP TABLE IF EXISTS sorteerstation;
DROP TABLE IF EXISTS campus;
SET FOREIGN_KEY_CHECKS = 1;

-- Campus waar SortSmart-stations staan
CREATE TABLE campus (
    campus_id   INT          NOT NULL AUTO_INCREMENT,
    naam        VARCHAR(100) NOT NULL,
    adres       VARCHAR(150) NOT NULL,
    postcode    CHAR(6)      NOT NULL,
    plaats      VARCHAR(80)  NOT NULL,
    CONSTRAINT pk_campus PRIMARY KEY (campus_id),
    CONSTRAINT uq_campus_naam UNIQUE (naam),
    CONSTRAINT ck_campus_postcode CHECK (postcode REGEXP '^[1-9][0-9]{3}[A-Z]{2}$')
) ENGINE = InnoDB;

-- Eén SortSmart-module bovenop een afvalbak
CREATE TABLE sorteerstation (
    station_id           INT          NOT NULL AUTO_INCREMENT,
    campus_id            INT          NOT NULL,
    serienummer          VARCHAR(30)  NOT NULL,
    locatie_omschrijving VARCHAR(150) NOT NULL,
    verdieping           TINYINT      NULL,
    installatiedatum     DATE         NOT NULL,
    firmware_versie      VARCHAR(20)  NOT NULL,
    status               VARCHAR(10)  NOT NULL DEFAULT 'Actief',
    CONSTRAINT pk_sorteerstation PRIMARY KEY (station_id),
    CONSTRAINT uq_station_serienummer UNIQUE (serienummer),
    CONSTRAINT fk_station_campus FOREIGN KEY (campus_id)
        REFERENCES campus (campus_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_station_status CHECK (status IN ('Actief','Onderhoud','Storing','Inactief'))
) ENGINE = InnoDB;

-- Afvalstroom: Papier, PMD, Rest, Etensresten
CREATE TABLE afvalfractie (
    fractie_id   INT          NOT NULL AUTO_INCREMENT,
    naam         VARCHAR(30)  NOT NULL,
    kleurcode    CHAR(7)      NOT NULL,
    omschrijving VARCHAR(255) NULL,
    CONSTRAINT pk_afvalfractie PRIMARY KEY (fractie_id),
    CONSTRAINT uq_fractie_naam UNIQUE (naam),
    CONSTRAINT ck_fractie_kleur CHECK (kleurcode REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE = InnoDB;

-- Vak (zak/container) binnen een station – zwakke entiteit
CREATE TABLE vak (
    station_id        INT      NOT NULL,
    vaknummer         TINYINT  NOT NULL,
    fractie_id        INT      NOT NULL,
    capaciteit_liter  SMALLINT NOT NULL,
    vulgraad_procent  TINYINT  NOT NULL DEFAULT 0,
    CONSTRAINT pk_vak PRIMARY KEY (station_id, vaknummer),
    CONSTRAINT uq_vak_station_fractie UNIQUE (station_id, fractie_id),
    CONSTRAINT fk_vak_station FOREIGN KEY (station_id)
        REFERENCES sorteerstation (station_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_vak_fractie FOREIGN KEY (fractie_id)
        REFERENCES afvalfractie (fractie_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_vak_nummer    CHECK (vaknummer BETWEEN 1 AND 4),
    CONSTRAINT ck_vak_capaciteit CHECK (capaciteit_liter > 0),
    CONSTRAINT ck_vak_vulgraad  CHECK (vulgraad_procent BETWEEN 0 AND 100)
) ENGINE = InnoDB;

-- Soort item dat de sensor kan herkennen (blikje, koffiebeker, ...)
CREATE TABLE itemtype (
    itemtype_id INT         NOT NULL AUTO_INCREMENT,
    fractie_id  INT         NOT NULL,
    naam        VARCHAR(60) NOT NULL,
    materiaal   VARCHAR(40) NOT NULL,
    CONSTRAINT pk_itemtype PRIMARY KEY (itemtype_id),
    CONSTRAINT uq_itemtype_naam UNIQUE (naam),
    CONSTRAINT fk_itemtype_fractie FOREIGN KEY (fractie_id)
        REFERENCES afvalfractie (fractie_id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

-- Elke keer dat iemand iets boven het scanplatform houdt
CREATE TABLE scan (
    scan_id          BIGINT       NOT NULL AUTO_INCREMENT,
    station_id       INT          NOT NULL,
    itemtype_id      INT          NULL,
    tijdstip         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    betrouwbaarheid  DECIMAL(5,2) NULL,
    resultaat        VARCHAR(25)  NOT NULL,
    CONSTRAINT pk_scan PRIMARY KEY (scan_id),
    CONSTRAINT fk_scan_station FOREIGN KEY (station_id)
        REFERENCES sorteerstation (station_id) ON UPDATE CASCADE ON DELETE CASCADE,
    -- geen ON DELETE/UPDATE-actie: MySQL staat dat niet toe op een kolom in een CHECK
    CONSTRAINT fk_scan_itemtype FOREIGN KEY (itemtype_id)
        REFERENCES itemtype (itemtype_id),
    CONSTRAINT ck_scan_resultaat CHECK (resultaat IN
        ('Gesorteerd','MeerdereItems','VloeistofGedetecteerd','NietHerkend')),
    CONSTRAINT ck_scan_betrouwbaarheid CHECK (betrouwbaarheid BETWEEN 0 AND 100),
    -- een gesorteerd item moet herkend zijn
    CONSTRAINT ck_scan_gesorteerd CHECK (resultaat <> 'Gesorteerd' OR itemtype_id IS NOT NULL)
) ENGINE = InnoDB;

-- Facilitair-, schoonmaak- of beheermedewerker
CREATE TABLE medewerker (
    medewerker_id INT          NOT NULL AUTO_INCREMENT,
    voornaam      VARCHAR(50)  NOT NULL,
    tussenvoegsel VARCHAR(15)  NULL,
    achternaam    VARCHAR(80)  NOT NULL,
    email         VARCHAR(150) NOT NULL,
    rol           VARCHAR(12)  NOT NULL,
    CONSTRAINT pk_medewerker PRIMARY KEY (medewerker_id),
    CONSTRAINT uq_medewerker_email UNIQUE (email),
    CONSTRAINT ck_medewerker_rol CHECK (rol IN ('Facilitair','Schoonmaak','Beheerder'))
) ENGINE = InnoDB;

-- Het legen van een vak door een medewerker
CREATE TABLE leging (
    leging_id            INT          NOT NULL AUTO_INCREMENT,
    station_id           INT          NOT NULL,
    vaknummer            TINYINT      NOT NULL,
    medewerker_id        INT          NOT NULL,
    tijdstip             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    vulgraad_voor_leging TINYINT      NOT NULL,
    gewicht_kg           DECIMAL(5,2) NULL,
    CONSTRAINT pk_leging PRIMARY KEY (leging_id),
    CONSTRAINT fk_leging_vak FOREIGN KEY (station_id, vaknummer)
        REFERENCES vak (station_id, vaknummer) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_leging_medewerker FOREIGN KEY (medewerker_id)
        REFERENCES medewerker (medewerker_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_leging_vulgraad CHECK (vulgraad_voor_leging BETWEEN 0 AND 100),
    CONSTRAINT ck_leging_gewicht  CHECK (gewicht_kg >= 0)
) ENGINE = InnoDB;

-- Melding van een station (vak vol, storing, offline)
CREATE TABLE melding (
    melding_id           INT          NOT NULL AUTO_INCREMENT,
    station_id           INT          NOT NULL,
    vaknummer            TINYINT      NULL,
    afgehandeld_door     INT          NULL,
    type                 VARCHAR(15)  NOT NULL,
    omschrijving         VARCHAR(255) NULL,
    tijdstip_gemeld      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status               VARCHAR(15)  NOT NULL DEFAULT 'Open',
    afgehandeld_op       DATETIME     NULL,
    CONSTRAINT pk_melding PRIMARY KEY (melding_id),
    CONSTRAINT fk_melding_station FOREIGN KEY (station_id)
        REFERENCES sorteerstation (station_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_melding_vak FOREIGN KEY (station_id, vaknummer)
        REFERENCES vak (station_id, vaknummer) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_melding_medewerker FOREIGN KEY (afgehandeld_door)
        REFERENCES medewerker (medewerker_id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_melding_type   CHECK (type IN ('VakVol','SensorStoring','ServoStoring','Offline')),
    CONSTRAINT ck_melding_status CHECK (status IN ('Open','InBehandeling','Afgehandeld')),
    -- een afgehandelde melding heeft een tijdstip van afhandeling
    CONSTRAINT ck_melding_afgehandeld CHECK (status <> 'Afgehandeld' OR afgehandeld_op IS NOT NULL)
    -- bedrijfsregel (in de applicatie): een melding 'VakVol' heeft altijd een vaknummer
) ENGINE = InnoDB;

-- =============================================================
-- Tabellen van de webapp zelf. Ze worden NIET verwijderd, zodat
-- instellingen, geschiedenis en API-sleutels bewaard blijven.
-- De server maakt ze ook zelf aan als ze ontbreken.
-- =============================================================

-- Instellingen en classificatiegeschiedenis van de app, als gzip-JSON.
CREATE TABLE IF NOT EXISTS app_opslag (
    naam           VARCHAR(20)  NOT NULL,          -- 'settings' of 'history'
    inhoud         LONGBLOB     NOT NULL,
    bijgewerkt_op  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (naam)
) ENGINE = InnoDB;

-- API-sleutels van AI-providers. Alleen de server leest ze; de browser krijgt ze nooit terug.
CREATE TABLE IF NOT EXISTS api_sleutel (
    provider_id    VARCHAR(64)  NOT NULL,
    sleutel        TEXT         NOT NULL,
    origin         VARCHAR(255) NOT NULL,          -- sleutel mag alleen naar deze host
    opgeslagen_op  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (provider_id)
) ENGINE = InnoDB;

-- Wat de snelle modus leerde van correcties: een beeld-embedding (float32-reeks), nooit de foto zelf.
CREATE TABLE IF NOT EXISTS leervoorbeeld (
    id             INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    model          VARCHAR(100)  NOT NULL,
    categorie      VARCHAR(20)   NOT NULL,
    embedding      BLOB          NOT NULL,
    aangemaakt_op  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_leervoorbeeld_model (model, id)
) ENGINE = InnoDB;

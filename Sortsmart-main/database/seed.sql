-- =====================================================================
-- SortSmart – voorbeelddata (optioneel, na schema.sql uitvoeren)
--
-- Uitvoeren:  mysql -u root -p < database/seed.sql
-- =====================================================================

USE sortsmart;

INSERT INTO campus (id, naam, adres, postcode, plaats) VALUES
  (1, 'Campus Centrum', 'Stationsplein 1', '1012 AB', 'Amsterdam'),
  (2, 'Campus Noord',   'Kennislaan 25',   '1031 KM', 'Amsterdam');

INSERT INTO afvalfractie (id, naam, kleurcode, omschrijving) VALUES
  (1, 'Restafval', '#6B7280', 'Alles wat niet in een andere fractie past'),
  (2, 'PMD',       '#F97316', 'Plastic, metalen verpakkingen en drankenkartons'),
  (3, 'Papier',    '#3B82F6', 'Papier en karton'),
  (4, 'GFT',       '#22C55E', 'Groente-, fruit- en etensresten'),
  (5, 'Statiegeld', '#FF6AC1', 'Plastic drankflessen en blikjes met statiegeld: inleveren, niet weggooien');

INSERT INTO itemtype (id, afvalfractie_id, naam, materiaal) VALUES
  (1, 5, 'PET-fles',          'Plastic (PET)'),
  (2, 5, 'Blikje',            'Aluminium'),
  (3, 2, 'Drinkpak',          'Drankkarton'),
  (4, 3, 'Koffiebeker',       'Karton'),
  (5, 3, 'Krant',             'Papier'),
  (6, 4, 'Bananenschil',      'Organisch'),
  (7, 4, 'Appelklokhuis',     'Organisch'),
  (8, 1, 'Chipszak',          'Multilaags folie');

INSERT INTO sorteerstation
  (id, campus_id, serienummer, locatie_omschrijving, verdieping, installatiedatum, firmware_versie, status) VALUES
  (1, 1, 'SS-2026-0001', 'Kantine, bij de uitgang',       0,    '2026-02-01', '1.4.2', 'Actief'),
  (2, 1, 'SS-2026-0002', 'Gang naast collegezaal 2.12',   2,    '2026-02-01', '1.4.2', 'Storing'),
  (3, 2, 'SS-2026-0003', 'Hoofdentree',                   NULL, '2026-05-15', '1.5.0', 'Actief');

INSERT INTO vak (id, sorteerstation_id, afvalfractie_id, vaknummer, capaciteit_liter, vulgraad_procent) VALUES
  (1,  1, 1, 1, 60, 35),
  (2,  1, 2, 2, 60, 92),
  (3,  1, 3, 3, 60, 48),
  (4,  1, 4, 4, 40, 20),
  (5,  2, 1, 1, 60, 10),
  (6,  2, 2, 2, 60, 15),
  (7,  2, 3, 3, 60, 5),
  (8,  3, 1, 1, 80, 60),
  (9,  3, 2, 2, 80, 55);

INSERT INTO medewerker (id, voornaam, tussenvoegsel, achternaam, email, rol) VALUES
  (1, 'Sanne', NULL,    'Jansen',  'sanne.jansen@example.com',  'Facilitair'),
  (2, 'Mohamed', NULL,  'El Amrani', 'mohamed.elamrani@example.com', 'Schoonmaak'),
  (3, 'Lotte', 'van',   'Dijk',    'lotte.vandijk@example.com', 'Beheerder');

INSERT INTO scan (sorteerstation_id, itemtype_id, tijdstip, betrouwbaarheid, resultaat) VALUES
  (1, 1,    '2026-11-09 09:12:04', 0.9731, 'Gesorteerd'),
  (1, 4,    '2026-11-09 09:15:40', 0.8820, 'Gesorteerd'),
  (1, NULL, '2026-11-09 09:17:22', 0.4100, 'NietHerkend'),
  (1, 2,    '2026-11-09 10:02:11', 0.9902, 'Gesorteerd'),
  (1, NULL, '2026-11-09 10:05:55', NULL,   'MeerdereItems'),
  (3, 3,    '2026-11-09 12:30:00', 0.7650, 'VloeistofGedetecteerd'),
  (3, 6,    '2026-11-09 12:31:18', 0.9455, 'Gesorteerd');

INSERT INTO melding
  (sorteerstation_id, vak_id, medewerker_id, tijdstip_gemeld, type, omschrijving, status, afgehandeld_op) VALUES
  (1, 2,    NULL, '2026-11-09 10:06:00', 'VakVol',        'PMD-vak boven 90%',              'Open',          NULL),
  (2, NULL, 3,    '2026-11-08 14:20:00', 'SensorStoring', 'Camera geeft geen beeld',        'InBehandeling', NULL),
  (1, 3,    2,    '2026-11-07 16:45:00', 'VakVol',        NULL,                             'Afgehandeld',   '2026-11-07 17:10:00');

INSERT INTO leging (vak_id, medewerker_id, tijdstip, vulgraad_voor_leging, gewicht_kg) VALUES
  (3, 2, '2026-11-07 17:10:00', 95, 7.40),
  (1, 2, '2026-11-08 08:30:00', 80, 11.25),
  (8, 2, '2026-11-08 09:00:00', 70, NULL);

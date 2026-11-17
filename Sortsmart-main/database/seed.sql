-- =============================================================
-- SortSmart – voorbeelddata (optioneel, na schema.sql uitvoeren)
--
-- Uitvoeren:  mysql -u root < database/seed.sql
-- =============================================================

USE sortsmart;

INSERT INTO campus (naam, adres, postcode, plaats) VALUES
 ('Campus Den Haag', 'Johanna Westerdijkplein 75', '2521EN', 'Den Haag');

INSERT INTO afvalfractie (naam, kleurcode, omschrijving) VALUES
 ('Papier',      '#1E63B5', 'Papier en karton, droog en schoon'),
 ('PMD',         '#F28C00', 'Plastic verpakkingen, metalen blik en drinkpakken'),
 ('Rest',        '#5A5A5A', 'Alles wat niet gerecycled kan worden'),
 ('Etensresten', '#3C8D2F', 'Groente-, fruit- en etensresten');

INSERT INTO sorteerstation (campus_id, serienummer, locatie_omschrijving, verdieping, installatiedatum, firmware_versie) VALUES
 (1, 'SS-2026-0001', 'Kantine, naast de koffieautomaat', 0, '2026-11-02', '0.3.1');

INSERT INTO vak (station_id, vaknummer, fractie_id, capaciteit_liter, vulgraad_procent) VALUES
 (1, 1, 1, 60, 35), (1, 2, 2, 60, 82), (1, 3, 3, 60, 20), (1, 4, 4, 30, 10);

INSERT INTO itemtype (fractie_id, naam, materiaal) VALUES
 (2, 'Frisdrankblikje', 'Aluminium'),
 (2, 'PET-flesje', 'Kunststof'),
 (2, 'Drinkpak', 'Karton/laminaat'),
 (1, 'Kartonnen broodjesdoos', 'Karton'),
 (3, 'Koffiebeker', 'Papier met PE-coating'),
 (4, 'Appelklokhuis', 'Organisch');

INSERT INTO medewerker (voornaam, tussenvoegsel, achternaam, email, rol) VALUES
 ('Sanne', NULL, 'Jansen', 's.jansen@campus.example', 'Facilitair'),
 ('Ahmed', 'el', 'Amrani', 'a.elamrani@campus.example', 'Schoonmaak');

INSERT INTO scan (station_id, itemtype_id, tijdstip, betrouwbaarheid, resultaat) VALUES
 (1, 1,    '2026-11-10 10:02:11', 97.40, 'Gesorteerd'),
 (1, 3,    '2026-11-10 10:02:45', 88.10, 'VloeistofGedetecteerd'),
 (1, NULL, '2026-11-10 10:03:30', NULL,  'MeerdereItems'),
 (1, 5,    '2026-11-10 10:04:02', 91.25, 'Gesorteerd');

INSERT INTO melding (station_id, vaknummer, type, omschrijving, tijdstip_gemeld) VALUES
 (1, 2, 'VakVol', 'PMD-vak boven 80%', '2026-11-10 10:05:00');

INSERT INTO leging (station_id, vaknummer, medewerker_id, tijdstip, vulgraad_voor_leging, gewicht_kg) VALUES
 (1, 2, 2, '2026-11-10 11:15:00', 84, 3.40);

UPDATE melding SET status = 'Afgehandeld', afgehandeld_door = 2,
       afgehandeld_op = '2026-11-10 11:15:00' WHERE melding_id = 1;
UPDATE vak SET vulgraad_procent = 0 WHERE station_id = 1 AND vaknummer = 2;

-- Voorbeeldquery: scheidingsresultaat per fractie
-- SELECT f.naam, COUNT(*) AS aantal
-- FROM scan s JOIN itemtype i ON i.itemtype_id = s.itemtype_id
--             JOIN afvalfractie f ON f.fractie_id = i.fractie_id
-- WHERE s.resultaat = 'Gesorteerd'
-- GROUP BY f.naam;

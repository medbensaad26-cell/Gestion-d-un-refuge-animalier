
CREATE DATABASE IF NOT EXISTS refuge_animalier
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE refuge_animalier;



CREATE TABLE IF NOT EXISTS admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS animal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    espece VARCHAR(100) NOT NULL,
    race VARCHAR(100),
    age INT NOT NULL,
    sexe VARCHAR(20) NOT NULL,
    etat_sante VARCHAR(255),
    statut VARCHAR(50) NOT NULL,
    admin_id BIGINT NOT NULL,

    CONSTRAINT fk_animal_admin
    FOREIGN KEY (admin_id)
    REFERENCES admin(id)
);


CREATE TABLE IF NOT EXISTS dossier_medical (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               vaccins TEXT,
                                               traitements TEXT,
                                               observations TEXT,
                                               date_mise_a_jour DATE,
                                               animal_id BIGINT NOT NULL UNIQUE,

                                               CONSTRAINT fk_dossier_animal
                                               FOREIGN KEY (animal_id)
    REFERENCES animal(id)
    ON DELETE CASCADE
    );


CREATE TABLE IF NOT EXISTS annonce (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       titre VARCHAR(150) NOT NULL,
    description TEXT,
    date_publication DATE NOT NULL,
    statut VARCHAR(50) NOT NULL,
    admin_id BIGINT NOT NULL,
    animal_id BIGINT NOT NULL,

    CONSTRAINT fk_annonce_admin
    FOREIGN KEY (admin_id)
    REFERENCES admin(id),

    CONSTRAINT fk_annonce_animal
    FOREIGN KEY (animal_id)
    REFERENCES animal(id)
    );



CREATE TABLE IF NOT EXISTS demande (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       nom_demandeur VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    telephone VARCHAR(30),
    date_demande DATE NOT NULL,
    statut ENUM('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE')
    NOT NULL DEFAULT 'EN_ATTENTE',
    animal_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,

    CONSTRAINT fk_demande_animal
    FOREIGN KEY (animal_id)
    REFERENCES animal(id),

    CONSTRAINT fk_demande_admin
    FOREIGN KEY (admin_id)
    REFERENCES admin(id)
    );


INSERT INTO admin (nom, email, mot_de_passe)
VALUES (
           'Administrateur Test',
           'admin.test@example.com',
           'COMPTE_TEST_NON_ACTIF'
       );


INSERT INTO animal
(nom, espece, race, age, sexe, etat_sante, statut, admin_id)
VALUES
    ('Rex', 'Chien', 'Berger Allemand', 3, 'Male',
     'Bonne sante', 'DISPONIBLE', 1),

    ('Mimi', 'Chat', 'Europeen', 2, 'Femelle',
     'Bonne sante', 'DISPONIBLE', 1),

    ('Luna', 'Chien', 'Labrador', 1, 'Femelle',
     'En traitement', 'EN_SOIN', 1);





INSERT INTO dossier_medical
(vaccins, traitements, observations, date_mise_a_jour, animal_id)
VALUES
    ('Rage, DHPP', 'Vermifuge',
     'Rex est en bonne sante', '2026-11-07', 1),

    ('Typhus, Coryza', 'Antiparasitaire',
     'Mimi est en bonne sante', '2026-11-07', 2),

    ('Rage', 'Traitement antibiotique',
     'Luna est sous surveillance', '2026-11-07', 3);





INSERT INTO annonce
(titre, description, date_publication, statut, admin_id, animal_id)
VALUES
    ('Adoptez Rex',
     'Rex est un chien affectueux de 3 ans.',
     '2026-11-07', 'PUBLIEE', 1, 1),

    ('Adoptez Mimi',
     'Mimi est une chatte calme de 2 ans.',
     '2026-11-07', 'PUBLIEE', 1, 2);



INSERT INTO demande
(nom_demandeur, email, telephone, date_demande,
 statut, animal_id, admin_id)
VALUES
    ('Sarra Ben Ali', 'sarra@example.com', '20123456',
     '2026-11-07', 'EN_ATTENTE', 1, 1),

    ('Youssef Trabelsi', 'youssef@example.com', '55123456',
     '2026-11-07', 'EN_ATTENTE', 2, 1);



-- Requetes de verification

SELECT * FROM animal;


SELECT id, nom, espece, age, statut
FROM animal
WHERE statut = 'DISPONIBLE';


SELECT id, nom_demandeur, statut
FROM demande
WHERE statut = 'EN_ATTENTE';

-- Fresh-install schema only. No database selection, credentials, accounts or sample data.
-- Run through the guarded FreshInstallation console on an EMPTY new database.
-- This is not a migration. Never import into an existing installation.
-- Repeated imports fail instead of migrating. All tables are explicitly transactional.

CREATE TABLE admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE animal (
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
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE dossier_medical (
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
    ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE annonce (
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
    ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;



CREATE TABLE demande (
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
    ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

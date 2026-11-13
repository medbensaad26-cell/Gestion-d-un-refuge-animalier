package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.dao.AnimalDAO;
import com.enicarthage.refugeanimalier.dao.AdminDAO;
import com.enicarthage.refugeanimalier.dao.DemandeDAO;
import com.enicarthage.refugeanimalier.dao.DossierMedicalDAO;
import com.enicarthage.refugeanimalier.model.Animal;
import com.enicarthage.refugeanimalier.model.Demande;
import com.enicarthage.refugeanimalier.model.StatutDemande;
import com.enicarthage.refugeanimalier.service.DemandeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class DiagramAlignmentTest extends MysqlTestSupport {
    @AfterEach
    void restoreIsolatedSchema() throws Exception {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS reject_medical_fixture");
            statement.execute("DROP PROCEDURE IF EXISTS refuge_step1_align");
            statement.executeUpdate("UPDATE demande SET admin_id = 1 WHERE admin_id IS NULL");
            statement.execute("ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NOT NULL");
        }
    }

    @Test
    void animalAndBlankMedicalRecordAreCreatedTogetherAndDeletedTogether() throws Exception {
        Animal animal = newAnimal();
        new AnimalDAO().create(animal);
        assertNotNull(animal.getId());
        assertEquals(1, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id = ?", animal.getId()));
        var medical = new DossierMedicalDAO().findByAnimalId(animal.getId());
        assertNull(medical.getVaccins());
        assertNull(medical.getTraitements());
        assertNull(medical.getObservations());
        assertNotNull(medical.getDateMiseAJour());
        assertThrows(IllegalStateException.class,
                () -> new DossierMedicalDAO().deleteByAnimalId(animal.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id = ?", animal.getId()));
        new AnimalDAO().delete(animal.getId());
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id = ?", animal.getId()));
    }

    @Test
    void failureToCreateMedicalRecordRollsBackAnimalCreation() throws Exception {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_medical_fixture BEFORE INSERT ON dossier_medical "
                    + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'isolated fixture failure'");
        }
        Animal animal = newAnimal();
        assertThrows(SQLException.class, () -> new AnimalDAO().create(animal));
        assertNull(animal.getId());
        assertEquals(0, count("SELECT COUNT(*) FROM animal"));
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical"));
    }

    @Test
    void serviceFindsSoleAdministratorWithoutAssumingIdOne() throws Exception {
        useSingleAdministrator();
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE admin SET id = 17 WHERE id = 1");
        }
        Animal animal = newAnimal();
        animal.setAdminId(17L);
        new AnimalDAO().create(animal);
        Demande request = newRequest(animal.getId());
        request.setAdminId(1L);
        new DemandeService().ajouterDemande(request);
        assertEquals(17, request.getAdminId());
        assertEquals("17", scalar("SELECT admin_id FROM demande WHERE animal_id = ?", animal.getId()));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE animal_id = ?", animal.getId()));
    }

    @Test
    void noAdministratorIsReportedWithoutSelectingOrCreatingAnAccount() throws Exception {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM admin");
            assertThrows(IllegalStateException.class, () -> new AdminDAO().findSoleAdministratorId(connection));
        }
        assertEquals(0, count("SELECT COUNT(*) FROM admin"));
        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
    }

    @Test
    void multipleAccountsAreRejectedWithoutCachingAnEarlierSingleSelection() throws Exception {
        useSingleAdministrator();
        long animalId = animal("DISPONIBLE");
        new DemandeService().ajouterDemande(newRequest(animalId));
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO admin (id, nom, email, mot_de_passe) "
                    + "SELECT 2, 'Second fixture', 'second@example.test', mot_de_passe FROM admin WHERE id = 1");
        }
        assertThrows(IllegalStateException.class, () -> new DemandeService().ajouterDemande(newRequest(animalId)));
        assertEquals(1, count("SELECT COUNT(*) FROM demande"));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE animal_id = ?", animalId));
    }

    @Test
    void directDaoAlsoResolvesSoleAccountInsteadOfTrustingSuppliedIdentity() throws Exception {
        useSingleAdministrator();
        long animalId = animal("DISPONIBLE");
        Demande request = newRequest(animalId);
        request.setAdminId(999999L);
        new DemandeDAO().create(request);
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE animal_id = ?", animalId));
        assertEquals(1, request.getAdminId());
    }

    @Test
    void actualMigrationPreservesExistingMedicalDetailsAndDecisionsAndIsRepeatable() throws Exception {
        useSingleAdministrator();
        long first = animal("DISPONIBLE");
        long second = animal("DISPONIBLE");
        long assigned = request(first);
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL");
            statement.executeUpdate("UPDATE demande SET statut = 'REFUSEE' WHERE id = " + assigned);
            statement.executeUpdate("INSERT INTO dossier_medical (animal_id, vaccins, observations, date_mise_a_jour) "
                    + "VALUES (" + first + ", 'Existing vaccine', 'Existing observation', '2026-01-01')");
            statement.executeUpdate("INSERT INTO demande (animal_id, nom_demandeur, email, date_demande) "
                    + "VALUES (" + second + ", 'Legacy visitor', 'visitor@example.test', '2026-01-02')");
            executeMigration(connection);
            executeMigration(connection);
        }
        assertEquals(2, count("SELECT COUNT(*) FROM animal"));
        assertEquals(2, count("SELECT COUNT(*) FROM demande"));
        assertEquals(2, count("SELECT COUNT(*) FROM dossier_medical"));
        assertEquals("Existing vaccine", scalar("SELECT vaccins FROM dossier_medical WHERE animal_id = ?", first));
        assertEquals("Existing observation", scalar("SELECT observations FROM dossier_medical WHERE animal_id = ?", first));
        assertEquals("2026-01-01", scalar("SELECT date_mise_a_jour FROM dossier_medical WHERE animal_id = ?", first));
        assertNull(scalar("SELECT vaccins FROM dossier_medical WHERE animal_id = ?", second));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", assigned));
        assertEquals("REFUSEE", scalar("SELECT statut FROM demande WHERE id = ?", assigned));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE animal_id = ?", second));
        assertEquals("NO", scalar("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demande' AND COLUMN_NAME = 'admin_id'"));
    }

    @Test
    void migrationRejectsMultipleAdministratorsBeforeChangingAnyRows() throws Exception {
        long id = animal("DISPONIBLE");
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL");
            assertThrows(SQLException.class, () -> executeMigration(connection));
        }
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id = ?", id));
        assertEquals("YES", scalar("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demande' AND COLUMN_NAME = 'admin_id'"));
    }

    @Test
    void migrationRefusesToInventAdministratorForDecidedHistoricalRequest() throws Exception {
        useSingleAdministrator();
        long id = animal("DISPONIBLE");
        long request = request(id);
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL");
            statement.executeUpdate("UPDATE demande SET admin_id = NULL, statut = 'REFUSEE' WHERE id = " + request);
            assertThrows(SQLException.class, () -> executeMigration(connection));
        }
        assertNull(scalar("SELECT admin_id FROM demande WHERE id = ?", request));
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical"));
    }

    @Test
    void migrationRejectsMissingAdministratorBeforeChangingSchema() throws Exception {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM admin");
            statement.execute("ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL");
            assertThrows(SQLException.class, () -> executeMigration(connection));
        }
        assertEquals(0, count("SELECT COUNT(*) FROM admin"));
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical"));
        assertEquals("YES", scalar("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demande' AND COLUMN_NAME = 'admin_id'"));
    }

    private void executeMigration(Connection connection) throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/sql/migrations/004_step1_diagram_alignment.sql")); var statement = connection.createStatement()) {
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)^\\s*--.*$", "")
                    .replaceAll("(?m)^DELIMITER.*$", "");
            for (String sql : source.split("\\$\\$")) {
                if (!sql.isBlank()) statement.execute(sql.strip());
            }
        }
    }

    private Animal newAnimal() {
        return new Animal(null, "Diagram animal", "Chat", null, 2, "Femelle", null, "DISPONIBLE", 1L);
    }

    private Demande newRequest(long animalId) {
        return new Demande(null, "Visitor", "visitor@example.test", "0102030405", LocalDate.of(2026, 11, 8),
                StatutDemande.EN_ATTENTE, animalId, null);
    }
}

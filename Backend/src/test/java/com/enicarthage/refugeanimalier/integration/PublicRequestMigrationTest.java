package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.dao.DemandeDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PublicRequestMigrationTest extends MysqlTestSupport {
    @AfterEach
    void restoreCurrentSchemaAfterHistoricalMigrationChecks() throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE demande SET admin_id = 1 WHERE admin_id IS NULL");
            statement.execute("ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NOT NULL");
        }
    }

    @Test
    void approvedMigrationPreservesExistingRecordsAndForeignKey() throws Exception {
        long firstAnimal = animal("DISPONIBLE");
        long secondAnimal = animal("EN_SOIN");
        long existingRequest = request(firstAnimal);
        request(secondAnimal);

        try (Connection connection = connection(); var statement = connection.createStatement()) {
            assertTrue(connection.getCatalog().matches("refuge_animalier_test_[a-f0-9]{32}"));
            // Simulate the previous installation only inside this owned disposable database.
            statement.execute("ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NOT NULL");
        }
        String animalsBefore = fingerprint("animal");
        String requestsBefore = fingerprint("demande");
        assertEquals("NO", scalar("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demande' AND COLUMN_NAME = 'admin_id'"));

        String migration = resource("migrations/003A_demande_admin_nullable.sql")
                .replaceAll("(?m)^\\s*--.*$", "").strip();
        assertEquals("ALTER TABLE demande\n    MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL;",
                migration.replace("\r\n", "\n"));
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute(migration);
        }

        assertEquals(animalsBefore, fingerprint("animal"));
        assertEquals(requestsBefore, fingerprint("demande"));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", existingRequest));
        assertEquals("YES", scalar("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demande' AND COLUMN_NAME = 'admin_id'"));
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'demande' "
                + "AND CONSTRAINT_NAME = 'fk_demande_admin' AND REFERENCED_TABLE_NAME = 'admin'"));

        insertWithoutAdministrator(firstAnimal);
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE admin_id IS NULL"));
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "UPDATE demande SET admin_id = ? WHERE id = ?")) {
            statement.setLong(1, 99999999);
            statement.setLong(2, existingRequest);
            assertThrows(SQLException.class, statement::executeUpdate);
        }
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", existingRequest));
    }

    @Test
    void freshSchemaRequiresRealAdministratorAndDaoReadsAssignedRequests() throws Exception {
        long animalId = animal("DISPONIBLE");
        assertThrows(SQLException.class, () -> insertWithoutAdministrator(animalId));
        long requestId = request(animalId);

        assertEquals(1, new DemandeDAO().findById(requestId).getAdminId());
        assertEquals(1, new DemandeDAO().findAll().getFirst().getAdminId());
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals("NO", scalar("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demande' AND COLUMN_NAME = 'admin_id'"));
    }

    @Test
    void documentedAdministratorJoinRetainsAnonymousRequests() throws Exception {
        // Exercise the old nullable installation before Step 1 backfill.
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL");
        }
        long animalId = animal("DISPONIBLE");
        long assigned = request(animalId);
        insertWithoutAdministrator(animalId);
        long unassigned = count("SELECT id FROM demande WHERE animal_id = ? AND admin_id IS NULL", animalId);
        String script = resource("verification.sql").replaceAll("(?m)^\\s*--.*$", "");
        String join = java.util.Arrays.stream(script.split(";"))
                .map(String::strip).filter(sql -> sql.contains("d.id AS demande_id")
                        && sql.contains("ad.nom AS admin_nom")).findFirst().orElseThrow();
        Set<Long> ids = new HashSet<>();
        try (Connection connection = connection(); var statement = connection.createStatement();
             var result = statement.executeQuery(join)) {
            while (result.next()) {
                long id = result.getLong("demande_id");
                ids.add(id);
                if (id == unassigned) assertNull(result.getObject("admin_id"));
            }
        }
        assertEquals(Set.of(assigned, unassigned), ids);
    }

    private void insertWithoutAdministrator(long animalId) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "INSERT INTO demande (nom_demandeur, email, telephone, date_demande, animal_id) "
                        + "VALUES ('Visitor fixture', 'visitor@example.test', '0102030405', CURRENT_DATE, ?)")) {
            statement.setLong(1, animalId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private String fingerprint(String table) throws Exception {
        if (!Set.of("animal", "demande").contains(table)) throw new IllegalArgumentException();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Connection connection = connection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
            while (result.next()) {
                for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                    String value = result.getString(column);
                    digest.update((value == null ? "<NULL>" : value).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String resource(String name) throws Exception {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream("/sql/" + name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

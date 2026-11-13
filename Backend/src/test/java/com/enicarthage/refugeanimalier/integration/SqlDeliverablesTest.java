package com.enicarthage.refugeanimalier.integration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class SqlDeliverablesTest extends MysqlTestSupport {
    @Test
    void readonlyVerificationQueriesExecuteAgainstActualMysql() throws Exception {
        long animalId = animal("DISPONIBLE");
        request(animalId);
        List<String> statements = statements("verification.sql");
        assertEquals(12, statements.size());
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            connection.setReadOnly(true);
            for (String sql : statements) {
                assertTrue(sql.startsWith("SELECT"));
                try (var result = statement.executeQuery(sql)) {
                    assertTrue(result.getMetaData().getColumnCount() > 0);
                    while (result.next()) {
                        // Execute the entire query without logging personal or credential data.
                    }
                }
            }
        }
    }

    @Test
    void pedagogicalCrudQueriesUseTheirOwnRowsAndRollback() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);
        long animalsBefore = count("SELECT COUNT(*) FROM animal");
        long requestsBefore = count("SELECT COUNT(*) FROM demande");
        boolean insertedAnimal = false;
        boolean updatedAnimal = false;
        boolean deletedAnimal = false;

        try (Connection connection = connection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                for (String sql : statements("crud-examples.sql")) {
                    boolean hasResult = statement.execute(sql);
                    if (hasResult) {
                        try (var result = statement.getResultSet()) {
                            while (result.next()) { /* Consume only; no secret output. */ }
                        }
                    } else if (sql.startsWith("INSERT INTO animal")) {
                        assertEquals(1, statement.getUpdateCount());
                        insertedAnimal = true;
                    } else if (sql.startsWith("UPDATE animal")) {
                        assertEquals(1, statement.getUpdateCount());
                        updatedAnimal = true;
                    } else if (sql.startsWith("DELETE FROM animal")) {
                        assertEquals(1, statement.getUpdateCount());
                        deletedAnimal = true;
                    }
                }
            } finally {
                connection.rollback();
            }
        }
        assertTrue(insertedAnimal && updatedAnimal && deletedAnimal);
        assertEquals(animalsBefore, count("SELECT COUNT(*) FROM animal"));
        assertEquals(requestsBefore, count("SELECT COUNT(*) FROM demande"));
        assertEquals("Milo", scalar("SELECT nom FROM animal WHERE id = ?", animalId));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
    }

    @Test
    void mysqlEnforcesDocumentedKeysAndRelationships() throws Exception {
        assertEquals(5, count("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'PRIMARY KEY'"));
        assertEquals(6, count("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE()"));
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND DELETE_RULE = 'CASCADE'"));
        long animalId = animal("DISPONIBLE");
        try (Connection connection = connection(); var insert = connection.prepareStatement(
                "INSERT INTO dossier_medical (animal_id) VALUES (?)")) {
            insert.setLong(1, animalId);
            assertEquals(1, insert.executeUpdate());
            assertThrows(SQLException.class, insert::executeUpdate, "Only one dossier per animal");
            insert.setLong(1, 99999999);
            assertThrows(SQLException.class, insert::executeUpdate, "Foreign key requires an existing animal");
        }
        try (Connection connection = connection(); var delete = connection.prepareStatement(
                "DELETE FROM animal WHERE id = ?")) {
            delete.setLong(1, animalId);
            assertEquals(1, delete.executeUpdate());
        }
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id = ?", animalId));
    }

    private List<String> statements(String resource) throws Exception {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream("/sql/" + resource))) {
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)^\\s*--.*$", "");
            List<String> statements = new ArrayList<>();
            for (String sql : source.split(";")) {
                if (!sql.isBlank()) {
                    statements.add(sql.strip());
                }
            }
            return statements;
        }
    }
}

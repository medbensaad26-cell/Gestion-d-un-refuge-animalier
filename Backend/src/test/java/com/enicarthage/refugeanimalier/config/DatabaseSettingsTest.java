package com.enicarthage.refugeanimalier.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSettingsTest {
    @TempDir
    Path directory;

    @Test
    void localSettingsWorkFromBackendAndRepositoryWorkingDirectories() throws Exception {
        Path backend = Files.createDirectory(directory.resolve("Backend"));
        Files.writeString(backend.resolve("database.local.properties"),
                "db.url=jdbc:mysql://example.invalid/test\ndb.user=test_user\ndb.password=\n");

        var repositorySettings = DatabaseSettings.load(Map.of(), directory);
        var backendSettings = DatabaseSettings.load(Map.of(), backend);
        assertEquals(repositorySettings, backendSettings);
        assertEquals("test_user", backendSettings.user());
        assertTrue(backendSettings.password().isEmpty(), "Existing empty local settings remain supported");
    }

    @Test
    void environmentOverridesLocalSettingsAndExplicitEmptyPasswordIsRetained() throws Exception {
        Files.writeString(directory.resolve("database.local.properties"),
                "db.url=jdbc:mysql://example.invalid/local\ndb.user=local_user\ndb.password=test-only\n");
        var settings = DatabaseSettings.load(Map.of(
                "REFUGE_DB_URL", "jdbc:mysql://example.invalid/environment",
                "REFUGE_DB_USER", "environment_user", "REFUGE_DB_PASSWORD", ""), directory);

        assertEquals("jdbc:mysql://example.invalid/environment", settings.url());
        assertEquals("environment_user", settings.user());
        assertTrue(settings.password().isEmpty());
        assertFalse(settings.toString().contains(settings.user()));
        assertFalse(settings.toString().contains(settings.url()));
    }

    @Test
    void explicitConfigurationPathWorksOutsideProjectDirectory() throws Exception {
        Path local = directory.resolve("chosen.properties");
        Files.writeString(local, "db.url=jdbc:mysql://example.invalid/test\ndb.user=test_user\ndb.password=\n");

        assertEquals("test_user", DatabaseSettings.load(
                Map.of("REFUGE_DB_CONFIG", local.toString()), directory).user());
    }

    @Test
    void missingConfigurationFailsBeforeAnyDatabaseConnection() {
        assertThrows(SQLException.class, () -> DatabaseSettings.load(Map.of(), directory));
        assertThrows(SQLException.class, () -> DatabaseSettings.load(
                Map.of("REFUGE_DB_CONFIG", "missing.properties"), directory));
    }

    @Test
    void missingUserOrPasswordAndNonMysqlUrlsAreRejected() {
        assertThrows(SQLException.class, () -> DatabaseSettings.load(
                Map.of("REFUGE_DB_URL", "jdbc:mysql://example.invalid/test"), directory));
        assertThrows(SQLException.class, () -> DatabaseSettings.load(Map.of(
                "REFUGE_DB_URL", "jdbc:mysql://example.invalid/test", "REFUGE_DB_USER", "test_user"), directory));
        assertThrows(SQLException.class, () -> DatabaseSettings.load(Map.of(
                "REFUGE_DB_URL", "jdbc:other:test", "REFUGE_DB_USER", "test_user", "REFUGE_DB_PASSWORD", ""), directory));
    }

    @Test
    void malformedPropertiesProduceAnErrorWithoutConfigurationValues() throws Exception {
        Files.writeString(directory.resolve("database.local.properties"), "db.password=\\uZZZZ\n");
        SQLException error = assertThrows(SQLException.class,
                () -> DatabaseSettings.load(Map.of(), directory));
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("ZZZZ"));
    }
}

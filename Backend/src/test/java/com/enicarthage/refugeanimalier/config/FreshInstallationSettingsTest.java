package com.enicarthage.refugeanimalier.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class FreshInstallationSettingsTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://localhost:3306/refuge_animalier",
            "jdbc:mysql://127.0.0.1/refuge_animalier",
            "jdbc:mysql://localhost/another_database",
            "jdbc:mysql://localhost/refuge_animalier_fresh_",
            "jdbc:mysql://example.invalid/refuge_animalier_fresh_demo",
            "jdbc:mysql://0.0.0.0/refuge_animalier_fresh_demo",
            "jdbc:mysql:loadbalance://localhost/refuge_animalier_fresh_demo",
            "jdbc:mysql://localhost/%72efuge_animalier_fresh_demo",
            "jdbc:mysql://localhost/refuge_animalier_fresh_demo/other",
            "jdbc:mysql://localhost/refuge_animalier_fresh_demo?databaseName=refuge_animalier",
            "jdbc:mysql://localhost/refuge_animalier_fresh_demo?sessionVariables=anything",
            "jdbc:mysql://localhost/refuge_animalier_fresh_demo?allowMultiQueries=true",
            "jdbc:mysql://localhost/refuge_animalier_fresh_demo?serverTimezone=UTC&serverTimezone=UTC",
            "jdbc:mysql://localhost/refuge_animalier_fresh_demo#fragment",
            "jdbc:mysql://user@localhost/refuge_animalier_fresh_demo",
            "jdbc:mysql://localhost:0/refuge_animalier_fresh_demo",
            "jdbc:mysql://localhost:65536/refuge_animalier_fresh_demo"
    })
    void unsafeTargetsAreRejectedBeforeAnyConnection(String url) {
        assertThrows(IllegalArgumentException.class, () -> FreshInstallation.validateTargetUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://127.0.0.1:3306/refuge_animalier_fresh_demo?serverTimezone=UTC",
            "jdbc:mysql://localhost/refuge_animalier_fresh_professor",
            "jdbc:mysql://[::1]:3306/refuge_animalier_fresh_demo",
            "jdbc:mysql://127.0.0.1:23456/refuge_animalier_test_0123456789abcdef0123456789abcdef?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=1000&socketTimeout=5000&serverTimezone=UTC"
    })
    void permittedFreshAndDisposableTargetsAreAccepted(String url) {
        assertTrue(FreshInstallation.validateTargetUrl(url).startsWith("refuge_animalier_"));
    }

    @Test void explicitMissingFileDoesNotFallBackToTheApplicationConfiguration() {
        assertThrows(SQLException.class, () -> FreshInstallation.explicitSettings(directory.resolve("missing.local.properties").toString()));
    }

    @Test void privateConfigurationSuffixIsRequired() {
        assertThrows(SQLException.class, () -> FreshInstallation.explicitSettings(directory.resolve("public.properties").toString()));
    }

    @Test void explicitFileUsesExistingParserWithoutAmbientDatabaseSettings() throws Exception {
        Path config = directory.resolve("fresh-install.local.properties");
        Files.writeString(config, "db.url=jdbc:mysql://localhost/refuge_animalier_fresh_demo\ndb.user=example_user\ndb.password=\n");
        var settings = FreshInstallation.explicitSettings(config.toString());
        assertEquals("refuge_animalier_fresh_demo", FreshInstallation.validateTargetUrl(settings.url()));
        assertTrue(settings.password().isEmpty());
        assertFalse(settings.toString().contains("example_user"));
    }

    @Test void packagedSchemaContainsExactlyTheFiveExplicitTransactionalDefinitions() throws Exception {
        var statements = FreshInstallation.schemaStatements();
        assertEquals(5, statements.size());
        for (String sql : statements) {
            assertTrue(sql.startsWith("CREATE TABLE "));
            assertTrue(sql.contains("ENGINE=InnoDB"));
            assertFalse(sql.contains("IF NOT EXISTS"));
            assertFalse(sql.contains("INSERT INTO"));
            assertFalse(sql.contains("USE refuge_animalier"));
        }
    }
}

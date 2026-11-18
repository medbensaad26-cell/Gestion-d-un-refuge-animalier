package com.enicarthage.refugeanimalier.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailSettingsTest {
    @TempDir
    Path directory;

    @Test
    void emailIsOptional() {
        assertTrue(EmailSettings.load(Map.of(), directory).isEmpty());
        assertTrue(EmailSettings.load(Map.of("REFUGE_MAIL_HOST", "  "), directory).isEmpty());
    }

    @Test
    void localFileWorksFromBackendAndRepositoryDirectoriesWithSecureDefaults() throws Exception {
        Path backend = Files.createDirectory(directory.resolve("Backend"));
        Files.writeString(backend.resolve("email.local.properties"),
                "mail.host=smtp.example.test\nmail.user=compte@example.test\nmail.password=test-only\n");

        var settings = EmailSettings.load(Map.of(), directory).orElseThrow();
        assertEquals(settings, EmailSettings.load(Map.of(), backend).orElseThrow());
        assertEquals(587, settings.port());
        assertEquals(EmailSettings.Securite.STARTTLS, settings.securite());
        assertEquals("compte@example.test", settings.expediteur(), "The login is the default sender");
    }

    @Test
    void environmentOverridesLocalFile() throws Exception {
        Files.writeString(directory.resolve("email.local.properties"),
                "mail.host=local.example.test\nmail.from=local@example.test\n");
        var settings = EmailSettings.load(Map.of("REFUGE_MAIL_HOST", "env.example.test",
                "REFUGE_MAIL_FROM", "env@example.test", "REFUGE_MAIL_SECURITY", "ssl"), directory).orElseThrow();
        assertEquals("env.example.test", settings.host());
        assertEquals("env@example.test", settings.expediteur());
        assertEquals(EmailSettings.Securite.SSL, settings.securite());
        assertEquals(465, settings.port());
    }

    @Test
    void explicitConfigurationFileIsUsedAndMustExist() throws Exception {
        Path file = directory.resolve("choisi.properties");
        Files.writeString(file, "mail.host=smtp.example.test\nmail.from=refuge@example.test\n");
        assertEquals("smtp.example.test",
                EmailSettings.load(Map.of("REFUGE_MAIL_CONFIG", file.toString()), directory).orElseThrow().host());
        assertThrows(IllegalArgumentException.class,
                () -> EmailSettings.load(Map.of("REFUGE_MAIL_CONFIG", "absent.properties"), directory));
    }

    @Test
    void invalidValuesAreRejectedWithoutBeingRepeated() {
        for (Map<String, String> invalide : new Map[]{
                Map.of("REFUGE_MAIL_HOST", "h"),
                Map.of("REFUGE_MAIL_HOST", "h", "REFUGE_MAIL_FROM", "valeur-secrete"),
                Map.of("REFUGE_MAIL_HOST", "h", "REFUGE_MAIL_FROM", "a@b.test", "REFUGE_MAIL_PORT", "valeur-secrete"),
                Map.of("REFUGE_MAIL_HOST", "h", "REFUGE_MAIL_FROM", "a@b.test", "REFUGE_MAIL_PORT", "70000"),
                Map.of("REFUGE_MAIL_HOST", "h", "REFUGE_MAIL_FROM", "a@b.test", "REFUGE_MAIL_SECURITY", "valeur-secrete"),
                Map.of("REFUGE_MAIL_HOST", "h", "REFUGE_MAIL_USER", "a@b.test")}) {
            var erreur = assertThrows(IllegalArgumentException.class, () -> EmailSettings.load(invalide, directory));
            assertFalse(erreur.getMessage().contains("valeur-secrete"));
        }
    }

    @Test
    void credentialsAreNeverSentWithoutEncryption() {
        assertThrows(IllegalArgumentException.class, () -> EmailSettings.load(Map.of(
                "REFUGE_MAIL_HOST", "h", "REFUGE_MAIL_USER", "a@b.test",
                "REFUGE_MAIL_PASSWORD", "test-only", "REFUGE_MAIL_SECURITY", "NONE"), directory));
    }

    @Test
    void textualFormHidesSettings() {
        var settings = EmailSettings.load(Map.of("REFUGE_MAIL_HOST", "smtp.example.test",
                "REFUGE_MAIL_USER", "compte@example.test", "REFUGE_MAIL_PASSWORD", "test-only"), directory).orElseThrow();
        assertFalse(settings.toString().contains("test-only"));
        assertFalse(settings.toString().contains("compte"));
    }
}

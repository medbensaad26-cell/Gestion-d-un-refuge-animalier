package com.enicarthage.refugeanimalier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImageAnimalServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2};
    private static final byte[] WEBP = "RIFFxxxxWEBPdata".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path directory;

    private ImageAnimalService service() {
        return new ImageAnimalService(directory.resolve("animaux"));
    }

    private static int statut(Runnable action) {
        return assertThrows(ImageAnimalService.ImageRefuseeException.class, action::run).statut();
    }

    private void envoyer(ImageAnimalService service, byte[] contenu, String type) {
        try {
            service.enregistrer(4, contenu, type);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void supportedFormatsAreStoredAndReadBack() throws Exception {
        var service = service();
        for (var cas : Map.of("image/png", PNG, "image/jpeg", JPEG, "image/webp", WEBP).entrySet()) {
            String url = service.enregistrer(7, cas.getValue(), cas.getKey() + "; charset=binary");
            assertTrue(url.matches("/api/animaux/7/image\\?v=\\d+"));
            var image = service.lire(7);
            assertEquals(cas.getKey(), image.typeContenu());
            assertArrayEquals(cas.getValue(), image.contenu());
        }
    }

    @Test
    void replacingAPhotoKeepsOnlyTheNewFile() throws Exception {
        var service = service();
        service.enregistrer(7, PNG, "image/png");
        service.enregistrer(7, JPEG, "image/jpeg");
        try (var files = Files.list(directory.resolve("animaux"))) {
            assertEquals(java.util.List.of("animal-7.jpg"), files.map(p -> p.getFileName().toString()).toList());
        }
        assertEquals("image/jpeg", service.lire(7).typeContenu());
    }

    @Test
    void photosStaySeparatedByAnimal() throws Exception {
        var service = service();
        service.enregistrer(1, PNG, "image/png");
        assertNull(service.urlImage(11));
        assertNull(service.lire(11));
        service.supprimer(11);
        assertNotNull(service.urlImage(1));
        service.supprimer(1);
        assertNull(service.urlImage(1));
    }

    @Test
    void contentMustReallyBeASupportedImage() {
        var service = service();
        assertEquals(400, statut(() -> envoyer(service, new byte[0], "image/png")));
        assertEquals(400, statut(() -> envoyer(service, null, "image/png")));
        assertEquals(415, statut(() -> envoyer(service, "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8), "image/png")));
        assertEquals(415, statut(() -> envoyer(service, "<html>".getBytes(StandardCharsets.UTF_8), "text/html")));
        assertEquals(415, statut(() -> envoyer(service, PNG, "image/jpeg")));
        assertEquals(415, statut(() -> envoyer(service, PNG, null)));
        assertEquals(415, statut(() -> envoyer(service, Arrays.copyOf(PNG, 4), "image/png")));
        assertFalse(Files.exists(directory.resolve("animaux")), "A refused file writes nothing");
    }

    @Test
    void sizeLimitIsExact() throws Exception {
        var service = service();
        byte[] limite = Arrays.copyOf(PNG, ImageAnimalService.TAILLE_MAX);
        assertNotNull(service.enregistrer(4, limite, "image/png"));
        assertEquals(413, statut(() -> envoyer(service, Arrays.copyOf(PNG, ImageAnimalService.TAILLE_MAX + 1), "image/png")));
        assertEquals(ImageAnimalService.TAILLE_MAX, service.lire(4).contenu().length, "The previous photo is kept");
    }

    @Test
    void defaultFolderWorksFromRepositoryAndBackendDirectories() throws Exception {
        Path backend = Files.createDirectory(directory.resolve("Backend"));
        Files.writeString(backend.resolve("pom.xml"), "<project/>");
        Path attendu = backend.resolve("uploads").resolve("animaux");
        assertEquals(attendu, ImageAnimalService.dossierConfigure(Map.of(), directory));
        assertEquals(attendu, ImageAnimalService.dossierConfigure(Map.of(), backend));
    }

    @Test
    void configuredFolderOverridesTheDefault() {
        assertEquals(directory.resolve("photos").resolve("animaux"),
                ImageAnimalService.dossierConfigure(Map.of("REFUGE_UPLOAD_DIR", "photos"), directory));
    }

    @Test
    void automatedTestsUseATemporaryFolder() {
        System.setProperty("refuge.test.database", "true");
        assertNull(ImageAnimalService.parDefaut().urlImage(Long.MAX_VALUE));
        assertFalse(Files.exists(Path.of("uploads")), "Tests must not create the application's photo folder");
    }
}

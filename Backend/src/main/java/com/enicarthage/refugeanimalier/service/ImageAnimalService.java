package com.enicarthage.refugeanimalier.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Stores one photo per animal on disk, named from its validated numeric ID. No database change is needed. */
public class ImageAnimalService {
    public static final int TAILLE_MAX = 5 * 1024 * 1024;

    private enum Format {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String type;
        private final String extension;

        Format(String type, String extension) {
            this.type = type;
            this.extension = extension;
        }
    }

    public record ImageAnimal(byte[] contenu, String typeContenu) {}

    /** A refused upload, with the HTTP status that describes it. */
    public static final class ImageRefuseeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int statut;

        public ImageRefuseeException(int statut, String message) {
            super(message);
            this.statut = statut;
        }

        public int statut() {
            return statut;
        }
    }

    private final Path dossier;

    public ImageAnimalService(Path dossier) {
        this.dossier = Objects.requireNonNull(dossier).toAbsolutePath().normalize();
    }

    public static ImageAnimalService parDefaut() {
        // Automated tests never write into the application's real photo folder.
        if (Boolean.getBoolean("refuge.test.database")) {
            return new ImageAnimalService(Path.of(System.getProperty("java.io.tmpdir"),
                    "refuge-images-test-" + ProcessHandle.current().pid(), "animaux"));
        }
        return new ImageAnimalService(dossierConfigure(System.getenv(), Path.of("").toAbsolutePath()));
    }

    static Path dossierConfigure(Map<String, String> environment, Path repertoireCourant) {
        String configure = environment.get("REFUGE_UPLOAD_DIR");
        if (configure != null && !configure.isBlank()) {
            return repertoireCourant.resolve(configure.strip()).resolve("animaux").normalize();
        }
        Path backend = Files.isRegularFile(repertoireCourant.resolve("Backend").resolve("pom.xml"))
                ? repertoireCourant.resolve("Backend")
                : repertoireCourant;
        return backend.resolve("uploads").resolve("animaux");
    }

    /** Validates and stores a photo, replacing any previous one, and returns its public URL. */
    public String enregistrer(long animalId, byte[] contenu, String typeDeclare) throws IOException {
        if (contenu == null || contenu.length == 0) {
            throw new ImageRefuseeException(400, "Image obligatoire");
        }
        if (contenu.length > TAILLE_MAX) {
            throw tropLourde();
        }
        Format format = detecter(contenu);
        if (format == null || !format.type.equals(typeNormalise(typeDeclare))) {
            throw new ImageRefuseeException(415, "Format accepte : JPEG, PNG ou WebP");
        }

        Files.createDirectories(dossier);
        Path temporaire = Files.createTempFile(dossier, "envoi-", ".tmp");
        try {
            Files.write(temporaire, contenu);
            Files.move(temporaire, chemin(animalId, format),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporaire);
        }
        for (Format autre : Format.values()) {
            if (autre != format) {
                Files.deleteIfExists(chemin(animalId, autre));
            }
        }
        return urlImage(animalId);
    }

    public static ImageRefuseeException tropLourde() {
        return new ImageRefuseeException(413, "Image trop lourde : 5 Mo maximum");
    }

    /** Public URL of the photo, or null. The version changes whenever the photo is replaced. */
    public String urlImage(long animalId) {
        Format format = formatEnregistre(animalId);
        if (format == null) {
            return null;
        }
        return "/api/animaux/" + animalId + "/image?v=" + chemin(animalId, format).toFile().lastModified();
    }

    public ImageAnimal lire(long animalId) throws IOException {
        Format format = formatEnregistre(animalId);
        return format == null ? null : new ImageAnimal(Files.readAllBytes(chemin(animalId, format)), format.type);
    }

    public void supprimer(long animalId) throws IOException {
        for (Format format : Format.values()) {
            Files.deleteIfExists(chemin(animalId, format));
        }
    }

    private Format formatEnregistre(long animalId) {
        Format recent = null;
        long date = -1;
        for (Format format : Format.values()) {
            File fichier = chemin(animalId, format).toFile();
            if (fichier.isFile() && fichier.lastModified() > date) {
                recent = format;
                date = fichier.lastModified();
            }
        }
        return recent;
    }

    private Path chemin(long animalId, Format format) {
        return dossier.resolve("animal-" + animalId + "." + format.extension);
    }

    private static String typeNormalise(String type) {
        return type == null ? "" : type.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }

    // The file's own signature decides its type; the browser's claim alone is not trusted.
    private static Format detecter(byte[] c) {
        if (commencePar(c, 0, 0xFF, 0xD8, 0xFF)) {
            return Format.JPEG;
        }
        if (commencePar(c, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Format.PNG;
        }
        if (c.length >= 12
                && Arrays.equals(c, 0, 4, "RIFF".getBytes(StandardCharsets.US_ASCII), 0, 4)
                && Arrays.equals(c, 8, 12, "WEBP".getBytes(StandardCharsets.US_ASCII), 0, 4)) {
            return Format.WEBP;
        }
        return null;
    }

    private static boolean commencePar(byte[] contenu, int debut, int... attendus) {
        if (contenu.length < debut + attendus.length) {
            return false;
        }
        for (int i = 0; i < attendus.length; i++) {
            if ((contenu[debut + i] & 0xFF) != attendus[i]) {
                return false;
            }
        }
        return true;
    }
}

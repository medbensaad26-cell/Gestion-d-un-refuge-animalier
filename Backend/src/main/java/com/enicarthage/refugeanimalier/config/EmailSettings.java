package com.enicarthage.refugeanimalier.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Optional SMTP settings. Without a host, email notifications are simply disabled. */
public record EmailSettings(
        String host,
        int port,
        String utilisateur,
        String motDePasse,
        String expediteur,
        Securite securite
) {
    public enum Securite { STARTTLS, SSL, NONE }

    private static final String FICHIER = "email.local.properties";

    /** Error messages name settings only, never their values. */
    public static Optional<EmailSettings> load(Map<String, String> environment, Path workingDirectory) {
        Properties local = localProperties(environment, workingDirectory);
        String host = value(environment, "REFUGE_MAIL_HOST", local, "mail.host");
        if (host == null) {
            return Optional.empty();
        }

        String utilisateur = value(environment, "REFUGE_MAIL_USER", local, "mail.user");
        String motDePasse = value(environment, "REFUGE_MAIL_PASSWORD", local, "mail.password");
        String expediteur = value(environment, "REFUGE_MAIL_FROM", local, "mail.from");
        if (expediteur == null) {
            expediteur = utilisateur;
        }
        if (expediteur == null || !expediteur.matches("[^\\s@]+@[^\\s@]+")) {
            throw new IllegalArgumentException("Configurer une adresse valide dans mail.from");
        }
        if ((utilisateur == null) != (motDePasse == null)) {
            throw new IllegalArgumentException("Configurer mail.user et mail.password ensemble");
        }

        Securite securite = securite(value(environment, "REFUGE_MAIL_SECURITY", local, "mail.security"));
        if (securite == Securite.NONE && utilisateur != null) {
            throw new IllegalArgumentException("mail.security=NONE interdit l'envoi d'identifiants");
        }
        int port = port(value(environment, "REFUGE_MAIL_PORT", local, "mail.port"), securite);
        return Optional.of(new EmailSettings(host, port, utilisateur, motDePasse, expediteur, securite));
    }

    private static Properties localProperties(Map<String, String> environment, Path workingDirectory) {
        Properties local = new Properties();
        String configured = environment.get("REFUGE_MAIL_CONFIG");
        Path file;
        if (configured != null && !configured.isBlank()) {
            file = workingDirectory.resolve(configured).normalize();
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Fichier REFUGE_MAIL_CONFIG introuvable");
            }
        } else {
            file = workingDirectory.resolve(FICHIER);
            if (!Files.isRegularFile(file)) {
                file = workingDirectory.resolve("Backend").resolve(FICHIER);
            }
        }
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                local.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Impossible de lire la configuration email locale");
            }
        }
        return local;
    }

    private static String value(Map<String, String> environment, String variable,
                                Properties local, String property) {
        String value = environment.containsKey(variable)
                ? environment.get(variable)
                : local.getProperty(property);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Securite securite(String value) {
        if (value == null) {
            return Securite.STARTTLS;
        }
        try {
            return Securite.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mail.security attend STARTTLS, SSL ou NONE");
        }
    }

    private static int port(String value, Securite securite) {
        if (value == null) {
            return securite == Securite.SSL ? 465 : 587;
        }
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException e) {
            // Reported below without repeating the configured value.
        }
        throw new IllegalArgumentException("mail.port attend un port entre 1 et 65535");
    }

    @Override
    public String toString() {
        return "EmailSettings[configuration masquee]";
    }
}

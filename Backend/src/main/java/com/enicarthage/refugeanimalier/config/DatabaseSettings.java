package com.enicarthage.refugeanimalier.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/** Resolves connection settings without opening a database connection. */
final class DatabaseSettings {
    private DatabaseSettings() {}

    static Settings load(Map<String, String> environment, Path workingDirectory) throws SQLException {
        Properties local = localProperties(environment, workingDirectory);
        String url = value(environment, "REFUGE_DB_URL", local, "db.url");
        String user = value(environment, "REFUGE_DB_USER", local, "db.user");
        String password = value(environment, "REFUGE_DB_PASSWORD", local, "db.password");
        if (url == null || url.isBlank() || !url.startsWith("jdbc:mysql://")) {
            throw new SQLException("Configurer une URL JDBC MySQL valide via REFUGE_DB_URL ou db.url");
        }
        if (user == null || user.isBlank() || password == null) {
            throw new SQLException("Configurer REFUGE_DB_USER et REFUGE_DB_PASSWORD ou leurs proprietes locales");
        }
        return new Settings(url, user, password);
    }

    static Properties localProperties(Map<String, String> environment, Path workingDirectory) throws SQLException {
        Properties local = new Properties();
        String configuredFile = environment.get("REFUGE_DB_CONFIG");
        Path file;
        if (configuredFile != null && !configuredFile.isBlank()) {
            file = workingDirectory.resolve(configuredFile).normalize();
            if (!Files.isRegularFile(file)) {
                throw new SQLException("Fichier REFUGE_DB_CONFIG introuvable");
            }
        } else {
            file = workingDirectory.resolve("database.local.properties");
            if (!Files.isRegularFile(file)) {
                file = workingDirectory.resolve("Backend/database.local.properties");
            }
        }
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                local.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                throw new SQLException("Impossible de lire la configuration locale de la base");
            }
        }

        return local;
    }

    private static String value(Map<String, String> environment, String variable,
                                Properties local, String property) {
        return environment.containsKey(variable) ? environment.get(variable) : local.getProperty(property);
    }

    record Settings(String url, String user, String password) {
        @Override
        public String toString() {
            return "DatabaseSettings[configuration masquee]";
        }
    }
}

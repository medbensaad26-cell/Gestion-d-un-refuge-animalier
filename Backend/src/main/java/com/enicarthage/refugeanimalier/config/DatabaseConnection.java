
package com.enicarthage.refugeanimalier.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Path;

public class DatabaseConnection {



    public static Connection getConnection()
            throws SQLException {

        if (Boolean.getBoolean("refuge.test.database")) {
            String testUrl = proprieteTestObligatoire("refuge.test.jdbc.url");
            // Only the uniquely named, disposable test database is accepted.
            if (!testUrl.matches(
                    "jdbc:mysql://[^/]+/refuge_animalier_test_[a-f0-9]{32}(?:\\?.*)?")) {
                throw new SQLException("Base de test isolee obligatoire");
            }
            return DriverManager.getConnection(
                    testUrl,
                    proprieteTestObligatoire("refuge.test.jdbc.user"),
                    proprieteTestObligatoire("refuge.test.jdbc.password"));
        }

        DatabaseSettings.Settings settings = DatabaseSettings.load(
                System.getenv(), Path.of("").toAbsolutePath());
        return DriverManager.getConnection(
                settings.url(), settings.user(), settings.password());
    }
    private static String proprieteTestObligatoire(String nom)
            throws SQLException {
        String valeur = System.getProperty(nom);
        if (valeur == null || valeur.isBlank()) {
            throw new SQLException("Configuration de test absente : " + nom);
        }
        return valeur;
    }
}

package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ResourceLock("refuge-isolated-mysql")
class TestDatabaseIsolationTest {
    @Test
    void missingTestConfigurationNeverFallsBackToDevelopmentDatabase() {
        withProperties(Map.of("refuge.test.database", "true"), () -> {
            System.clearProperty("refuge.test.jdbc.url");
            assertThrows(SQLException.class, DatabaseConnection::getConnection);
        });
    }

    @Test
    void developmentDatabaseUrlIsRejectedBeforeConnecting() {
        withProperties(Map.of(
                "refuge.test.database", "true",
                "refuge.test.jdbc.url", "jdbc:mysql://localhost:3306/refuge_animalier"),
                () -> assertThrows(SQLException.class, DatabaseConnection::getConnection));
    }

    private void withProperties(Map<String, String> replacements, Runnable test) {
        String[] keys = {"refuge.test.database", "refuge.test.jdbc.url",
                "refuge.test.jdbc.user", "refuge.test.jdbc.password"};
        Map<String, String> original = new HashMap<>();
        for (String key : keys) {
            original.put(key, System.getProperty(key));
        }
        try {
            replacements.forEach(System::setProperty);
            test.run();
        } finally {
            original.forEach((key, value) -> {
                if (value == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, value);
                }
            });
        }
    }
}

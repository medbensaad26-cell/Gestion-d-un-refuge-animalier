package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.security.PasswordUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.mysql.MySQLContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;

@ResourceLock("refuge-isolated-mysql")
abstract class MysqlTestSupport {
    private static AutoCloseable runtime;

    @BeforeAll
    static void startIsolatedMysql() throws Exception {
        // Set before starting the runtime: a failed setup can never use localhost:3306.
        System.setProperty("refuge.test.database", "true");
        String databaseName = "refuge_animalier_test_" + UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        String executable = System.getProperty("refuge.test.mysql.executable");
        if (executable == null || executable.isBlank()) {
            MySQLContainer mysql = new MySQLContainer("mysql:8.4.7")
                    .withDatabaseName(databaseName)
                    .withUsername("test_user")
                    .withPassword(password);
            runtime = mysql;
            mysql.start();
            configure(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        } else {
            PortableMysql mysql = new PortableMysql(Path.of(executable), databaseName, password);
            runtime = mysql;
            configure(mysql.jdbcUrl(), mysql.username(), mysql.password());
        }

        // Execute the shipped schema unchanged in an isolated database, including its engines.
        try (InputStream input = Objects.requireNonNull(
                MysqlTestSupport.class.getResourceAsStream("/sql/database.sql"));
             Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String ddl = source.replaceAll("(?m)^\\s*--.*$", "");
            for (String sql : ddl.split(";")) {
                if (sql.stripLeading().startsWith("CREATE TABLE")) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static void configure(String url, String user, String password) {
        System.setProperty("refuge.test.jdbc.url", url);
        System.setProperty("refuge.test.jdbc.user", user);
        System.setProperty("refuge.test.jdbc.password", password);
    }

    @AfterAll
    static void stopIsolatedMysql() throws Exception {
        try {
            if (runtime != null) {
                runtime.close();
            }
        } finally {
            runtime = null;
            System.clearProperty("refuge.test.jdbc.url");
            System.clearProperty("refuge.test.jdbc.user");
            System.clearProperty("refuge.test.jdbc.password");
            // Keep test mode enabled so any later accidental connection still fails closed.
        }
    }

    @BeforeEach
    void resetIsolatedData() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM dossier_medical");
            statement.executeUpdate("DELETE FROM annonce");
            statement.executeUpdate("DELETE FROM demande");
            statement.executeUpdate("DELETE FROM animal");
            statement.executeUpdate("DELETE FROM admin");
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO admin (id, nom, email, mot_de_passe) VALUES (?, ?, ?, ?)")) {
            String hash = PasswordUtils.hacher(UUID.randomUUID().toString());
            for (long id = 1; id <= 2; id++) {
                statement.setLong(1, id);
                statement.setString(2, "Admin " + id);
                statement.setString(3, "admin" + id + "@example.test");
                statement.setString(4, hash);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    protected Connection connection() throws SQLException {
        return DatabaseConnection.getConnection();
    }

    // Use before creating fixture data when testing the intended single-admin application.
    protected void useSingleAdministrator() throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM admin WHERE id <> 1");
        }
    }

    protected long animal(String status) throws SQLException {
        return insert("INSERT INTO animal (nom, espece, race, age, sexe, etat_sante, statut, admin_id) "
                + "VALUES ('Milo', 'CHAT', 'Europeen', 2, 'MALE', 'BON', ?, 1)", status);
    }

    protected long request(long animalId) throws SQLException {
        return insert("INSERT INTO demande (nom_demandeur, email, telephone, date_demande, statut, animal_id, admin_id) "
                + "VALUES ('Adoptant', 'adoptant@example.test', '0102030405', CURRENT_DATE, 'EN_ATTENTE', ?, 1)", animalId);
    }

    private long insert(String sql, Object parameter) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, parameter);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Missing generated test ID");
                }
                return keys.getLong(1);
            }
        }
    }

    protected String scalar(String sql, long... parameters) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setLong(index + 1, parameters[index]);
            }
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("No test result");
                }
                return result.getString(1);
            }
        }
    }

    protected long count(String sql, long... parameters) throws SQLException {
        return Long.parseLong(scalar(sql, parameters));
    }
}

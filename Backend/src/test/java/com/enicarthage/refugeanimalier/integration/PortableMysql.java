package com.enicarthage.refugeanimalier.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs an optional portable MySQL binary against a new, disposable data directory. */
final class PortableMysql implements AutoCloseable {
    private static final String DIRECTORY_PREFIX = "refuge-isolated-mysql-";
    private static final String TEST_USER = "refuge_test";

    private final Path temporaryDirectory;
    private final Path dataDirectory;
    private final String databaseName;
    private final String testPassword;
    private final int port;
    private Process initialization;
    private Process server;
    private boolean rootPasswordAssigned;

    PortableMysql(Path executable, String databaseName, String testPassword)
            throws IOException, SQLException, InterruptedException {
        Path binary = executable.toAbsolutePath().normalize();
        if (!Files.isRegularFile(binary)) {
            throw new IllegalArgumentException("Portable MySQL executable is missing");
        }
        if (databaseName == null || !databaseName.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid isolated database name");
        }
        if (testPassword == null || testPassword.isBlank()) {
            throw new IllegalArgumentException("An isolated database password is required");
        }
        this.databaseName = databaseName;
        this.testPassword = testPassword;
        this.temporaryDirectory = Files.createTempDirectory(DIRECTORY_PREFIX).toRealPath();
        this.dataDirectory = temporaryDirectory.resolve("data");
        this.port = freeNonDefaultPort();

        try {
            Path baseDirectory = binary.getParent().getParent();
            initialization = new ProcessBuilder(List.of(
                    binary.toString(), "--no-defaults", "--initialize-insecure",
                    "--basedir=" + baseDirectory, "--datadir=" + dataDirectory))
                    .redirectErrorStream(true)
                    .redirectOutput(temporaryDirectory.resolve("initialize.log").toFile())
                    .start();
            if (!initialization.waitFor(90, TimeUnit.SECONDS)) {
                initialization.destroyForcibly().waitFor();
                throw new IOException("Isolated MySQL initialization timed out");
            }
            if (initialization.exitValue() != 0) {
                throw new IOException("Isolated MySQL initialization failed; see "
                        + temporaryDirectory.resolve("initialize.log"));
            }

            server = new ProcessBuilder(List.of(
                    binary.toString(), "--no-defaults", "--basedir=" + baseDirectory,
                    "--datadir=" + dataDirectory, "--bind-address=127.0.0.1",
                    "--port=" + port, "--mysqlx=OFF", "--skip-log-bin",
                    "--pid-file=" + temporaryDirectory.resolve("server.pid"),
                    "--log-error=" + temporaryDirectory.resolve("server.log")))
                    .redirectErrorStream(true)
                    .redirectOutput(temporaryDirectory.resolve("server-console.log").toFile())
                    .start();
            awaitServer();
            provisionTestDatabase();
        } catch (IOException | SQLException | InterruptedException | RuntimeException e) {
            try {
                close();
            } catch (Exception cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    String jdbcUrl() {
        return "jdbc:mysql://127.0.0.1:" + port + "/" + databaseName + connectionOptions();
    }

    String username() {
        return TEST_USER;
    }

    String password() {
        return testPassword;
    }

    private String rootJdbcUrl() {
        return "jdbc:mysql://127.0.0.1:" + port + "/" + connectionOptions();
    }

    private static String connectionOptions() {
        return "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                + "&connectTimeout=1000&socketTimeout=5000";
    }

    private static int freeNonDefaultPort() throws IOException {
        for (;;) {
            try (ServerSocket socket = new ServerSocket(0, 1,
                    InetAddress.getByName("127.0.0.1"))) {
                if (socket.getLocalPort() != 3306) {
                    return socket.getLocalPort();
                }
            }
        }
    }

    private void awaitServer() throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            if (!server.isAlive()) {
                throw new IOException("Isolated MySQL exited during startup; see "
                        + temporaryDirectory.resolve("server.log"));
            }
            try (Connection connection = DriverManager.getConnection(rootJdbcUrl(), "root", "")) {
                verifyOwnDataDirectory(connection);
                return;
            } catch (SQLException e) {
                Thread.sleep(200);
            }
        }
        throw new IOException("Isolated MySQL startup timed out");
    }

    private void provisionTestDatabase() throws SQLException, IOException {
        try (Connection connection = DriverManager.getConnection(rootJdbcUrl(), "root", "")) {
            verifyOwnDataDirectory(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE `" + databaseName
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE USER '" + TEST_USER + "'@'localhost' IDENTIFIED BY ?")) {
                statement.setString(1, testPassword);
                statement.executeUpdate();
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("GRANT ALL PRIVILEGES ON `" + databaseName
                        + "`.* TO '" + TEST_USER + "'@'localhost'");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "ALTER USER 'root'@'localhost' IDENTIFIED BY ?")) {
                statement.setString(1, testPassword);
                statement.executeUpdate();
                rootPasswordAssigned = true;
            }
        }
    }

    /** Also guards against another process claiming the selected free port first. */
    private void verifyOwnDataDirectory(Connection connection) throws SQLException, IOException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT @@datadir")) {
            if (!result.next()
                    || !Path.of(result.getString(1)).toRealPath().equals(dataDirectory.toRealPath())) {
                throw new IOException("Refusing to use a server outside the isolated data directory");
            }
        }
    }

    @Override
    public void close() throws IOException, InterruptedException {
        if (initialization != null && initialization.isAlive()) {
            initialization.destroyForcibly().waitFor();
        }
        if (server != null && server.isAlive()) {
            try (Connection connection = DriverManager.getConnection(rootJdbcUrl(), "root",
                    rootPasswordAssigned ? testPassword : "")) {
                verifyOwnDataDirectory(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SHUTDOWN");
                }
            } catch (SQLException | IOException ignored) {
                // Only this helper's Process handle is used for fallback termination.
            }
            if (!server.waitFor(15, TimeUnit.SECONDS)) {
                server.destroy();
                if (!server.waitFor(5, TimeUnit.SECONDS)) {
                    server.destroyForcibly().waitFor();
                }
            }
        }
        removeOwnTemporaryDirectory();
    }

    private void removeOwnTemporaryDirectory() throws IOException {
        if (!Files.exists(temporaryDirectory)) {
            return;
        }
        Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        Path target = temporaryDirectory.toRealPath();
        if (!target.getParent().equals(temporaryRoot)
                || !target.getFileName().toString().startsWith(DIRECTORY_PREFIX)) {
            throw new IOException("Refusing to remove a directory outside the isolated test runtime");
        }
        // Files.walk does not follow symbolic links; only this newly created directory is removed.
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}

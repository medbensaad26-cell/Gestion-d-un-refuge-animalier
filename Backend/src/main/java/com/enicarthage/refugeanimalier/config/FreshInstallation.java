package com.enicarthage.refugeanimalier.config;

import com.enicarthage.refugeanimalier.security.PasswordUtils;
import java.io.Console;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Explicit console-only fresh setup. Main/Router never invoke it. Never resets an account. */
public final class FreshInstallation {
    private static final Set<String> TABLES = Set.of("admin", "animal", "dossier_medical", "annonce", "demande");
    private static final Set<String> URL_OPTIONS = Set.of("serverTimezone", "useSSL", "allowPublicKeyRetrieval", "connectTimeout", "socketTimeout", "characterEncoding", "connectionCollation");

    private FreshInstallation() {}

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Fresh setup: --config <fresh-install.local.properties> --initialize-empty");
            System.out.println("Requires a real console and an empty local refuge_animalier_fresh_* database.");
            System.out.println("Existing refuge_animalier is refused before connecting. Passwords are prompted privately.");
            return;
        }
        if (args.length != 3 || !"--config".equals(args[0]) || !"--initialize-empty".equals(args[2])) {
            System.err.println("No action. Use --help. Never supply a password as an argument.");
            System.exit(2);
            return;
        }
        Console console = System.console();
        if (console == null || Boolean.getBoolean("refuge.test.database")) {
            System.err.println("No action. Use a normal local terminal without pipes/redirection or test mode.");
            System.exit(2);
            return;
        }
        char[] first = null, second = null;
        int result = 0;
        try {
            var settings = explicitSettings(args[1]);
            String database = validateTargetUrl(settings.url());
            if (!database.startsWith("refuge_animalier_fresh_")) throw new IllegalArgumentException("Fresh console namespace required");
            console.printf("Create five tables and ONE administrator in NEW database %s.%n", database);
            String confirmation = console.readLine("Type INITIALIZE %s to continue: ", database);
            if (!("INITIALIZE " + database).equals(confirmation)) {
                console.printf("Cancelled before connection.%n");
                return;
            }
            String name = console.readLine("Administrator name: ");
            String email = console.readLine("Administrator email: ");
            first = console.readPassword("New administrator password (12 characters minimum, 72 UTF-8 bytes maximum): ");
            second = console.readPassword("Repeat password: ");
            if (first == null || second == null || !Arrays.equals(first, second)) {
                console.printf("Cancelled: passwords do not match. No connection opened.%n");
                return;
            }
            // The URL was validated BEFORE connection. No normal/local config fallback.
            try (Connection connection = DriverManager.getConnection(settings.url(), settings.user(), settings.password())) {
                initialize(connection, name, email, first);
            }
            console.printf("Fresh installation complete: five InnoDB tables, one BCrypt administrator, empty catalogue.%n");
            console.printf("Use this dedicated config when starting Java. No password, hash or token is displayed.%n");
        } catch (IllegalArgumentException error) {
            System.err.println("Setup refused: check the new database name, local URL/options, name/email and password policy. See docs/fresh-installation.md.");
            result = 1;
        } catch (SQLException | IOException | RuntimeException error) {
            // Never expose JDBC messages, hashes, passwords or private configuration.
            System.err.println("Setup did not finish. Never retry on a nonempty database or assume a commit failed.");
            System.err.println("MySQL DDL can remain after failure. Inspect only this new database; see the recovery instructions.");
            result = 1;
        } finally {
            if (first != null) Arrays.fill(first, '\0');
            if (second != null) Arrays.fill(second, '\0');
        }
        if (result != 0) System.exit(result);
    }

    // Explicit file only: do not consult existing application config or ambient DB vars.
    static DatabaseSettings.Settings explicitSettings(String file) throws SQLException {
        if (file == null || !file.endsWith(".local.properties")) throw new SQLException("Use a private .local.properties file");
        return DatabaseSettings.load(Map.of("REFUGE_DB_CONFIG", file), Path.of("").toAbsolutePath());
    }

    /** Validate before opening any connection; return the permitted database name. */
    public static String validateTargetUrl(String url) {
        if (url == null || !url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("Direct MySQL URL required");
        URI uri = URI.create(url.substring(5));
        if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost() == null
                || !Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost().toLowerCase(Locale.ROOT))
                || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException("Local MySQL required");
        String path = uri.getRawPath();
        if (path == null || !path.matches("/(refuge_animalier_fresh_[a-z0-9_]{1,32}|refuge_animalier_test_[a-f0-9]{32})")) {
            throw new IllegalArgumentException("New isolated database name required");
        }
        if (uri.getRawQuery() != null) {
            Set<String> used = new HashSet<>();
            for (String part : uri.getRawQuery().split("&", -1)) {
                String[] pair = part.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                if (pair.length != 2 || !URL_OPTIONS.contains(key) || !used.add(key)
                        || !URLDecoder.decode(pair[1], StandardCharsets.UTF_8).matches("[A-Za-z0-9_+:/.-]{1,80}")) {
                    throw new IllegalArgumentException("Unsupported connection option");
                }
            }
        }
        return path.substring(1);
    }

    /** Canonical DDL. No seed inserts, database switching, migrations or destructive SQL. */
    public static List<String> schemaStatements() throws IOException {
        try (var input = FreshInstallation.class.getResourceAsStream("/sql/database.sql")) {
            if (input == null) throw new IOException("Packaged schema missing");
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--.*$", "");
            List<String> statements = Arrays.stream(source.split(";")).map(String::strip).filter(s -> !s.isBlank()).toList();
            Set<String> found = new HashSet<>();
            for (String sql : statements) {
                var match = java.util.regex.Pattern.compile("(?is)^CREATE TABLE (admin|animal|dossier_medical|annonce|demande) \\(.*\\) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci$").matcher(sql);
                if (!match.matches() || !found.add(match.group(1))) throw new IOException("Unexpected schema content");
            }
            if (statements.size() != 5 || !found.equals(TABLES)) throw new IOException("Five-table schema required");
            return statements;
        }
    }

    /** Console and isolated tests use the same implementation. Existing schemas are refused. */
    public static long initialize(Connection connection, String name, String email, char[] password) throws SQLException, IOException {
        String plain = null;
        try {
            String expected = validateTargetUrl(connection.getMetaData().getURL());
            if (!expected.equals(connection.getCatalog()) || !connection.getAutoCommit()) throw new IllegalArgumentException("Explicit new database required");
            if (name == null || name.isBlank() || name.strip().codePointCount(0, name.strip().length()) > 100
                    || email == null || email.strip().length() > 255 || !email.strip().matches("[^\\s@]+@[^\\s@]+")) {
                throw new IllegalArgumentException("Administrator name/email invalid");
            }
            if (password == null) throw new IllegalArgumentException("Password required");
            plain = new String(password);
            String hash = PasswordUtils.hacher(plain);
            if (!PasswordUtils.verifier(plain, hash)) throw new IllegalArgumentException("Local hash verification failed");
            plain = null;
            List<String> statements = schemaStatements();
            try (var statement = connection.prepareStatement("SELECT DATABASE(), (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE())"); var rows = statement.executeQuery()) {
                if (!rows.next() || !expected.equals(rows.getString(1)) || rows.getLong(2) != 0) {
                    throw new IllegalStateException("Database must be completely empty");
                }
            }
            // CREATE TABLE has implicit commits in MySQL: no claim of whole-install rollback.
            try (var statement = connection.createStatement()) {
                for (String sql : statements) statement.execute(sql);
            }
            connection.setAutoCommit(false);
            try {
                long id;
                try (var insert = connection.prepareStatement("INSERT INTO admin (nom, email, mot_de_passe) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, name.strip()); insert.setString(2, email.strip()); insert.setString(3, hash);
                    if (insert.executeUpdate() != 1) throw new SQLException("Administrator insertion failed");
                    try (var keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Administrator ID missing");
                        id = keys.getLong(1);
                    }
                }
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException ignored) { /* No retry or secret output. */ }
                throw error;
            }
        } finally {
            plain = null;
            if (password != null) Arrays.fill(password, '\0');
        }
    }
}

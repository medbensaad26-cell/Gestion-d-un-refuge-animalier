import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.security.PasswordUtils;

import java.io.Console;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;

/** Manual maintenance only. Not registered with Main, Maven startup, or the HTTP API. */
public final class ResetAdminPassword {
    private static final String DATABASE = "refuge_animalier";
    private static final long ADMIN_ID = 1L;

    private ResetAdminPassword() {}

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Manual password reset for local refuge_animalier.admin, ID 1 only.");
            System.out.println("Run with --reset-admin-1 only after approving the procedure.");
            System.out.println("A real terminal is required; passwords are entered twice without echo.");
            System.out.println("Never pass a password as an argument, environment variable, or file.");
            return;
        }
        if (args.length != 1 || !"--reset-admin-1".equals(args[0])) {
            System.err.println("No action. Use --help, or --reset-admin-1 after approval.");
            System.exit(2);
            return;
        }
        Console console = System.console();
        if (console == null) {
            System.err.println("No action. Open Windows Terminal/PowerShell and run directly, without pipes or redirection.");
            System.exit(2);
            return;
        }
        if (Boolean.getBoolean("refuge.test.database")) {
            System.err.println("No action. This maintenance command cannot run in test database mode.");
            System.exit(2);
            return;
        }

        console.printf("Target: local %s.admin, ID %d. Stop the Java backend first.%n", DATABASE, ADMIN_ID);
        String approval = console.readLine("Type RESET 1 to confirm this single-account reset (anything else cancels): ");
        if (!"RESET 1".equals(approval)) {
            console.printf("Cancelled. No database connection was opened.%n");
            return;
        }

        char[] first = null;
        char[] second = null;
        String password = null;
        String hash = null;
        int exitCode = 0;
        try {
            first = console.readPassword("New password (minimum 12 characters; maximum 72 UTF-8 bytes): ");
            second = console.readPassword("Repeat new password: ");
            if (first == null || second == null || !Arrays.equals(first, second)) {
                console.printf("Cancelled: passwords did not match. No database connection was opened.%n");
                return;
            }
            // Existing application API accepts String; never print or persist this value.
            password = new String(first);
            hash = PasswordUtils.hacher(password);
            if (!PasswordUtils.verifier(password, hash)) {
                throw new IllegalStateException("Local BCrypt verification failed.");
            }
            password = null;
            Arrays.fill(first, '\0');
            Arrays.fill(second, '\0');
            reset(hash, console);
        } catch (IllegalArgumentException error) {
            System.err.println("No reset: password must satisfy the existing 12-character minimum and 72-byte UTF-8 maximum, or configuration was rejected.");
            exitCode = 1;
        } catch (ResetFailure error) {
            // Only our fixed messages are printed; never JDBC exception text or a stack trace.
            System.err.println(error.getMessage());
            exitCode = 1;
        } catch (SQLException | RuntimeException error) {
            System.err.println("No reset was attempted: connection, configuration, or local BCrypt preparation failed. No secret details were logged.");
            exitCode = 1;
        } finally {
            if (first != null) Arrays.fill(first, '\0');
            if (second != null) Arrays.fill(second, '\0');
            password = null;
            hash = null;
        }
        if (exitCode != 0) System.exit(exitCode);
    }

    private static void reset(String hash, Console console) throws SQLException, ResetFailure {
        Connection connection = DatabaseConnection.getConnection();
        boolean commitAttempted = false;
        boolean committed = false;
        try {
            connection.setAutoCommit(false);
            requireLocalTarget(connection);
            requireTransactionalTableWithoutTriggers(connection);

            try (var statement = connection.prepareStatement("SELECT id FROM admin WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, ADMIN_ID);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getLong(1) != ADMIN_ID || rows.next()) {
                        throw new ResetFailure("Reset refused: administrator ID 1 must already exist as exactly one row.");
                    }
                }
            }
            try (var statement = connection.prepareStatement("UPDATE admin SET mot_de_passe = ? WHERE id = ?")) {
                statement.setString(1, hash);
                statement.setLong(2, ADMIN_ID);
                if (statement.executeUpdate() != 1) {
                    throw new ResetFailure("Reset refused: update did not affect exactly one administrator.");
                }
            }
            try (var statement = connection.prepareStatement("SELECT mot_de_passe FROM admin WHERE id = ?")) {
                statement.setLong(1, ADMIN_ID);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || !hash.equals(rows.getString(1)) || rows.next()) {
                        throw new ResetFailure("Reset refused: stored BCrypt value could not be verified before commit.");
                    }
                }
            }
            commitAttempted = true;
            connection.commit();
            committed = true;
            console.printf("Success: only administrator ID 1's password was updated. No password or hash is displayed.%n");
            console.printf("Restart the Java backend, then sign in using the unchanged email and your new password.%n");
        } catch (SQLException | RuntimeException | ResetFailure error) {
            boolean rollbackConfirmed = true;
            try { connection.rollback(); } catch (SQLException rollbackError) { rollbackConfirmed = false; }
            if (commitAttempted) {
                throw new ResetFailure("Commit outcome could not be confirmed. Do not retry automatically. Restart Java and check login with the new password first.");
            }
            if (!rollbackConfirmed) {
                throw new ResetFailure("Reset could not finish and rollback could not be confirmed. Do not retry automatically; review the local database state first.");
            }
            if (error instanceof ResetFailure safeError) throw safeError;
            throw new ResetFailure("Reset failed before commit; transaction rolled back. No JDBC details, password, or hash were logged.");
        } finally {
            try { connection.close(); }
            catch (SQLException closeError) {
                if (committed) System.err.println("The reset was committed, but connection cleanup reported an error. Do not repeat the reset for that reason.");
            }
        }
    }

    private static void requireLocalTarget(Connection connection) throws SQLException, ResetFailure {
        String jdbcUrl = connection.getMetaData().getURL();
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new ResetFailure("Reset refused: a direct local MySQL connection is required.");
        }
        URI uri;
        try { uri = URI.create(jdbcUrl.substring("jdbc:".length())); }
        catch (IllegalArgumentException error) { throw new ResetFailure("Reset refused: the configured connection could not be validated as local."); }
        if (uri.getHost() == null || !Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) {
            throw new ResetFailure("Reset refused: only localhost or a literal loopback address is allowed.");
        }
        try (var statement = connection.prepareStatement("SELECT DATABASE()"); var rows = statement.executeQuery()) {
            if (!rows.next() || !DATABASE.equals(rows.getString(1))) {
                throw new ResetFailure("Reset refused: the existing configuration must select refuge_animalier.");
            }
        }
    }

    private static void requireTransactionalTableWithoutTriggers(Connection connection) throws SQLException, ResetFailure {
        try (var statement = connection.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'admin'")) {
            statement.setString(1, DATABASE);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !"InnoDB".equalsIgnoreCase(rows.getString(1))) {
                    throw new ResetFailure("Reset refused: the administrator table must use InnoDB for rollback safety.");
                }
            }
        }
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ? AND EVENT_OBJECT_TABLE = 'admin'")) {
            statement.setString(1, DATABASE);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getLong(1) != 0) {
                    throw new ResetFailure("Reset refused: administrator-table triggers require review to exclude side effects.");
                }
            }
        }
    }

    private static final class ResetFailure extends Exception {
        ResetFailure(String message) { super(message); }
    }
}

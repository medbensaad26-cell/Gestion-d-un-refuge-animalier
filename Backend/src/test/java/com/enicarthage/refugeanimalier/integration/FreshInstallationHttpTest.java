package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.config.FreshInstallation;
import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.PasswordUtils;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.enicarthage.refugeanimalier.service.AuthService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Uses ONLY the owned disposable MySQL instance. Never reads production settings. */
class FreshInstallationHttpTest extends MysqlTestSupport {
    @Override @BeforeEach void resetIsolatedData() throws SQLException {
        try (var c = connection()) {
            if (!c.getCatalog().matches("refuge_animalier_test_[a-f0-9]{32}")) throw new SQLException("Disposable database required");
            try (var s = c.createStatement()) {
                // Own test schema only. Order preserves FK checks; never disable them.
                for (String table : new String[]{"dossier_medical", "annonce", "demande", "animal", "admin", "setup_sentinel"}) {
                    s.execute("DROP TABLE IF EXISTS " + table);
                }
            }
        }
    }

    @Test void realShippedSchemaAndProvisionedAdministratorWorkThroughTheActualHttpApi() throws Exception {
        String password = UUID.randomUUID().toString();
        long adminId;
        try (var c = connection()) { adminId = FreshInstallation.initialize(c, "Professor Demo", "professor@example.test", password.toCharArray()); }
        assertTrue(adminId > 0);
        assertEquals(1, count("SELECT COUNT(*) FROM admin"));
        for (String table : new String[]{"animal", "dossier_medical", "annonce", "demande"}) assertEquals(0, count("SELECT COUNT(*) FROM " + table));
        assertEquals(5, count("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND ENGINE='InnoDB'"));
        assertEquals(6, count("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()"));
        String hash = scalar("SELECT mot_de_passe FROM admin");
        assertTrue(hash.startsWith("$2a$12$"));
        assertTrue(PasswordUtils.verifier(password, hash));
        assertFalse(hash.equals(password));

        var server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        String token = null;
        try (var client = HttpClient.newHttpClient()) {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            JsonObject credentials = new JsonObject(); credentials.addProperty("email", "professor@example.test"); credentials.addProperty("motDePasse", password);
            var login = send(client, base, "POST", "/api/auth/login", credentials.toString(), null);
            assertEquals(200, login.statusCode());
            JsonObject result = JsonParser.parseString(login.body()).getAsJsonObject();
            token = result.get("token").getAsString();
            assertEquals(adminId, result.getAsJsonObject("admin").get("id").getAsLong());
            assertFalse(login.body().contains(password)); assertFalse(login.body().contains(hash));
            var dashboard = send(client, base, "GET", "/api/admin/dashboard", null, token);
            assertEquals(200, dashboard.statusCode());
            var summary = JsonParser.parseString(dashboard.body()).getAsJsonObject();
            for (String key : new String[]{"animaux", "annonces", "demandes"}) assertEquals(0, summary.get(key).getAsInt());
            assertEquals(200, send(client, base, "GET", "/api/animaux", null, null).statusCode());
            var animal = "{\"nom\":\"First arrival\",\"espece\":\"Chat\",\"age\":1,\"sexe\":\"FEMELLE\",\"statut\":\"DISPONIBLE\"}";
            assertEquals(201, send(client, base, "POST", "/api/animaux", animal, token).statusCode());
            assertEquals(1, count("SELECT COUNT(*) FROM animal"));
            assertEquals(1, count("SELECT COUNT(*) FROM dossier_medical"));
            assertEquals(adminId, count("SELECT admin_id FROM animal"));
            credentials.addProperty("motDePasse", UUID.randomUUID().toString());
            assertEquals(401, send(client, base, "POST", "/api/auth/login", credentials.toString(), null).statusCode());
            assertEquals(200, send(client, base, "POST", "/api/auth/logout", null, token).statusCode());
            assertEquals(401, send(client, base, "GET", "/api/admin/dashboard", null, token).statusCode());
        } finally { SessionManager.supprimerSession(token); server.stop(0); }
    }

    @Test void rerunningSetupCannotResetReplaceOrAddAnAdministrator() throws Exception {
        String password = UUID.randomUUID().toString();
        try (var c = connection()) { FreshInstallation.initialize(c, "First admin", "first@example.test", password.toCharArray()); }
        String before = scalar("SELECT mot_de_passe FROM admin");
        try (var c = connection()) {
            assertThrows(IllegalStateException.class, () -> FreshInstallation.initialize(c, "Other admin", "other@example.test", UUID.randomUUID().toString().toCharArray()));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM admin"));
        assertTrue(before.equals(scalar("SELECT mot_de_passe FROM admin")));
        assertNotNull(new AuthService().connecter("first@example.test", password));
    }

    @Test void nonemptyDatabaseIsRefusedWithoutTouchingItsRecords() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("CREATE TABLE setup_sentinel (id INT PRIMARY KEY) ENGINE=InnoDB");
            s.executeUpdate("INSERT INTO setup_sentinel VALUES (7)");
            assertThrows(IllegalStateException.class, () -> FreshInstallation.initialize(c, "Admin", "admin@example.test", UUID.randomUUID().toString().toCharArray()));
        }
        assertEquals(7, count("SELECT id FROM setup_sentinel"));
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"));
    }

    @Test void invalidPasswordOrIdentityCreatesNoTablesAndClearsInput() throws Exception {
        char[] invalid = new char[]{'x'};
        try (var c = connection()) {
            assertThrows(IllegalArgumentException.class, () -> FreshInstallation.initialize(c, "Admin", "admin@example.test", invalid));
            assertThrows(IllegalArgumentException.class, () -> FreshInstallation.initialize(c, "", "invalid", UUID.randomUUID().toString().toCharArray()));
        }
        assertEquals('\0', invalid[0]);
        assertEquals(0, count("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"));
    }

    @Test void partialSchemaIsRefusedEvenIfItsAdministratorTableIsEmpty() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute(FreshInstallation.schemaStatements().getFirst());
            assertThrows(IllegalStateException.class, () -> FreshInstallation.initialize(c, "Admin", "admin@example.test", UUID.randomUUID().toString().toCharArray()));
        }
        assertEquals(0, count("SELECT COUNT(*) FROM admin"));
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"));
    }

    @Test void administratorInsertionFailureLeavesNoAccountAndCannotBeRetriedAsAMigration() throws Exception {
        try (var actual = connection()) {
            Connection failing = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql && sql.startsWith("INSERT INTO admin")) throw new SQLException("Forced isolated insertion failure");
                try { return method.invoke(actual, args); } catch (InvocationTargetException e) { throw e.getCause(); }
            });
            assertThrows(SQLException.class, () -> FreshInstallation.initialize(failing, "Admin", "admin@example.test", UUID.randomUUID().toString().toCharArray()));
        }
        assertEquals(0, count("SELECT COUNT(*) FROM admin"));
        assertEquals(5, count("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"));
        try (var c = connection()) { assertThrows(IllegalStateException.class, () -> FreshInstallation.initialize(c, "Admin", "admin@example.test", UUID.randomUUID().toString().toCharArray())); }
    }

    private HttpResponse<String> send(HttpClient client, URI base, String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}

package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.controller.AnimalController;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.enicarthage.refugeanimalier.service.ImageAnimalService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AnimalImageHttpTest extends MysqlTestSupport {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 7, 8, 9};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 7, 8, 9};

    @TempDir
    Path uploads;

    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String token;
    private long animalId;

    @BeforeEach
    void start() throws Exception {
        useSingleAdministrator();
        animalId = animal("DISPONIBLE");
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/animaux", new AnimalController(new ImageAnimalService(uploads)));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        token = SessionManager.creerSession(1);
    }

    @AfterEach
    void stop() {
        SessionManager.supprimerSession(token);
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }

    private String path() {
        return "/api/animaux/" + animalId + "/image";
    }

    private HttpResponse<byte[]> send(String method, String path, byte[] body, String type, String session) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (type != null) request.header("Content-Type", type);
        if (session != null) request.header("Authorization", "Bearer " + session);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> upload(byte[] body, String type) throws Exception {
        return send("PUT", path(), body, type, token);
    }

    private static JsonObject json(HttpResponse<byte[]> response) {
        return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private JsonObject animalJson() throws Exception {
        return json(send("GET", "/api/animaux/" + animalId, null, null, null));
    }

    private long storedFiles() throws Exception {
        try (var files = Files.list(uploads)) {
            return files.count();
        }
    }

    @Test
    void uploadedPhotoIsPublicAndAppearsInListAndDetail() throws Exception {
        var result = upload(PNG, "image/png");
        assertEquals(200, result.statusCode());
        String url = json(result).get("imageUrl").getAsString();
        assertTrue(url.startsWith(path() + "?v="));

        var image = send("GET", url, null, null, null);
        assertEquals(200, image.statusCode());
        assertArrayEquals(PNG, image.body());
        assertEquals("image/png", image.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("nosniff", image.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertTrue(image.headers().firstValue("Content-Security-Policy").orElseThrow().contains("sandbox"));

        assertEquals(url, animalJson().get("imageUrl").getAsString());
        var list = JsonParser.parseString(new String(send("GET", "/api/animaux", null, null, null).body(), StandardCharsets.UTF_8)).getAsJsonArray();
        assertEquals(url, list.get(0).getAsJsonObject().get("imageUrl").getAsString());
    }

    @Test
    void animalsWithoutPhotoKeepTheirExistingResponse() throws Exception {
        var animal = animalJson();
        assertFalse(animal.has("imageUrl"));
        assertEquals("Milo", animal.get("nom").getAsString());
        assertEquals(animalId, animal.get("id").getAsLong());
        assertEquals(404, send("GET", path(), null, null, null).statusCode());
    }

    @Test
    void creatingAnAnimalReturnsTheIdNeededForItsPhoto() throws Exception {
        String body = "{\"nom\":\"Nala\",\"espece\":\"Chat\",\"age\":1,\"sexe\":\"FEMELLE\",\"statut\":\"DISPONIBLE\"}";
        var created = send("POST", "/api/animaux", body.getBytes(StandardCharsets.UTF_8), "application/json", token);
        assertEquals(201, created.statusCode());
        assertEquals("Animal ajoute", json(created).get("message").getAsString());
        long id = json(created).get("id").getAsLong();
        assertEquals("Nala", scalar("SELECT nom FROM animal WHERE id = ?", id));
        assertEquals(200, send("PUT", "/api/animaux/" + id + "/image", JPEG, "image/jpeg", token).statusCode());
    }

    @Test
    void onlyAValidAdministratorSessionCanChangePhotos() throws Exception {
        assertEquals(401, send("PUT", path(), PNG, "image/png", null).statusCode());
        assertEquals(401, send("PUT", path(), PNG, "image/png", "forged").statusCode());
        assertEquals(0, storedFiles());
        assertEquals(200, upload(PNG, "image/png").statusCode());
        assertEquals(401, send("DELETE", path(), null, null, null).statusCode());
        assertEquals(200, send("GET", path(), null, null, null).statusCode());
    }

    @Test
    void invalidUploadsAreRefusedAndKeepThePreviousPhoto() throws Exception {
        assertEquals(200, upload(PNG, "image/png").statusCode());
        assertEquals(400, upload(new byte[0], "image/png").statusCode());
        assertEquals(415, upload("<svg/>".getBytes(StandardCharsets.UTF_8), "image/svg+xml").statusCode());
        assertEquals(415, upload("<html>".getBytes(StandardCharsets.UTF_8), "image/png").statusCode());
        assertEquals(415, upload(JPEG, "image/png").statusCode());
        assertEquals(413, upload(Arrays.copyOf(PNG, ImageAnimalService.TAILLE_MAX + 1), "image/png").statusCode());
        assertArrayEquals(PNG, send("GET", path(), null, null, null).body());
        assertEquals(1, storedFiles());
    }

    @Test
    void routesRejectUnknownAnimalsBadIdsAndOtherMethods() throws Exception {
        assertEquals(404, send("PUT", "/api/animaux/999999/image", PNG, "image/png", token).statusCode());
        assertEquals(404, send("DELETE", "/api/animaux/999999/image", null, null, token).statusCode());
        assertEquals(0, storedFiles());
        for (String id : new String[]{"0", "-1", "abc", "1%2F..%2F2"}) {
            assertEquals(400, send("GET", "/api/animaux/" + id + "/image", null, null, null).statusCode(), id);
        }
        assertEquals(400, send("GET", "/api/animaux/image", null, null, null).statusCode());
        var post = send("POST", path(), PNG, "image/png", token);
        assertEquals(405, post.statusCode());
        assertEquals("GET, PUT, DELETE", post.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void replacingChangesTheServedFormatAndRemovingRestoresTheFallback() throws Exception {
        assertEquals(200, upload(PNG, "image/png").statusCode());
        assertEquals(200, upload(JPEG, "image/jpeg").statusCode());
        var image = send("GET", path(), null, null, null);
        assertEquals("image/jpeg", image.headers().firstValue("Content-Type").orElseThrow());
        assertArrayEquals(JPEG, image.body());
        assertEquals(1, storedFiles());

        assertEquals(200, send("DELETE", path(), null, null, token).statusCode());
        assertEquals(200, send("DELETE", path(), null, null, token).statusCode());
        assertEquals(404, send("GET", path(), null, null, null).statusCode());
        assertFalse(animalJson().has("imageUrl"));
    }

    @Test
    void deletingAnAnimalDeletesItsPhoto() throws Exception {
        assertEquals(200, upload(PNG, "image/png").statusCode());
        assertEquals(200, send("DELETE", "/api/animaux/" + animalId, null, null, token).statusCode());
        assertEquals(0, storedFiles());
        assertEquals(404, send("GET", path(), null, null, null).statusCode());
    }

    @Test
    void refusedAnimalDeletionKeepsItsPhoto() throws Exception {
        assertEquals(200, upload(PNG, "image/png").statusCode());
        try (var c = connection(); var s = c.prepareStatement("UPDATE animal SET statut='ADOPTE' WHERE id=?")) {
            s.setLong(1, animalId);
            s.executeUpdate();
        }
        assertEquals(409, send("DELETE", "/api/animaux/" + animalId, null, null, token).statusCode());
        assertEquals(1, storedFiles());
        assertEquals(200, send("GET", path(), null, null, null).statusCode());
    }

    @Test
    void photosDoNotChangeTheAnimalRow() throws Exception {
        String before = scalar("SELECT CONCAT_WS('|', nom, espece, age, sexe, statut, admin_id) FROM animal WHERE id = ?", animalId);
        assertEquals(200, upload(PNG, "image/png").statusCode());
        assertEquals(before, scalar("SELECT CONCAT_WS('|', nom, espece, age, sexe, statut, admin_id) FROM animal WHERE id = ?", animalId));
    }
}

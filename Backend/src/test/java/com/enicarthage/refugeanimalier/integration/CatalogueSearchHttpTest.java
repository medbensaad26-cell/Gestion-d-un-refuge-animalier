package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.*;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class CatalogueSearchHttpTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String token;
    private long cat, dog, publicCat, publicDog, privateCat;

    @BeforeEach
    void start() throws Exception {
        cat = animal("DISPONIBLE");
        dog = animal("EN_SOIN");
        try (var c = connection(); var s = c.prepareStatement(
                "UPDATE animal SET nom='Rex', espece='Chien', race='Labrador' WHERE id=?")) {
            s.setLong(1, dog); s.executeUpdate();
        }
        publicCat = announcement(cat, "Adoptez Milo", "Doux et câlin", "PUBLIEE");
        publicDog = announcement(dog, "Une famille pour Rex", "Calme et sociable", "PUBLIEE");
        privateCat = announcement(cat, "PRIVATE SECRET", "Internal-only fixture", "INTERNE");
        announcement(cat, "Noncanonical publication", "Hidden fixture", "publiee");
        server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        token = SessionManager.creerSession(2);
    }

    @AfterEach
    void stop() {
        SessionManager.supprimerSession(token);
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }

    @Test
    void publicCatalogueAndDetailsExposeOnlyExactPublishedStatus() throws Exception {
        var list = get("/api/annonces", null);
        assertEquals(Set.of(publicCat, publicDog), ids(list));
        assertFalse(list.body().contains("PRIVATE SECRET"));
        assertEquals(404, get("/api/annonces/" + privateCat, null).statusCode());
        assertEquals(404, get("/api/annonces/999999", null).statusCode());
        assertEquals(200, get("/api/annonces/" + publicCat, null).statusCode());
        assertEquals("Authorization", list.headers().firstValue("Vary").orElseThrow());
        assertEquals("no-store", list.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void authenticatedAdministratorCanConsultAllStatusesAndPrivateDetail() throws Exception {
        assertEquals(4, ids(get("/api/annonces", "Bearer " + token)).size());
        var detail = get("/api/annonces/" + privateCat, "Bearer " + token);
        assertEquals(200, detail.statusCode());
        assertEquals("PRIVATE SECRET", JsonParser.parseString(detail.body()).getAsJsonObject().get("titre").getAsString());
        assertEquals("no-store", detail.headers().firstValue("Cache-Control").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer invalid", "Basic invalid"})
    void suppliedInvalidAuthorizationCannotAccessAnnouncementListsOrDetails(String authorization) throws Exception {
        for (String path : new String[]{"/api/annonces", "/api/annonces/" + publicCat, "/api/annonces/" + privateCat}) {
            var response = get(path, authorization);
            assertEquals(401, response.statusCode());
            assertFalse(response.body().contains("PRIVATE SECRET"));
        }
    }

    @Test
    void loggedOutSessionCannotReadPrivateAnnouncements() throws Exception {
        SessionManager.supprimerSession(token);
        assertEquals(401, get("/api/annonces", "Bearer " + token).statusCode());
        assertEquals(401, get("/api/annonces/" + privateCat, "Bearer " + token).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mIlO", "Doux", "câlin", "Europeen"})
    void announcementSearchCoversTitleDescriptionAndLinkedAnimal(String term) throws Exception {
        assertEquals(Set.of(publicCat), ids(get("/api/annonces?q=" + encode(term), null)));
    }

    @Test
    void filtersCombineAndApplyOnServerWithVisibilityRestriction() throws Exception {
        assertEquals(Set.of(publicDog), ids(get("/api/annonces?espece=chien&statutAnimal=en_soin&q=calme", null)));
        assertEquals(Set.of(), ids(get("/api/annonces?espece=chat&statutAnimal=EN_SOIN", null)));
        assertEquals(Set.of(publicCat), ids(get("/api/annonces?espece=chat", null)));
        assertEquals(Set.of(privateCat), ids(get("/api/annonces?q=PRIVATE", "Bearer " + token)));
        assertEquals(Set.of(), ids(get("/api/annonces?q=PRIVATE", null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mIlO", "CHAT", "Europeen"})
    void animalSearchMatchesNameSpeciesAndBreed(String term) throws Exception {
        assertEquals(Set.of(cat), ids(get("/api/animaux?q=" + encode(term), null)));
    }

    @Test
    void animalFilteringKeepsAvailableAndCareAnimalsBrowsable() throws Exception {
        assertEquals(Set.of(cat, dog), ids(get("/api/animaux", null)));
        assertEquals(Set.of(dog), ids(get("/api/animaux?espece=Chien&statutAnimal=EN_SOIN", null)));
        assertEquals(Set.of(cat), ids(get("/api/animaux?statutAnimal=DISPONIBLE&q=milo", null)));
        assertEquals(Set.of(), ids(get("/api/animaux?statutAnimal=ADOPTE", null)));
        assertEquals(200, get("/api/animaux/" + cat, null).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "' OR 1=1 --"})
    void searchTermsAreLiteralDataWithoutWildcardsOrSqlInjection(String term) throws Exception {
        assertEquals(Set.of(), ids(get("/api/animaux?q=" + encode(term), null)));
        assertEquals(Set.of(), ids(get("/api/annonces?q=" + encode(term), null)));
        assertEquals(2, count("SELECT COUNT(*) FROM animal"));
        assertEquals(4, count("SELECT COUNT(*) FROM annonce"));
    }

    @Test
    void literalPercentUnderscoreAndUnicodeSearchCanMatchRealText() throws Exception {
        long id = announcement(cat, "100% _special_ 🐾", null, "PUBLIEE");
        for (String term : new String[]{"100%", "_special_", "🐾"}) {
            assertEquals(Set.of(id), ids(get("/api/annonces?q=" + encode(term), null)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"q=a&q=b", "espece=chat&espece=chien", "statut=INTERNE", "adminId=1", "statutAnimal=UNKNOWN"})
    void duplicateUnknownAndInvalidFiltersReturn400(String query) throws Exception {
        for (String endpoint : new String[]{"/api/animaux", "/api/annonces"}) {
            var response = get(endpoint + "?" + query, null);
            assertEquals(400, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
        }
    }

    @Test
    void criteriaHaveLengthLimitsAndWhitespaceMeansNoFilter() throws Exception {
        for (String endpoint : new String[]{"/api/animaux", "/api/annonces"}) {
            assertEquals(400, get(endpoint + "?q=" + "x".repeat(151), null).statusCode());
            assertEquals(400, get(endpoint + "?espece=" + "x".repeat(101), null).statusCode());
            assertEquals(ids(get(endpoint, null)), ids(get(endpoint + "?q=+++&espece=&statutAnimal=", null)));
        }
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10)).GET();
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Set<Long> ids(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response::body);
        Set<Long> ids = new HashSet<>();
        for (var row : JsonParser.parseString(response.body()).getAsJsonArray()) ids.add(row.getAsJsonObject().get("id").getAsLong());
        return ids;
    }

    private long announcement(long animalId, String title, String description, String status) throws Exception {
        try (var c = connection(); var s = c.prepareStatement("INSERT INTO annonce "
                + "(titre, description, date_publication, statut, admin_id, animal_id) VALUES (?, ?, '2026-11-08', ?, 1, ?)", Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, title); s.setString(2, description); s.setString(3, status); s.setLong(4, animalId);
            s.executeUpdate();
            try (var keys = s.getGeneratedKeys()) { assertTrue(keys.next()); return keys.getLong(1); }
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}

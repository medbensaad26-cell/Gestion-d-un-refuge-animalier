package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.PasswordUtils;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Acceptance journey across every class and both PDF actors, against disposable MySQL. */
class DiagramLifecycleHttpTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String token;

    @BeforeEach void start() throws Exception {
        useSingleAdministrator();
        server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0)); server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newHttpClient();
    }
    @AfterEach void stop() { SessionManager.supprimerSession(token); if (server != null) server.stop(0); if (client != null) client.close(); }

    @Test void completeDiagramJourneyPreservesPublicPrivacySoleAdministratorAndAdoptionHistory() throws Exception {
        String password = UUID.randomUUID().toString();
        try (var c = connection(); var s = c.prepareStatement("UPDATE admin SET id=17,mot_de_passe=? WHERE id=1")) { s.setString(1, PasswordUtils.hacher(password)); s.executeUpdate(); }
        var credentials = new JsonObject(); credentials.addProperty("email", "admin1@example.test"); credentials.addProperty("motDePasse", password);
        var login = send("POST", "/api/auth/login", credentials.toString(), null);
        assertEquals(200, login.statusCode()); token = json(login).get("token").getAsString();
        assertEquals(17, json(login).getAsJsonObject("admin").get("id").getAsLong());
        assertFalse(login.body().contains(password));

        var animal = new JsonObject();
        animal.addProperty("nom", "Milo parcours final"); animal.addProperty("espece", "Chat"); animal.addProperty("race", "Européen"); animal.addProperty("age", 2);
        animal.addProperty("sexe", "MALE"); animal.addProperty("etatSante", "Bon état"); animal.addProperty("statut", "DISPONIBLE"); animal.addProperty("adminId", 999999);
        assertEquals(401, send("POST", "/api/animaux", animal.toString(), null).statusCode());
        assertEquals(201, send("POST", "/api/animaux", animal.toString(), token).statusCode());
        long animalId = count("SELECT id FROM animal");
        assertEquals("17", scalar("SELECT admin_id FROM animal WHERE id=?", animalId));
        assertEquals(1, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id=?", animalId));
        String profile = "/api/animaux/" + animalId, medical = profile + "/dossier-medical";
        var publicProfile = send("GET", profile, "", null); assertEquals(200, publicProfile.statusCode());
        for (String field : new String[]{"nom","espece","race","age","sexe","etatSante","statut"}) assertTrue(json(publicProfile).has(field));
        assertEquals(401, send("GET", medical, "", null).statusCode());
        var blankMedical = json(send("GET", medical, "", token));
        assertTrue(!blankMedical.has("vaccins") || blankMedical.get("vaccins").isJsonNull());
        String medicalJson = "{\"vaccins\":\"Note médicale privée de test\",\"traitements\":null,\"observations\":\"Contrôle\\nSuivi\",\"dateMiseAJour\":\"2026-11-08\"}";
        assertEquals(200, send("PUT", medical, medicalJson, token).statusCode());
        assertEquals(409, send("POST", medical, medicalJson, token).statusCode());
        assertEquals(405, send("DELETE", medical, "", token).statusCode());
        assertFalse(send("GET", profile, "", null).body().contains("Note médicale privée"));
        assertEquals(1, JsonParser.parseString(send("GET", "/api/animaux?q=Milo&espece=Chat&statutAnimal=DISPONIBLE", "", null).body()).getAsJsonArray().size());

        var announcement = new JsonObject();
        announcement.addProperty("titre", "Une famille pour Milo"); announcement.addProperty("description", "Un compagnon de démonstration."); announcement.addProperty("datePublication", "2026-11-08");
        announcement.addProperty("statut", "INTERNE"); announcement.addProperty("animalId", animalId); announcement.addProperty("adminId", 999999);
        assertEquals(201, send("POST", "/api/annonces", announcement.toString(), token).statusCode());
        long announcementId = count("SELECT id FROM annonce");
        assertEquals("17", scalar("SELECT admin_id FROM annonce WHERE id=?", announcementId));
        assertEquals(0, JsonParser.parseString(send("GET", "/api/annonces", "", null).body()).getAsJsonArray().size());
        assertEquals(404, send("GET", "/api/annonces/" + announcementId, "", null).statusCode());
        announcement.addProperty("statut", "PUBLIEE");
        assertEquals(200, send("PUT", "/api/annonces/" + announcementId, announcement.toString(), token).statusCode());
        var published = JsonParser.parseString(send("GET", "/api/annonces?q=Milo&espece=Chat&statutAnimal=DISPONIBLE", "", null).body()).getAsJsonArray();
        assertEquals(1, published.size()); assertEquals(animalId, published.get(0).getAsJsonObject().get("animalId").getAsLong());

        var adoption = new JsonObject();
        adoption.addProperty("nomDemandeur", "Camille parcours final"); adoption.addProperty("email", "camille@example.test"); adoption.addProperty("telephone", "0102030405");
        adoption.addProperty("dateDemande", "2026-11-08"); adoption.addProperty("animalId", animalId); adoption.addProperty("adminId", 999999); adoption.addProperty("statut", "ACCEPTEE");
        assertEquals(201, send("POST", "/api/demandes", adoption.toString(), null).statusCode());
        long requestId = count("SELECT id FROM demande");
        adoption.addProperty("nomDemandeur", "Autre parcours final"); adoption.addProperty("email", "autre@example.test");
        assertEquals(201, send("POST", "/api/demandes", adoption.toString(), null).statusCode());
        long otherId = count("SELECT MAX(id) FROM demande");
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
        assertEquals("17", scalar("SELECT admin_id FROM demande WHERE id=?", requestId));
        for (String route : new String[]{"/api/demandes", "/api/demandes/" + requestId, "/api/admin/dashboard"}) assertEquals(401, send("GET", route, "", null).statusCode());
        var dashboard = json(send("GET", "/api/admin/dashboard", "", token));
        assertEquals(1, dashboard.get("animaux").getAsInt()); assertEquals(1, dashboard.get("annonces").getAsInt()); assertEquals(2, dashboard.get("demandes").getAsInt());
        assertFalse(dashboard.toString().contains("camille@example.test"));
        assertEquals(2, JsonParser.parseString(send("GET", "/api/demandes", "", token).body()).getAsJsonArray().size());
        assertEquals("camille@example.test", json(send("GET", "/api/demandes/" + requestId, "", token)).get("email").getAsString());
        assertEquals(200, send("PATCH", "/api/demandes/" + requestId + "/accepter", "", token).statusCode());
        assertEquals("ADOPTE", json(send("GET", profile, "", null)).get("statut").getAsString());
        assertEquals(409, send("PATCH", "/api/demandes/" + otherId + "/accepter", "", token).statusCode());
        assertEquals(200, send("PATCH", "/api/demandes/" + otherId + "/refuser", "", token).statusCode());
        assertEquals(409, send("POST", "/api/demandes", adoption.toString(), null).statusCode());
        assertEquals(409, send("DELETE", profile, "", token).statusCode());
        assertEquals(409, send("DELETE", "/api/demandes/" + requestId, "", token).statusCode());
        assertEquals(409, send("PUT", profile, animal.toString(), token).statusCode());
        animal.addProperty("statut", "ADOPTE"); animal.addProperty("race", "Race précisée");
        assertEquals(200, send("PUT", profile, animal.toString(), token).statusCode());
        assertEquals("Note médicale privée de test", scalar("SELECT vaccins FROM dossier_medical WHERE animal_id=?", animalId));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id=? AND statut='ACCEPTEE'", animalId));
        assertEquals("17", scalar("SELECT admin_id FROM demande WHERE id=?", requestId));
        assertEquals(200, send("POST", "/api/auth/logout", "", token).statusCode());
        for (String route : new String[]{"/api/admin/dashboard", "/api/demandes", medical}) assertEquals(401, send("GET", route, "", token).statusCode());
        assertEquals(200, send("GET", profile, "", null).statusCode());
        assertEquals(200, send("GET", "/api/annonces/" + announcementId, "", null).statusCode());
        assertEquals(1, count("SELECT COUNT(*) FROM admin"));
    }

    private JsonObject json(HttpResponse<String> response) { return JsonParser.parseString(response.body()).getAsJsonObject(); }
    private HttpResponse<String> send(String method, String path, String body, String value) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (value != null) request.header("Authorization", "Bearer " + value);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.controller.DemandeController;
import com.enicarthage.refugeanimalier.dao.DemandeDAO;
import com.enicarthage.refugeanimalier.model.Demande;
import com.enicarthage.refugeanimalier.model.StatutDemande;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.enicarthage.refugeanimalier.service.DemandeService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PublicAdoptionRequestTest extends MysqlTestSupport {
    private HttpServer server;
    private ExecutorService httpExecutor;
    private HttpClient client;
    private URI base;
    private String token;

    @BeforeEach
    void startServer() throws Exception {
        useSingleAdministrator();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/demandes", new DemandeController());
        httpExecutor = Executors.newFixedThreadPool(4);
        server.setExecutor(httpExecutor);
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        token = SessionManager.creerSession(1);
    }

    @AfterEach
    void stopServer() throws Exception {
        SessionManager.supprimerSession(token);
        if (server != null) server.stop(0);
        if (client != null) client.close();
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            assertTrue(httpExecutor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void anonymousSubmissionStoresPendingRequestWithSoleAdministratorAndReturnsOnlyMessage() throws Exception {
        long animalId = animal("DISPONIBLE");
        JsonObject payload = applicant(animalId);
        payload.addProperty("nomDemandeur", "  Public applicant  ");

        HttpResponse<String> response = submit(payload.toString(), null);

        assertPublicSuccess(response);
        assertEquals("Public applicant", scalar("SELECT nom_demandeur FROM demande WHERE animal_id = ?", animalId));
        assertEquals("public@example.test", scalar("SELECT email FROM demande WHERE animal_id = ?", animalId));
        assertEquals("0102030405", scalar("SELECT telephone FROM demande WHERE animal_id = ?", animalId));
        assertEquals("2026-11-08", scalar("SELECT date_demande FROM demande WHERE animal_id = ?", animalId));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE animal_id = ?", animalId));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE animal_id = ?", animalId));
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\"adminId\":1,\"statut\":\"ACCEPTEE\"",
            ",\"adminId\":-1,\"statut\":\"REFUSEE\"",
            ",\"adminId\":{\"id\":2},\"statut\":{\"decision\":\"ACCEPTEE\"}",
            ",\"adminId\":[2],\"statut\":[\"ACCEPTEE\"]",
            ",\"adminId\":\"forged\",\"statut\":123",
            ",\"adminId\":true,\"statut\":false",
            ",\"adminId\":null,\"statut\":null",
            ",\"adminId\":999999,\"statut\":\"UNKNOWN\""
    })
    void anonymousSubmissionCompletelyIgnoresServerOwnedFields(String fields) throws Exception {
        long animalId = animal("DISPONIBLE");
        String payload = applicant(animalId).toString();
        payload = payload.substring(0, payload.length() - 1) + fields + "}";

        assertPublicSuccess(submit(payload, null));

        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE animal_id = ?", animalId));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE animal_id = ?", animalId));
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    @Test
    void authenticatedSubmissionAlsoUsesSoleAdministrator() throws Exception {
        long animalId = animal("DISPONIBLE");
        JsonObject payload = applicant(animalId);
        payload.add("adminId", JsonParser.parseString("{\"forged\":1}"));
        payload.add("statut", JsonParser.parseString("[\"ACCEPTEE\"]"));

        assertPublicSuccess(submit(payload.toString(), "Bearer " + token));

        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE animal_id = ?", animalId));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE animal_id = ?", animalId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer ", "Bearer invalid-session", "Basic invalid-session"})
    void suppliedInvalidAuthorizationNeverFallsBackToAnonymous(String authorization) throws Exception {
        long animalId = animal("DISPONIBLE");

        assertPrivateError(401, submit(applicant(animalId).toString(), authorization));

        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
    }

    @ParameterizedTest(name = "invalid applicant field {0}: {1}")
    @MethodSource("invalidApplicantFields")
    void invalidApplicantDetailsAndIdentifiersReturn400(String field, String rawValue) throws Exception {
        long animalId = animal("DISPONIBLE");
        JsonObject payload = applicant(animalId);
        if (rawValue.equals("MISSING")) {
            payload.remove(field);
        } else {
            payload.add(field, JsonParser.parseString(rawValue));
        }

        assertPrivateError(400, submit(payload.toString(), null));

        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
    }

    static Stream<Arguments> invalidApplicantFields() {
        return Stream.of(
                Arguments.of("nomDemandeur", "MISSING"),
                Arguments.of("nomDemandeur", "null"),
                Arguments.of("nomDemandeur", "\"   \""),
                Arguments.of("nomDemandeur", "42"),
                Arguments.of("nomDemandeur", "\"" + "x".repeat(151) + "\""),
                Arguments.of("email", "MISSING"),
                Arguments.of("email", "null"),
                Arguments.of("email", "\"invalid-email\""),
                Arguments.of("email", "42"),
                Arguments.of("email", "\"" + "x".repeat(244) + "@example.test\""),
                Arguments.of("telephone", "MISSING"),
                Arguments.of("telephone", "null"),
                Arguments.of("telephone", "\"   \""),
                Arguments.of("telephone", "123"),
                Arguments.of("telephone", "\"" + "1".repeat(31) + "\""),
                Arguments.of("dateDemande", "MISSING"),
                Arguments.of("dateDemande", "null"),
                Arguments.of("dateDemande", "{}"),
                Arguments.of("dateDemande", "\"2026-02-30\""),
                Arguments.of("dateDemande", "\"0999-12-31\""),
                Arguments.of("dateDemande", "\"+10000-01-01\""),
                Arguments.of("animalId", "MISSING"),
                Arguments.of("animalId", "null"),
                Arguments.of("animalId", "0"),
                Arguments.of("animalId", "-1"),
                Arguments.of("animalId", "1.5"),
                Arguments.of("animalId", "\"1\""),
                Arguments.of("animalId", "true"),
                Arguments.of("animalId", "{}"),
                Arguments.of("animalId", "9223372036854775808")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[]", "null", "{", "42", "{} {}"})
    void malformedPublicBodiesReturn400WithoutCreatingRequests(String body) throws Exception {
        assertPrivateError(400, submit(body, null));
        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
    }

    @Test
    void missingAnimalReturns404() throws Exception {
        assertPrivateError(404, submit(applicant(999999).toString(), null));
        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN_SOIN", "ADOPTE"})
    void unavailableAnimalReturns409(String status) throws Exception {
        long animalId = animal(status);

        assertPrivateError(409, submit(applicant(animalId).toString(), null));

        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
        assertEquals(status, scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    @Test
    void legacyAcceptedHistoryPreventsSubmissionEvenIfAnimalIsMarkedAvailable() throws Exception {
        long animalId = animal("DISPONIBLE");
        long accepted = request(animalId);
        try (var connection = connection(); var statement = connection.prepareStatement(
                "UPDATE demande SET statut = 'ACCEPTEE' WHERE id = ?")) {
            statement.setLong(1, accepted);
            statement.executeUpdate();
        }

        assertPrivateError(409, submit(applicant(animalId).toString(), null));

        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id = ?", animalId));
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id = ?", accepted));
    }

    @Test
    void onlyExactCreationPathIsPublicAndPrivateRoutesNeverExposeApplicantData() throws Exception {
        long animalId = animal("DISPONIBLE");
        assertPublicSuccess(submit(applicant(animalId).toString(), null));
        long requestId = Long.parseLong(scalar("SELECT id FROM demande WHERE animal_id = ?", animalId));

        for (String[] route : new String[][]{
                {"GET", "/api/demandes"},
                {"GET", "/api/demandes/" + requestId},
                {"DELETE", "/api/demandes/" + requestId},
                {"PUT", "/api/demandes/" + requestId},
                {"PATCH", "/api/demandes/" + requestId + "/accepter"},
                {"PATCH", "/api/demandes/" + requestId + "/refuser"},
                {"POST", "/api/demandes/"},
                {"POST", "/api/demandes/" + requestId},
                {"POST", "/api/demandes-extra"}
        }) {
            assertPrivateError(401, send(route[0], route[1], "{}", null));
        }

        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", requestId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accepter", "refuser"})
    void authenticatedDecisionRecordsActualDecisionMakerForPublicRequest(String action) throws Exception {
        long animalId = animal("DISPONIBLE");
        assertPublicSuccess(submit(applicant(animalId).toString(), null));
        long requestId = Long.parseLong(scalar("SELECT id FROM demande WHERE animal_id = ?", animalId));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", requestId));

        HttpResponse<String> detail = send("GET", "/api/demandes/" + requestId, "", "Bearer " + token);
        assertStatus(200, detail);
        assertEquals(1, JsonParser.parseString(detail.body()).getAsJsonObject().get("adminId").getAsLong());
        // A later extra account may process an existing request without rewriting its initial recipient.
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO admin (id, nom, email, mot_de_passe) "
                    + "SELECT 2, 'Later fixture', 'later@example.test', mot_de_passe FROM admin WHERE id = 1");
        }
        SessionManager.supprimerSession(token);
        token = SessionManager.creerSession(2);
        assertStatus(200, send("PATCH", "/api/demandes/" + requestId + "/" + action,
                "{\"adminId\":1}", "Bearer " + token));

        assertEquals("2", scalar("SELECT admin_id FROM demande WHERE id = ?", requestId));
        assertEquals(action.equals("accepter") ? "ACCEPTEE" : "REFUSEE",
                scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals(action.equals("accepter") ? "ADOPTE" : "DISPONIBLE",
                scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    @Test
    void multipleAdministratorAccountsReturnConflictWithoutSavingOrDisclosingApplicant() throws Exception {
        long animalId = animal("DISPONIBLE");
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO admin (id, nom, email, mot_de_passe) "
                    + "SELECT 2, 'Second fixture', 'second@example.test', mot_de_passe FROM admin WHERE id = 1");
        }
        assertPrivateError(409, submit(applicant(animalId).toString(), null));
        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void serviceAndDaoRejectNonpositiveExplicitAdministratorIds(long adminId) throws Exception {
        long animalId = animal("DISPONIBLE");
        Demande demande = new Demande(null, "Public applicant", "public@example.test", "0102030405",
                LocalDate.of(2026, 11, 8), StatutDemande.EN_ATTENTE, animalId, adminId);

        assertThrows(IllegalArgumentException.class, () -> new DemandeService().ajouterDemande(demande));
        assertThrows(IllegalArgumentException.class, () -> new DemandeDAO().create(demande));
        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
    }

    @RepeatedTest(5)
    void anonymousSubmissionRacingApprovalRemainsAtomic() throws Exception {
        long animalId = animal("DISPONIBLE");
        long existingRequest = request(animalId);
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var publicSubmission = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return submit(applicant(animalId).toString(), null);
            });
            var approval = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return send("PATCH", "/api/demandes/" + existingRequest + "/accepter", "", "Bearer " + token);
            });
            HttpResponse<String> submitted = publicSubmission.get(25, TimeUnit.SECONDS);
            assertStatus(200, approval.get(25, TimeUnit.SECONDS));
            assertTrue(submitted.statusCode() == 201 || submitted.statusCode() == 409, submitted::body);
            if (submitted.statusCode() == 201) {
                assertPublicSuccess(submitted);
                assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id = ? AND admin_id = 1 AND statut = 'EN_ATTENTE'", animalId));
            } else {
                assertPrivateError(409, submitted);
                assertEquals(0, count("SELECT COUNT(*) FROM demande WHERE animal_id = ? AND statut = 'EN_ATTENTE'", animalId));
            }
        }

        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id = ?", existingRequest));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", existingRequest));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id = ? AND statut = 'ACCEPTEE'", animalId));
        assertPrivateError(409, submit(applicant(animalId).toString(), null));
    }

    private HttpResponse<String> submit(String body, String authorization) throws Exception {
        return send("POST", "/api/demandes", body, authorization);
    }

    private HttpResponse<String> send(String method, String path, String body, String authorization)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) builder.header("Authorization", authorization);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject applicant(long animalId) {
        return JsonParser.parseString("""
                {"nomDemandeur":"Public applicant","email":"public@example.test","telephone":"0102030405",
                 "dateDemande":"2026-11-08","animalId":%d}
                """.formatted(animalId)).getAsJsonObject();
    }

    private static void assertPublicSuccess(HttpResponse<String> response) {
        assertStatus(201, response);
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(Set.of("message"), result.keySet());
        assertEquals("Demande ajoutee", result.get("message").getAsString());
    }

    private static void assertPrivateError(int expected, HttpResponse<String> response) {
        assertStatus(expected, response);
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(Set.of("erreur"), result.keySet());
        assertFalse(response.body().contains("Public applicant"));
        assertFalse(response.body().contains("public@example.test"));
        assertFalse(response.body().contains("0102030405"));
    }

    private static void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response::body);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }
}

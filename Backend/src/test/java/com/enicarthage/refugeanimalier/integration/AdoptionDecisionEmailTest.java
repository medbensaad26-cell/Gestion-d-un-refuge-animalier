package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.config.EmailSettings;
import com.enicarthage.refugeanimalier.controller.DemandeController;
import com.enicarthage.refugeanimalier.dao.DemandeDAO;
import com.enicarthage.refugeanimalier.dto.DecisionAdoption;
import com.enicarthage.refugeanimalier.model.StatutDemande;
import com.enicarthage.refugeanimalier.notification.FauxServeurSmtp;
import com.enicarthage.refugeanimalier.notification.NotificateurDecision;
import com.enicarthage.refugeanimalier.notification.NotificateurEmailAsynchrone;
import com.enicarthage.refugeanimalier.notification.SmtpExpediteurEmail;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.enicarthage.refugeanimalier.service.DemandeService;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AdoptionDecisionEmailTest extends MysqlTestSupport {
    private final List<DecisionAdoption> notifications = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private HttpClient client;
    private String token;
    private long animalId, requestId;

    @BeforeEach
    void prepare() throws Exception {
        animalId = animal("DISPONIBLE");
        requestId = request(animalId);
        token = SessionManager.creerSession(2);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        SessionManager.supprimerSession(token);
        if (server != null) server.stop(0);
        client.close();
    }

    private void start(NotificateurDecision notificateur) throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/demandes", new DemandeController(new DemandeService(new DemandeDAO(), notificateur)));
        server.start();
    }

    private HttpResponse<String> decide(long id, String action, String session) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/api/demandes/" + id + "/" + action))
                .timeout(Duration.ofSeconds(10)).method("PATCH", HttpRequest.BodyPublishers.noBody());
        if (session != null) request.header("Authorization", "Bearer " + session);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void acceptanceNotifiesTheApplicantOnceWithStoredDetails() throws Exception {
        start(notifications::add);
        assertEquals(200, decide(requestId, "accepter", token).statusCode());
        assertEquals(List.of(new DecisionAdoption(requestId, StatutDemande.ACCEPTEE,
                "Adoptant", "adoptant@example.test", "Milo")), notifications);
    }

    @Test
    void refusalNotifiesTheApplicantOnce() throws Exception {
        start(notifications::add);
        assertEquals(200, decide(requestId, "refuser", token).statusCode());
        assertEquals(1, notifications.size());
        assertEquals(StatutDemande.REFUSEE, notifications.get(0).statut());
        assertEquals("adoptant@example.test", notifications.get(0).email());
    }

    @Test
    void decisionsThatAreNotSavedNotifyNobody() throws Exception {
        start(notifications::add);
        assertEquals(401, decide(requestId, "accepter", null).statusCode());
        assertEquals(404, decide(999999, "accepter", token).statusCode());
        try (var c = connection(); var s = c.prepareStatement("UPDATE animal SET statut='EN_SOIN' WHERE id=?")) {
            s.setLong(1, animalId);
            s.executeUpdate();
        }
        assertEquals(409, decide(requestId, "accepter", token).statusCode());
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
        assertTrue(notifications.isEmpty());
    }

    @Test
    void repeatingADecisionDoesNotSendASecondNotification() throws Exception {
        start(notifications::add);
        assertEquals(200, decide(requestId, "accepter", token).statusCode());
        assertEquals(409, decide(requestId, "accepter", token).statusCode());
        assertEquals(409, decide(requestId, "refuser", token).statusCode());
        assertEquals(1, notifications.size());
    }

    @Test
    void notificationHappensOnlyAfterTheDecisionIsVisibleToOtherConnections() throws Exception {
        List<String> statutsVus = new CopyOnWriteArrayList<>();
        start(decision -> {
            try {
                statutsVus.add(scalar("SELECT statut FROM demande WHERE id=?", decision.demandeId()));
                statutsVus.add(scalar("SELECT statut FROM animal WHERE id=?", animalId));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(200, decide(requestId, "accepter", token).statusCode());
        assertEquals(List.of("ACCEPTEE", "ADOPTE"), statutsVus);
    }

    @Test
    void aFailingNotificationNeverChangesTheDecisionOrItsResponse() throws Exception {
        var originalError = System.err;
        try (var capture = new java.io.PrintStream(new java.io.ByteArrayOutputStream())) {
            System.setErr(capture);
            start(decision -> { throw new IllegalStateException("adoptant@example.test"); });
            var result = decide(requestId, "accepter", token);
            assertEquals(200, result.statusCode());
            var body = JsonParser.parseString(result.body()).getAsJsonObject();
            assertEquals("Demande acceptee", body.get("message").getAsString());
            assertEquals(requestId, body.get("id").getAsLong());
            assertEquals("ACCEPTEE", body.get("statut").getAsString());
            assertEquals(3, body.size(), "The API response is unchanged");
        } finally {
            System.setErr(originalError);
        }
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id=?", animalId));
    }

    @Test
    void anUnreachableMailServerStillGivesAnImmediateSuccessfulDecision() throws Exception {
        int portFerme;
        try (var libre = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portFerme = libre.getLocalPort();
        }
        var originalError = System.err;
        try (var capture = new java.io.PrintStream(new java.io.ByteArrayOutputStream());
             var notificateur = new NotificateurEmailAsynchrone(new SmtpExpediteurEmail(new EmailSettings(
                     "127.0.0.1", portFerme, null, null, "refuge@example.test", EmailSettings.Securite.NONE)))) {
            System.setErr(capture);
            start(notificateur);
            assertEquals(200, decide(requestId, "refuser", token).statusCode());
            assertEquals("REFUSEE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
        } finally {
            System.setErr(originalError);
        }
    }

    @Test
    void decisionsReachTheApplicantThroughARealSmtpConversation() throws Exception {
        long refusedRequest = request(animalId);
        try (var smtp = new FauxServeurSmtp()) {
            try (var notificateur = new NotificateurEmailAsynchrone(new SmtpExpediteurEmail(new EmailSettings(
                    "127.0.0.1", smtp.port(), null, null, "refuge@example.test", EmailSettings.Securite.NONE)))) {
                start(notificateur);
                assertEquals(200, decide(requestId, "accepter", token).statusCode());
                assertEquals(200, decide(refusedRequest, "refuser", token).statusCode());
            }
            assertEquals(2, smtp.emails().size());
            assertEquals("adoptant@example.test", smtp.emails().get(0).enveloppeDestinataire());
            assertEquals("Votre demande d'adoption pour Milo a été acceptée", smtp.emails().get(0).message().getSubject());
            assertEquals("Votre demande d'adoption pour Milo n'a pas été retenue", smtp.emails().get(1).message().getSubject());
        }
    }
}

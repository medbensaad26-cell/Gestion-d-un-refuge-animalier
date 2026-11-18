package com.enicarthage.refugeanimalier.notification;

import com.enicarthage.refugeanimalier.config.EmailSettings;
import com.enicarthage.refugeanimalier.dto.DecisionAdoption;
import com.enicarthage.refugeanimalier.model.StatutDemande;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NotificationEmailTest {
    private static final String EMAIL = "adoptant@example.test";

    @TempDir
    Path directory;

    private static DecisionAdoption decision(StatutDemande statut) {
        return new DecisionAdoption(7, statut, "Amel Ben Ali", EMAIL, "Milo");
    }

    @Test
    void acceptanceMessageClearlyAnnouncesTheDecision() {
        MessageEmail message = MessageEmail.pourDecision(decision(StatutDemande.ACCEPTEE));
        assertEquals(EMAIL, message.destinataire());
        assertEquals("Votre demande d'adoption pour Milo a été acceptée", message.sujet());
        assertTrue(message.corps().startsWith("Bonjour Amel Ben Ali,"));
        assertTrue(message.corps().contains("pour Milo a été acceptée"));
        assertFalse(message.corps().contains("n'a pas été retenue"));
    }

    @Test
    void refusalMessageClearlyAnnouncesTheDecision() {
        MessageEmail message = MessageEmail.pourDecision(decision(StatutDemande.REFUSEE));
        assertEquals("Votre demande d'adoption pour Milo n'a pas été retenue", message.sujet());
        assertTrue(message.corps().contains("pour Milo n'a pas été retenue"));
        assertFalse(message.corps().contains("a été acceptée"));
    }

    @Test
    void pendingStatusIsNeverAnnounced() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageEmail.pourDecision(decision(StatutDemande.EN_ATTENTE)));
    }

    @Test
    void storedValuesCannotAddHeadersOrLines() {
        MessageEmail message = MessageEmail.pourDecision(new DecisionAdoption(7, StatutDemande.ACCEPTEE,
                "Amel\r\nBcc: autre@example.test", EMAIL, "Milo\nX-Test: 1 fin"));
        assertFalse(message.sujet().matches("(?s).*[\\r\\n\\u2028].*"));
        assertTrue(message.sujet().contains("Milo X-Test: 1 fin"));
        assertTrue(message.corps().startsWith("Bonjour Amel Bcc: autre@example.test,\n"));
    }

    @Test
    void textualFormsHideApplicantDetails() {
        DecisionAdoption decision = decision(StatutDemande.ACCEPTEE);
        assertFalse(decision.toString().contains(EMAIL));
        assertFalse(decision.toString().contains("Amel"));
        assertFalse(MessageEmail.pourDecision(decision).toString().contains(EMAIL));
    }

    @Test
    void notificationReturnsWithoutWaitingForASlowMailServer() throws Exception {
        CountDownLatch liberer = new CountDownLatch(1);
        CountDownLatch envoye = new CountDownLatch(1);
        try (var notificateur = new NotificateurEmailAsynchrone(message -> {
            attendre(liberer);
            envoye.countDown();
        }, 0, 5)) {
            notificateur.notifier(decision(StatutDemande.ACCEPTEE));
            assertEquals(1, envoye.getCount(), "The caller must not wait for delivery");
            liberer.countDown();
            assertTrue(envoye.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void temporaryFailuresAreRetriedThenDelivered() {
        AtomicInteger tentatives = new AtomicInteger();
        List<MessageEmail> livres = new CopyOnWriteArrayList<>();
        try (var notificateur = new NotificateurEmailAsynchrone(message -> {
            if (tentatives.incrementAndGet() < 3) throw new MessagingException("temporaire");
            livres.add(message);
        }, 0, 5)) {
            notificateur.notifier(decision(StatutDemande.REFUSEE));
        }
        assertEquals(3, tentatives.get());
        assertEquals(1, livres.size());
    }

    @Test
    void permanentFailuresStopAndLogNoPrivateDetails() {
        AtomicInteger tentatives = new AtomicInteger();
        String journal = journalErreurs(() -> {
            try (var notificateur = new NotificateurEmailAsynchrone(message -> {
                tentatives.incrementAndGet();
                throw new AuthenticationFailedException("refus pour " + EMAIL);
            }, 0, 5)) {
                notificateur.notifier(decision(StatutDemande.ACCEPTEE));
            }
        });
        assertEquals(1, tentatives.get());
        assertTrue(journal.contains("demande 7"));
        assertTrue(journal.contains("AuthenticationFailedException"));
        assertFalse(journal.contains(EMAIL));
    }

    @Test
    void exhaustedRetriesAndUnexpectedErrorsDoNotEscape() {
        AtomicInteger tentatives = new AtomicInteger();
        journalErreurs(() -> {
            try (var notificateur = new NotificateurEmailAsynchrone(message -> {
                if (tentatives.incrementAndGet() == 4) throw new IllegalStateException("inattendue");
                throw new MessagingException("indisponible");
            }, 0, 5)) {
                assertDoesNotThrow(() -> notificateur.notifier(decision(StatutDemande.ACCEPTEE)));
                assertDoesNotThrow(() -> notificateur.notifier(decision(StatutDemande.REFUSEE)));
            }
        });
        assertEquals(4, tentatives.get(), "Three attempts, then one for the following email");
    }

    @Test
    void aFullQueueDropsTheEmailWithoutFailingTheCaller() {
        CountDownLatch liberer = new CountDownLatch(1);
        AtomicInteger envois = new AtomicInteger();
        String journal = journalErreurs(() -> {
            try (var notificateur = new NotificateurEmailAsynchrone(message -> {
                attendre(liberer);
                envois.incrementAndGet();
            }, 0, 1)) {
                for (int i = 0; i < 3; i++) {
                    assertDoesNotThrow(() -> notificateur.notifier(decision(StatutDemande.ACCEPTEE)));
                }
                liberer.countDown();
            }
        });
        assertEquals(2, envois.get());
        assertTrue(journal.contains("RejectedExecutionException"));
    }

    @Test
    void realSmtpClientDeliversAReadableUtf8Message() throws Exception {
        try (var serveur = new FauxServeurSmtp()) {
            new SmtpExpediteurEmail(new EmailSettings("127.0.0.1", serveur.port(), null, null,
                    "refuge@example.test", EmailSettings.Securite.NONE))
                    .envoyer(MessageEmail.pourDecision(decision(StatutDemande.ACCEPTEE)));

            assertEquals(1, serveur.emails().size());
            var recu = serveur.emails().get(0);
            assertEquals(EMAIL, recu.enveloppeDestinataire());
            assertEquals("Votre demande d'adoption pour Milo a été acceptée", recu.message().getSubject());
            assertTrue(recu.message().getContent().toString().contains("a été acceptée"));
            var expediteur = (InternetAddress) recu.message().getFrom()[0];
            assertEquals("refuge@example.test", expediteur.getAddress());
            assertEquals("Pawtale", expediteur.getPersonal());
        }
    }

    @Test
    void smtpSessionRequiresEncryptionAndBoundsEveryWait() {
        var starttls = SmtpExpediteurEmail.proprietes(new EmailSettings("smtp.example.test", 587, "compte", "secret",
                "refuge@example.test", EmailSettings.Securite.STARTTLS));
        assertEquals("true", starttls.getProperty("mail.smtp.auth"));
        assertEquals("true", starttls.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", starttls.getProperty("mail.smtp.ssl.checkserveridentity"));
        for (String delai : List.of("connectiontimeout", "timeout", "writetimeout")) {
            assertEquals("10000", starttls.getProperty("mail.smtp." + delai));
        }
        var ssl = SmtpExpediteurEmail.proprietes(new EmailSettings("smtp.example.test", 465, "compte", "secret",
                "refuge@example.test", EmailSettings.Securite.SSL));
        assertEquals("true", ssl.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void withoutConfigurationNothingIsSent() {
        assertSame(NotificateurDecision.AUCUN, Notifications.creer(Map.of(), directory));
    }

    @Test
    void invalidConfigurationDisablesEmailInsteadOfStoppingTheApplication() {
        String journal = journalErreurs(() -> assertSame(NotificateurDecision.AUCUN, Notifications.creer(
                Map.of("REFUGE_MAIL_HOST", "smtp.example.test", "REFUGE_MAIL_PORT", "invalide",
                        "REFUGE_MAIL_FROM", "refuge@example.test"), directory)));
        assertTrue(journal.contains("mail.port"));
        assertFalse(journal.contains("smtp.example.test"));
    }

    @Test
    void validConfigurationEnablesEmail() {
        var notificateur = Notifications.creer(Map.of("REFUGE_MAIL_HOST", "127.0.0.1",
                "REFUGE_MAIL_FROM", "refuge@example.test", "REFUGE_MAIL_SECURITY", "NONE"), directory);
        assertInstanceOf(NotificateurEmailAsynchrone.class, notificateur);
        ((NotificateurEmailAsynchrone) notificateur).close();
    }

    @Test
    void automatedTestsNeverUseRealMailSettings() {
        String precedent = System.getProperty("refuge.test.database");
        System.setProperty("refuge.test.database", "true");
        try {
            assertSame(NotificateurDecision.AUCUN, Notifications.parDefaut());
        } finally {
            if (precedent == null) System.clearProperty("refuge.test.database");
            else System.setProperty("refuge.test.database", precedent);
        }
    }

    private static void attendre(CountDownLatch verrou) {
        try {
            assertTrue(verrou.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String journalErreurs(Runnable action) {
        PrintStream original = System.err;
        var capture = new ByteArrayOutputStream();
        try (var flux = new PrintStream(capture, true, StandardCharsets.UTF_8)) {
            System.setErr(flux);
            action.run();
        } finally {
            System.setErr(original);
        }
        return capture.toString(StandardCharsets.UTF_8);
    }
}

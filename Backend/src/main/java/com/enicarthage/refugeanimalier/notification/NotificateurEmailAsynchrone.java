package com.enicarthage.refugeanimalier.notification;

import com.enicarthage.refugeanimalier.dto.DecisionAdoption;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Sends emails on one background thread. The HTTP server handles requests one at a time,
 * so a slow mail server must never hold up an API response.
 */
public final class NotificateurEmailAsynchrone implements NotificateurDecision, AutoCloseable {
    private static final int CAPACITE_FILE = 200;
    private static final int TENTATIVES = 3;
    private static final long ATTENTE_FERMETURE_SECONDES = 15;

    private final ExpediteurEmail expediteur;
    private final long attenteEntreTentativesMs;
    private final ThreadPoolExecutor executeur;

    public NotificateurEmailAsynchrone(ExpediteurEmail expediteur) {
        this(expediteur, 5_000, CAPACITE_FILE);
    }

    NotificateurEmailAsynchrone(ExpediteurEmail expediteur, long attenteEntreTentativesMs, int capaciteFile) {
        this.expediteur = Objects.requireNonNull(expediteur);
        this.attenteEntreTentativesMs = attenteEntreTentativesMs;
        this.executeur = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capaciteFile), tache -> {
                    Thread thread = new Thread(tache, "refuge-notifications-email");
                    thread.setDaemon(true);
                    // Mail providers must resolve from the application's own class loader.
                    thread.setContextClassLoader(NotificateurEmailAsynchrone.class.getClassLoader());
                    return thread;
                });
    }

    @Override
    public void notifier(DecisionAdoption decision) {
        long demandeId = decision.demandeId();
        MessageEmail message = MessageEmail.pourDecision(decision);
        try {
            executeur.execute(() -> envoyerAvecReprises(demandeId, message));
        } catch (RejectedExecutionException e) {
            journaliserEchec(demandeId, e);
        }
    }

    private void envoyerAvecReprises(long demandeId, MessageEmail message) {
        for (int tentative = 1; tentative <= TENTATIVES; tentative++) {
            try {
                expediteur.envoyer(message);
                return;
            } catch (MessagingException | RuntimeException e) {
                if (tentative == TENTATIVES || !reessayable(e)) {
                    journaliserEchec(demandeId, e);
                    return;
                }
            }
            try {
                Thread.sleep(attenteEntreTentativesMs * tentative);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                journaliserEchec(demandeId, e);
                return;
            }
        }
    }

    // Wrong credentials or a rejected address will not be fixed by trying again.
    private static boolean reessayable(Exception erreur) {
        return erreur instanceof MessagingException
                && !(erreur instanceof AuthenticationFailedException)
                && !(erreur instanceof SendFailedException)
                && !(erreur instanceof AddressException);
    }

    // Mail errors can quote addresses, so only the request ID and error type are logged.
    private static void journaliserEchec(long demandeId, Exception erreur) {
        System.err.println("Email de decision non envoye pour la demande " + demandeId
                + " (" + erreur.getClass().getSimpleName() + ")");
    }

    /** Lets queued emails finish, within a short limit. */
    @Override
    public void close() {
        executeur.shutdown();
        try {
            if (!executeur.awaitTermination(ATTENTE_FERMETURE_SECONDES, TimeUnit.SECONDS)) {
                executeur.shutdownNow();
            }
        } catch (InterruptedException e) {
            executeur.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

package com.enicarthage.refugeanimalier.notification;

import com.enicarthage.refugeanimalier.config.EmailSettings;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Chooses how decisions are notified. Email problems never stop the application from starting. */
public final class Notifications {
    private static NotificateurDecision parDefaut;

    private Notifications() {}

    public static synchronized NotificateurDecision parDefaut() {
        // Automated tests never read the private mail settings or contact a real mail server.
        if (Boolean.getBoolean("refuge.test.database")) {
            return NotificateurDecision.AUCUN;
        }
        if (parDefaut == null) {
            parDefaut = creer(System.getenv(), Path.of("").toAbsolutePath());
            if (parDefaut instanceof NotificateurEmailAsynchrone notificateur) {
                Runtime.getRuntime().addShutdownHook(
                        new Thread(notificateur::close, "refuge-notifications-arret"));
            }
        }
        return parDefaut;
    }

    static NotificateurDecision creer(Map<String, String> environment, Path workingDirectory) {
        try {
            Optional<EmailSettings> settings = EmailSettings.load(environment, workingDirectory);
            if (settings.isEmpty()) {
                System.out.println("Notifications email desactivees : aucun serveur SMTP configure");
                return NotificateurDecision.AUCUN;
            }
            NotificateurDecision notificateur =
                    new NotificateurEmailAsynchrone(new SmtpExpediteurEmail(settings.get()));
            System.out.println("Notifications email activees");
            return notificateur;
        } catch (RuntimeException e) {
            System.err.println("Notifications email desactivees : " + e.getMessage());
            return NotificateurDecision.AUCUN;
        }
    }
}


package com.enicarthage.refugeanimalier.security;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final Duration DUREE =
            Duration.ofHours(2);

    private static final Map<String, Session> SESSIONS =
            new ConcurrentHashMap<>();

    private record Session(
            long adminId,
            Instant expiration
    ) {}

    private SessionManager() {}

    // Creer une session securisee
    public static String creerSession(long adminId) {

        byte[] aleatoire = new byte[32];
        RANDOM.nextBytes(aleatoire);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(aleatoire);

        SESSIONS.put(
                empreinte(token),
                new Session(
                        adminId,
                        Instant.now().plus(DUREE)
                )
        );

        return token;
    }

    // Verifier une session
    public static Long verifierSession(String token) {

        if (token == null || token.isBlank()) {
            return null;
        }

        Session session = SESSIONS.get(empreinte(token));

        if (session == null) {
            return null;
        }

        if (!Instant.now().isBefore(session.expiration())) {
            SESSIONS.remove(empreinte(token));
            return null;
        }

        return session.adminId();
    }

    // Deconnecter un administrateur
    public static void supprimerSession(String token) {

        if (token != null && !token.isBlank()) {
            SESSIONS.remove(empreinte(token));
        }
    }

    // Ne conserver que l'empreinte du token
    private static String empreinte(String token) {

        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(token.getBytes(
                            StandardCharsets.UTF_8));

            return Base64.getEncoder()
                    .encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

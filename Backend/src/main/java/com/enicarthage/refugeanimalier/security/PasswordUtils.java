
package com.enicarthage.refugeanimalier.security;

import org.mindrot.jbcrypt.BCrypt;
import java.nio.charset.StandardCharsets;

public final class PasswordUtils {

    private PasswordUtils() {
    }

    // Transformer un mot de passe en hash securise
    public static String hacher(String motDePasse) {

        if (motDePasse == null ||
                motDePasse.length() < 12 ||
                motDePasse.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Mot de passe : 12 caracteres minimum, " +
                            "72 octets maximum");
        }

        return BCrypt.hashpw(
                motDePasse,
                BCrypt.gensalt(12)
        );
    }

    // Verifier un mot de passe contre son hash
    public static boolean verifier(
            String motDePasse, String hash) {

        if (motDePasse == null || hash == null ||
                motDePasse.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }

        try {
            return BCrypt.checkpw(motDePasse, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

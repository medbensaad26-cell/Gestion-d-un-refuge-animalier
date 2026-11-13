package com.enicarthage.refugeanimalier.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;

/** Checks application input against the existing MySQL column limits. */
final class ValidationDonnees {
    private ValidationDonnees() {}

    static void texte(String valeur, String champ, int maximum, boolean obligatoire) {
        if (valeur == null) {
            if (obligatoire) {
                throw new IllegalArgumentException("Champ obligatoire : " + champ);
            }
            return;
        }
        if ((obligatoire && valeur.isBlank())
                || valeur.codePointCount(0, valeur.length()) > maximum) {
            throw new IllegalArgumentException("Champ " + champ + " invalide (max " + maximum + " caracteres)");
        }
    }

    static void texteLong(String valeur, String champ) {
        if (valeur != null && valeur.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new IllegalArgumentException("Champ " + champ + " trop long (max 65535 octets UTF-8)");
        }
    }

    static void dateMysql(LocalDate valeur, String champ) {
        if (valeur == null || valeur.getYear() < 1000 || valeur.getYear() > 9999) {
            throw new IllegalArgumentException("Champ " + champ + " : date entre 1000-01-01 et 9999-12-31 obligatoire");
        }
    }

    static void email(String valeur) {
        texte(valeur, "email", 255, true);
        if (!valeur.matches("[^\\s@]+@[^\\s@]+")) {
            throw new IllegalArgumentException("Adresse email invalide");
        }
    }

    static void statutAnimal(String statut) {
        if (!Set.of("DISPONIBLE", "EN_SOIN", "ADOPTE").contains(statut)) {
            throw new IllegalArgumentException("Statut animal invalide : DISPONIBLE, EN_SOIN ou ADOPTE attendu");
        }
    }
}

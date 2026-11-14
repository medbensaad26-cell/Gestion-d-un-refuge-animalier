package com.enicarthage.refugeanimalier.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Optional preferences, never persisted. Age bands are product rules, not medical categories. */
public record PreferencesMatching(String espece, String categorieAge, String sexe) {
    public PreferencesMatching {
        espece = valider(espece, "espece", Set.of("CHIEN", "CHAT"), false);
        categorieAge = valider(categorieAge, "categorieAge", Set.of("JEUNE", "ADULTE", "SENIOR"), false);
        sexe = valider(sexe, "sexe", Set.of("MALE", "FEMELLE"), true);
    }

    private static String valider(String valeur, String champ, Set<String> autorisees, boolean sexe) {
        ValidationDonnees.texte(valeur, champ, 32, false);
        String normalisee = sexe ? normaliserSexe(valeur) : normaliser(valeur);
        if (normalisee != null && !autorisees.contains(normalisee)) {
            throw new IllegalArgumentException("Preference " + champ + " invalide");
        }
        return normalisee;
    }

    static String normaliser(String valeur) {
        if (valeur == null || valeur.isBlank()) return null;
        return Normalizer.normalize(valeur.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    static String normaliserSexe(String valeur) {
        String normalisee = normaliser(valeur);
        if ("M".equals(normalisee)) return "MALE";
        if ("F".equals(normalisee)) return "FEMELLE";
        return normalisee;
    }
}

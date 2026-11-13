package com.enicarthage.refugeanimalier.service;

import java.util.Locale;

/** Read-only catalogue criteria, validated before reaching JDBC. */
public record FiltresCatalogue(String q, String espece, String statutAnimal) {
    public static final FiltresCatalogue AUCUN = new FiltresCatalogue(null, null, null);

    public FiltresCatalogue {
        q = normaliser(q);
        espece = normaliser(espece);
        statutAnimal = normaliser(statutAnimal);
        ValidationDonnees.texte(q, "q", 150, false);
        ValidationDonnees.texte(espece, "espece", 100, false);
        if (statutAnimal != null) {
            statutAnimal = statutAnimal.toUpperCase(Locale.ROOT);
            ValidationDonnees.statutAnimal(statutAnimal);
        }
    }

    private static String normaliser(String texte) {
        return texte == null || texte.isBlank() ? null : texte.strip();
    }
}

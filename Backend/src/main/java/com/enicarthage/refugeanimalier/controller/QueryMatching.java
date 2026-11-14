package com.enicarthage.refugeanimalier.controller;

import com.enicarthage.refugeanimalier.service.PreferencesMatching;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;

final class QueryMatching {
    private QueryMatching() {}

    static PreferencesMatching lire(URI uri) {
        String query = uri.getRawQuery();
        var valeurs = new HashMap<String, String>();
        if (query != null && query.length() > 512) {
            throw new IllegalArgumentException("Parametres de matching trop longs");
        }
        if (query != null && !query.isEmpty()) {
            for (String partie : query.split("&", -1)) {
                if (partie.isEmpty()) continue;
                String[] paire = partie.split("=", 2);
                String cle = URLDecoder.decode(paire[0], StandardCharsets.UTF_8);
                String valeur = paire.length == 2 ? URLDecoder.decode(paire[1], StandardCharsets.UTF_8) : "";
                if (!Set.of("espece", "categorieAge", "sexe").contains(cle)
                        || valeurs.putIfAbsent(cle, valeur) != null) {
                    throw new IllegalArgumentException("Parametre de matching inconnu ou repete");
                }
            }
        }
        return new PreferencesMatching(valeurs.get("espece"), valeurs.get("categorieAge"), valeurs.get("sexe"));
    }
}

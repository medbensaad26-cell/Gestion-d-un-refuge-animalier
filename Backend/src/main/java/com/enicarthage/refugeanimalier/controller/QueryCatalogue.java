package com.enicarthage.refugeanimalier.controller;

import com.enicarthage.refugeanimalier.service.FiltresCatalogue;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;

final class QueryCatalogue {
    private QueryCatalogue() {}

    static FiltresCatalogue lire(URI uri) {
        var valeurs = new HashMap<String, String>();
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            for (String partie : query.split("&", -1)) {
                if (partie.isEmpty()) continue;
                String[] pair = partie.split("=", 2);
                String cle = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String valeur = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                if (!Set.of("q", "espece", "statutAnimal").contains(cle) || valeurs.putIfAbsent(cle, valeur) != null) {
                    throw new IllegalArgumentException("Parametre de recherche inconnu ou repete");
                }
            }
        }
        return new FiltresCatalogue(valeurs.get("q"), valeurs.get("espece"), valeurs.get("statutAnimal"));
    }
}

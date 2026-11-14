package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.AnimalDAO;
import com.enicarthage.refugeanimalier.dto.RecommandationAdoption;
import com.enicarthage.refugeanimalier.model.Animal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Rule-based preference agreement, not a prediction of adoption success or health/lifestyle fit. */
public class MatchingService {
    private static final int POIDS_ESPECE = 50;
    private static final int POIDS_AGE = 30;
    private static final int POIDS_SEXE = 20;
    private final AnimalDAO animalDAO;

    public MatchingService() {
        this(new AnimalDAO());
    }

    public MatchingService(AnimalDAO animalDAO) {
        this.animalDAO = Objects.requireNonNull(animalDAO);
    }

    public List<RecommandationAdoption> recommander(PreferencesMatching preferences) throws SQLException {
        Objects.requireNonNull(preferences);
        // Read all available candidates before ranking; an early SQL LIMIT could hide the best match.
        return animalDAO.rechercher(new FiltresCatalogue(null, null, "DISPONIBLE")).stream()
                // MySQL's existing collation may also match noncanonical case/trailing spaces.
                .filter(animal -> "DISPONIBLE".equals(animal.getStatut()))
                .map(animal -> evaluer(animal, preferences))
                .sorted(Comparator.comparingInt(RecommandationAdoption::score).reversed()
                        .thenComparingLong(resultat -> resultat.animal().id()))
                .limit(3)
                .toList();
    }

    private RecommandationAdoption evaluer(Animal animal, PreferencesMatching preferences) {
        int points = 0;
        int maximum = 0;
        List<String> raisons = new ArrayList<>();
        if (preferences.espece() != null) {
            maximum += POIDS_ESPECE;
            boolean correspond = preferences.espece().equals(PreferencesMatching.normaliser(animal.getEspece()));
            if (correspond) points += POIDS_ESPECE;
            raisons.add("Espece : " + (correspond ? "correspond" : "differente"));
        }
        if (preferences.categorieAge() != null) {
            maximum += POIDS_AGE;
            String categorie = categorieAge(animal.getAge());
            boolean correspond = preferences.categorieAge().equals(categorie);
            if (correspond) points += POIDS_AGE;
            raisons.add("Age (" + preferences.categorieAge() + ") : "
                    + (categorie == null ? "non exploitable" : correspond ? "correspond" : "different"));
        }
        if (preferences.sexe() != null) {
            maximum += POIDS_SEXE;
            String sexe = PreferencesMatching.normaliserSexe(animal.getSexe());
            boolean connu = "MALE".equals(sexe) || "FEMELLE".equals(sexe);
            boolean correspond = preferences.sexe().equals(sexe);
            if (correspond) points += POIDS_SEXE;
            raisons.add("Sexe : " + (!connu ? "non renseigne ou non reconnu" : correspond ? "correspond" : "different"));
        }
        int score = maximum == 0 ? 0 : (int) Math.round(100.0 * points / maximum);
        String explication = maximum == 0
                ? "Aucune preference renseignee : score non evalue (0), ordre par identifiant."
                : String.join(" ; ", raisons) + ".";
        return RecommandationAdoption.depuis(animal, score, explication);
    }

    private static String categorieAge(int age) {
        if (age < 0) return null;
        if (age < 2) return "JEUNE";
        if (age < 8) return "ADULTE";
        return "SENIOR";
    }
}

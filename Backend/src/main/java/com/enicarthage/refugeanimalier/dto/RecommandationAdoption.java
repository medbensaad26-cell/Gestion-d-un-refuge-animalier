package com.enicarthage.refugeanimalier.dto;

import com.enicarthage.refugeanimalier.model.Animal;

/** Public projection: no administrator ID, medical content or applicant information. */
public record RecommandationAdoption(AnimalPublic animal, int score, String explication) {
    public record AnimalPublic(long id, String nom, String espece, int age, String sexe, String statut) {}

    public static RecommandationAdoption depuis(Animal animal, int score, String explication) {
        return new RecommandationAdoption(new AnimalPublic(animal.getId(), animal.getNom(),
                animal.getEspece(), animal.getAge(), animal.getSexe(), animal.getStatut()), score, explication);
    }
}

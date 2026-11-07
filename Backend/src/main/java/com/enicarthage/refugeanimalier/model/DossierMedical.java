
package com.enicarthage.refugeanimalier.model;

import java.time.LocalDate;

public class DossierMedical {

    private Long id;
    private String vaccins;
    private String traitements;
    private String observations;
    private LocalDate dateMiseAJour;
    private Long animalId;

    // Constructeur vide
    public DossierMedical() {
    }

    // Constructeur avec parametres
    public DossierMedical(Long id, String vaccins,
                          String traitements, String observations,
                          LocalDate dateMiseAJour, Long animalId) {

        this.id = id;
        this.vaccins = vaccins;
        this.traitements = traitements;
        this.observations = observations;
        this.dateMiseAJour = dateMiseAJour;
        this.animalId = animalId;
    }

    public Long getId() {
        return id;
    }

    public Long getAnimalId() {
        return animalId;
    }

    public LocalDate getDateMiseAJour() {
        return dateMiseAJour;
    }

    public String getObservations() {
        return observations;
    }

    public String getTraitements() {
        return traitements;
    }

    public String getVaccins() {
        return vaccins;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setAnimalId(Long animalId) {
        this.animalId = animalId;
    }

    public void setDateMiseAJour(LocalDate dateMiseAJour) {
        this.dateMiseAJour = dateMiseAJour;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public void setTraitements(String traitements) {
        this.traitements = traitements;
    }

    public void setVaccins(String vaccins) {
        this.vaccins = vaccins;
    }
}

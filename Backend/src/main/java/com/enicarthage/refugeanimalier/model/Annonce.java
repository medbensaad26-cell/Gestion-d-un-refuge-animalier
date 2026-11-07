
package com.enicarthage.refugeanimalier.model;

import java.time.LocalDate;

public class Annonce {

    private Long id;
    private String titre;
    private String description;
    private LocalDate datePublication;
    private String statut;
    private Long adminId;
    private Long animalId;

    // Constructeur vide
    public Annonce() {
    }

    // Constructeur avec parametres
    public Annonce(Long id, String titre,
                   String description, LocalDate datePublication,
                   String statut, Long adminId, Long animalId) {

        this.id = id;
        this.titre = titre;
        this.description = description;
        this.datePublication = datePublication;
        this.statut = statut;
        this.adminId = adminId;
        this.animalId = animalId;
    }

    public Long getAnimalId() {
        return animalId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getStatut() {
        return statut;
    }

    public LocalDate getDatePublication() {
        return datePublication;
    }

    public String getDescription() {
        return description;
    }

    public String getTitre() {
        return titre;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDatePublication(LocalDate datePublication) {
        this.datePublication = datePublication;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public void setAnimalId(Long animalId) {
        this.animalId = animalId;
    }
}

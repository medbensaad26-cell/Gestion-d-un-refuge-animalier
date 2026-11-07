
package com.enicarthage.refugeanimalier.model;

import java.time.LocalDate;

public class Demande {

    private Long id;
    private String nomDemandeur;
    private String email;
    private String telephone;
    private LocalDate dateDemande;
    private StatutDemande statut;
    private Long animalId;
    private Long adminId;

    // Constructeur vide
    public Demande() {
    }

    // Constructeur avec parametres
    public Demande(Long id, String nomDemandeur,
                   String email, String telephone,
                   LocalDate dateDemande,
                   StatutDemande statut,
                   Long animalId, Long adminId) {

        this.id = id;
        this.nomDemandeur = nomDemandeur;
        this.email = email;
        this.telephone = telephone;
        this.dateDemande = dateDemande;
        this.statut = statut;
        this.animalId = animalId;
        this.adminId = adminId;
    }

    public Long getId() {
        return id;
    }

    public Long getAdminId() {
        return adminId;
    }

    public Long getAnimalId() {
        return animalId;
    }

    public StatutDemande getStatut() {
        return statut;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getEmail() {
        return email;
    }

    public String getNomDemandeur() {
        return nomDemandeur;
    }

    public LocalDate getDateDemande() {
        return dateDemande;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public void setAnimalId(Long animalId) {
        this.animalId = animalId;
    }

    public void setStatut(StatutDemande statut) {
        this.statut = statut;
    }

    public void setDateDemande(LocalDate dateDemande) {
        this.dateDemande = dateDemande;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setNomDemandeur(String nomDemandeur) {
        this.nomDemandeur = nomDemandeur;
    }
}

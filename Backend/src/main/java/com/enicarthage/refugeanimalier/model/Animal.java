
package com.enicarthage.refugeanimalier.model;

public class Animal {

    private Long id;
    private String nom;
    private String espece;
    private String race;
    private int age;
    private String sexe;
    private String etatSante;
    private String statut;
    private Long adminId;

    // Constructeur vide
    public Animal() {
    }

    // Constructeur avec parametres
    public Animal(Long id, String nom, String espece,
                  String race, int age, String sexe,
                  String etatSante, String statut,
                  Long adminId) {

        this.id = id;
        this.nom = nom;
        this.espece = espece;
        this.race = race;
        this.age = age;
        this.sexe = sexe;
        this.etatSante = etatSante;
        this.statut = statut;
        this.adminId = adminId;
    }

    public Long getId() {
        return id;
    }

    public String getEspece() {
        return espece;
    }

    public String getRace() {
        return race;
    }

    public int getAge() {
        return age;
    }

    public String getSexe() {
        return sexe;
    }

    public String getEtatSante() {
        return etatSante;
    }

    public String getStatut() {
        return statut;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEspece(String espece) {
        this.espece = espece;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public void setRace(String race) {
        this.race = race;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public void setSexe(String sexe) {
        this.sexe = sexe;
    }

    public void setEtatSante(String etatSante) {
        this.etatSante = etatSante;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }
    public String getNom() {
        return nom;
    }
}


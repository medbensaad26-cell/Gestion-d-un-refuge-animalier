
package com.enicarthage.refugeanimalier.model;

public class Admin {

    private Long id;
    private String nom;
    private String email;
    private String motDePasse;

    // Constructeur vide
    public Admin() {
    }

    // Constructeur avec parametres
    public Admin(Long id, String nom,
                 String email, String motDePasse) {

        this.id = id;
        this.nom = nom;
        this.email = email;
        this.motDePasse = motDePasse;
    }

    public Long getId() {
        return id;
    }

    public String getMotDePasse() {
        return motDePasse;
    }

    public String getEmail() {
        return email;
    }

    public String getNom() {
        return nom;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    public void setNom(String nom) {
        this.nom = nom;
    }

}

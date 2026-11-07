
package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.AnnonceDAO;
import com.enicarthage.refugeanimalier.model.Annonce;

import java.sql.SQLException;
import java.util.List;

public class AnnonceService {

    private final AnnonceDAO annonceDAO;

    public AnnonceService() {
        this.annonceDAO = new AnnonceDAO();
    }

    // Recuperer toutes les annonces
    public List<Annonce> getAllAnnonces()
            throws SQLException {

        return annonceDAO.findAll();
    }

    // Recuperer une annonce par ID
    public Annonce getAnnonceById(long id)
            throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID annonce invalide");
        }

        Annonce annonce = annonceDAO.findById(id);

        if (annonce == null) {
            throw new IllegalArgumentException(
                    "Annonce introuvable avec ID : " + id);
        }

        return annonce;
    }

    // Ajouter une annonce
    public void ajouterAnnonce(Annonce annonce)
            throws SQLException {

        validerAnnonce(annonce);

        annonceDAO.create(annonce);
    }

    // Modifier une annonce
    public void modifierAnnonce(Annonce annonce)
            throws SQLException {

        if (annonce == null || annonce.getId() == null) {
            throw new IllegalArgumentException(
                    "ID annonce obligatoire");
        }

        validerAnnonce(annonce);

        getAnnonceById(annonce.getId());

        annonceDAO.update(annonce);
    }

    // Supprimer une annonce
    public void supprimerAnnonce(long id)
            throws SQLException {

        getAnnonceById(id);

        annonceDAO.delete(id);
    }

    // Validation des informations
    private void validerAnnonce(Annonce annonce) {

        if (annonce == null) {
            throw new IllegalArgumentException(
                    "Annonce obligatoire");
        }

        if (annonce.getTitre() == null ||
                annonce.getTitre().isBlank()) {
            throw new IllegalArgumentException(
                    "Le titre est obligatoire");
        }

        if (annonce.getDatePublication() == null) {
            throw new IllegalArgumentException(
                    "La date de publication est obligatoire");
        }

        if (annonce.getStatut() == null ||
                annonce.getStatut().isBlank()) {
            throw new IllegalArgumentException(
                    "Le statut est obligatoire");
        }

        if (annonce.getAdminId() == null ||
                annonce.getAdminId() <= 0) {
            throw new IllegalArgumentException(
                    "ID administrateur invalide");
        }

        if (annonce.getAnimalId() == null ||
                annonce.getAnimalId() <= 0) {
            throw new IllegalArgumentException(
                    "ID animal invalide");
        }
    }
}

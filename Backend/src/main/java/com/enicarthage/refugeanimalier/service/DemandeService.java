
package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.DemandeDAO;
import com.enicarthage.refugeanimalier.model.Demande;
import com.enicarthage.refugeanimalier.model.StatutDemande;

import java.sql.SQLException;
import java.util.List;

public class DemandeService {

    private final DemandeDAO demandeDAO;

    public DemandeService() {
        this.demandeDAO = new DemandeDAO();
    }

    // Recuperer toutes les demandes
    public List<Demande> getAllDemandes()
            throws SQLException {

        return demandeDAO.findAll();
    }

    // Recuperer une demande par ID
    public Demande getDemandeById(long id)
            throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID demande invalide");
        }

        Demande demande = demandeDAO.findById(id);

        if (demande == null) {
            throw new IllegalArgumentException(
                    "Demande introuvable avec ID : " + id);
        }

        return demande;
    }

    // Ajouter une demande

    public void ajouterDemande(Demande demande)
            throws SQLException {

        validerDemande(demande);

        if (demande.getStatut() !=
                StatutDemande.EN_ATTENTE) {

            throw new IllegalArgumentException(
                    "Une nouvelle demande doit etre EN_ATTENTE");
        }

        demandeDAO.create(demande);
    }


    // Modifier une demande

    public void modifierDemande(Demande demande)
            throws SQLException {

        throw new IllegalArgumentException(
                "Modification generale temporairement desactivee. " +
                        "Utilisez les actions accepter ou refuser.");
    }


    // Supprimer une demande
    public void supprimerDemande(long id)
            throws SQLException {

        getDemandeById(id);

        demandeDAO.delete(id);
    }

    // Changer le statut d'une demande


    // Accepter une demande d'adoption
    public void accepterDemande(long id)
            throws SQLException {

        changerStatut(id, StatutDemande.ACCEPTEE);
    }

    // Refuser une demande d'adoption
    public void refuserDemande(long id)
            throws SQLException {

        changerStatut(id, StatutDemande.REFUSEE);
    }

    // Traiter une demande avec transaction MySQL
    public void changerStatut(
            long id, StatutDemande statut)
            throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID demande invalide");
        }

        if (statut != StatutDemande.ACCEPTEE &&
                statut != StatutDemande.REFUSEE) {

            throw new IllegalArgumentException(
                    "Decision invalide");
        }

        // Executer la transaction dans DemandeDAO
        demandeDAO.traiterDemande(id, statut);
    }



    // Valider les informations
    private void validerDemande(Demande demande) {

        if (demande == null) {
            throw new IllegalArgumentException(
                    "Demande obligatoire");
        }

        if (demande.getNomDemandeur() == null ||
                demande.getNomDemandeur().isBlank()) {

            throw new IllegalArgumentException(
                    "Le nom du demandeur est obligatoire");
        }

        if (demande.getEmail() == null ||
                demande.getEmail().isBlank()) {

            throw new IllegalArgumentException(
                    "L'email est obligatoire");
        }

        if (demande.getTelephone() == null ||
                demande.getTelephone().isBlank()) {

            throw new IllegalArgumentException(
                    "Le telephone est obligatoire");
        }

        if (demande.getDateDemande() == null) {
            throw new IllegalArgumentException(
                    "La date de demande est obligatoire");
        }

        if (demande.getStatut() == null) {
            throw new IllegalArgumentException(
                    "Le statut est obligatoire");
        }

        if (demande.getAnimalId() == null ||
                demande.getAnimalId() <= 0) {

            throw new IllegalArgumentException(
                    "ID animal invalide");
        }

        if (demande.getAdminId() != null &&
                demande.getAdminId() <= 0) {

            throw new IllegalArgumentException(
                    "ID administrateur invalide");
        }
    }
}

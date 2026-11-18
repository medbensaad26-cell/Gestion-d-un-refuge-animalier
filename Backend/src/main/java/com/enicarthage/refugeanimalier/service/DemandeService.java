
package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.DemandeDAO;
import com.enicarthage.refugeanimalier.dto.DecisionAdoption;
import com.enicarthage.refugeanimalier.exception.RessourceIntrouvableException;
import com.enicarthage.refugeanimalier.model.Demande;
import com.enicarthage.refugeanimalier.model.StatutDemande;
import com.enicarthage.refugeanimalier.notification.NotificateurDecision;
import com.enicarthage.refugeanimalier.notification.Notifications;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class DemandeService {

    private final DemandeDAO demandeDAO;
    private final NotificateurDecision notificateur;

    public DemandeService() {
        this(new DemandeDAO(), Notifications.parDefaut());
    }

    public DemandeService(DemandeDAO demandeDAO, NotificateurDecision notificateur) {
        this.demandeDAO = Objects.requireNonNull(demandeDAO);
        this.notificateur = Objects.requireNonNull(notificateur);
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
            throw new RessourceIntrouvableException(
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

        // The DAO resolves the sole existing administrator in the insertion transaction.
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

        // Existence et historique sont verifies dans la transaction du DAO.
        demandeDAO.delete(id);
    }

    // Changer le statut d'une demande


    // Accepter une demande d'adoption
    public void accepterDemande(long id, long adminId)
            throws SQLException {

        changerStatut(id, StatutDemande.ACCEPTEE, adminId);
    }

    // Refuser une demande d'adoption
    public void refuserDemande(long id, long adminId)
            throws SQLException {

        changerStatut(id, StatutDemande.REFUSEE, adminId);
    }

    // Traiter une demande avec transaction MySQL
    public void changerStatut(
            long id, StatutDemande statut, long adminId)
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

        if (adminId <= 0) {
            throw new IllegalArgumentException("ID administrateur invalide");
        }

        // Executer la transaction dans DemandeDAO
        DecisionAdoption decision = demandeDAO.traiterDemande(id, statut, adminId);

        // La decision est validee : un probleme d'email ne doit plus la faire echouer.
        try {
            notificateur.notifier(decision);
        } catch (RuntimeException e) {
            System.err.println("Notification de decision impossible pour la demande " + id
                    + " (" + e.getClass().getSimpleName() + ")");
        }
    }



    // Valider les informations
    private void validerDemande(Demande demande) {

        if (demande == null) {
            throw new IllegalArgumentException(
                    "Demande obligatoire");
        }

        ValidationDonnees.texte(demande.getNomDemandeur(), "nomDemandeur", 150, true);
        ValidationDonnees.email(demande.getEmail());
        ValidationDonnees.texte(demande.getTelephone(), "telephone", 30, true);
        ValidationDonnees.dateMysql(demande.getDateDemande(), "dateDemande");

        if (demande.getStatut() == null) {
            throw new IllegalArgumentException(
                    "Le statut est obligatoire");
        }

        if (demande.getAnimalId() == null ||
                demande.getAnimalId() <= 0) {

            throw new IllegalArgumentException(
                    "ID animal invalide");
        }

        if (demande.getAdminId() != null && demande.getAdminId() <= 0) {

            throw new IllegalArgumentException(
                    "ID administrateur invalide");
        }
    }
}

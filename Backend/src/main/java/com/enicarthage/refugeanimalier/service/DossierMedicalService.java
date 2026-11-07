
package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.DossierMedicalDAO;
import com.enicarthage.refugeanimalier.model.DossierMedical;

import java.sql.SQLException;

public class DossierMedicalService {

    private final DossierMedicalDAO dossierDAO;

    public DossierMedicalService() {
        this.dossierDAO = new DossierMedicalDAO();
    }

    public DossierMedical getDossierByAnimalId(long animalId)
            throws SQLException {

        if (animalId <= 0) {
            throw new IllegalArgumentException(
                    "ID animal invalide");
        }

        return dossierDAO.findByAnimalId(animalId);
    }

    public void ajouterDossier(DossierMedical dossier)
            throws SQLException {

        validerDossier(dossier);
        dossierDAO.create(dossier);
    }

    public void modifierDossier(DossierMedical dossier)
            throws SQLException {

        validerDossier(dossier);
        dossierDAO.updateByAnimalId(dossier);
    }

    public void supprimerDossier(long animalId)
            throws SQLException {

        if (animalId <= 0) {
            throw new IllegalArgumentException(
                    "ID animal invalide");
        }

        dossierDAO.deleteByAnimalId(animalId);
    }

    private void validerDossier(DossierMedical dossier) {

        if (dossier == null) {
            throw new IllegalArgumentException(
                    "Dossier medical obligatoire");
        }

        if (dossier.getAnimalId() == null ||
                dossier.getAnimalId() <= 0) {
            throw new IllegalArgumentException(
                    "ID animal obligatoire");
        }

        if (dossier.getDateMiseAJour() == null) {
            throw new IllegalArgumentException(
                    "Date de mise a jour obligatoire");
        }
    }

}

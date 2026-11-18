package com.enicarthage.refugeanimalier.dto;

import com.enicarthage.refugeanimalier.model.StatutDemande;

/** Committed decision and the details needed to inform the applicant. Never returned by the API. */
public record DecisionAdoption(
        long demandeId,
        StatutDemande statut,
        String nomDemandeur,
        String email,
        String nomAnimal
) {
    @Override
    public String toString() {
        return "DecisionAdoption[demande=" + demandeId + ", statut=" + statut + "]";
    }
}

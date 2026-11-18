package com.enicarthage.refugeanimalier.notification;

import com.enicarthage.refugeanimalier.dto.DecisionAdoption;
import com.enicarthage.refugeanimalier.model.StatutDemande;

/** Plain-text message sent to an applicant. Edit the wording of the emails here. */
public record MessageEmail(String destinataire, String sujet, String corps) {

    public static MessageEmail pourDecision(DecisionAdoption decision) {
        String nom = surUneLigne(decision.nomDemandeur());
        String animal = surUneLigne(decision.nomAnimal());
        String salutation = nom.isEmpty() ? "Bonjour," : "Bonjour " + nom + ",";

        if (decision.statut() == StatutDemande.ACCEPTEE) {
            return new MessageEmail(
                    decision.email(),
                    "Votre demande d'adoption pour " + animal + " a été acceptée",
                    salutation + "\n\n"
                            + "Bonne nouvelle : votre demande d'adoption pour " + animal + " a été acceptée.\n\n"
                            + "Le refuge vous contactera pour organiser la suite de l'adoption.\n\n"
                            + "Merci pour votre engagement,\n"
                            + "L'équipe Pawtale\n");
        }
        if (decision.statut() == StatutDemande.REFUSEE) {
            return new MessageEmail(
                    decision.email(),
                    "Votre demande d'adoption pour " + animal + " n'a pas été retenue",
                    salutation + "\n\n"
                            + "Après examen, votre demande d'adoption pour " + animal + " n'a pas été retenue.\n\n"
                            + "Nous vous remercions de l'intérêt que vous portez au refuge. "
                            + "D'autres animaux attendent peut-être encore leur famille.\n\n"
                            + "Cordialement,\n"
                            + "L'équipe Pawtale\n");
        }
        throw new IllegalArgumentException("Decision invalide");
    }

    // Stored values must not be able to add lines to the message or its headers.
    private static String surUneLigne(String valeur) {
        return valeur == null ? "" : valeur.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]+", " ").strip();
    }

    @Override
    public String toString() {
        return "MessageEmail[contenu masque]";
    }
}

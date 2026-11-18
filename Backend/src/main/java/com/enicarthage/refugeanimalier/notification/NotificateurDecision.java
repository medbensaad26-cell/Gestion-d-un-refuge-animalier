package com.enicarthage.refugeanimalier.notification;

import com.enicarthage.refugeanimalier.dto.DecisionAdoption;

/** Informs an applicant of a decision that has already been committed. */
@FunctionalInterface
public interface NotificateurDecision {
    NotificateurDecision AUCUN = decision -> { };

    void notifier(DecisionAdoption decision);
}

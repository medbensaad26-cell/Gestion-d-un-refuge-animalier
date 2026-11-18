package com.enicarthage.refugeanimalier.notification;

import jakarta.mail.MessagingException;

@FunctionalInterface
public interface ExpediteurEmail {
    void envoyer(MessageEmail message) throws MessagingException;
}

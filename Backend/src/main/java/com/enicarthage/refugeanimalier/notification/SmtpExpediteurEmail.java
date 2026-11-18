package com.enicarthage.refugeanimalier.notification;

import com.enicarthage.refugeanimalier.config.EmailSettings;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;

public final class SmtpExpediteurEmail implements ExpediteurEmail {
    private static final String DELAI_MS = "10000";
    private static final String NOM_EXPEDITEUR = "Pawtale";

    private final Session session;
    private final String expediteur;

    public SmtpExpediteurEmail(EmailSettings settings) {
        Objects.requireNonNull(settings);
        this.expediteur = settings.expediteur();
        Authenticator authentification = settings.utilisateur() == null ? null : new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(settings.utilisateur(), settings.motDePasse());
            }
        };
        this.session = Session.getInstance(proprietes(settings), authentification);
    }

    static Properties proprietes(EmailSettings settings) {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", settings.host());
        properties.setProperty("mail.smtp.port", Integer.toString(settings.port()));
        properties.setProperty("mail.smtp.from", settings.expediteur());
        properties.setProperty("mail.smtp.auth", Boolean.toString(settings.utilisateur() != null));
        properties.setProperty("mail.smtp.connectiontimeout", DELAI_MS);
        properties.setProperty("mail.smtp.timeout", DELAI_MS);
        properties.setProperty("mail.smtp.writetimeout", DELAI_MS);
        if (settings.securite() == EmailSettings.Securite.STARTTLS) {
            properties.setProperty("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.starttls.required", "true");
        } else if (settings.securite() == EmailSettings.Securite.SSL) {
            properties.setProperty("mail.smtp.ssl.enable", "true");
        }
        if (settings.securite() != EmailSettings.Securite.NONE) {
            properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        }
        return properties;
    }

    @Override
    public void envoyer(MessageEmail contenu) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        try {
            message.setFrom(new InternetAddress(expediteur, NOM_EXPEDITEUR, StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(contenu.destinataire(), true));
        message.setSubject(contenu.sujet(), StandardCharsets.UTF_8.name());
        message.setText(contenu.corps(), StandardCharsets.UTF_8.name());
        message.setSentDate(new Date());
        Transport.send(message);
    }
}

package com.enicarthage.refugeanimalier.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal loopback SMTP server: emails are kept in memory and never leave the test. */
public final class FauxServeurSmtp implements AutoCloseable {
    public record EmailRecu(String enveloppeDestinataire, MimeMessage message) {}

    private final ServerSocket socket;
    private final List<EmailRecu> emails = new CopyOnWriteArrayList<>();

    public FauxServeurSmtp() throws IOException {
        socket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(this::accepter, "faux-smtp");
        thread.setDaemon(true);
        thread.start();
    }

    public int port() {
        return socket.getLocalPort();
    }

    public List<EmailRecu> emails() {
        return emails;
    }

    private void accepter() {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                dialoguer(client);
            } catch (Exception e) {
                // Closing the server ends the loop; a broken test client is reported by its assertions.
            }
        }
    }

    private void dialoguer(Socket client) throws Exception {
        var entree = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
        var sortie = new PrintWriter(client.getOutputStream(), true, StandardCharsets.ISO_8859_1);
        String destinataire = null;
        repondre(sortie, "220 faux-smtp");
        for (String ligne; (ligne = entree.readLine()) != null; ) {
            String commande = ligne.toUpperCase();
            if (commande.startsWith("RCPT TO:")) {
                destinataire = ligne.substring("RCPT TO:".length()).replaceAll("[<>\\s]", "");
                repondre(sortie, "250 ok");
            } else if (commande.equals("DATA")) {
                repondre(sortie, "354 go");
                StringBuilder contenu = new StringBuilder();
                for (String donnee; !(donnee = entree.readLine()).equals("."); ) {
                    contenu.append(donnee.startsWith("..") ? donnee.substring(1) : donnee).append("\r\n");
                }
                emails.add(new EmailRecu(destinataire, new MimeMessage(Session.getInstance(new Properties()),
                        new ByteArrayInputStream(contenu.toString().getBytes(StandardCharsets.ISO_8859_1)))));
                repondre(sortie, "250 ok");
            } else if (commande.equals("QUIT")) {
                repondre(sortie, "221 bye");
                return;
            } else {
                repondre(sortie, "250 ok");
            }
        }
    }

    private static void repondre(PrintWriter sortie, String reponse) {
        sortie.print(reponse + "\r\n");
        sortie.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}

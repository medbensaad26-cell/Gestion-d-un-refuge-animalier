
package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.AdminDAO;
import com.enicarthage.refugeanimalier.model.Admin;
import com.enicarthage.refugeanimalier.security.PasswordUtils;

import java.sql.SQLException;

public class AuthService {

    private final AdminDAO adminDAO = new AdminDAO();

    // Informations publiques de l'administrateur
    // Le mot de passe n'est jamais inclus
    public record AdminConnecte(
            Long id,
            String nom,
            String email
    ) {}

    // Authentifier un administrateur
    public AdminConnecte connecter(
            String email,
            String motDePasse) throws SQLException {

        // Verifier les champs obligatoires
        if (email == null || email.isBlank() ||
                motDePasse == null ||
                motDePasse.isBlank()) {

            throw new IllegalArgumentException(
                    "Email et mot de passe obligatoires");
        }

        // Rechercher l'administrateur dans MySQL
        Admin admin = adminDAO.findByEmail(email.trim());

        // Verifier le mot de passe avec BCrypt
        if (admin == null ||
                !PasswordUtils.verifier(
                        motDePasse,
                        admin.getMotDePasse())) {

            throw new SecurityException(
                    "Identifiants invalides");
        }

        // Retourner uniquement les donnees publiques
        return new AdminConnecte(
                admin.getId(),
                admin.getNom(),
                admin.getEmail()
        );
    }
}

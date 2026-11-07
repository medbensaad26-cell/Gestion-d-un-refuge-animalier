
package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.model.Admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminDAO {

    // Rechercher un administrateur par email
    public Admin findByEmail(String email)
            throws SQLException {

        String sql = "SELECT id, nom, email, mot_de_passe " +
                "FROM admin WHERE email = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet rs = statement.executeQuery()) {

                if (rs.next()) {

                    Admin admin = new Admin();

                    admin.setId(rs.getLong("id"));

                    admin.setNom(
                            rs.getString("nom"));

                    admin.setEmail(
                            rs.getString("email"));

                    admin.setMotDePasse(
                            rs.getString("mot_de_passe"));

                    return admin;
                }
            }
        }

        return null;
    }
}

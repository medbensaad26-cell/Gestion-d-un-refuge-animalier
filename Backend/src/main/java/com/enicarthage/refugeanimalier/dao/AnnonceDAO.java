
package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.model.Annonce;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

public class AnnonceDAO {

    public List<Annonce> findAll() throws SQLException {

        List<Annonce> annonces = new ArrayList<>();

        String sql = "SELECT * FROM annonce";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet rs =
                     statement.executeQuery()) {

            while (rs.next()) {

                Annonce annonce = new Annonce();

                annonce.setId(rs.getLong("id"));
                annonce.setTitre(rs.getString("titre"));
                annonce.setDescription(
                        rs.getString("description"));

                Date date = rs.getDate("date_publication");

                if (date != null) {
                    annonce.setDatePublication(
                            date.toLocalDate());
                }

                annonce.setStatut(
                        rs.getString("statut"));

                annonce.setAdminId(
                        rs.getLong("admin_id"));

                annonce.setAnimalId(
                        rs.getLong("animal_id"));

                annonces.add(annonce);
            }
        }

        return annonces;
    }

    public Annonce findById(long id) throws SQLException {

        String sql = "SELECT * FROM annonce WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet rs = statement.executeQuery()) {

                if (rs.next()) {
                    Annonce annonce = new Annonce();

                    annonce.setId(rs.getLong("id"));
                    annonce.setTitre(rs.getString("titre"));
                    annonce.setDescription(
                            rs.getString("description"));

                    Date date = rs.getDate("date_publication");

                    if (date != null) {
                        annonce.setDatePublication(
                                date.toLocalDate());
                    }

                    annonce.setStatut(rs.getString("statut"));
                    annonce.setAdminId(
                            rs.getLong("admin_id"));
                    annonce.setAnimalId(
                            rs.getLong("animal_id"));

                    return annonce;
                }
            }
        }

        return null;
    }

    public int create(Annonce annonce) throws SQLException {

        String sql = "INSERT INTO annonce " +
                "(titre, description, date_publication, " +
                "statut, admin_id, animal_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, annonce.getTitre());
            statement.setString(2, annonce.getDescription());
            statement.setDate(3, Date.valueOf(
                    annonce.getDatePublication()));
            statement.setString(4, annonce.getStatut());
            statement.setLong(5, annonce.getAdminId());
            statement.setLong(6, annonce.getAnimalId());

            return statement.executeUpdate();
        }
    }

    // Modifier une annonce
    public int update(Annonce annonce) throws SQLException {

        String sql = "UPDATE annonce SET " +
                "titre = ?, description = ?, " +
                "date_publication = ?, statut = ?, " +
                "admin_id = ?, animal_id = ? " +
                "WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, annonce.getTitre());
            statement.setString(2, annonce.getDescription());
            statement.setDate(3, Date.valueOf(
                    annonce.getDatePublication()));
            statement.setString(4, annonce.getStatut());
            statement.setLong(5, annonce.getAdminId());
            statement.setLong(6, annonce.getAnimalId());
            statement.setLong(7, annonce.getId());

            return statement.executeUpdate();
        }
    }

    // Supprimer une annonce
    public int delete(long id) throws SQLException {

        String sql = "DELETE FROM annonce WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            return statement.executeUpdate();
        }
    }


}

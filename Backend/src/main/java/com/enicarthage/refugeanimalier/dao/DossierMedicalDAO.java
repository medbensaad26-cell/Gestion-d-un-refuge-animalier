
package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.model.DossierMedical;
import com.enicarthage.refugeanimalier.exception.RessourceIntrouvableException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

public class DossierMedicalDAO {

    public DossierMedical findByAnimalId(long animalId)
            throws SQLException {

        String sql = "SELECT * FROM dossier_medical " +
                "WHERE animal_id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, animalId);

            try (ResultSet rs = statement.executeQuery()) {

                if (rs.next()) {

                    DossierMedical dossier = new DossierMedical();

                    dossier.setId(rs.getLong("id"));
                    dossier.setVaccins(rs.getString("vaccins"));
                    dossier.setTraitements(
                            rs.getString("traitements"));
                    dossier.setObservations(
                            rs.getString("observations"));
                    dossier.setAnimalId(
                            rs.getLong("animal_id"));

                    Date date = rs.getDate("date_mise_a_jour");
                    if (date != null) {
                        dossier.setDateMiseAJour(
                                date.toLocalDate());
                    }

                    return dossier;
                }
            }
        }

        return null;
    }

    public int create(DossierMedical dossier) throws SQLException {

        String sql = "INSERT INTO dossier_medical " +
                "(vaccins, traitements, observations, " +
                "date_mise_a_jour, animal_id) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, dossier.getVaccins());
            statement.setString(2, dossier.getTraitements());
            statement.setString(3, dossier.getObservations());
            statement.setDate(4, Date.valueOf(
                    dossier.getDateMiseAJour()));
            statement.setLong(5, dossier.getAnimalId());

            return statement.executeUpdate();
        }
    }

    public int updateByAnimalId(DossierMedical dossier)
            throws SQLException {

        String sql = "UPDATE dossier_medical SET " +
                "vaccins = ?, traitements = ?, " +
                "observations = ?, date_mise_a_jour = ? " +
                "WHERE animal_id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, dossier.getVaccins());
            statement.setString(2, dossier.getTraitements());
            statement.setString(3, dossier.getObservations());
            statement.setDate(4, Date.valueOf(
                    dossier.getDateMiseAJour()));
            statement.setLong(5, dossier.getAnimalId());

            int lignes = statement.executeUpdate();
            if (lignes == 0) {
                throw new RessourceIntrouvableException("Dossier medical introuvable");
            }
            return lignes;
        }
    }

    public int deleteByAnimalId(long animalId)
            throws SQLException {
        throw new IllegalStateException(
                "Un animal doit conserver son dossier medical ; modifiez son contenu");
    }

}

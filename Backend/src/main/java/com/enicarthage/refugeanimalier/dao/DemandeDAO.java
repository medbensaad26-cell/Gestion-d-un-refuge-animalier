
package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.model.Demande;
import com.enicarthage.refugeanimalier.model.StatutDemande;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

public class DemandeDAO {

    public List<Demande> findAll() throws SQLException {

        List<Demande> demandes = new ArrayList<>();

        String sql = "SELECT * FROM demande ORDER BY id";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet rs =
                     statement.executeQuery()) {

            while (rs.next()) {

                Demande demande = new Demande();

                demande.setId(rs.getLong("id"));

                demande.setNomDemandeur(
                        rs.getString("nom_demandeur"));

                demande.setEmail(
                        rs.getString("email"));

                demande.setTelephone(
                        rs.getString("telephone"));

                Date date = rs.getDate("date_demande");

                if (date != null) {
                    demande.setDateDemande(
                            date.toLocalDate());
                }

                demande.setStatut(
                        StatutDemande.valueOf(
                                rs.getString("statut")));

                demande.setAnimalId(
                        rs.getLong("animal_id"));

                demande.setAdminId(
                        rs.getObject("admin_id", Long.class));

                demandes.add(demande);
            }
        }

        return demandes;
    }

    public Demande findById(long id) throws SQLException {

        String sql = "SELECT * FROM demande WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet rs = statement.executeQuery()) {

                if (rs.next()) {

                    Demande demande = new Demande();

                    demande.setId(rs.getLong("id"));

                    demande.setNomDemandeur(
                            rs.getString("nom_demandeur"));

                    demande.setEmail(
                            rs.getString("email"));

                    demande.setTelephone(
                            rs.getString("telephone"));

                    Date date = rs.getDate("date_demande");

                    if (date != null) {
                        demande.setDateDemande(
                                date.toLocalDate());
                    }

                    demande.setStatut(
                            StatutDemande.valueOf(
                                    rs.getString("statut")));

                    demande.setAnimalId(
                            rs.getLong("animal_id"));

                    demande.setAdminId(
                            rs.getObject("admin_id", Long.class));

                    return demande;
                }
            }
        }

        return null;
    }

    public int create(Demande demande) throws SQLException {

        String sql = "INSERT INTO demande " +
                "(nom_demandeur, email, telephone, " +
                "date_demande, statut, animal_id, admin_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1, demande.getNomDemandeur());

            statement.setString(
                    2, demande.getEmail());

            statement.setString(
                    3, demande.getTelephone());

            statement.setDate(
                    4, Date.valueOf(demande.getDateDemande()));

            statement.setString(
                    5, demande.getStatut().name());

            statement.setLong(
                    6, demande.getAnimalId());

            statement.setObject(
                    7, demande.getAdminId(),
                    java.sql.Types.BIGINT);

            return statement.executeUpdate();
        }

    }

    public int update(Demande demande) throws SQLException {

        String sql = "UPDATE demande SET " +
                "nom_demandeur = ?, email = ?, " +
                "telephone = ?, date_demande = ?, " +
                "statut = ?, animal_id = ?, admin_id = ? " +
                "WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1, demande.getNomDemandeur());

            statement.setString(
                    2, demande.getEmail());

            statement.setString(
                    3, demande.getTelephone());

            statement.setDate(
                    4, Date.valueOf(demande.getDateDemande()));

            statement.setString(
                    5, demande.getStatut().name());

            statement.setLong(
                    6, demande.getAnimalId());

            statement.setObject(
                    7, demande.getAdminId(),
                    java.sql.Types.BIGINT);

            statement.setLong(
                    8, demande.getId());

            return statement.executeUpdate();
        }
    }


    public int delete(long id) throws SQLException {

        String sql = "DELETE FROM demande WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            return statement.executeUpdate();
        }
    }

    // Accepter ou refuser une demande avec transaction
    public void traiterDemande(
            long id, StatutDemande decision)
            throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID demande invalide");
        }

        if (decision != StatutDemande.ACCEPTEE &&
                decision != StatutDemande.REFUSEE) {
            throw new IllegalArgumentException(
                    "Decision invalide");
        }

        try (Connection connection =
                     DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try {
                Long animalId = null;

                // Identifier l'animal de la demande
                String sqlId =
                        "SELECT animal_id FROM demande WHERE id = ?";

                try (PreparedStatement ps =
                             connection.prepareStatement(sqlId)) {

                    ps.setLong(1, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            animalId = rs.getLong("animal_id");
                        }
                    }
                }

                if (animalId == null) {
                    throw new IllegalArgumentException(
                            "Demande introuvable");
                }

                // Verrouiller l'animal en premier
                String statutAnimal;

                try (PreparedStatement ps =
                             connection.prepareStatement(
                                     "SELECT statut FROM animal " +
                                             "WHERE id = ? FOR UPDATE")) {

                    ps.setLong(1, animalId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Animal introuvable");
                        }

                        statutAnimal = rs.getString("statut");
                    }
                }

                // Verrouiller et verifier la demande
                try (PreparedStatement ps =
                             connection.prepareStatement(
                                     "SELECT statut, animal_id FROM demande " +
                                             "WHERE id = ? FOR UPDATE")) {

                    ps.setLong(1, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Demande introuvable");
                        }

                        if (rs.getLong("animal_id") != animalId) {
                            throw new IllegalStateException(
                                    "Animal de la demande modifie");
                        }

                        if (!"EN_ATTENTE".equals(
                                rs.getString("statut"))) {
                            throw new IllegalStateException(
                                    "Demande deja traitee");
                        }
                    }
                }

                if (decision == StatutDemande.ACCEPTEE &&
                        !"DISPONIBLE".equals(statutAnimal)) {
                    throw new IllegalStateException(
                            "Animal non disponible");
                }

                // Mettre a jour la demande
                String sqlDemande =
                        "UPDATE demande SET statut = ? " +
                                "WHERE id = ? AND statut = 'EN_ATTENTE'";

                try (PreparedStatement ps =
                             connection.prepareStatement(sqlDemande)) {

                    ps.setString(1, decision.name());
                    ps.setLong(2, id);

                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Modification de demande impossible");
                    }
                }

                // Si acceptee, marquer l'animal comme adopte
                if (decision == StatutDemande.ACCEPTEE) {

                    String sqlAnimal =
                            "UPDATE animal SET statut = 'ADOPTE' " +
                                    "WHERE id = ? AND statut = 'DISPONIBLE'";

                    try (PreparedStatement ps =
                                 connection.prepareStatement(sqlAnimal)) {

                        ps.setLong(1, animalId);

                        if (ps.executeUpdate() != 1) {
                            throw new IllegalStateException(
                                    "Modification de l'animal impossible");
                        }
                    }
                }

                connection.commit();

            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }





}


package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.exception.RessourceIntrouvableException;
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
        if (demande.getStatut() != StatutDemande.EN_ATTENTE) {
            throw new IllegalArgumentException("Une nouvelle demande doit etre EN_ATTENTE");
        }
        if (demande.getAdminId() != null && demande.getAdminId() <= 0) {
            throw new IllegalArgumentException("ID administrateur invalide");
        }

        String sql = "INSERT INTO demande " +
                "(nom_demandeur, email, telephone, " +
                "date_demande, statut, animal_id, admin_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String statutAnimal = verrouillerAnimal(connection, demande.getAnimalId());
                if (!"DISPONIBLE".equals(statutAnimal)
                        || possedeAdoptionAcceptee(connection, demande.getAnimalId())) {
                    throw new IllegalStateException("Animal non disponible");
                }

                long adminId = new AdminDAO().findSoleAdministratorId(connection);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, demande.getNomDemandeur());
                    statement.setString(2, demande.getEmail());
                    statement.setString(3, demande.getTelephone());
                    statement.setDate(4, Date.valueOf(demande.getDateDemande()));
                    statement.setString(5, StatutDemande.EN_ATTENTE.name());
                    statement.setLong(6, demande.getAnimalId());
                    statement.setLong(7, adminId);
                    int lignes = statement.executeUpdate();
                    if (lignes != 1) {
                        throw new IllegalStateException("Creation de la demande impossible");
                    }
                    connection.commit();
                    demande.setAdminId(adminId);
                    return lignes;
                }
            } catch (SQLException | RuntimeException e) {
                annuler(connection, e);
                throw e;
            }
        }
    }

    public int update(Demande demande) throws SQLException {
        // Le DAO ne doit pas offrir un contournement des decisions atomiques.
        throw new IllegalArgumentException(
                "Modification generale temporairement desactivee. " +
                        "Utilisez les actions accepter ou refuser.");
    }

    public int delete(long id) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long animalId = trouverAnimalId(connection, id);
                // Meme ordre de verrouillage que l'acceptation : animal, demande.
                verrouillerAnimal(connection, animalId);
                String statut = verrouillerDemande(connection, id, animalId);
                if (StatutDemande.ACCEPTEE.name().equals(statut)) {
                    throw new IllegalStateException(
                            "Une demande acceptee appartient a l'historique d'adoption");
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM demande WHERE id = ? AND statut <> 'ACCEPTEE'")) {
                    statement.setLong(1, id);
                    int lignes = statement.executeUpdate();
                    if (lignes != 1) {
                        throw new IllegalStateException("Suppression de la demande impossible");
                    }
                    connection.commit();
                    return lignes;
                }
            } catch (SQLException | RuntimeException e) {
                annuler(connection, e);
                throw e;
            }
        }
    }

    // La decision et l'identite de l'administrateur sont enregistrees ensemble.
    public void traiterDemande(long id, StatutDemande decision, long adminId)
            throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("ID demande invalide");
        }
        if (adminId <= 0) {
            throw new IllegalArgumentException("ID administrateur invalide");
        }
        if (decision != StatutDemande.ACCEPTEE && decision != StatutDemande.REFUSEE) {
            throw new IllegalArgumentException("Decision invalide");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long animalId = trouverAnimalId(connection, id);
                String statutAnimal = verrouillerAnimal(connection, animalId);
                String statutDemande = verrouillerDemande(connection, id, animalId);
                if (!StatutDemande.EN_ATTENTE.name().equals(statutDemande)) {
                    throw new IllegalStateException("Demande deja traitee");
                }

                if (decision == StatutDemande.ACCEPTEE
                        && (!"DISPONIBLE".equals(statutAnimal)
                        || possedeAdoptionAcceptee(connection, animalId))) {
                    throw new IllegalStateException("Animal non disponible");
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE demande SET statut = ?, admin_id = ? " +
                                "WHERE id = ? AND statut = 'EN_ATTENTE'")) {
                    statement.setString(1, decision.name());
                    statement.setLong(2, adminId);
                    statement.setLong(3, id);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("Modification de demande impossible");
                    }
                }

                if (decision == StatutDemande.ACCEPTEE) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE animal SET statut = 'ADOPTE' " +
                                    "WHERE id = ? AND statut = 'DISPONIBLE'")) {
                        statement.setLong(1, animalId);
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalStateException("Modification de l'animal impossible");
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                annuler(connection, e);
                throw e;
            }
        }
    }

    private long trouverAnimalId(Connection connection, long demandeId)
            throws SQLException {
        if (demandeId <= 0) {
            throw new IllegalArgumentException("ID demande invalide");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT animal_id FROM demande WHERE id = ?")) {
            statement.setLong(1, demandeId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new RessourceIntrouvableException("Demande introuvable");
                }
                return rs.getLong("animal_id");
            }
        }
    }

    private String verrouillerAnimal(Connection connection, long animalId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT statut FROM animal WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, animalId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new RessourceIntrouvableException("Animal introuvable");
                }
                return rs.getString("statut");
            }
        }
    }

    private String verrouillerDemande(Connection connection, long demandeId, long animalId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT statut, animal_id FROM demande WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, demandeId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new RessourceIntrouvableException("Demande introuvable");
                }
                if (rs.getLong("animal_id") != animalId) {
                    throw new IllegalStateException("Animal de la demande modifie");
                }
                return rs.getString("statut");
            }
        }
    }

    private boolean possedeAdoptionAcceptee(Connection connection, long animalId)
            throws SQLException {
        // FOR UPDATE est une lecture courante, pas le snapshot cree par la
        // premiere recherche de demande sous REPEATABLE READ.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM demande WHERE animal_id = ? " +
                        "AND statut = 'ACCEPTEE' LIMIT 1 FOR UPDATE")) {
            statement.setLong(1, animalId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void annuler(Connection connection, Exception erreur) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            erreur.addSuppressed(e);
        }
    }
}

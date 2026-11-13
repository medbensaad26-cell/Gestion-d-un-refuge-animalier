
package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.service.FiltresCatalogue;
import com.enicarthage.refugeanimalier.exception.RessourceIntrouvableException;
import com.enicarthage.refugeanimalier.model.Animal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AnimalDAO {

    public List<Animal> findAll() throws SQLException {
        return rechercher(FiltresCatalogue.AUCUN);
    }

    public List<Animal> rechercher(FiltresCatalogue filtres) throws SQLException {

        List<Animal> animaux = new ArrayList<>();

        StringBuilder sql = new StringBuilder("SELECT a.* FROM animal a WHERE 1=1");
        List<String> valeurs = new ArrayList<>();
        RechercheSQL.appliquer(sql, valeurs, filtres, "a", "a.nom", "a.espece", "a.race");
        sql.append(" ORDER BY a.id");

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {
            RechercheSQL.lier(statement, valeurs);
            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {

                    Animal animal = new Animal();

                    animal.setId(resultSet.getLong("id"));
                    animal.setNom(resultSet.getString("nom"));
                    animal.setEspece(resultSet.getString("espece"));
                    animal.setRace(resultSet.getString("race"));
                    animal.setAge(resultSet.getInt("age"));
                    animal.setSexe(resultSet.getString("sexe"));
                    animal.setEtatSante(
                            resultSet.getString("etat_sante"));
                    animal.setStatut(
                            resultSet.getString("statut"));
                    animal.setAdminId(
                            resultSet.getLong("admin_id"));

                    animaux.add(animal);
                }
            }
        }

        return animaux;
    }

    public void create(Animal animal) throws SQLException {

        if (estAdopte(animal.getStatut())) {
            throw new IllegalStateException(
                    "Une adoption doit etre enregistree en acceptant une demande");
        }

        String sql = "INSERT INTO animal " +
                "(nom, espece, race, age, sexe, " +
                "etat_sante, statut, admin_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, animal.getNom());
                    statement.setString(2, animal.getEspece());
                    statement.setString(3, animal.getRace());
                    statement.setInt(4, animal.getAge());
                    statement.setString(5, animal.getSexe());
                    statement.setString(6, animal.getEtatSante());
                    statement.setString(7, animal.getStatut());
                    statement.setLong(8, animal.getAdminId());
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("Creation de l'animal impossible");
                    }
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Identifiant animal absent");
                        id = keys.getLong(1);
                    }
                }
                // Unknown medical details stay NULL; no vaccination or treatment is invented.
                try (PreparedStatement medical = connection.prepareStatement(
                        "INSERT INTO dossier_medical (animal_id, date_mise_a_jour) VALUES (?, CURRENT_DATE)")) {
                    medical.setLong(1, id);
                    if (medical.executeUpdate() != 1) throw new SQLException("Creation du dossier impossible");
                }
                connection.commit();
                animal.setId(id);
            } catch (SQLException | RuntimeException e) {
                annuler(connection, e);
                throw e;
            }
        }
    }

    public int update(Animal animal) throws SQLException {

        String sql = "UPDATE animal SET " +
                "nom = ?, espece = ?, race = ?, " +
                "age = ?, sexe = ?, etat_sante = ?, " +
                "statut = ?, admin_id = ? " +
                "WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String statutActuel = verrouillerAnimal(connection, animal.getId());
                boolean adoptionExistante = estAdopte(statutActuel)
                        || possedeAdoptionAcceptee(connection, animal.getId());

                if (adoptionExistante && !"ADOPTE".equals(animal.getStatut())) {
                    throw new IllegalStateException(
                            "Un animal adopte doit conserver le statut ADOPTE");
                }
                if (!adoptionExistante && estAdopte(animal.getStatut())) {
                    throw new IllegalStateException(
                            "Une adoption doit etre enregistree en acceptant une demande");
                }

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, animal.getNom());
                    statement.setString(2, animal.getEspece());
                    statement.setString(3, animal.getRace());
                    statement.setInt(4, animal.getAge());
                    statement.setString(5, animal.getSexe());
                    statement.setString(6, animal.getEtatSante());
                    statement.setString(7, animal.getStatut());
                    statement.setLong(8, animal.getAdminId());
                    statement.setLong(9, animal.getId());

                    int lignes = statement.executeUpdate();
                    if (lignes != 1) {
                        throw new IllegalStateException("Modification de l'animal impossible");
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

    public int delete(long id) throws SQLException {

        String sql = "DELETE FROM animal WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String statut = verrouillerAnimal(connection, id);
                if (estAdopte(statut) || possedeAdoptionAcceptee(connection, id)) {
                    throw new IllegalStateException(
                            "L'historique d'un animal adopte ne peut pas etre supprime");
                }

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, id);
                    int lignes = statement.executeUpdate();
                    if (lignes != 1) {
                        throw new IllegalStateException("Suppression de l'animal impossible");
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


    public Animal findById(long id) throws SQLException {

        String sql = "SELECT * FROM animal WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {

                    Animal animal = new Animal();

                    animal.setId(resultSet.getLong("id"));
                    animal.setNom(resultSet.getString("nom"));
                    animal.setEspece(resultSet.getString("espece"));
                    animal.setRace(resultSet.getString("race"));
                    animal.setAge(resultSet.getInt("age"));
                    animal.setSexe(resultSet.getString("sexe"));
                    animal.setEtatSante(
                            resultSet.getString("etat_sante"));
                    animal.setStatut(resultSet.getString("statut"));
                    animal.setAdminId(resultSet.getLong("admin_id"));

                    return animal;
                }
            }
        }

        return null;
    }



    private String verrouillerAnimal(Connection connection, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT statut FROM animal WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new RessourceIntrouvableException("Animal introuvable avec ID : " + id);
                }
                return rs.getString("statut");
            }
        }
    }

    private boolean possedeAdoptionAcceptee(Connection connection, long animalId)
            throws SQLException {
        // Une lecture verrouillante voit les decisions deja validees, meme si
        // une autre lecture a precedemment etabli un snapshot REPEATABLE READ.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM demande WHERE animal_id = ? " +
                        "AND statut = 'ACCEPTEE' LIMIT 1 FOR UPDATE")) {
            statement.setLong(1, animalId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean estAdopte(String statut) {
        return statut != null && "ADOPTE".equalsIgnoreCase(statut.strip());
    }

    private void annuler(Connection connection, Exception erreur) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            erreur.addSuppressed(e);
        }
    }
}

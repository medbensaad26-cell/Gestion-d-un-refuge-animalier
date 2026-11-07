
package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import com.enicarthage.refugeanimalier.model.Animal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnimalDAO {

    public List<Animal> findAll() throws SQLException {

        List<Animal> animaux = new ArrayList<>();

        String sql = "SELECT * FROM animal";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet resultSet =
                     statement.executeQuery()) {

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

        return animaux;
    }

    public void create(Animal animal) throws SQLException {

        String sql = "INSERT INTO animal " +
                "(nom, espece, race, age, sexe, " +
                "etat_sante, statut, admin_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, animal.getNom());
            statement.setString(2, animal.getEspece());
            statement.setString(3, animal.getRace());
            statement.setInt(4, animal.getAge());
            statement.setString(5, animal.getSexe());
            statement.setString(6, animal.getEtatSante());
            statement.setString(7, animal.getStatut());
            statement.setLong(8, animal.getAdminId());

            statement.executeUpdate();
        }
    }

    public int update(Animal animal) throws SQLException {

        String sql = "UPDATE animal SET " +
                "nom = ?, espece = ?, race = ?, " +
                "age = ?, sexe = ?, etat_sante = ?, " +
                "statut = ?, admin_id = ? " +
                "WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, animal.getNom());
            statement.setString(2, animal.getEspece());
            statement.setString(3, animal.getRace());
            statement.setInt(4, animal.getAge());
            statement.setString(5, animal.getSexe());
            statement.setString(6, animal.getEtatSante());
            statement.setString(7, animal.getStatut());
            statement.setLong(8, animal.getAdminId());
            statement.setLong(9, animal.getId());

            return statement.executeUpdate();
        }
    }

    public int delete(long id) throws SQLException {

        String sql = "DELETE FROM animal WHERE id = ?";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            return statement.executeUpdate();
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



}

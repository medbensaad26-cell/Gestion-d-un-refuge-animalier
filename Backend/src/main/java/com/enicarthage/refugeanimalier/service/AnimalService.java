
package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.AnimalDAO;
import com.enicarthage.refugeanimalier.model.Animal;

import java.sql.SQLException;
import java.util.List;

public class AnimalService {

    private final AnimalDAO animalDAO;

    public AnimalService() {
        this.animalDAO = new AnimalDAO();
    }

    // Afficher tous les animaux
    public List<Animal> getAllAnimaux() throws SQLException {
        return animalDAO.findAll();
    }


    public Animal getAnimalById(long id) throws SQLException {

        Animal animal = animalDAO.findById(id);

        if (animal == null) {
            throw new IllegalArgumentException(
                    "Animal introuvable avec ID : " + id);
        }

        return animal;
    }


    // Ajouter un animal avec validation
    public void ajouterAnimal(Animal animal) throws SQLException {

        if (animal == null) {
            throw new IllegalArgumentException(
                    "L'animal ne peut pas etre null.");
        }

        if (animal.getNom() == null ||
                animal.getNom().isBlank()) {
            throw new IllegalArgumentException(
                    "Le nom de l'animal est obligatoire.");
        }

        if (animal.getAge() < 0) {
            throw new IllegalArgumentException(
                    "L'age ne peut pas etre negatif.");
        }

        if (animal.getAdminId() == null) {
            throw new IllegalArgumentException(
                    "Un administrateur est obligatoire.");
        }

        animalDAO.create(animal);
    }

    public void modifierAnimal(Animal animal) throws SQLException {

        if (animal == null || animal.getId() == null) {
            throw new IllegalArgumentException(
                    "L'animal et son ID sont obligatoires.");
        }

        if (animal.getNom() == null ||
                animal.getNom().isBlank()) {
            throw new IllegalArgumentException(
                    "Le nom de l'animal est obligatoire.");
        }

        if (animal.getAge() < 0) {
            throw new IllegalArgumentException(
                    "L'age ne peut pas etre negatif.");
        }

        if (animal.getAdminId() == null) {
            throw new IllegalArgumentException(
                    "Un administrateur est obligatoire.");
        }

        // Verifier que l'animal existe
        getAnimalById(animal.getId());

        // Modifier l'animal dans MySQL
        animalDAO.update(animal);
    }

    public void supprimerAnimal(long id) throws SQLException {

        // Verifier que l'animal existe
        getAnimalById(id);

        // Supprimer l'animal dans MySQL
        animalDAO.delete(id);
    }


}

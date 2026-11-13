
package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.AnimalDAO;
import com.enicarthage.refugeanimalier.exception.RessourceIntrouvableException;
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

    public List<Animal> rechercherAnimaux(FiltresCatalogue filtres) throws SQLException {
        return animalDAO.rechercher(filtres);
    }


    public Animal getAnimalById(long id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("ID animal invalide");
        }
        Animal animal = animalDAO.findById(id);

        if (animal == null) {
            throw new RessourceIntrouvableException(
                    "Animal introuvable avec ID : " + id);
        }

        return animal;
    }


    // Ajouter un animal avec validation
    public void ajouterAnimal(Animal animal) throws SQLException {

        validerAnimal(animal);

        animalDAO.create(animal);
    }

    public void modifierAnimal(Animal animal) throws SQLException {

        if (animal == null || animal.getId() == null || animal.getId() <= 0) {
            throw new IllegalArgumentException(
                    "L'animal et son ID sont obligatoires.");
        }

        validerAnimal(animal);

        // Le DAO verifie l'existence et l'adoption sous le meme verrou.
        animalDAO.update(animal);
    }

    public void supprimerAnimal(long id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("ID animal invalide");
        }
        // Le DAO protege aussi l'historique sous le verrou de l'animal.
        animalDAO.delete(id);
    }


    private void validerAnimal(Animal animal) {
        if (animal == null) {
            throw new IllegalArgumentException("Animal obligatoire");
        }
        ValidationDonnees.texte(animal.getNom(), "nom", 100, true);
        ValidationDonnees.texte(animal.getEspece(), "espece", 100, true);
        ValidationDonnees.texte(animal.getRace(), "race", 100, false);
        ValidationDonnees.texte(animal.getSexe(), "sexe", 20, true);
        ValidationDonnees.texte(animal.getEtatSante(), "etatSante", 255, false);
        ValidationDonnees.texte(animal.getStatut(), "statut", 50, true);
        ValidationDonnees.statutAnimal(animal.getStatut());
        if (animal.getAge() < 0) {
            throw new IllegalArgumentException("L'age doit etre positif ou nul");
        }
        if (animal.getAdminId() == null || animal.getAdminId() <= 0) {
            throw new IllegalArgumentException("Un administrateur est obligatoire");
        }
    }

}

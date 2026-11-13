
package com.enicarthage.refugeanimalier.controller;
import com.enicarthage.refugeanimalier.service.DossierMedicalService;
import com.enicarthage.refugeanimalier.model.DossierMedical;
import com.google.gson.JsonObject;
import com.enicarthage.refugeanimalier.model.Animal;
import com.enicarthage.refugeanimalier.service.AnimalService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.enicarthage.refugeanimalier.exception.RessourceIntrouvableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.enicarthage.refugeanimalier.security.AuthGuard;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

public class AnimalController implements HttpHandler {

    private static final String BASE = "/api/animaux";
    private final AnimalService animalService = new AnimalService();
    private final Gson gson = new Gson();

    private final DossierMedicalService dossierMedicalService =
            new DossierMedicalService();


    public List<Animal> listerAnimaux() throws SQLException {
        return animalService.getAllAnimaux();
    }

    public Animal detailAnimal(long id) throws SQLException {
        return animalService.getAnimalById(id);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        boolean dossierMedical =
                path.startsWith(BASE + "/") &&
                        path.contains("/dossier-medical");

        if (dossierMedical) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Vary", "Authorization");
        }

        Long adminId = null;
        if (dossierMedical || !method.equals("GET")) {
            adminId = AuthGuard.obtenirAdminAuthentifie(exchange);
            if (adminId == null) {
                return;
            }
        }

        try {
            if (path.equals(BASE)) {
                switch (method) {
                    case "GET" ->
                            repondre(exchange, 200, animalService.rechercherAnimaux(QueryCatalogue.lire(exchange.getRequestURI())));

                    case "POST" -> {
                        Animal animal = lireAnimal(exchange);
                        animal.setAdminId(adminId);
                        animal.setId(null);

                        animalService.ajouterAnimal(animal);

                        repondre(exchange, 201,
                                Map.of("message", "Animal ajoute"));
                    }

                    default ->
                            methodeNonAutorisee(exchange, "GET, POST");
                }
                return;
            }



            // GET /api/animaux/{id}/dossier-medical

            if (path.startsWith(BASE + "/") &&
                    path.endsWith("/dossier-medical")) {

                String idText = path.substring(
                        (BASE + "/").length(),
                        path.length() - "/dossier-medical".length()
                );

                long animalId;

                try {
                    animalId = Long.parseLong(idText);
                    if (animalId <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    erreur(exchange, 400, "ID animal invalide");
                    return;
                }

                if (!method.equals("GET") &&
                        !method.equals("POST") &&
                        !method.equals("PUT")) {
                    methodeNonAutorisee(
                            exchange, "GET, POST, PUT");
                    return;
                }

                DossierMedical existant =
                        dossierMedicalService
                                .getDossierByAnimalId(animalId);

                if (method.equals("GET")) {
                    if (existant == null) {
                        erreur(exchange, 404,
                                "Dossier medical introuvable");
                    } else {
                        repondre(exchange, 200,
                                jsonDossier(existant));
                    }
                    return;
                }

                if (method.equals("POST")) {
                    try {
                        animalService.getAnimalById(animalId);
                    } catch (IllegalArgumentException e) {
                        erreur(exchange, 404, "Animal introuvable");
                        return;
                    }

                    if (existant != null) {
                        erreur(exchange, 409,
                                "Cet animal possede deja un dossier");
                        return;
                    }

                    DossierMedical dossier =
                            lireDossier(exchange, animalId);

                    dossierMedicalService.ajouterDossier(dossier);

                    repondre(exchange, 201,
                            jsonDossier(dossierMedicalService
                                    .getDossierByAnimalId(animalId)));
                    return;
                }

                if (existant == null) {
                    erreur(exchange, 404,
                            "Dossier medical introuvable");
                    return;
                }

                if (method.equals("PUT")) {
                    DossierMedical dossier =
                            lireDossier(exchange, animalId);

                    dossierMedicalService.modifierDossier(dossier);

                    repondre(exchange, 200,
                            jsonDossier(dossierMedicalService
                                    .getDossierByAnimalId(animalId)));
                    return;
                }

            }



            if (path.startsWith(BASE + "/")) {
                String idTexte =
                        path.substring((BASE + "/").length());

                long id;
                try {
                    id = Long.parseLong(idTexte);
                    if (id <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    erreur(exchange, 400, "ID invalide");
                    return;
                }

                if (!method.equals("GET") &&
                        !method.equals("PUT") &&
                        !method.equals("DELETE")) {
                    methodeNonAutorisee(
                            exchange, "GET, PUT, DELETE");
                    return;
                }

                Animal existant;
                try {
                    existant = detailAnimal(id);
                } catch (IllegalArgumentException e) {
                    erreur(exchange, 404, "Animal introuvable");
                    return;
                }

                switch (method) {
                    case "GET" ->
                            repondre(exchange, 200, existant);

                    case "PUT" -> {
                        Animal animal = lireAnimal(exchange);
                        animal.setAdminId(adminId);
                        animal.setId(id);

                        animalService.modifierAnimal(animal);

                        repondre(exchange, 200,
                                Map.of(
                                        "message", "Animal modifie",
                                        "id", id
                                ));
                    }

                    case "DELETE" -> {
                        animalService.supprimerAnimal(id);

                        repondre(exchange, 200,
                                Map.of(
                                        "message", "Animal supprime",
                                        "id", id
                                ));
                    }

                    default -> throw new IllegalStateException();
                }
                return;
            }

            erreur(exchange, 404, "Route introuvable");

        } catch (JsonParseException e) {
            erreur(exchange, 400, "JSON invalide");

        } catch (RessourceIntrouvableException e) {
            erreur(exchange, 404, e.getMessage());

        } catch (IllegalArgumentException e) {
            erreur(exchange, 400, e.getMessage());

        } catch (IllegalStateException e) {
            erreur(exchange, 409, e.getMessage());

        } catch (SQLIntegrityConstraintViolationException e) {
            erreur(exchange, 409,
                    "Operation impossible : contrainte de base de donnees");

        } catch (SQLException e) {
            e.printStackTrace();
            erreur(exchange, 500, "Erreur de base de donnees");
        }
    }

    private Animal lireAnimal(HttpExchange exchange) throws IOException {
        JsonObject json = JsonRequest.lireObjet(exchange);
        Animal animal = new Animal();
        animal.setNom(texteJson(json, "nom"));
        animal.setEspece(texteJson(json, "espece"));
        animal.setRace(texteJson(json, "race"));
        animal.setSexe(texteJson(json, "sexe"));
        animal.setEtatSante(texteJson(json, "etatSante"));
        animal.setStatut(texteJson(json, "statut"));

        JsonElement age = json.get("age");
        if (age == null || !age.isJsonPrimitive()
                || !age.getAsJsonPrimitive().isNumber()
                || !age.getAsString().matches("[0-9]+")) {
            throw new IllegalArgumentException("age entier positif ou nul obligatoire");
        }
        try {
            animal.setAge(Integer.parseInt(age.getAsString()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("age entier invalide");
        }
        // id and adminId from JSON are deliberately ignored.
        return animal;
    }

    private void methodeNonAutorisee(
            HttpExchange exchange, String methodes)
            throws IOException {

        exchange.getResponseHeaders().set("Allow", methodes);
        erreur(exchange, 405, "Methode non autorisee");
    }

    private void erreur(
            HttpExchange exchange, int status, String message)
            throws IOException {

        repondre(exchange, status,
                Map.of("erreur", message));
    }

    private void repondre(
            HttpExchange exchange, int status, Object contenu)
            throws IOException {

        byte[] data = gson.toJson(contenu)
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(status, data.length);

        try (OutputStream output =
                     exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private DossierMedical lireDossier(
            HttpExchange exchange, long animalId)
            throws IOException {

        JsonObject json = JsonRequest.lireObjet(exchange);

        String dateText = texteJson(
                json, "dateMiseAJour");

        if (dateText == null) {
            throw new IllegalArgumentException(
                    "dateMiseAJour obligatoire");
        }

        java.time.LocalDate date;

        try {
            date = java.time.LocalDate.parse(dateText);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Date invalide : utiliser AAAA-MM-JJ");
        }

        DossierMedical dossier = new DossierMedical();

        dossier.setAnimalId(animalId);
        dossier.setVaccins(texteJson(json, "vaccins"));
        dossier.setTraitements(
                texteJson(json, "traitements"));
        dossier.setObservations(
                texteJson(json, "observations"));
        dossier.setDateMiseAJour(date);

        return dossier;
    }

    private String texteJson(JsonObject json, String champ) {
        JsonElement valeur = json.get(champ);

        if (valeur == null || valeur.isJsonNull()) {
            return null;
        }

        if (!valeur.isJsonPrimitive() ||
                !valeur.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Champ invalide : " + champ);
        }

        return valeur.getAsString();
    }

    private JsonObject jsonDossier(DossierMedical dossier) {
        JsonObject json = new JsonObject();

        json.addProperty("id", dossier.getId());
        json.addProperty("vaccins", dossier.getVaccins());
        json.addProperty("traitements", dossier.getTraitements());
        json.addProperty("observations",
                dossier.getObservations());

        json.addProperty(
                "dateMiseAJour",
                dossier.getDateMiseAJour() == null
                        ? null
                        : dossier.getDateMiseAJour().toString()
        );

        json.addProperty("animalId", dossier.getAnimalId());

        return json;
    }

}

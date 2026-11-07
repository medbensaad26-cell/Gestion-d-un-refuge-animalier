
package com.enicarthage.refugeanimalier.main;

import com.enicarthage.refugeanimalier.router.Router;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {

        System.out.println("Backend Refuge Animalier demarre !");

        try {
            Router.start();

        } catch (IOException e) {
            System.out.println(
                    "Erreur serveur HTTP : " + e.getMessage());
        }
    }
}

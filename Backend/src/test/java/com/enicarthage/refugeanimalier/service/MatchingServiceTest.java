package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.AnimalDAO;
import com.enicarthage.refugeanimalier.dto.RecommandationAdoption;
import com.enicarthage.refugeanimalier.model.Animal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MatchingServiceTest {
    private static Animal animal(long id, String species, int age, String sex, String status) {
        return new Animal(id, "Animal " + id, species, "Race", age, sex, "Non exploitable pour le matching", status, 9L);
    }

    private static MatchingService service(List<Animal> animals) {
        return new MatchingService(new AnimalDAO() {
            @Override public List<Animal> rechercher(FiltresCatalogue filters) {
                assertNull(filters.q());
                assertNull(filters.espece()); // Preferences are soft ranking criteria, not hard SQL filters.
                assertEquals("DISPONIBLE", filters.statutAnimal());
                return animals;
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"Chien,4,FEMELLE,100", "Chien,4,MALE,80", "Chien,8,FEMELLE,70",
            "Chat,4,FEMELLE,50", "Chien,1,MALE,50", "Chat,4,MALE,30",
            "Chat,8,FEMELLE,20", "Chat,1,MALE,0"})
    void allEightCombinationsHaveExplainableWeightedScores(String species, int age, String sex, int score) throws Exception {
        var result = service(List.of(animal(1, species, age, sex, "DISPONIBLE")))
                .recommander(new PreferencesMatching("Chien", "ADULTE", "FEMELLE")).getFirst();
        assertEquals(score, result.score());
        assertTrue(result.explication().contains("Espece :"));
        assertTrue(result.explication().contains("Age (ADULTE) :"));
        assertTrue(result.explication().contains("Sexe :"));
    }

    @Test
    void omittedCriteriaAreExcludedFromDenominatorAndRoundingIsDefined() throws Exception {
        var service = service(List.of(animal(1, "Chien", 4, "MALE", "DISPONIBLE")));
        assertEquals(100, service.recommander(new PreferencesMatching("Chien", null, null)).getFirst().score());
        assertEquals(100, service.recommander(new PreferencesMatching(null, "ADULTE", null)).getFirst().score());
        assertEquals(100, service.recommander(new PreferencesMatching(null, null, "MALE")).getFirst().score());
        assertEquals(63, service.recommander(new PreferencesMatching("Chien", "SENIOR", null)).getFirst().score());
        assertEquals(38, service.recommander(new PreferencesMatching("Chat", "ADULTE", null)).getFirst().score());
        assertEquals(71, service.recommander(new PreferencesMatching("Chien", null, "FEMELLE")).getFirst().score());
        assertEquals(29, service.recommander(new PreferencesMatching("Chat", null, "MALE")).getFirst().score());
        assertEquals(60, service.recommander(new PreferencesMatching(null, "ADULTE", "FEMELLE")).getFirst().score());
        assertEquals(40, service.recommander(new PreferencesMatching(null, "SENIOR", "MALE")).getFirst().score());
    }

    @ParameterizedTest
    @CsvSource({"0,JEUNE", "1,JEUNE", "2,ADULTE", "7,ADULTE", "8,SENIOR", "2147483647,SENIOR"})
    void ageBandsUseExistingAgeInYearsWithExplicitBoundaries(int age, String category) throws Exception {
        var result = service(List.of(animal(1, "Chat", age, "F", "DISPONIBLE")))
                .recommander(new PreferencesMatching(null, category, null)).getFirst();
        assertEquals(100, result.score());
    }

    @Test
    void rankingUsesAllCandidatesThenScoreDescendingAndIdAscendingWithoutMutatingInput() throws Exception {
        var animals = new ArrayList<>(List.of(animal(8, "Chat", 1, "MALE", "DISPONIBLE"),
                animal(4, "Chat", 1, "MALE", "DISPONIBLE"), animal(2, "Chat", 1, "MALE", "DISPONIBLE"),
                animal(20, "Chien", 3, "MALE", "DISPONIBLE")));
        var originalIds = animals.stream().map(Animal::getId).toList();
        var preferences = new PreferencesMatching("Chien", null, null);
        var first = service(animals).recommander(preferences);
        assertEquals(List.of(20L, 2L, 4L), first.stream().map(r -> r.animal().id()).toList());
        assertEquals(List.of(100, 0, 0), first.stream().map(RecommandationAdoption::score).toList());
        assertEquals(originalIds, animals.stream().map(Animal::getId).toList());
        Collections.reverse(animals);
        assertEquals(first, service(animals).recommander(preferences));
    }

    @Test
    void noPreferencesAreExplicitlyUnevaluatedWithStableIdOrderAndAtMostThreeResults() throws Exception {
        var result = service(List.of(animal(9, "Chat", 2, "F", "DISPONIBLE"),
                animal(3, "Chien", 4, "M", "DISPONIBLE"), animal(1, "Chat", 6, "F", "DISPONIBLE"),
                animal(2, "Chien", 8, "M", "DISPONIBLE")))
                .recommander(new PreferencesMatching(null, " ", ""));
        assertEquals(List.of(1L, 2L, 3L), result.stream().map(r -> r.animal().id()).toList());
        assertTrue(result.stream().allMatch(r -> r.score() == 0 && r.explication().contains("non evalue")));
    }

    @Test
    void onlyExactAvailableStatusCanEverBeRecommendedEvenIfDaoReturnsOtherStatuses() throws Exception {
        var result = service(List.of(animal(1, "Chat", 1, "M", "ADOPTE"),
                animal(2, "Chat", 1, "M", "EN_SOIN"), animal(3, "Chat", 1, "M", "disponible"),
                animal(4, "Chat", 1, "M", "DISPONIBLE "), animal(5, "Chat", 1, "M", null),
                animal(6, "Chat", 1, "M", "DISPONIBLE")))
                .recommander(new PreferencesMatching("Chat", null, null));
        assertEquals(List.of(6L), result.stream().map(r -> r.animal().id()).toList());
    }

    @Test
    void emptyCatalogueOrNoAvailableAnimalsReturnsAnEmptyList() throws Exception {
        var preferences = new PreferencesMatching(null, null, null);
        assertEquals(List.of(), service(List.of()).recommander(preferences));
        assertEquals(List.of(), service(List.of(animal(1, "Chat", 1, "M", "ADOPTE"))).recommander(preferences));
    }

    @Test
    void accentCaseWhitespaceAndSexAliasesAreNormalizedWithoutChangingStoredOutput() throws Exception {
        var preferences = new PreferencesMatching(" cHiEn ", " adulte ", " mâle ");
        assertEquals(new PreferencesMatching("CHIEN", "ADULTE", "M"), preferences);
        var a = animal(1, "chien", 4, "Mâle", "DISPONIBLE");
        var result = service(List.of(a)).recommander(preferences).getFirst();
        assertEquals(100, result.score());
        assertEquals("chien", result.animal().espece());
        assertEquals("Mâle", result.animal().sexe());
        assertEquals(9L, a.getAdminId());
        assertEquals("FEMELLE", new PreferencesMatching(null, null, " f ").sexe());
    }

    @Test
    void normalizationDoesNotDependOnMachineLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(100, service(List.of(animal(1, "chien", 1, "M", "DISPONIBLE")))
                    .recommander(new PreferencesMatching("chien", null, null)).getFirst().score());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void missingOrUnknownSexAndInvalidAgeAreNeverInvented() throws Exception {
        for (String sex : new String[]{null, "", "INCONNU"}) {
            var result = service(List.of(animal(1, "Chat", -1, sex, "DISPONIBLE")))
                    .recommander(new PreferencesMatching(null, "JEUNE", "MALE")).getFirst();
            assertEquals(0, result.score());
            assertTrue(result.explication().contains("Age (JEUNE) : non exploitable"));
            assertTrue(result.explication().contains("Sexe : non renseigne ou non reconnu"));
        }
    }

    @Test
    void preferenceValidationRejectsUnknownOrOversizedValues() {
        assertThrows(IllegalArgumentException.class, () -> new PreferencesMatching("Lapin", null, null));
        assertThrows(IllegalArgumentException.class, () -> new PreferencesMatching(null, "BABY", null));
        assertThrows(IllegalArgumentException.class, () -> new PreferencesMatching(null, null, "INCONNU"));
        assertThrows(IllegalArgumentException.class, () -> new PreferencesMatching(" ".repeat(33), null, null));
        assertThrows(IllegalArgumentException.class, () -> new PreferencesMatching(null, "x".repeat(33), null));
        assertThrows(IllegalArgumentException.class, () -> new PreferencesMatching(null, null, "x".repeat(33)));
    }
}

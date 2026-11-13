package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.dao.AnimalDAO;
import com.enicarthage.refugeanimalier.dao.DemandeDAO;
import com.enicarthage.refugeanimalier.model.Demande;
import com.enicarthage.refugeanimalier.model.StatutDemande;
import com.enicarthage.refugeanimalier.service.DemandeService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AdoptionWorkflowTest extends MysqlTestSupport {
    private final DemandeService service = new DemandeService();
    private final AnimalDAO animals = new AnimalDAO();
    private final DemandeDAO requests = new DemandeDAO();

    @RepeatedTest(5)
    void competingApprovalsAcceptExactlyOneRequest() throws Exception {
        long animalId = animal("DISPONIBLE");
        long first = request(animalId);
        long second = request(animalId);

        List<String> results = concurrently(
                () -> approve(first), () -> approve(second));

        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("CONFLICT"::equals).count());
        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id = ? AND statut = 'ACCEPTEE'", animalId));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id = ? AND statut = 'EN_ATTENTE'", animalId));
        assertEquals("2", scalar("SELECT admin_id FROM demande WHERE animal_id = ? AND statut = 'ACCEPTEE'", animalId));
    }

    @Test
    void repeatedDecisionsCannotRewriteHistoryOrItsAdministrator() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);
        service.accepterDemande(requestId, 2);

        assertThrows(IllegalStateException.class, () -> service.accepterDemande(requestId, 1));
        assertThrows(IllegalStateException.class, () -> service.refuserDemande(requestId, 1));
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals("2", scalar("SELECT admin_id FROM demande WHERE id = ?", requestId));
    }

    @Test
    void rejectionPreservesAvailabilityAndAllowsAnotherRequest() throws Exception {
        long animalId = animal("DISPONIBLE");
        long rejected = request(animalId);
        long accepted = request(animalId);

        service.refuserDemande(rejected, 2);
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertEquals("2", scalar("SELECT admin_id FROM demande WHERE id = ?", rejected));
        assertThrows(IllegalStateException.class, () -> service.accepterDemande(rejected, 1));
        service.accepterDemande(accepted, 1);
        assertEquals("REFUSEE", scalar("SELECT statut FROM demande WHERE id = ?", rejected));
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id = ?", accepted));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN_SOIN", "ADOPTE"})
    void unavailableAnimalCannotBeApprovedOrReceiveNewRequests(String status) throws Exception {
        long animalId = animal(status);
        long requestId = request(animalId);

        assertThrows(IllegalStateException.class, () -> service.accepterDemande(requestId, 2));
        assertThrows(IllegalStateException.class, () -> service.ajouterDemande(newRequest(animalId)));
        assertEquals(status, scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", requestId));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id = ?", animalId));
        service.refuserDemande(requestId, 2);
        assertEquals(status, scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    @Test
    void failedAdministratorForeignKeyRollsBackAndReleasesLocks() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);

        assertThrows(SQLException.class, () -> service.accepterDemande(requestId, 999999));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", requestId));
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        service.accepterDemande(requestId, 2);
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
    }

    @Test
    void staleAnimalEditCannotReopenAnAdoption() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);
        var stale = animals.findById(animalId);
        stale.setNom("Stale edit");

        service.accepterDemande(requestId, 2);
        assertThrows(IllegalStateException.class, () -> animals.update(stale));
        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertEquals("Milo", scalar("SELECT nom FROM animal WHERE id = ?", animalId));

        stale.setStatut("ADOPTE");
        assertEquals(1, animals.update(stale));
        assertEquals("Stale edit", scalar("SELECT nom FROM animal WHERE id = ?", animalId));
        assertEquals(1, animals.update(stale), "An unchanged metadata save remains valid");
    }

    @RepeatedTest(3)
    void simultaneousMetadataSaveCannotUndoApproval() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);
        var edited = animals.findById(animalId);
        edited.setNom("Concurrent edit");

        List<String> results = concurrently(() -> approve(requestId), () -> {
            try {
                animals.update(edited);
                return "OK";
            } catch (IllegalStateException expected) {
                return "CONFLICT";
            }
        });

        assertEquals("OK", results.getFirst());
        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
    }

    @Test
    void acceptedRequestAndAnimalCannotBeDeleted() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);
        service.accepterDemande(requestId, 2);

        assertThrows(IllegalStateException.class, () -> service.supprimerDemande(requestId));
        assertThrows(IllegalStateException.class, () -> animals.delete(animalId));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE id = ?", requestId));
        assertEquals(1, count("SELECT COUNT(*) FROM animal WHERE id = ?", animalId));
    }

    @RepeatedTest(5)
    void approvalRacingDeletionNeverErasesAcceptedHistory() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);
        List<String> results = concurrently(() -> {
            try {
                return approve(requestId);
            } catch (IllegalArgumentException deletedFirst) {
                return "MISSING";
            }
        }, () -> {
            try {
                service.supprimerDemande(requestId);
                return "DELETED";
            } catch (IllegalStateException acceptedFirst) {
                return "PROTECTED";
            }
        });

        if (results.getFirst().equals("OK")) {
            assertEquals("PROTECTED", results.getLast());
            assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
            assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        } else {
            assertEquals(List.of("MISSING", "DELETED"), results);
            assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
            assertEquals(0, count("SELECT COUNT(*) FROM demande WHERE id = ?", requestId));
        }
    }

    @Test
    void pendingAndRejectedRequestsRemainDeletable() throws Exception {
        long animalId = animal("DISPONIBLE");
        long pending = request(animalId);
        long rejected = request(animalId);
        service.refuserDemande(rejected, 2);

        service.supprimerDemande(pending);
        service.supprimerDemande(rejected);
        assertEquals(0, count("SELECT COUNT(*) FROM demande WHERE animal_id = ?", animalId));
        assertEquals(1, animals.delete(animalId));
    }

    @Test
    void legacyAcceptedHistoryPreventsASecondAdoptionEvenWithAvailableStatus() throws Exception {
        long animalId = animal("DISPONIBLE");
        long first = request(animalId);
        long second = request(animalId);
        // Deliberately inconsistent fixtures exist only in this disposable database.
        try (var connection = connection(); var statement = connection.prepareStatement(
                "UPDATE demande SET statut = 'ACCEPTEE' WHERE id = ?")) {
            statement.setLong(1, first);
            statement.executeUpdate();
        }

        assertThrows(IllegalStateException.class, () -> service.accepterDemande(second, 2));
        assertThrows(IllegalStateException.class, () -> service.ajouterDemande(newRequest(animalId)));
        assertThrows(IllegalStateException.class, () -> animals.update(animals.findById(animalId)));
        assertThrows(IllegalStateException.class, () -> animals.delete(animalId));
        var repaired = animals.findById(animalId);
        repaired.setStatut("ADOPTE");
        assertEquals(1, animals.update(repaired));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id = ? AND statut = 'ACCEPTEE'", animalId));
    }

    @Test
    void genericDaoUpdateCannotFabricateAnApproval() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);
        var forged = requests.findById(requestId);
        forged.setStatut(StatutDemande.ACCEPTEE);

        assertThrows(IllegalArgumentException.class, () -> requests.update(forged));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    private String approve(long requestId) throws SQLException {
        try {
            service.accepterDemande(requestId, 2);
            return "OK";
        } catch (IllegalStateException expected) {
            return "CONFLICT";
        }
    }

    private Demande newRequest(long animalId) {
        return new Demande(null, "Adoptant", "adoptant@example.test", "0102030405",
                LocalDate.now(), StatutDemande.EN_ATTENTE, animalId, 1L);
    }

    private List<String> concurrently(Callable<String> first, Callable<String> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            var b = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }
}

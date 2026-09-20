package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.CreditFournisseur;
import model.Fournisseur;
import util.DatabaseSetup;

/**
 * Crédit fournisseur : atomicité des ajouts et des débits.
 *
 * Les deux opérations lisaient le solde, le recalculaient en Java puis
 * réécrivaient le total. Deux caisses créditant le même fournisseur en même
 * temps partaient de la même valeur et l'une des deux additions disparaissait.
 * Le dernier test reproduit cette situation.
 */
@DisplayName("Crédit fournisseur — atomicité")
class CreditFournisseurDAOIT {

    private static final Logger LOG = LoggerFactory.getLogger(CreditFournisseurDAOIT.class);

    /** Écritures concurrentes du test de perte de mise à jour. */
    private static final int AJOUTS_CONCURRENTS = 20;

    private static boolean dbAvailable;
    private static String suffix;
    private static int fournisseurId;

    private CreditFournisseurDAO dao;

    @BeforeAll
    static void setUp() {
        if (util.Config.isProductionSchema()) {
            LOG.warn("[IGNORÉ] Ces tests refusent d'écrire dans le schéma « public ».");
            dbAvailable = false;
            return;
        }
        dbAvailable = DBConnector.testConnection();
        if (!dbAvailable) {
            return;
        }
        assertTrue(DatabaseSetup.initializeDatabase());

        suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);

        Fournisseur f = new Fournisseur("Fournisseur crédit " + suffix,
                "00000000", "credit" + suffix + "@test.local", "Adresse de test");
        assertTrue(new FournisseurDAO().create(f));
        fournisseurId = f.getId();
    }

    @AfterAll
    static void tearDown() {
        if (!dbAvailable) {
            return;
        }
        String[] nettoyage = {
            "DELETE FROM credits_fournisseur WHERE fournisseur_id = " + fournisseurId,
            "DELETE FROM fournisseurs WHERE id = " + fournisseurId,
        };
        try (Connection conn = DBConnector.getConnection()) {
            for (String sql : nettoyage) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    LOG.warn("Nettoyage partiel : {}", e.getMessage());
                }
            }
        } catch (SQLException e) {
            LOG.warn("Nettoyage impossible : {}", e.getMessage());
        }
    }

    @BeforeEach
    void remettreLeSoldeAZero() {
        if (!dbAvailable) {
            return;
        }
        dao = new CreditFournisseurDAO();
        // Chaque test part d'un solde nul : ils partagent le même fournisseur.
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM credits_fournisseur WHERE fournisseur_id = " + fournisseurId);
        } catch (SQLException e) {
            LOG.warn("Remise à zéro impossible : {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("le premier ajout crée la ligne de crédit")
    void premierAjoutCreeLaLigne() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertTrue(dao.ajouterCredit(fournisseurId, new BigDecimal("40.500")));

        CreditFournisseur credit = dao.findByFournisseurId(fournisseurId);
        assertNotNull(credit, "la ligne doit avoir été créée");
        assertEquals(0, new BigDecimal("40.500").compareTo(credit.getMontant()));
    }

    @Test
    @DisplayName("les ajouts successifs s'accumulent")
    void ajoutsSuccessifsSAccumulent() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertTrue(dao.ajouterCredit(fournisseurId, new BigDecimal("10.000")));
        assertTrue(dao.ajouterCredit(fournisseurId, new BigDecimal("15.250")));

        assertEquals(0, new BigDecimal("25.250").compareTo(solde()));
    }

    @Test
    @DisplayName("un débit dans la limite du solde le diminue d'autant")
    void debitDansLaLimiteDuSolde() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        dao.ajouterCredit(fournisseurId, new BigDecimal("30.000"));

        assertTrue(dao.utiliserCredit(fournisseurId, new BigDecimal("12.750")));
        assertEquals(0, new BigDecimal("17.250").compareTo(solde()));
    }

    @Test
    @DisplayName("un débit supérieur au solde échoue et laisse le solde intact")
    void debitSuperieurAuSoldeRefuse() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        dao.ajouterCredit(fournisseurId, new BigDecimal("20.000"));

        assertFalse(dao.utiliserCredit(fournisseurId, new BigDecimal("20.001")),
                "le débit doit être refusé");
        assertEquals(0, new BigDecimal("20.000").compareTo(solde()),
                "le solde ne doit pas avoir bougé");
    }

    @Test
    @DisplayName("un débit égal au solde est accepté et le ramène à zéro")
    void debitEgalAuSoldeAccepte() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        dao.ajouterCredit(fournisseurId, new BigDecimal("20.000"));

        assertTrue(dao.utiliserCredit(fournisseurId, new BigDecimal("20.000")));
        assertEquals(0, BigDecimal.ZERO.compareTo(solde()));
    }

    @Test
    @DisplayName("un débit sur un fournisseur sans ligne de crédit échoue")
    void debitSansLigneDeCreditRefuse() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertFalse(dao.utiliserCredit(fournisseurId, new BigDecimal("5.000")));
    }

    @Test
    @DisplayName("des ajouts simultanés ne se perdent pas")
    void ajoutsConcurrentsNePerdentAucuneEcriture() throws Exception {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        BigDecimal montantUnitaire = new BigDecimal("5.000");

        // Départ simultané : sans ce verrou, les fils s'exécuteraient l'un
        // après l'autre et la perte de mise à jour ne se produirait pas, même
        // avec l'ancien code.
        CountDownLatch depart = new CountDownLatch(1);
        CountDownLatch arrivee = new CountDownLatch(AJOUTS_CONCURRENTS);
        List<Thread> fils = new ArrayList<>();

        for (int i = 0; i < AJOUTS_CONCURRENTS; i++) {
            Thread fil = new Thread(() -> {
                try {
                    depart.await();
                    new CreditFournisseurDAO().ajouterCredit(fournisseurId, montantUnitaire);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    arrivee.countDown();
                }
            });
            fils.add(fil);
            fil.start();
        }

        depart.countDown();
        assertTrue(arrivee.await(30, TimeUnit.SECONDS), "les ajouts doivent tous se terminer");
        for (Thread fil : fils) {
            fil.join();
        }

        BigDecimal attendu = montantUnitaire.multiply(BigDecimal.valueOf(AJOUTS_CONCURRENTS));
        assertEquals(0, attendu.compareTo(solde()),
                "le solde doit être la somme exacte des " + AJOUTS_CONCURRENTS + " ajouts");
    }

    private BigDecimal solde() {
        CreditFournisseur credit = dao.findByFournisseurId(fournisseurId);
        return credit != null ? credit.getMontant() : BigDecimal.ZERO;
    }
}

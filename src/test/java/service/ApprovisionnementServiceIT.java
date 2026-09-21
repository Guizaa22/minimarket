package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.CreditFournisseurDAO;
import dao.DBConnector;
import dao.FournisseurDAO;
import dao.ProduitDAO;
import dao.UtilisateurDAO;
import exception.ApplicationException;
import model.Fournisseur;
import model.Produit;
import model.Utilisateur;
import util.DatabaseSetup;
import util.SecurityUtil;

/**
 * Transaction de réapprovisionnement : tout ou rien.
 *
 * L'écran d'ajout de stock enchaînait quatre écritures sur quatre connexions
 * distinctes — stock, historique, paiement, crédit. Une panne au milieu
 * laissait par exemple le stock augmenté et le crédit fournisseur intact.
 * Ces tests vérifient que les quatre écritures sont validées ou annulées
 * ensemble, sur une base réelle : une transaction ne se teste pas avec des
 * doublures.
 */
@DisplayName("Réapprovisionnement — transaction tout ou rien")
class ApprovisionnementServiceIT {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovisionnementServiceIT.class);

    private static boolean dbAvailable;
    private static String suffix;
    private static int employeId;
    private static int fournisseurId;

    private static final java.util.concurrent.atomic.AtomicInteger COMPTEUR =
            new java.util.concurrent.atomic.AtomicInteger();

    private int produitId;
    private ApprovisionnementService service;

    @BeforeAll
    static void setUpAll() {
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

        Utilisateur employe = new Utilisateur("appro_employe_" + suffix,
                SecurityUtil.hashPassword("motdepasse-test"), Utilisateur.Role.Employé);
        assertTrue(new UtilisateurDAO().create(employe));
        employeId = employe.getId();

        Fournisseur f = new Fournisseur("Fournisseur appro " + suffix,
                "00000000", "appro" + suffix + "@test.local", "Adresse");
        assertTrue(new FournisseurDAO().create(f));
        fournisseurId = f.getId();
    }

    @BeforeEach
    void setUp() {
        if (!dbAvailable) {
            return;
        }
        service = new ApprovisionnementService();

        // Un produit neuf par test : chaque méthode modifie le stock.
        String cle = suffix + "_" + COMPTEUR.incrementAndGet();
        Produit p = new Produit("APPRO" + cle, "Produit appro " + cle, "Divers",
                new BigDecimal("2.000"), new BigDecimal("5.000"), 10, "unité", 2);
        assertTrue(new ProduitDAO().create(p));
        produitId = p.getId();

        repartirDUnFournisseurVierge();
    }

    @AfterAll
    static void tearDown() {
        if (!dbAvailable) {
            return;
        }
        String[] nettoyage = {
            "DELETE FROM stock_movements WHERE product_id IN "
                + "(SELECT id FROM produits WHERE code_barre LIKE 'APPRO%" + suffix + "%')",
            "DELETE FROM ajouts_stock WHERE employe_id = " + employeId,
            "DELETE FROM paiements_fournisseur WHERE employe_id = " + employeId,
            "DELETE FROM produits WHERE code_barre LIKE 'APPRO%" + suffix + "%'",
            "DELETE FROM credits_fournisseur WHERE fournisseur_id = " + fournisseurId,
            "DELETE FROM fournisseurs WHERE id = " + fournisseurId,
            "DELETE FROM audit_logs WHERE user_id = " + employeId,
            "DELETE FROM utilisateurs WHERE id = " + employeId,
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

    // ------------------------------------------------------------------

    /**
     * Efface le crédit et les paiements du fournisseur partagé.
     *
     * Le fournisseur est commun à toute la classe : sans cette remise à zéro,
     * les paiements d'un test seraient comptés par le suivant et le résultat
     * dépendrait de l'ordre d'exécution, que JUnit ne garantit pas.
     */
    private static void repartirDUnFournisseurVierge() {
        String[] nettoyage = {
            "DELETE FROM credits_fournisseur WHERE fournisseur_id = " + fournisseurId,
            "DELETE FROM paiements_fournisseur WHERE fournisseur_id = " + fournisseurId,
        };
        try (Connection conn = DBConnector.getConnection()) {
            for (String sql : nettoyage) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }
        } catch (SQLException e) {
            LOG.warn("Remise à zéro du fournisseur impossible : {}", e.getMessage());
        }
    }

    private int stock() {
        Produit p = new ProduitDAO().findById(produitId);
        return p != null ? p.getQuantiteStock() : -1;
    }

    private BigDecimal credit() {
        var c = new CreditFournisseurDAO().findByFournisseurId(fournisseurId);
        return c != null ? c.getMontant() : BigDecimal.ZERO;
    }

    private int compter(String table, String colonne, int valeur) {
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + colonne + " = ?")) {
            stmt.setInt(1, valeur);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LOG.warn("Comptage impossible : {}", e.getMessage());
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Chemin nominal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un ajout simple incrémente le stock et laisse une trace")
    void ajoutSimple() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        int apres = service.enregistrerAjout(produitId, employeId, null, 5,
                BigDecimal.ZERO, BigDecimal.ZERO, "Ajout rapide", null, null);

        assertEquals(15, apres, "10 + 5");
        assertEquals(15, stock());
        assertEquals(1, compter("ajouts_stock", "produit_id", produitId),
                "l'historique doit porter l'ajout");
        assertEquals(1, compter("stock_movements", "product_id", produitId),
                "le mouvement de stock doit être journalisé");
    }

    @Test
    @DisplayName("les quatre écritures aboutissent ensemble")
    void ajoutCompletAvecPaiementEtCredit() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertTrue(new CreditFournisseurDAO().ajouterCredit(fournisseurId, new BigDecimal("100.000")));

        service.enregistrerAjout(produitId, employeId, fournisseurId, 8,
                new BigDecimal("30.000"), new BigDecimal("25.000"), "Réappro", null, null);

        assertEquals(18, stock(), "10 + 8");
        assertEquals(1, compter("ajouts_stock", "produit_id", produitId));
        assertEquals(1, compter("paiements_fournisseur", "fournisseur_id", fournisseurId));
        assertEquals(0, new BigDecimal("75.000").compareTo(credit()), "100 - 25");
    }

    @Test
    @DisplayName("un ajout de tabac conserve son type et sa quantité de cigarettes")
    void ajoutTabac() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        service.enregistrerAjout(produitId, employeId, null, 3,
                BigDecimal.ZERO, BigDecimal.ZERO, "Cartouche", "cigarette", 60);

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT type_ajout_tabac, quantite_cigarettes FROM ajouts_stock "
                     + "WHERE produit_id = ?")) {
            stmt.setInt(1, produitId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("cigarette", rs.getString("type_ajout_tabac"));
                assertEquals(60, rs.getInt("quantite_cigarettes"));
            }
        } catch (SQLException e) {
            throw new AssertionError("lecture impossible", e);
        }
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un crédit insuffisant annule tout : ni stock, ni historique, ni paiement")
    void creditInsuffisantAnnuleToutLAjout() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertTrue(new CreditFournisseurDAO().ajouterCredit(fournisseurId, new BigDecimal("10.000")));
        int stockAvant = stock();

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.enregistrerAjout(produitId, employeId, fournisseurId, 7,
                        new BigDecimal("20.000"), new BigDecimal("50.000"), "Trop de crédit", null, null));

        assertTrue(e.getMessage().toLowerCase().contains("crédit"),
                "le motif doit être explicite : " + e.getMessage());

        // C'est le cœur du correctif : avant, le stock était déjà incrémenté et
        // le paiement déjà écrit quand le crédit échouait.
        assertEquals(stockAvant, stock(), "le stock ne doit pas avoir bougé");
        assertEquals(0, compter("ajouts_stock", "produit_id", produitId),
                "aucun historique ne doit subsister");
        assertEquals(0, compter("paiements_fournisseur", "fournisseur_id", fournisseurId),
                "aucun paiement ne doit subsister");
        assertEquals(0, new BigDecimal("10.000").compareTo(credit()),
                "le crédit doit être intact");
    }

    @Test
    @DisplayName("un produit inexistant annule tout")
    void produitInexistantAnnuleTout() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertThrows(ApplicationException.class,
                () -> service.enregistrerAjout(-999, employeId, fournisseurId, 5,
                        new BigDecimal("10.000"), BigDecimal.ZERO, "Produit fantôme", null, null));

        assertEquals(0, compter("paiements_fournisseur", "fournisseur_id", fournisseurId),
                "le paiement ne doit pas avoir été écrit");
    }

    // ------------------------------------------------------------------
    // Validation d'entrée
    // ------------------------------------------------------------------

    @Test
    @DisplayName("une quantité nulle ou négative est refusée")
    void quantiteInvalideRefusee() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertThrows(ApplicationException.class,
                () -> service.enregistrerAjout(produitId, employeId, null, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO, "Rien", null, null));
        assertEquals(10, stock(), "le stock ne doit pas bouger");
    }

    @Test
    @DisplayName("un montant négatif est refusé")
    void montantNegatifRefuse() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertThrows(ApplicationException.class,
                () -> service.enregistrerAjout(produitId, employeId, fournisseurId, 3,
                        new BigDecimal("-1.000"), BigDecimal.ZERO, "Négatif", null, null));
        assertEquals(10, stock());
    }

    @Test
    @DisplayName("un paiement sans fournisseur est refusé")
    void paiementSansFournisseurRefuse() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.enregistrerAjout(produitId, employeId, null, 3,
                        new BigDecimal("10.000"), BigDecimal.ZERO, "Sans fournisseur", null, null));

        assertTrue(e.getMessage().toLowerCase().contains("fournisseur"));
        assertEquals(10, stock());
    }

    @Test
    @DisplayName("un ajout sans employé identifié est refusé")
    void employeObligatoire() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertThrows(ApplicationException.class,
                () -> service.enregistrerAjout(produitId, -1, null, 3,
                        BigDecimal.ZERO, BigDecimal.ZERO, "Sans employé", null, null));
        assertEquals(10, stock());
    }

    @Test
    @DisplayName("l'ajout est tracé dans le journal d'audit")
    void ajoutTraceDansLAudit() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        service.enregistrerAjout(produitId, employeId, null, 4,
                BigDecimal.ZERO, BigDecimal.ZERO, "Réappro tracée", null, null);

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND action = 'AJOUT_STOCK'")) {
            stmt.setInt(1, employeId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0, "l'ajout doit laisser une trace d'audit");
            }
        } catch (SQLException e) {
            throw new AssertionError("lecture impossible", e);
        }
        assertNotNull(new ProduitDAO().findById(produitId));
    }
}

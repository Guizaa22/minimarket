package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.AjoutStock;
import model.Categorie;
import model.Produit;
import model.TypeCategorie;
import model.Utilisateur;
import util.DatabaseSetup;
import util.SecurityUtil;

/**
 * Ajout de stock de tabac écrit contre le schéma canonique.
 *
 * Les colonnes type_ajout_tabac et quantite_cigarettes étaient absentes de
 * schema_postgres.sql : le DAO les créait lui-même, par un ALTER TABLE joué à
 * la première insertion. Ce test vérifie qu'elles existent et se remplissent
 * sur une base construite uniquement à partir du schéma, sans aucune migration
 * à chaud.
 */
@DisplayName("Ajout de stock — colonnes tabac du schéma")
class AjoutStockDAOIT {

    private static final Logger LOG = LoggerFactory.getLogger(AjoutStockDAOIT.class);

    private static boolean dbAvailable;
    private static String suffix;
    private static int employeId;
    private static int produitId;

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

        Utilisateur employe = new Utilisateur("ajt_employe_" + suffix,
                SecurityUtil.hashPassword("motdepasse-test"), Utilisateur.Role.Employé);
        assertTrue(new UtilisateurDAO().create(employe));
        employeId = employe.getId();

        Categorie cat = new Categorie("CatAjout_" + suffix, TypeCategorie.Tabac);
        assertTrue(new CategorieDAO().create(cat));

        Produit p = new Produit("AJTTEST" + suffix, "Marlboro ajout " + suffix, cat.getNom(),
                new BigDecimal("5.000"), new BigDecimal("8.500"), 10, "unité", 2);
        assertTrue(new ProduitDAO().create(p));
        produitId = p.getId();
    }

    @AfterAll
    static void tearDown() {
        if (!dbAvailable) {
            return;
        }
        String[] nettoyage = {
            "DELETE FROM ajouts_stock WHERE employe_id = " + employeId,
            "DELETE FROM produits WHERE id = " + produitId,
            "DELETE FROM categories WHERE nom = 'CatAjout_" + suffix + "'",
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

    @Test
    @DisplayName("les colonnes tabac appartiennent au schéma, sans ALTER à l'exécution")
    void colonnesTabacPresentesDansLeSchema() throws SQLException {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT type_ajout_tabac, quantite_cigarettes FROM ajouts_stock LIMIT 0")) {
            assertNotNull(rs.getMetaData());
            assertEquals(2, rs.getMetaData().getColumnCount());
        }
    }

    @Test
    @DisplayName("un ajout en cigarettes conserve son type et sa quantité")
    void ajoutEnCigarettesPersiste() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        AjoutStockDAO dao = new AjoutStockDAO();
        AjoutStock ajout = new AjoutStock(produitId, employeId, null, 3,
                new BigDecimal("12.500"), BigDecimal.ZERO, "Réappro cigarettes",
                LocalDateTime.now(), "cigarette", 60);

        assertTrue(dao.create(ajout), "l'ajout doit être enregistré");
        assertTrue(ajout.getId() > 0, "l'identifiant généré doit être renseigné");

        AjoutStock relu = retrouver(dao, ajout.getId());
        assertNotNull(relu, "l'ajout doit être relisible");
        assertEquals("cigarette", relu.getTypeAjoutTabac());
        assertEquals(60, relu.getQuantiteCigarettes());
        assertEquals(3, relu.getQuantite());
        assertEquals(0, new BigDecimal("12.500").compareTo(relu.getMontantPaiement()));
    }

    @Test
    @DisplayName("un ajout en paquets conserve son type")
    void ajoutEnPaquetsPersiste() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        AjoutStockDAO dao = new AjoutStockDAO();
        AjoutStock ajout = new AjoutStock(produitId, employeId, null, 5,
                BigDecimal.ZERO, BigDecimal.ZERO, "Réappro paquets",
                LocalDateTime.now(), "paquet", null);

        assertTrue(dao.create(ajout));

        AjoutStock relu = retrouver(dao, ajout.getId());
        assertNotNull(relu);
        assertEquals("paquet", relu.getTypeAjoutTabac());
        assertNull(relu.getQuantiteCigarettes());
    }

    @Test
    @DisplayName("un ajout ordinaire laisse les colonnes tabac vides")
    void ajoutOrdinaireSansColonnesTabac() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        AjoutStockDAO dao = new AjoutStockDAO();
        AjoutStock ajout = new AjoutStock(produitId, employeId, null, 2,
                BigDecimal.ZERO, BigDecimal.ZERO, "Réappro standard", LocalDateTime.now());

        assertTrue(dao.create(ajout));

        AjoutStock relu = retrouver(dao, ajout.getId());
        assertNotNull(relu);
        assertNull(relu.getTypeAjoutTabac());
        assertNull(relu.getQuantiteCigarettes());
    }

    /** Relit un ajout par son identifiant parmi ceux de l'employé de test. */
    private static AjoutStock retrouver(AjoutStockDAO dao, int id) {
        List<AjoutStock> ajouts = dao.findByEmployeAndDate(employeId, LocalDateTime.now());
        return ajouts.stream().filter(a -> a.getId() == id).findFirst().orElse(null);
    }
}

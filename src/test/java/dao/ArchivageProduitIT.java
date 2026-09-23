package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.Produit;
import model.Utilisateur;
import util.DatabaseSetup;
import util.SecurityUtil;

/**
 * Archivage d'un produit : l'historique comptable doit survivre.
 *
 * La « suppression forcée » effaçait les lignes de detailsvente du produit.
 * Le chiffre d'affaires, calculé depuis ventes.total_vente, restait inchangé,
 * mais le bénéfice — calculé depuis detailsvente — tombait à zéro : le
 * résultat d'un mois déjà clôturé changeait après coup, et les ventes
 * concernées se retrouvaient sans aucune ligne.
 */
@DisplayName("Archivage d'un produit — préservation de l'historique")
class ArchivageProduitIT {

    private static final Logger LOG = LoggerFactory.getLogger(ArchivageProduitIT.class);

    /**
     * Jour de référence, propre à chaque test.
     *
     * Les totaux interrogés portent sur une journée entière : partager la même
     * date ferait s'additionner les ventes des différents tests, et les
     * montants attendus dépendraient de l'ordre d'exécution.
     */
    private static final LocalDate PREMIER_JOUR = LocalDate.of(2002, 6, 9);

    private static boolean dbAvailable;
    private static String suffix;
    private static int employeId;

    /**
     * Produits recréés avant chaque test.
     *
     * Chaque méthode archive, réactive ou supprime : les partager rendrait le
     * résultat dépendant de l'ordre d'exécution, que JUnit ne garantit pas.
     */
    private int produitVenduId;
    private int produitNeufId;
    private LocalDate jour;

    /**
     * Compteur statique : JUnit instancie la classe de test à chaque méthode,
     * un compteur d'instance repartirait de zéro et les code-barres, uniques en
     * base, entreraient en collision dès le deuxième test.
     */
    private static final java.util.concurrent.atomic.AtomicInteger COMPTEUR =
            new java.util.concurrent.atomic.AtomicInteger();

    @BeforeAll
    static void setUp() throws SQLException {
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

        Utilisateur employe = new Utilisateur("arch_employe_" + suffix,
                SecurityUtil.hashPassword("motdepasse-test"), Utilisateur.Role.Employé);
        assertTrue(new UtilisateurDAO().create(employe));
        employeId = employe.getId();
    }

    @BeforeEach
    void creerLesProduits() throws SQLException {
        if (!dbAvailable) {
            return;
        }
        int rang = COMPTEUR.incrementAndGet();
        String cle = suffix + "_" + rang;
        jour = PREMIER_JOUR.plusDays(rang);
        ProduitDAO dao = new ProduitDAO();

        Produit vendu = new Produit("ARCHV" + cle, "Produit vendu " + cle, "Divers",
                new BigDecimal("2.000"), new BigDecimal("5.000"), 50, "unité", 2);
        assertTrue(dao.create(vendu));
        produitVenduId = vendu.getId();

        Produit neuf = new Produit("ARCHN" + cle, "Produit jamais vendu " + cle, "Divers",
                new BigDecimal("1.000"), new BigDecimal("2.000"), 10, "unité", 2);
        assertTrue(dao.create(neuf));
        produitNeufId = neuf.getId();

        insererVente();
    }

    @AfterAll
    static void tearDown() {
        if (!dbAvailable) {
            return;
        }
        String[] nettoyage = {
            "DELETE FROM detailsvente WHERE id_produit IN "
                + "(SELECT id FROM produits WHERE code_barre LIKE 'ARCH%" + suffix + "%')",
            "DELETE FROM ventes WHERE id_utilisateur = " + employeId,
            "DELETE FROM produits WHERE code_barre LIKE 'ARCH%" + suffix + "%'",
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

    private void insererVente() throws SQLException {
        try (Connection conn = DBConnector.getConnection()) {
            int venteId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO ventes (date_vente, total_vente, id_utilisateur) "
                    + "VALUES (?, ?, ?) RETURNING id")) {
                stmt.setTimestamp(1, Timestamp.valueOf(jour.atTime(11, 0)));
                stmt.setBigDecimal(2, new BigDecimal("20.000"));
                stmt.setInt(3, employeId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    venteId = rs.getInt(1);
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO detailsvente (id_vente, id_produit, quantite, "
                    + "prix_vente_unitaire, prix_achat_unitaire) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setInt(1, venteId);
                stmt.setInt(2, produitVenduId);
                stmt.setInt(3, 4);
                stmt.setBigDecimal(4, new BigDecimal("5.000"));
                stmt.setBigDecimal(5, new BigDecimal("2.000"));
                stmt.executeUpdate();
            }
        }
    }

    private LocalDateTime debut() {
        return jour.atStartOfDay();
    }

    private LocalDateTime fin() {
        return jour.plusDays(1).atStartOfDay();
    }

    @Test
    @DisplayName("un produit vendu ne peut plus être supprimé, même de force")
    void produitVenduNonSupprimable() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        ProduitDAO dao = new ProduitDAO();

        SQLException e = assertThrows(SQLException.class,
                () -> dao.delete(produitVenduId, true),
                "la suppression forcée ne doit plus effacer l'historique");
        assertTrue(e.getMessage().toLowerCase().contains("archiv"),
                "le message doit orienter vers l'archivage : " + e.getMessage());

        assertNotNull(dao.findById(produitVenduId), "le produit doit toujours exister");
    }

    @Test
    @DisplayName("le bénéfice d'une période close ne bouge pas après archivage")
    void beneficeInchangeApresArchivage() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        VenteDAO venteDAO = new VenteDAO();
        BigDecimal caAvant = venteDAO.getTotalRecettes(debut(), fin());
        BigDecimal beneficeAvant = venteDAO.getTotalProfit(debut(), fin());

        // (5,000 - 2,000) × 4 = 12,000
        assertEquals(0, new BigDecimal("12.000").compareTo(beneficeAvant));

        assertTrue(new ProduitDAO().definirActif(produitVenduId, false));

        assertEquals(0, caAvant.compareTo(venteDAO.getTotalRecettes(debut(), fin())),
                "le chiffre d'affaires ne doit pas bouger");
        assertEquals(0, beneficeAvant.compareTo(venteDAO.getTotalProfit(debut(), fin())),
                "le bénéfice d'un mois clos ne doit pas bouger : c'était le défaut");
    }

    @Test
    @DisplayName("un produit archivé disparaît des listes de vente")
    void produitArchiveAbsentDesListes() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        ProduitDAO dao = new ProduitDAO();
        assertTrue(dao.definirActif(produitVenduId, false));

        assertFalse(dao.findAll().stream().anyMatch(p -> p.getId() == produitVenduId),
                "un produit archivé ne doit plus être proposé à la vente");
        assertTrue(dao.findAll(true).stream().anyMatch(p -> p.getId() == produitVenduId),
                "il reste consultable avec les archives");

        // Toujours accessible par identifiant : les rapports et les tickets
        // doivent pouvoir nommer un produit archivé.
        Produit relu = dao.findById(produitVenduId);
        assertNotNull(relu);
        assertFalse(relu.isActif());
    }

    @Test
    @DisplayName("un produit réactivé revient dans les listes")
    void reactivationRemetEnVente() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        ProduitDAO dao = new ProduitDAO();
        assertTrue(dao.definirActif(produitVenduId, false));
        assertTrue(dao.definirActif(produitVenduId, true));

        assertTrue(dao.findAll().stream().anyMatch(p -> p.getId() == produitVenduId));
        assertTrue(dao.findById(produitVenduId).isActif());
    }

    @Test
    @DisplayName("un produit jamais vendu reste supprimable")
    void produitNeufResteSupprimable() throws SQLException {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        ProduitDAO dao = new ProduitDAO();
        assertTrue(dao.delete(produitNeufId, false),
                "rien ne référence ce produit : la suppression reste permise");
        org.junit.jupiter.api.Assertions.assertNull(dao.findById(produitNeufId));
    }
}

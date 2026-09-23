package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.Categorie;
import model.DetailVente;
import model.Produit;
import model.TypeCategorie;
import model.Utilisateur;
import model.Vente;
import util.DatabaseSetup;
import util.SecurityUtil;

/**
 * Journal des mouvements de stock à la vente.
 *
 * stock_movements se présente comme « le journal unique de toutes les
 * variations de stock », et la lacune qu'il devait combler était justement que
 * les ventes n'y figuraient pas. Or seuls les réapprovisionnements et les
 * corrections d'inventaire y étaient écrits : DESKTOP_SALE était déclaré dans
 * la contrainte du schéma mais jamais produit, et l'historique d'un stock
 * restait impossible à reconstituer.
 */
@DisplayName("Mouvements de stock — ventes journalisées")
class MouvementsVenteIT {

    private static final Logger LOG = LoggerFactory.getLogger(MouvementsVenteIT.class);

    private static final java.util.concurrent.atomic.AtomicInteger COMPTEUR =
            new java.util.concurrent.atomic.AtomicInteger();

    private static boolean dbAvailable;
    private static String suffix;
    private static int employeId;

    private int produitId;
    private int paquetId;
    private int frakId;

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

        Utilisateur employe = new Utilisateur("mvt_employe_" + suffix,
                SecurityUtil.hashPassword("motdepasse-test"), Utilisateur.Role.Employé);
        assertTrue(new UtilisateurDAO().create(employe));
        employeId = employe.getId();

        Categorie tabac = new Categorie("CatMvtTabac_" + suffix, TypeCategorie.Tabac);
        assertTrue(new CategorieDAO().create(tabac));
        Categorie frak = new Categorie("CatMvtFrak_" + suffix, TypeCategorie.FrakCigarette);
        assertTrue(new CategorieDAO().create(frak));
    }

    @BeforeEach
    void setUp() {
        if (!dbAvailable) {
            return;
        }
        String cle = suffix + "_" + COMPTEUR.incrementAndGet();
        ProduitDAO dao = new ProduitDAO();

        Produit standard = new Produit("MVTS" + cle, "Café " + cle, "Divers",
                new BigDecimal("2.000"), new BigDecimal("5.000"), 100, "unité", 5);
        assertTrue(dao.create(standard));
        produitId = standard.getId();

        Produit paquet = new Produit("MVTP" + cle, "Marlboro " + cle, "CatMvtTabac_" + suffix,
                new BigDecimal("5.000"), new BigDecimal("8.500"), 50, "paquet", 5);
        paquet.setPrixVenteCigarette(new BigDecimal("0.600"));
        assertTrue(dao.create(paquet));
        paquetId = paquet.getId();

        Produit frak = new Produit("MVTF" + cle, "Marlboro unité " + cle, "CatMvtFrak_" + suffix,
                new BigDecimal("0.300"), new BigDecimal("0.600"), 0, "cigarette", 0);
        assertTrue(dao.create(frak));
        frakId = frak.getId();
    }

    @AfterAll
    static void tearDown() {
        if (!dbAvailable) {
            return;
        }
        String produitsDuTest = "(SELECT id FROM produits WHERE code_barre LIKE 'MVT%" + suffix + "%')";
        String[] nettoyage = {
            "DELETE FROM stock_movements WHERE product_id IN " + produitsDuTest,
            "DELETE FROM detailsvente WHERE id_produit IN " + produitsDuTest,
            "DELETE FROM ventes WHERE id_utilisateur = " + employeId,
            "DELETE FROM produits WHERE code_barre LIKE 'MVT%" + suffix + "%'",
            "DELETE FROM categories WHERE nom LIKE 'CatMvt%" + suffix + "'",
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

    /** Mouvements d'un produit : {quantity_change, stock_apres, type}. */
    private List<int[]> mouvements(int produit) {
        List<int[]> lignes = new java.util.ArrayList<>();
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT quantity_change, stock_apres FROM stock_movements "
                     + "WHERE product_id = ? AND type = 'DESKTOP_SALE' ORDER BY id")) {
            stmt.setInt(1, produit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lignes.add(new int[]{rs.getInt(1), rs.getInt(2)});
                }
            }
        } catch (SQLException e) {
            throw new AssertionError("lecture des mouvements impossible", e);
        }
        return lignes;
    }

    private Vente venteDe(DetailVente... details) {
        Vente vente = new Vente();
        vente.setDateVente(LocalDateTime.now());
        vente.setUtilisateurId(employeId);
        vente.setTypePaiement(Vente.PAIEMENT_ESPECES);
        BigDecimal total = BigDecimal.ZERO;
        for (DetailVente d : details) {
            vente.addDetail(d);
            total = total.add(d.getPrixVenteUnitaire().multiply(BigDecimal.valueOf(d.getQuantite())));
        }
        vente.setTotalVente(total);
        return vente;
    }

    private DetailVente ligne(int produit, int quantite, String unite, String prix) {
        DetailVente d = new DetailVente();
        d.setProduitId(produit);
        d.setQuantite(quantite);
        d.setPrixVenteUnitaire(new BigDecimal(prix));
        d.setPrixAchatUnitaire(new BigDecimal("2.000"));
        d.setTypeVenteTabac(unite);
        return d;
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("une vente ordinaire laisse un mouvement négatif")
    void venteOrdinaireJournalisee() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertTrue(new VenteDAO().create(venteDe(ligne(produitId, 3, "unite", "5.000"))));

        List<int[]> lignes = mouvements(produitId);
        assertEquals(1, lignes.size(), "une ligne de vente, un mouvement");
        assertEquals(-3, lignes.get(0)[0], "sortie de 3 unités, donc -3");
        assertEquals(97, lignes.get(0)[1], "stock après : 100 - 3");
    }

    @Test
    @DisplayName("le mouvement suit le stock réel, pas la quantité facturée")
    void venteALaCigaretteJournaliseeEnPaquets() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        // 7 cigarettes entament 1 paquet : le stock, tenu en paquets, perd 1.
        assertTrue(new VenteDAO().create(venteDe(ligne(paquetId, 7, "cigarette", "0.600"))));

        List<int[]> lignes = mouvements(paquetId);
        assertEquals(1, lignes.size());
        assertEquals(-1, lignes.get(0)[0], "7 cigarettes retirent 1 paquet, pas 7");
        assertEquals(49, lignes.get(0)[1], "stock après : 50 - 1");
    }

    @Test
    @DisplayName("une « frak cigarette » journalise le paquet associé, pas elle-même")
    void frakCigaretteJournaliseLePaquetAssocie() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        DetailVente d = ligne(frakId, 25, "cigarette", "0.600");
        d.setProduitTabacAssocieId(paquetId);
        assertTrue(new VenteDAO().create(venteDe(d)));

        assertTrue(mouvements(frakId).isEmpty(),
                "le produit à l'unité n'a pas de stock propre");

        List<int[]> lignes = mouvements(paquetId);
        assertEquals(1, lignes.size());
        assertEquals(-2, lignes.get(0)[0], "25 cigarettes entament 2 paquets");
        assertEquals(48, lignes.get(0)[1], "stock après : 50 - 2");
    }

    @Test
    @DisplayName("une vente à plusieurs lignes journalise chaque produit")
    void venteMultiLignes() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertTrue(new VenteDAO().create(venteDe(
                ligne(produitId, 2, "unite", "5.000"),
                ligne(paquetId, 3, "paquet", "8.500"))));

        assertEquals(1, mouvements(produitId).size());
        assertEquals(-2, mouvements(produitId).get(0)[0]);
        assertEquals(1, mouvements(paquetId).size());
        assertEquals(-3, mouvements(paquetId).get(0)[0]);
    }

    @Test
    @DisplayName("une vente annulée pour stock insuffisant ne laisse aucun mouvement")
    void venteAnnuleeNeJournalisePas() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        org.junit.jupiter.api.Assertions.assertThrows(
                exception.StockInsuffisantException.class,
                () -> new VenteDAO().create(venteDe(ligne(produitId, 5_000, "unite", "5.000"))));

        assertTrue(mouvements(produitId).isEmpty(),
                "le journal fait partie de la transaction : rien ne doit subsister");
    }

    @Test
    @DisplayName("le mouvement porte l'employé et la référence de la vente")
    void mouvementPorteLAuteurEtLaReference() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        Vente vente = venteDe(ligne(produitId, 1, "unite", "5.000"));
        assertTrue(new VenteDAO().create(vente));

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT user_id, reference FROM stock_movements "
                     + "WHERE product_id = ? AND type = 'DESKTOP_SALE'")) {
            stmt.setInt(1, produitId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(employeId, rs.getInt("user_id"));
                assertEquals("Vente #" + vente.getId(), rs.getString("reference"));
            }
        } catch (SQLException e) {
            throw new AssertionError("lecture impossible", e);
        }
    }
}

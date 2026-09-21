package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import model.Categorie;
import model.DetailVente;
import model.Produit;
import model.TypeCategorie;
import model.Utilisateur;
import model.Vente;
import util.DatabaseSetup;
import util.SecurityUtil;

/**
 * Vente du tabac : décrément du stock et unité enregistrée.
 *
 * Le stock des produits de tabac est tenu en paquets. Vendre à la cigarette
 * ne doit donc pas retirer autant d'unités que de cigarettes vendues.
 */
@DisplayName("Vente de tabac — stock et unité")
class VenteTabacIT {

    private static boolean dbAvailable;
    private static String suffix;
    private static int employeId;
    private static int produitId;

    @BeforeAll
    static void setUp() {
        if (util.Config.isProductionSchema()) {
            System.out.println("[IGNORÉ] Ces tests refusent d'écrire dans le schéma « public ».");
            dbAvailable = false;
            return;
        }
        dbAvailable = DBConnector.testConnection();
        if (!dbAvailable) {
            return;
        }
        assertTrue(DatabaseSetup.initializeDatabase());

        suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);

        Utilisateur employe = new Utilisateur("tab_employe_" + suffix,
                SecurityUtil.hashPassword("motdepasse-test"), Utilisateur.Role.Employé);
        assertTrue(new UtilisateurDAO().create(employe));
        employeId = employe.getId();

        Categorie cat = new Categorie("CatTabac_" + suffix, TypeCategorie.Tabac);
        assertTrue(new CategorieDAO().create(cat));

        Produit p = new Produit("TABTEST" + suffix, "Marlboro test " + suffix, cat.getNom(),
                new BigDecimal("5.000"), new BigDecimal("8.500"), 10, "unité", 2);
        p.setPrixVenteCigarette(new BigDecimal("0.600"));
        assertTrue(new ProduitDAO().create(p));
        produitId = p.getId();
    }

    /**
     * Remet le produit partagé dans un état connu : dix paquets pleins, aucun
     * paquet entamé. Depuis que le reliquat du paquet ouvert est mémorisé, le
     * stock retiré par une vente à la cigarette dépend de ce qui reste : sans
     * cette remise à zéro, le résultat de chaque test dépendrait de l'ordre
     * d'exécution.
     */
    @BeforeEach
    void repartirDUnStockNeuf() {
        if (!dbAvailable) {
            return;
        }
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE produits SET quantite_stock = 10, "
                    + "cigarettes_restantes = 0 WHERE id = " + produitId);
        } catch (SQLException e) {
            throw new AssertionError("remise à zéro du produit impossible", e);
        }
    }

    /** Reliquat du paquet entamé pour le produit de test. */
    private int reliquat() {
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(
                     "SELECT cigarettes_restantes FROM produits WHERE id = " + produitId)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new AssertionError("lecture du reliquat impossible", e);
        }
    }

    @AfterAll
    static void tearDown() {
        if (!dbAvailable) {
            return;
        }
        String[] nettoyage = {
            "DELETE FROM detailsvente WHERE id_produit = " + produitId,
            "DELETE FROM ventes WHERE id_utilisateur = " + employeId,
            "DELETE FROM stock_movements WHERE product_id = " + produitId,
            "DELETE FROM audit_logs WHERE user_id = " + employeId,
            "DELETE FROM produits WHERE id = " + produitId,
            "DELETE FROM categories WHERE nom LIKE 'CatTabac\\_" + suffix + "'",
            "DELETE FROM utilisateurs WHERE id = " + employeId,
        };
        try (Connection conn = DBConnector.getConnection()) {
            for (String sql : nettoyage) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    System.err.println("Nettoyage — " + sql + " : " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Nettoyage impossible : " + e.getMessage());
        }
        DBConnector.closeConnection();
    }

    private Vente venteDe(DetailVente ligne, BigDecimal total) {
        Vente vente = new Vente();
        vente.setDateVente(LocalDateTime.now());
        vente.setTotalVente(total);
        vente.setUtilisateurId(employeId);
        vente.setTypePaiement(Vente.PAIEMENT_ESPECES);
        vente.addDetail(ligne);
        return vente;
    }

    private DetailVente ligne(int quantite, String unite, String prixUnitaire) {
        DetailVente d = new DetailVente();
        d.setProduitId(produitId);
        d.setQuantite(quantite);
        d.setPrixVenteUnitaire(new BigDecimal(prixUnitaire));
        d.setPrixAchatUnitaire(new BigDecimal("5.000"));
        d.setTypeVenteTabac(unite);
        return d;
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("7 cigarettes, paquet neuf : un seul paquet entamé")
    void venteCigarettesNeRetireQuUnPaquet() {
        assumeTrue(dbAvailable);

        ProduitDAO dao = new ProduitDAO();
        int avant = dao.findById(produitId).getQuantiteStock();

        new VenteDAO().create(venteDe(ligne(7, "cigarette", "0.600"), new BigDecimal("4.200")));

        int apres = dao.findById(produitId).getQuantiteStock();
        assertEquals(avant - 1, apres,
                "7 cigarettes entament 1 paquet ; le stock retirait auparavant 7 paquets");
    }

    @Test
    @DisplayName("25 cigarettes, paquet neuf : deux paquets entamés")
    void vingtCinqCigarettesEntamentDeuxPaquets() {
        assumeTrue(dbAvailable);

        ProduitDAO dao = new ProduitDAO();
        int avant = dao.findById(produitId).getQuantiteStock();

        new VenteDAO().create(venteDe(ligne(25, "cigarette", "0.600"), new BigDecimal("15.000")));

        assertEquals(avant - 2, dao.findById(produitId).getQuantiteStock());
    }

    @Test
    @DisplayName("Le paquet entamé est mémorisé après une vente au détail")
    void reliquatMemorise() {
        assumeTrue(dbAvailable);

        new VenteDAO().create(venteDe(ligne(7, "cigarette", "0.600"), new BigDecimal("4.200")));

        assertEquals(13, reliquat(), "20 - 7 cigarettes restent dans le paquet ouvert");
    }

    @Test
    @DisplayName("Une seconde vente puise dans le paquet entamé, sans toucher au stock")
    void secondeVenteServieParLeReliquat() {
        assumeTrue(dbAvailable);

        ProduitDAO dao = new ProduitDAO();
        VenteDAO venteDAO = new VenteDAO();

        venteDAO.create(venteDe(ligne(7, "cigarette", "0.600"), new BigDecimal("4.200")));
        int apresPremiere = dao.findById(produitId).getQuantiteStock();

        venteDAO.create(venteDe(ligne(7, "cigarette", "0.600"), new BigDecimal("4.200")));

        assertEquals(apresPremiere, dao.findById(produitId).getQuantiteStock(),
                "13 cigarettes restaient : la seconde vente n'ouvre aucun paquet");
        assertEquals(6, reliquat(), "13 - 7");
    }

    @Test
    @DisplayName("Cinq ventes de 7 cigarettes ne consomment que deux paquets")
    void ventesRepeteesNeGaspillentPlusDePaquets() {
        assumeTrue(dbAvailable);

        ProduitDAO dao = new ProduitDAO();
        VenteDAO venteDAO = new VenteDAO();
        int avant = dao.findById(produitId).getQuantiteStock();

        for (int i = 0; i < 5; i++) {
            venteDAO.create(venteDe(ligne(7, "cigarette", "0.600"), new BigDecimal("4.200")));
        }

        // 35 cigarettes vendues : deux paquets ouverts, 5 cigarettes restantes.
        // L'arrondi au paquet supérieur à chaque vente en consommait cinq,
        // soit 100 cigarettes pour 35 réellement sorties.
        assertEquals(avant - 2, dao.findById(produitId).getQuantiteStock(),
                "le stock ne doit plus dériver à chaque vente partielle");
        assertEquals(5, reliquat(), "2 x 20 - 35");
    }

    @Test
    @DisplayName("Une vente au paquet ne touche pas au reliquat")
    void ventePaquetNAffectePasLeReliquat() {
        assumeTrue(dbAvailable);

        VenteDAO venteDAO = new VenteDAO();
        venteDAO.create(venteDe(ligne(7, "cigarette", "0.600"), new BigDecimal("4.200")));
        venteDAO.create(venteDe(ligne(2, "paquet", "8.500"), new BigDecimal("17.000")));

        assertEquals(13, reliquat(), "vendre des paquets entiers laisse le paquet ouvert intact");
    }

    @Test
    @DisplayName("Une vente au paquet retire le nombre de paquets vendus")
    void ventePaquetRetireLesPaquets() {
        assumeTrue(dbAvailable);

        ProduitDAO dao = new ProduitDAO();
        int avant = dao.findById(produitId).getQuantiteStock();

        new VenteDAO().create(venteDe(ligne(2, "paquet", "8.500"), new BigDecimal("17.000")));

        assertEquals(avant - 2, dao.findById(produitId).getQuantiteStock());
    }

    @Test
    @DisplayName("L'unité vendue est relue depuis la base")
    void uniteVenteRelue() {
        assumeTrue(dbAvailable);

        VenteDAO venteDAO = new VenteDAO();
        Vente vente = venteDe(ligne(3, "cigarette", "0.600"), new BigDecimal("1.800"));
        venteDAO.create(vente);

        List<DetailVente> details = venteDAO.findDetailsByVente(vente.getId());
        assertEquals(1, details.size());
        assertEquals("cigarette", details.get(0).getTypeVenteTabac(),
                "sans persistance de unite_vente, la ligne était relue comme des paquets "
                + "et les statistiques comptaient 3 cigarettes comme 3 paquets");
    }

    @Test
    @DisplayName("Un produit ordinaire est relu sans unité tabac")
    void produitOrdinaireSansUnite() {
        assumeTrue(dbAvailable);

        VenteDAO venteDAO = new VenteDAO();
        Vente vente = venteDe(ligne(1, null, "8.500"), new BigDecimal("8.500"));
        venteDAO.create(vente);

        List<DetailVente> details = venteDAO.findDetailsByVente(vente.getId());
        assertEquals(1, details.size());
        org.junit.jupiter.api.Assertions.assertNull(details.get(0).getTypeVenteTabac(),
                "« unite » ne doit pas être interprété comme une vente tabac");
    }
}

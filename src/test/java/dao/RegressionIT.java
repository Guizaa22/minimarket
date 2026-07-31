package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import model.DetailVente;
import model.NoteJour;
import model.Produit;
import model.Utilisateur;
import model.Vente;
import util.DatabaseSetup;
import util.SecurityUtil;
import util.SessionManager;

/**
 * Tests de non-régression sur les défauts corrigés.
 *
 * Chaque test correspond à un bug constaté en production :
 *   1. les requêtes « du jour » ne renvoyaient jamais rien (DATE() sur epoch-millis)
 *   2. toutes les ventes étaient attribuées à admin (SessionManager jamais démarré)
 *   3. les ajouts de stock n'étaient jamais historisés
 *   4. les montants perdaient en précision (REAL au lieu de NUMERIC)
 *   5. le stock était écrasé au lieu d'être incrémenté
 *
 * Ces tests nécessitent une base PostgreSQL joignable. Ils sont ignorés
 * (et non en échec) si aucune base n'est configurée.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Non-régression 2M Market")
class RegressionIT {

    private static boolean dbAvailable;

    private static int employeId;
    private static int produitId;
    private static String suffix;

    @BeforeAll
    static void setUp() {
        // Garde-fou : ces tests écrivent des ventes, des produits et des utilisateurs.
        // Ils refusent de s'exécuter dans le schéma d'exploitation ("public").
        if (util.Config.isProductionSchema()) {
            System.out.println("[IGNORÉ] Ces tests refusent d'écrire dans le schéma « public ».");
            System.out.println("         Lancez :  mvn test -DPGSCHEMA=test_2m");
            dbAvailable = false;
            return;
        }

        System.out.println("Schéma de test : " + util.Config.getDbSchema());
        dbAvailable = DBConnector.testConnection();
        if (!dbAvailable) {
            System.out.println("[IGNORÉ] Aucune base PostgreSQL joignable - tests sautés.");
            return;
        }

        assertTrue(DatabaseSetup.initializeDatabase(), "le schéma doit s'appliquer");

        suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);

        UtilisateurDAO userDAO = new UtilisateurDAO();
        Utilisateur employe = new Utilisateur(
                "test_employe_" + suffix,
                SecurityUtil.hashPassword("motdepasse-test"),
                Utilisateur.Role.Employé);
        assertTrue(userDAO.create(employe), "création de l'employé de test");
        employeId = employe.getId();

        ProduitDAO produitDAO = new ProduitDAO();
        Produit produit = new Produit(
                "TEST" + suffix, "Produit de test " + suffix, "Divers",
                new BigDecimal("1.250"), new BigDecimal("2.750"),
                100, "unité", 10);
        assertTrue(produitDAO.create(produit), "création du produit de test");
        produitId = produit.getId();
    }

    @AfterAll
    static void tearDown() {
        if (!dbAvailable) {
            return;
        }
        // Nettoyage dans l'ordre inverse des dépendances.
        // Chaque suppression est isolée : si l'une échoue, les autres doivent
        // tout de même s'exécuter, sinon un test en échec laisse des données
        // résiduelles derrière lui.
        String[] nettoyage = {
            "DELETE FROM detailsvente WHERE id_produit IN "
                + "(SELECT id FROM produits WHERE code_barre LIKE 'TEST%" + suffix + "')",
            "DELETE FROM detailsvente WHERE id_produit = " + produitId,
            "DELETE FROM ventes WHERE id_utilisateur = " + employeId,
            "DELETE FROM ajouts_stock WHERE employe_id = " + employeId,
            "DELETE FROM notes_jour WHERE employe_id = " + employeId,
            "DELETE FROM produits WHERE code_barre LIKE 'TEST%" + suffix + "'",
            "DELETE FROM produits WHERE id = " + produitId,
            "DELETE FROM categories WHERE nom LIKE 'Cat%\\_" + suffix + "'",
            "DELETE FROM utilisateurs WHERE id = " + employeId,
        };
        try (Connection conn = DBConnector.getConnection()) {
            for (String sql : nettoyage) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    System.err.println("Nettoyage — échec de [" + sql + "] : " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Nettoyage impossible : " + e.getMessage());
        }
        SessionManager.reset();
        DBConnector.closeConnection();
    }

    // ------------------------------------------------------------------
    // 1. Le pool ne se referme plus tout seul
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("Une connexion refermée ne casse pas les suivantes (ancien singleton partagé)")
    void poolSurvivesClose() throws SQLException {
        assumeTrue(dbAvailable);

        try (Connection first = DBConnector.getConnection()) {
            assertFalse(first.isClosed());
        } // l'ancienne implémentation fermait ici la connexion partagée

        try (Connection second = DBConnector.getConnection();
             Statement stmt = second.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next(), "la base doit rester utilisable après fermeture");
        }
    }

    // ------------------------------------------------------------------
    // 2. Les index existent réellement
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("Les index sont créés (la base SQLite n'en avait aucun)")
    void indexesExist() throws SQLException {
        assumeTrue(dbAvailable);

        String sql = "SELECT COUNT(*) FROM pg_indexes "
                   + "WHERE schemaname = 'public' AND indexname LIKE 'idx_%'";
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            int count = rs.getInt(1);
            assertTrue(count >= 20, "au moins 20 index attendus, trouvés : " + count);
        }
    }

    // ------------------------------------------------------------------
    // 3. Attribution des ventes à l'utilisateur connecté
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("Une vente est attribuée à l'employé connecté, pas à admin")
    void saleAttributedToLoggedInUser() {
        assumeTrue(dbAvailable);

        Utilisateur employe = new UtilisateurDAO().findById(employeId);
        assertNotNull(employe);
        SessionManager.startSession(employe);
        assertEquals(employeId, SessionManager.getCurrentUserId(),
                "startSession() doit renseigner la session");

        Vente vente = new Vente();
        vente.setDateVente(LocalDateTime.now());
        vente.setTotalVente(new BigDecimal("5.500"));
        vente.setUtilisateurId(SessionManager.getCurrentUserId());
        vente.setTypePaiement(Vente.PAIEMENT_ESPECES);

        DetailVente detail = new DetailVente();
        detail.setProduitId(produitId);
        detail.setQuantite(2);
        detail.setPrixVenteUnitaire(new BigDecimal("2.750"));
        detail.setPrixAchatUnitaire(new BigDecimal("1.250"));
        vente.addDetail(detail);

        assertTrue(new VenteDAO().create(vente), "la vente doit être enregistrée");
        assertTrue(vente.getId() > 0, "l'ID de vente doit être renseigné (RETURNING id)");

        List<Vente> ventes = new VenteDAO().findByUtilisateur(employeId);
        assertFalse(ventes.isEmpty(), "la vente doit être rattachée à l'employé");
        assertEquals(employeId, ventes.get(0).getUtilisateurId());
    }

    // ------------------------------------------------------------------
    // 4. Les requêtes « du jour » renvoient enfin des données
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("La recette du jour retrouve la vente (DATE() renvoyait toujours vide)")
    void dailyReportFindsSale() {
        assumeTrue(dbAvailable);

        LocalDateTime today = LocalDateTime.now();
        List<Vente> ventes = new VenteDAO()
                .findByUtilisateurAndDate(employeId, today.toLocalDate().atStartOfDay(), today);

        assertFalse(ventes.isEmpty(),
                "la vente du jour doit être retrouvée - c'était le bug principal");
    }

    @Test
    @Order(5)
    @DisplayName("Les notes du jour sont retrouvées")
    void dailyNotesFound() {
        assumeTrue(dbAvailable);

        NoteJourDAO noteDAO = new NoteJourDAO();
        NoteJour note = new NoteJour(employeId, NoteJour.TypeNote.Credit,
                new BigDecimal("12.500"), "note de test", LocalDateTime.now());
        assertTrue(noteDAO.create(note), "la note doit être créée");

        List<NoteJour> notes = noteDAO.findByEmployeAndDate(employeId, LocalDateTime.now());
        assertEquals(1, notes.size(), "la note du jour doit être retrouvée");

        BigDecimal total = noteDAO.getTotalByTypeAndDate(
                NoteJour.TypeNote.Credit, LocalDateTime.now());
        assertTrue(total.compareTo(BigDecimal.ZERO) > 0,
                "le total des crédits du jour ne doit pas être nul");
    }

    // ------------------------------------------------------------------
    // 5. Précision monétaire
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("Les montants gardent leur précision (NUMERIC et non REAL)")
    void moneyKeepsPrecision() throws SQLException {
        assumeTrue(dbAvailable);

        // 0.1 + 0.2 est le cas d'école qui échoue en virgule flottante.
        BigDecimal montant = new BigDecimal("0.300");

        String insert = "INSERT INTO notes_jour (employe_id, type_note, montant, description, date_note) "
                      + "VALUES (?, 'Autre', ?, 'precision', ?) RETURNING id";
        int noteId;
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setInt(1, employeId);
            stmt.setBigDecimal(2, montant);
            stmt.setTimestamp(3, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                noteId = rs.getInt(1);
            }
        }

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT montant FROM notes_jour WHERE id = ?")) {
            stmt.setInt(1, noteId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, montant.compareTo(rs.getBigDecimal(1)),
                        "le montant doit être restitué à l'identique");
            }
        }
    }

    // ------------------------------------------------------------------
    // Catégories
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    @DisplayName("Une catégorie sans produit apparaît quand même (bug signalé)")
    void emptyCategoryIsVisible() {
        assumeTrue(dbAvailable);

        CategorieDAO catDAO = new CategorieDAO();
        String nom = "CatVide_" + suffix;
        assertTrue(catDAO.create(new model.Categorie(nom)), "création de la catégorie");

        List<String> visibles = new ProduitDAO().findAllCategories();
        assertTrue(visibles.contains(nom),
                "une catégorie sans produit doit être visible côté caisse ; "
                + "l'ancien INNER JOIN sur produits la masquait. Trouvées : " + visibles);
    }

    @Test
    @Order(11)
    @DisplayName("findByCategoryId renvoie aussi les produits en rupture")
    void categoryIncludesOutOfStock() {
        assumeTrue(dbAvailable);

        ProduitDAO dao = new ProduitDAO();
        Produit rupture = new Produit(
                "TESTRUP" + suffix, "Produit rupture " + suffix, "Divers",
                new BigDecimal("1.000"), new BigDecimal("2.000"),
                0, "unité", 5);
        assertTrue(dao.create(rupture), "création du produit à stock nul");

        Integer catId = categoryIdOf(rupture.getId());
        assumeTrue(catId != null, "le produit doit être rattaché à une catégorie");

        boolean present = dao.findByCategoryId(catId).stream()
                .anyMatch(p -> p.getId() == rupture.getId());
        assertTrue(present,
                "un produit à stock 0 doit rester listé ; le filtre quantite_stock > 0 "
                + "le faisait disparaître de l'inventaire");
    }

    @Test
    @Order(12)
    @DisplayName("Renommer une catégorie reclasse ses produits")
    void renamePropagatesToProducts() {
        assumeTrue(dbAvailable);

        CategorieDAO catDAO = new CategorieDAO();
        String initial = "CatRenom_" + suffix;
        model.Categorie cat = new model.Categorie(initial);
        assertTrue(catDAO.create(cat));

        ProduitDAO dao = new ProduitDAO();
        Produit p = new Produit("TESTREN" + suffix, "Produit renom " + suffix, initial,
                new BigDecimal("1.000"), new BigDecimal("2.000"), 5, "unité", 1);
        assertTrue(dao.create(p));

        String nouveau = "CatRenomme_" + suffix;
        cat.setNom(nouveau);
        assertTrue(catDAO.update(cat), "le renommage doit réussir");

        assertEquals(nouveau, dao.findById(p.getId()).getCategorie(),
                "le produit doit suivre le nouveau nom ; sinon la caisse et la liste "
                + "des produits le classent différemment");
    }

    @Test
    @Order(13)
    @DisplayName("Supprimer une catégorie utilisée est refusé")
    void deleteUsedCategoryIsRejected() {
        assumeTrue(dbAvailable);

        CategorieDAO catDAO = new CategorieDAO();
        model.Categorie cat = new model.Categorie("CatSuppr_" + suffix);
        assertTrue(catDAO.create(cat));

        ProduitDAO dao = new ProduitDAO();
        Produit p = new Produit("TESTSUP" + suffix, "Produit suppr " + suffix, cat.getNom(),
                new BigDecimal("1.000"), new BigDecimal("2.000"), 5, "unité", 1);
        assertTrue(dao.create(p));

        SQLException refus = org.junit.jupiter.api.Assertions.assertThrows(
                SQLException.class, () -> catDAO.delete(cat.getId()),
                "la suppression doit être refusée tant qu'un produit l'utilise");
        assertTrue(refus.getMessage().contains("produit"),
                "le message doit expliquer la cause : " + refus.getMessage());
    }

    @Test
    @Order(14)
    @DisplayName("Le type de catégorie survit au renommage (plus de détection par le nom)")
    void categoryTypeSurvivesRename() {
        assumeTrue(dbAvailable);

        CategorieDAO catDAO = new CategorieDAO();
        // Nom volontairement dépourvu des mots « tabac » / « cigarette » :
        // l'ancienne heuristique ne l'aurait jamais reconnu.
        model.Categorie cat = new model.Categorie(
                "CatType_" + suffix, model.TypeCategorie.Tabac);
        assertTrue(catDAO.create(cat), "création de la catégorie tabac");

        ProduitDAO dao = new ProduitDAO();
        Produit p = new Produit("TESTTYP" + suffix, "Produit typé " + suffix, cat.getNom(),
                new BigDecimal("5.000"), new BigDecimal("8.000"), 10, "unité", 2);
        assertTrue(dao.create(p));

        assertTrue(dao.findById(p.getId()).isTabac(),
                "le produit doit être reconnu comme tabac via le type, "
                + "alors que son libellé ne contient aucun mot-clé");

        // Renommage vers un libellé tout aussi neutre : le type doit primer.
        cat.setNom("Renomme_" + suffix);
        assertTrue(catDAO.update(cat));

        assertTrue(dao.findById(p.getId()).isTabac(),
                "le renommage ne doit pas changer le comportement métier ; "
                + "avec la détection par libellé, le produit cessait d'être du tabac");
    }

    @Test
    @Order(15)
    @DisplayName("Un libellé trompeur ne rend pas un produit « tabac »")
    void misleadingNameDoesNotImplyTobacco() {
        assumeTrue(dbAvailable);

        CategorieDAO catDAO = new CategorieDAO();
        // Contient « cigarette » mais déclaré Standard : le type doit gagner.
        model.Categorie cat = new model.Categorie(
                "CatCigarette_" + suffix, model.TypeCategorie.Standard);
        assertTrue(catDAO.create(cat));

        ProduitDAO dao = new ProduitDAO();
        Produit p = new Produit("TESTMIS" + suffix, "Briquet " + suffix, cat.getNom(),
                new BigDecimal("1.000"), new BigDecimal("2.000"), 10, "unité", 2);
        assertTrue(dao.create(p));

        assertFalse(dao.findById(p.getId()).isTabac(),
                "le type explicite doit primer sur le libellé ; l'ancienne "
                + "heuristique classait ce produit comme du tabac");
    }

    /** Renvoie le category_id d'un produit, ou null. */
    private static Integer categoryIdOf(int produitId) {
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT category_id FROM produits WHERE id = ?")) {
            stmt.setInt(1, produitId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return rs.wasNull() ? null : id;
                }
            }
        } catch (SQLException e) {
            System.err.println("categoryIdOf : " + e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 6. Incrément de stock atomique
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("augmenterStock incrémente sans écraser une vente concurrente")
    void stockIncrementIsAtomic() {
        assumeTrue(dbAvailable);

        ProduitDAO dao = new ProduitDAO();
        int before = dao.findById(produitId).getQuantiteStock();

        assertTrue(dao.augmenterStock(produitId, 10));
        assertTrue(dao.augmenterStock(produitId, 5));

        assertEquals(before + 15, dao.findById(produitId).getQuantiteStock(),
                "les deux incréments doivent se cumuler");
    }

    @Test
    @Order(8)
    @DisplayName("Le stock ne peut pas devenir négatif")
    void stockCannotGoNegative() {
        assumeTrue(dbAvailable);

        ProduitDAO dao = new ProduitDAO();
        int current = dao.findById(produitId).getQuantiteStock();

        assertFalse(dao.augmenterStock(produitId, -(current + 1)),
                "un retrait supérieur au stock doit être refusé");
        assertEquals(current, dao.findById(produitId).getQuantiteStock(),
                "le stock doit rester inchangé");
    }

    // ------------------------------------------------------------------
    // 7. Historique des ajouts de stock
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    @DisplayName("Un ajout de stock est historisé (ajouts_stock restait vide)")
    void stockAdditionIsRecorded() {
        assumeTrue(dbAvailable);

        AjoutStockDAO dao = new AjoutStockDAO();
        model.AjoutStock ajout = new model.AjoutStock(
                produitId, employeId, null, 7,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "test historisation", LocalDateTime.now());

        assertTrue(dao.create(ajout), "l'ajout doit être enregistré");

        List<model.AjoutStock> ajouts = dao.findByEmployeAndDate(employeId, LocalDateTime.now());
        assertFalse(ajouts.isEmpty(),
                "l'historique du jour doit contenir l'ajout - il restait vide auparavant");
    }
}

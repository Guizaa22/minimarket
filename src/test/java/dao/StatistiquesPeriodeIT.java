package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.Produit;
import model.Utilisateur;
import util.DatabaseSetup;
import util.SecurityUtil;

/**
 * Bornes des statistiques de période.
 *
 * Les totaux utilisaient {@code BETWEEN ? AND ?} avec une borne de fin à
 * 23:59:59 : une vente encaissée à 23:59:59,5 — cas banal un soir de
 * fermeture — n'était comptée nulle part. L'intervalle est désormais
 * semi-ouvert {@code [debut, fin[}, la borne de fin étant le début du
 * lendemain.
 */
@DisplayName("Statistiques de période — bornes de l'intervalle")
class StatistiquesPeriodeIT {

    private static final Logger LOG = LoggerFactory.getLogger(StatistiquesPeriodeIT.class);

    private static boolean dbAvailable;
    private static String suffix;
    private static int employeId;
    private static int produitId;

    /** Jour de référence, volontairement dans le passé pour ne croiser aucune autre donnée. */
    private static final LocalDate JOUR = LocalDate.of(2001, 3, 14);

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

        Utilisateur employe = new Utilisateur("stat_employe_" + suffix,
                SecurityUtil.hashPassword("motdepasse-test"), Utilisateur.Role.Employé);
        assertTrue(new UtilisateurDAO().create(employe));
        employeId = employe.getId();

        Produit p = new Produit("STATTEST" + suffix, "Produit stats " + suffix, "Divers",
                new BigDecimal("2.000"), new BigDecimal("5.000"), 1000, "unité", 2);
        assertTrue(new ProduitDAO().create(p));
        produitId = p.getId();

        // Une vente en milieu de journée, une autre dans la toute dernière
        // seconde : c'est celle-ci que l'ancienne borne laissait échapper.
        insererVente(JOUR.atTime(10, 0, 0), new BigDecimal("15.000"), 3);
        insererVente(JOUR.atTime(23, 59, 59, 500_000_000), new BigDecimal("5.000"), 1);
    }

    @AfterAll
    static void tearDown() {
        if (!dbAvailable) {
            return;
        }
        String[] nettoyage = {
            "DELETE FROM detailsvente WHERE id_produit = " + produitId,
            "DELETE FROM ventes WHERE id_utilisateur = " + employeId,
            "DELETE FROM produits WHERE id = " + produitId,
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

    private static void insererVente(LocalDateTime quand, BigDecimal total, int quantite)
            throws SQLException {
        try (Connection conn = DBConnector.getConnection()) {
            int venteId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO ventes (date_vente, total_vente, id_utilisateur) "
                    + "VALUES (?, ?, ?) RETURNING id")) {
                stmt.setTimestamp(1, Timestamp.valueOf(quand));
                stmt.setBigDecimal(2, total);
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
                stmt.setInt(2, produitId);
                stmt.setInt(3, quantite);
                stmt.setBigDecimal(4, new BigDecimal("5.000"));
                stmt.setBigDecimal(5, new BigDecimal("2.000"));
                stmt.executeUpdate();
            }
        }
    }

    private static LocalDateTime debut() {
        return JOUR.atStartOfDay();
    }

    private static LocalDateTime fin() {
        return JOUR.plusDays(1).atStartOfDay();
    }

    @Test
    @DisplayName("la recette inclut la vente de la dernière seconde du jour")
    void recetteInclutLaDerniereSeconde() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        BigDecimal recette = new VenteDAO().getTotalRecettes(debut(), fin());

        assertEquals(0, new BigDecimal("20.000").compareTo(recette),
                "15.000 + 5.000 : la vente de 23:59:59,5 doit être comptée");
    }

    @Test
    @DisplayName("le nombre de ventes inclut celle de la dernière seconde")
    void nombreVentesInclutLaDerniereSeconde() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        assertEquals(2, new VenteDAO().getNombreVentesParPeriode(debut(), fin()));
    }

    @Test
    @DisplayName("le bénéfice inclut la vente de la dernière seconde")
    void beneficeInclutLaDerniereSeconde() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        BigDecimal benefice = new VenteDAO().getTotalProfit(debut(), fin());

        // (5,000 - 2,000) × (3 + 1) = 12,000
        assertEquals(0, new BigDecimal("12.000").compareTo(benefice));
    }

    @Test
    @DisplayName("le classement des produits inclut la vente de la dernière seconde")
    void topProduitsInclutLaDerniereSeconde() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        var top = new DetailVenteDAO().getTopProduitsParPeriode(debut(), fin(), 10).stream()
                .filter(s -> s.getNomProduit().endsWith(suffix))
                .findFirst()
                .orElse(null);

        assertTrue(top != null, "le produit de test doit figurer au classement");
        assertEquals(4, top.getQuantiteVendue(), "3 + 1 unités");
    }

    @Test
    @DisplayName("la borne de fin est exclue : le lendemain ne compte pas la veille")
    void borneDeFinExclue() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        // Journée suivante : aucune vente. Si la borne de fin était incluse,
        // la vente de 23:59:59,5 de la veille serait comptée deux fois.
        BigDecimal lendemain = new VenteDAO().getTotalRecettes(
                JOUR.plusDays(1).atStartOfDay(), JOUR.plusDays(2).atStartOfDay());

        assertEquals(0, BigDecimal.ZERO.compareTo(lendemain));
    }

    @Test
    @DisplayName("deux jours consécutifs ne comptent aucune vente en double")
    void joursConsecutifsSansDoubleComptage() {
        assumeTrue(dbAvailable, "Aucune base PostgreSQL joignable");

        VenteDAO dao = new VenteDAO();
        BigDecimal veille = dao.getTotalRecettes(
                JOUR.minusDays(1).atStartOfDay(), JOUR.atStartOfDay());
        BigDecimal jour = dao.getTotalRecettes(debut(), fin());

        assertEquals(0, veille.add(jour).compareTo(new BigDecimal("20.000")),
                "la somme des deux journées doit valoir exactement le total");
    }
}

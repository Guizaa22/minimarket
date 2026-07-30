package dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import controller.GestionVentesController.ProduitStats;
import model.DetailVente;

/**
 * DAO pour les détails de vente avec fonctions statistiques
 */
public class DetailVenteDAO {

    /**
     * Récupère tous les détails de vente pour une vente donnée
     * @param venteId L'ID de la vente
     * @return Liste des détails de vente
     */
    public List<DetailVente> findByVenteId(int venteId) {
        List<DetailVente> details = new ArrayList<>();
        String sql = "SELECT * FROM detailsvente WHERE id_vente = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, venteId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                DetailVente detail = new DetailVente(
                        rs.getInt("id"),
                        rs.getInt("id_vente"),
                        rs.getInt("id_produit"),
                        rs.getInt("quantite"),
                        rs.getBigDecimal("prix_vente_unitaire"),
                        rs.getBigDecimal("prix_achat_unitaire")
                );
                details.add(detail);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des détails de vente: " + e.getMessage());
        }

        return details;
    }

    /**
     * Récupère les ventes totales par catégorie de produit
     * @return Map avec la catégorie comme clé et le CA comme valeur
     */
    public Map<String, BigDecimal> getVentesParCategorie() {
        Map<String, BigDecimal> ventesParCategorie = new LinkedHashMap<>();

        String sql = "SELECT p.categorie, SUM(dv.prix_vente_unitaire * dv.quantite) as total " +
                "FROM detailsvente dv " +
                "INNER JOIN produits p ON dv.id_produit = p.id " +
                "GROUP BY p.categorie " +
                "ORDER BY total DESC";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String categorie = rs.getString("categorie");
                BigDecimal total = rs.getBigDecimal("total");

                // Si la catégorie est null ou vide, utiliser "Divers"
                if (categorie == null || categorie.trim().isEmpty()) {
                    categorie = "Divers";
                }

                ventesParCategorie.put(categorie, total != null ? total : BigDecimal.ZERO);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des ventes par catégorie: " + e.getMessage());
        }

        // Si aucune donnée, retourner des données par défaut pour éviter un graphique vide
        if (ventesParCategorie.isEmpty()) {
            ventesParCategorie.put("Alimentaire", BigDecimal.ZERO);
            ventesParCategorie.put("Boissons", BigDecimal.ZERO);
            ventesParCategorie.put("Divers", BigDecimal.ZERO);
        }

        return ventesParCategorie;
    }

    /**
     * Récupère les N produits les plus vendus
     * @param limit Nombre de produits à retourner
     * @return Liste des statistiques des produits les plus vendus
     */
    public List<ProduitStats> getTopProduits(int limit) {
        List<ProduitStats> topProduits = new ArrayList<>();

        String sql = "SELECT p.nom, " +
                "       SUM(dv.quantite) as quantite_totale, " +
                "       SUM(dv.prix_vente_unitaire * dv.quantite) as ca_total " +
                "FROM detailsvente dv " +
                "INNER JOIN produits p ON dv.id_produit = p.id " +
                "GROUP BY p.id, p.nom " +
                "ORDER BY quantite_totale DESC " +
                "LIMIT ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String nomProduit = rs.getString("nom");
                int quantiteTotale = rs.getInt("quantite_totale");
                BigDecimal caTotal = rs.getBigDecimal("ca_total");

                ProduitStats stats = new ProduitStats(
                        nomProduit,
                        quantiteTotale,
                        caTotal != null ? caTotal : BigDecimal.ZERO
                );

                topProduits.add(stats);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération du top produits: " + e.getMessage());
        }

        return topProduits;
    }

    /**
     * Récupère les statistiques de vente pour un produit spécifique
     * @param produitId L'ID du produit
     * @return Les statistiques du produit
     */
    public ProduitStats getStatsProduit(int produitId) {
        String sql = "SELECT p.nom, " +
                "       SUM(dv.quantite) as quantite_totale, " +
                "       SUM(dv.prix_vente_unitaire * dv.quantite) as ca_total " +
                "FROM detailsvente dv " +
                "INNER JOIN produits p ON dv.id_produit = p.id " +
                "WHERE p.id = ? " +
                "GROUP BY p.id, p.nom";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, produitId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new ProduitStats(
                        rs.getString("nom"),
                        rs.getInt("quantite_totale"),
                        rs.getBigDecimal("ca_total")
                );
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des stats du produit: " + e.getMessage());
        }

        return null;
    }

    /**
     * Récupère les produits qui génèrent le plus de profit
     * @param limit Nombre de produits à retourner
     * @return Liste des produits avec leur profit
     */
    public List<ProduitStats> getTopProduitsByProfit(int limit) {
        List<ProduitStats> topProduits = new ArrayList<>();

        String sql = "SELECT p.nom, " +
                "       SUM(dv.quantite) as quantite_totale, " +
                "       SUM((dv.prix_vente_unitaire - dv.prix_achat_unitaire) * dv.quantite) as profit_total " +
                "FROM detailsvente dv " +
                "INNER JOIN produits p ON dv.id_produit = p.id " +
                "GROUP BY p.id, p.nom " +
                "ORDER BY profit_total DESC " +
                "LIMIT ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String nomProduit = rs.getString("nom");
                int quantiteTotale = rs.getInt("quantite_totale");
                BigDecimal profitTotal = rs.getBigDecimal("profit_total");

                ProduitStats stats = new ProduitStats(
                        nomProduit,
                        quantiteTotale,
                        profitTotal != null ? profitTotal : BigDecimal.ZERO
                );

                topProduits.add(stats);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération du top produits par profit: " + e.getMessage());
        }

        return topProduits;
    }

    /**
     * Récupère le nombre total d'articles vendus pour une vente
     * @param venteId L'ID de la vente
     * @return Le nombre total d'articles
     */
    public int getNombreArticlesVente(int venteId) {
        String sql = "SELECT COALESCE(SUM(quantite), 0) FROM detailsvente WHERE id_vente = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, venteId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors du comptage des articles: " + e.getMessage());
        }

        return 0;
    }

    /**
     * Récupère les catégories les plus vendues avec leur pourcentage
     * @return Map avec la catégorie et son pourcentage de vente
     */
    public Map<String, Double> getPourcentageVentesParCategorie() {
        Map<String, Double> pourcentages = new LinkedHashMap<>();

        // D'abord, calculer le total global
        String sqlTotal = "SELECT SUM(dv.prix_vente_unitaire * dv.quantite) as total_global " +
                "FROM detailsvente dv";

        BigDecimal totalGlobal = BigDecimal.ZERO;

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlTotal);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                totalGlobal = rs.getBigDecimal("total_global");
                if (totalGlobal == null) {
                    totalGlobal = BigDecimal.ZERO;
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors du calcul du total global: " + e.getMessage());
            return pourcentages;
        }

        // Si le total est zéro, retourner une map vide
        if (totalGlobal.compareTo(BigDecimal.ZERO) == 0) {
            return pourcentages;
        }

        // Ensuite, calculer les pourcentages par catégorie
        Map<String, BigDecimal> ventesParCategorie = getVentesParCategorie();

        for (Map.Entry<String, BigDecimal> entry : ventesParCategorie.entrySet()) {
            double pourcentage = entry.getValue()
                    .divide(totalGlobal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

            pourcentages.put(entry.getKey(), pourcentage);
        }

        return pourcentages;
    }
}
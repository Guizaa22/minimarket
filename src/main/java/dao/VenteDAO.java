package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import model.DetailVente;
import model.Produit;
import model.Vente;
import util.DayRange;

/**
 * DAO pour la gestion des ventes + statistiques
 */
public class VenteDAO {

    /**
     * Créer une vente avec ses détails + mise à jour stock
     */
    public boolean create(Vente vente) {
        Connection conn = null;
        PreparedStatement stmtVente = null;
        PreparedStatement stmtDetail = null;
        PreparedStatement stmtStock = null;

        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);

            // Vérifier que l'utilisateur existe avant d'insérer la vente
            try (PreparedStatement checkUser = conn.prepareStatement("SELECT id FROM utilisateurs WHERE id = ?")) {
                checkUser.setInt(1, vente.getUtilisateurId());
                try (ResultSet rs = checkUser.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        throw new SQLException("L'utilisateur avec l'ID " + vente.getUtilisateurId() + " n'existe pas dans la base de données");
                    }
                }
            }

            // INSERT VENTE - RETURNING id est la forme native PostgreSQL
            String sqlVente = "INSERT INTO ventes (date_vente, total_vente, id_utilisateur, type_paiement) "
                            + "VALUES (?, ?, ?, ?) RETURNING id";
            stmtVente = conn.prepareStatement(sqlVente);

            stmtVente.setTimestamp(1, Timestamp.valueOf(vente.getDateVente()));
            stmtVente.setBigDecimal(2, vente.getTotalVente());
            stmtVente.setInt(3, vente.getUtilisateurId());
            stmtVente.setString(4, vente.getTypePaiement() != null
                    ? vente.getTypePaiement() : "Espèces");

            int venteId = -1;
            try (ResultSet rs = stmtVente.executeQuery()) {
                if (rs.next()) {
                    venteId = rs.getInt(1);
                }
            }

            if (venteId <= 0) {
                conn.rollback();
                System.err.println("Erreur: Impossible d'obtenir l'ID de la vente créée");
                return false;
            }
            vente.setId(venteId);

            // Vérifier qu'il y a des détails AVANT de commencer
            if (vente.getDetails() == null || vente.getDetails().isEmpty()) {
                conn.rollback();
                System.err.println("========================================");
                System.err.println("ERREUR: Aucun détail de vente à enregistrer - le panier est vide");
                System.err.println("========================================");
                return false;
            }

            // Valider tous les détails AVANT de commencer la transaction
            for (DetailVente d : vente.getDetails()) {
                if (d == null) {
                    conn.rollback();
                    throw new SQLException("Un détail de vente est null");
                }
                if (d.getProduitId() <= 0) {
                    conn.rollback();
                    throw new SQLException("ID produit invalide: " + d.getProduitId());
                }
                if (d.getQuantite() <= 0) {
                    conn.rollback();
                    throw new SQLException("Quantité invalide: " + d.getQuantite());
                }
                if (d.getPrixVenteUnitaire() == null) {
                    conn.rollback();
                    throw new SQLException("Prix de vente unitaire manquant pour produit ID " + d.getProduitId());
                }
                if (d.getPrixAchatUnitaire() == null) {
                    conn.rollback();
                    throw new SQLException("Prix d'achat unitaire manquant pour produit ID " + d.getProduitId());
                }
            }

            // INSERT DETAILS + UPDATE STOCK
            String sqlDetail = "INSERT INTO detailsvente (id_vente, id_produit, quantite, prix_vente_unitaire, prix_achat_unitaire) VALUES (?, ?, ?, ?, ?)";
            String sqlStock = "UPDATE produits SET quantite_stock = quantite_stock - ? WHERE id = ? AND quantite_stock >= ?";

            stmtDetail = conn.prepareStatement(sqlDetail);
            stmtStock = conn.prepareStatement(sqlStock);
            
            // DAO pour récupérer les produits
            ProduitDAO produitDAO = new ProduitDAO();

            for (DetailVente d : vente.getDetails()) {
                
                // Récupérer le produit pour vérifier s'il est "frak cigarette"
                Produit produitVendu = produitDAO.findById(d.getProduitId());
                if (produitVendu == null) {
                    throw new SQLException("Produit ID " + d.getProduitId() + " introuvable dans la base de données");
                }
                
                // Pour les produits "frak cigarette", on ne vérifie pas leur stock (ils n'ont pas de stock)
                // Le stock sera décrémenté sur le produit tabac associé
                if (!produitVendu.isFrakCigarette()) {
                    // Vérifier le stock avant d'ajouter (uniquement pour les produits normaux)
                    try (PreparedStatement checkStock = conn.prepareStatement("SELECT quantite_stock FROM produits WHERE id = ?")) {
                        checkStock.setInt(1, d.getProduitId());
                        try (ResultSet rs = checkStock.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Produit ID " + d.getProduitId() + " introuvable dans la base de données");
                            }
                            int stockActuel = rs.getInt("quantite_stock");
                            if (stockActuel < d.getQuantite()) {
                                throw new SQLException("Stock insuffisant pour produit ID " + d.getProduitId() + ". Stock disponible: " + stockActuel + ", Quantité demandée: " + d.getQuantite());
                            }
                        }
                    }
                }

                stmtDetail.setInt(1, venteId);
                stmtDetail.setInt(2, d.getProduitId());
                stmtDetail.setInt(3, d.getQuantite());
                stmtDetail.setBigDecimal(4, d.getPrixVenteUnitaire());
                stmtDetail.setBigDecimal(5, d.getPrixAchatUnitaire());
                stmtDetail.addBatch();

                // Si c'est un "frak cigarette", décrémenter le stock du produit tabac associé
                if (produitVendu.isFrakCigarette()) {
                    // Utiliser le produitTabacAssocieId stocké dans DetailVente
                    Integer produitTabacAssocieId = d.getProduitTabacAssocieId();
                    if (produitTabacAssocieId != null && produitTabacAssocieId > 0) {
                        // Récupérer le produit tabac associé
                        Produit produitTabacAssocie = produitDAO.findById(produitTabacAssocieId);
                        if (produitTabacAssocie != null) {
                            // Calculer combien de paquets à décrémenter (20 cigarettes = 1 paquet)
                            int totalCigarettes = d.getQuantite();
                            int paquetsADecrémenter = (totalCigarettes + 19) / 20; // Arrondir vers le haut
                            
                            if (paquetsADecrémenter > 0) {
                                // Vérifier le stock du produit tabac associé
                                try (PreparedStatement checkStockTabac = conn.prepareStatement("SELECT quantite_stock FROM produits WHERE id = ?")) {
                                    checkStockTabac.setInt(1, produitTabacAssocie.getId());
                                    try (ResultSet rs = checkStockTabac.executeQuery()) {
                                        if (rs.next()) {
                                            int stockTabac = rs.getInt("quantite_stock");
                                            if (stockTabac < paquetsADecrémenter) {
                                                throw new SQLException("Stock insuffisant pour produit tabac associé '" + produitTabacAssocie.getNom() + 
                                                    "'. Stock disponible: " + stockTabac + " paquets, Quantité demandée: " + paquetsADecrémenter + " paquets (pour " + totalCigarettes + " cigarettes)");
                                            }
                                        }
                                    }
                                }
                                
                                // Décrémenter le stock du produit tabac associé
                                stmtStock.setInt(1, paquetsADecrémenter);
                                stmtStock.setInt(2, produitTabacAssocie.getId());
                                stmtStock.setInt(3, paquetsADecrémenter);
                                stmtStock.addBatch();
                                
                                System.out.println("✓ Frak cigarette: " + totalCigarettes + " cigarettes vendues -> " + paquetsADecrémenter + " paquet(s) décrémenté(s) du produit '" + produitTabacAssocie.getNom() + "'");
                            }
                        } else {
                            System.err.println("⚠ ATTENTION: Produit tabac associé ID " + produitTabacAssocieId + " introuvable pour le produit 'frak cigarette' '" + produitVendu.getNom() + "'.");
                        }
                    } else {
                        System.err.println("⚠ ATTENTION: Produit 'frak cigarette' '" + produitVendu.getNom() + "' vendu, mais aucun produit tabac associé spécifié. Le stock du produit tabac ne sera pas décrémenté.");
                    }
                } else {
                    // Produit normal: décrémenter son propre stock
                    stmtStock.setInt(1, d.getQuantite());
                    stmtStock.setInt(2, d.getProduitId());
                    stmtStock.setInt(3, d.getQuantite()); // Vérification dans WHERE
                    stmtStock.addBatch();
                }
            }

            stmtDetail.executeBatch();
            int[] stockResults = stmtStock.executeBatch();
            
            // Vérifier que toutes les mises à jour de stock ont réussi
            for (int result : stockResults) {
                if (result == 0) {
                    throw new SQLException("Échec de la mise à jour du stock pour un produit");
                }
            }

            // Commit explicite avec vérification
            conn.commit();
            
            // Vérifier que tout est bien sauvegardé
            try (Statement verifyStmt = conn.createStatement();
                 ResultSet rs = verifyStmt.executeQuery("SELECT COUNT(*) FROM detailsvente WHERE id_vente = " + venteId)) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count != vente.getDetails().size()) {
                        System.err.println("ATTENTION: Nombre de détails sauvegardés (" + count + ") ne correspond pas au nombre attendu (" + vente.getDetails().size() + ")");
                    } else {
                        System.out.println("✓ Vente sauvegardée avec succès: ID=" + venteId + ", Détails=" + count);
                    }
                }
            }
            
            return true;

        } catch (SQLException e) {
            System.err.println("========================================");
            System.err.println("ERREUR création vente:");
            System.err.println("Message: " + e.getMessage());
            System.err.println("Code SQL: " + e.getSQLState());
            System.err.println("Erreur SQLite: " + e.getErrorCode());
            System.err.println("Utilisateur ID: " + (vente != null ? vente.getUtilisateurId() : "null"));
            System.err.println("Total: " + (vente != null ? vente.getTotalVente() : "null"));
            System.err.println("Nombre de détails: " + (vente != null && vente.getDetails() != null ? vente.getDetails().size() : 0));
            if (vente != null && vente.getDetails() != null) {
                for (int i = 0; i < vente.getDetails().size(); i++) {
                    DetailVente d = vente.getDetails().get(i);
                    System.err.println("  Détail " + i + ": Produit ID=" + d.getProduitId() + 
                                      ", Quantité=" + d.getQuantite() + 
                                      ", PrixVente=" + d.getPrixVenteUnitaire() + 
                                      ", PrixAchat=" + d.getPrixAchatUnitaire());
                }
            }
            e.printStackTrace();
            System.err.println("========================================");
            
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.rollback();
                    System.out.println("✓ Rollback effectué");
                }
            } catch (SQLException ex) {
                System.err.println("✗ Erreur lors du rollback: " + ex.getMessage());
                ex.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("========================================");
            System.err.println("ERREUR INATTENDUE création vente:");
            System.err.println("Type: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
            System.err.println("========================================");
            
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                System.err.println("Erreur lors du rollback: " + ex.getMessage());
            }
        } finally {
            try {
                if (stmtVente != null) stmtVente.close();
                if (stmtDetail != null) stmtDetail.close();
                if (stmtStock != null) stmtStock.close();
                if (conn != null) {
                    conn.setAutoCommit(true);
                    // Rend la connexion au pool (ne coupe pas la connexion réseau).
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("Erreur lors de la fermeture des ressources: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Liste toutes les ventes
     */
    public List<Vente> findAll() {
        List<Vente> ventes = new ArrayList<>();
        String sql = "SELECT * FROM ventes ORDER BY date_vente DESC";

        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }

        } catch (SQLException e) {
            System.err.println("Erreur findAll: " + e.getMessage());
        }
        return ventes;
    }

    /**
     * 🔥 Nouvelle méthode : dernières ventes limitées
     */
    public List<Vente> findRecent(int limit) {
        List<Vente> ventes = new ArrayList<>();
        String sql = "SELECT * FROM ventes ORDER BY date_vente DESC LIMIT ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }

        } catch (SQLException e) {
            System.err.println("Erreur findRecent: " + e.getMessage());
        }

        return ventes;
    }

    /**
     * Ventes par utilisateur
     */
    public List<Vente> findByUtilisateur(int utilisateurId) {
        List<Vente> ventes = new ArrayList<>();
        String sql = "SELECT * FROM ventes WHERE id_utilisateur = ? ORDER BY date_vente DESC";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, utilisateurId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }

        } catch (SQLException e) {
            System.err.println("Erreur findByUtilisateur: " + e.getMessage());
        }

        return ventes;
    }
    
    /**
     * Ventes par utilisateur pour une date spécifique
     */
    public List<Vente> findByUtilisateurAndDate(int utilisateurId, LocalDateTime dateDebut, LocalDateTime dateFin) {
        List<Vente> ventes = new ArrayList<>();
        // Intervalle semi-ouvert [jour, lendemain[ : compatible index, contrairement
        // à DATE(date_vente) qui empêchait toute utilisation de l'index.
        String sql = "SELECT * FROM ventes WHERE id_utilisateur = ? AND "
                   + DayRange.where("date_vente") + " ORDER BY date_vente DESC";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, utilisateurId);
            DayRange.bind(stmt, 2, dateDebut);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }
            
            System.out.println("findByUtilisateurAndDate: Trouvé " + ventes.size() + " ventes pour utilisateur " + utilisateurId + " le " + dateDebut.toLocalDate());

        } catch (SQLException e) {
            System.err.println("Erreur findByUtilisateurAndDate: " + e.getMessage());
            e.printStackTrace();
        }

        return ventes;
    }

    /**
     * Charger les détails d'une vente
     */
    public List<DetailVente> findDetailsByVente(int venteId) {
        List<DetailVente> details = new ArrayList<>();
        String sql = "SELECT * FROM detailsvente WHERE id_vente = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, venteId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                details.add(new DetailVente(
                        rs.getInt("id"),
                        rs.getInt("id_vente"),
                        rs.getInt("id_produit"),
                        rs.getInt("quantite"),
                        rs.getBigDecimal("prix_vente_unitaire"),
                        rs.getBigDecimal("prix_achat_unitaire")
                ));
            }

        } catch (SQLException e) {
            System.err.println("Erreur détails vente: " + e.getMessage());
        }

        return details;
    }

    /**
     * Total des recettes sur une période
     */
    public BigDecimal getTotalRecettes(LocalDateTime debut, LocalDateTime fin) {
        String sql = "SELECT SUM(total_vente) FROM ventes WHERE date_vente BETWEEN ? AND ?";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(debut));
            stmt.setTimestamp(2, Timestamp.valueOf(fin));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }

        } catch (SQLException e) {
            System.err.println("Erreur total recettes: " + e.getMessage());
        }

        return BigDecimal.ZERO;
    }

    /**
     * 🔥 Nouvelle méthode : CA (chiffre d'affaires)
     */
    public BigDecimal getCAParPeriode(LocalDateTime debut, LocalDateTime fin) {
        return getTotalRecettes(debut, fin);
    }

    /**
     * 🔥 Nouvelle méthode : nombre total de ventes sur une période
     */
    public int getNombreVentesParPeriode(LocalDateTime debut, LocalDateTime fin) {
        String sql = "SELECT COUNT(*) FROM ventes WHERE date_vente BETWEEN ? AND ?";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(debut));
            stmt.setTimestamp(2, Timestamp.valueOf(fin));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return rs.getInt(1);

        } catch (SQLException e) {
            System.err.println("Erreur nombre ventes: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Profit total sur une période
     */
    public BigDecimal getTotalProfit(LocalDateTime debut, LocalDateTime fin) {
        String sql = """
                SELECT SUM((dv.prix_vente_unitaire - dv.prix_achat_unitaire) * dv.quantite)
                FROM detailsvente dv
                JOIN ventes v ON dv.id_vente = v.id
                WHERE v.date_vente BETWEEN ? AND ?
                """;

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(debut));
            stmt.setTimestamp(2, Timestamp.valueOf(fin));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                BigDecimal profit = rs.getBigDecimal(1);
                return profit != null ? profit : BigDecimal.ZERO;
            }

        } catch (SQLException e) {
            System.err.println("Erreur profit: " + e.getMessage());
        }

        return BigDecimal.ZERO;
    }

    /**
     * Récupère les ventes contenant des produits de tabac (tabac, puff, terrea, etc.)
     * @return Liste des ventes avec produits de tabac
     */
    public List<Vente> findVentesTabac() {
        List<Vente> ventes = new ArrayList<>();
        String sql = """
            SELECT DISTINCT v.* 
            FROM ventes v
            INNER JOIN detailsvente dv ON v.id = dv.id_vente
            INNER JOIN produits p ON dv.id_produit = p.id
            WHERE LOWER(p.categorie) LIKE '%tabac%' 
               OR LOWER(p.categorie) LIKE '%puff%' 
               OR LOWER(p.categorie) LIKE '%terrea%'
               OR LOWER(p.categorie) LIKE '%cigarette%'
               OR (LOWER(p.categorie) LIKE '%frak%' AND LOWER(p.categorie) LIKE '%cigarette%')
            ORDER BY v.date_vente DESC
            """;
        
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des ventes de tabac: " + e.getMessage());
        }
        
        return ventes;
    }
    
    /**
     * Calcule le total des ventes de tabac sur une période
     */
    public BigDecimal getTotalVentesTabac(LocalDateTime debut, LocalDateTime fin) {
        String sql = """
            SELECT SUM(dv.prix_vente_unitaire * dv.quantite)
            FROM detailsvente dv
            INNER JOIN ventes v ON dv.id_vente = v.id
            INNER JOIN produits p ON dv.id_produit = p.id
            WHERE v.date_vente BETWEEN ? AND ?
              AND (LOWER(p.categorie) LIKE '%tabac%' 
               OR LOWER(p.categorie) LIKE '%puff%' 
               OR LOWER(p.categorie) LIKE '%terrea%'
               OR LOWER(p.categorie) LIKE '%cigarette%'
               OR (LOWER(p.categorie) LIKE '%frak%' AND LOWER(p.categorie) LIKE '%cigarette%'))
            """;
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(debut));
            stmt.setTimestamp(2, Timestamp.valueOf(fin));
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal(1);
                return total != null ? total : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors du calcul du total des ventes de tabac: " + e.getMessage());
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Trouve le produit tabac associé à un produit "frak cigarette"
     * Cherche un produit tabac avec un nom similaire (sans "frak")
     * @param conn La connexion à la base de données
     * @param produitFrak Le produit "frak cigarette"
     * @return Le produit tabac associé, ou null si non trouvé
     */
    private Produit trouverProduitTabacAssocie(Connection conn, Produit produitFrak) {
        try {
            String nomFrak = produitFrak.getNom().trim();
            // Enlever "frak" du nom (insensible à la casse)
            String nomRecherche = nomFrak.replaceAll("(?i)\\s*frak\\s*", "").trim();
            
            if (nomRecherche.isEmpty()) {
                return null;
            }
            
            ProduitDAO produitDAO = new ProduitDAO();
            
            // Chercher un produit tabac avec un nom similaire
            // D'abord essayer une correspondance exacte (sans "frak")
            String sql = """
                SELECT p.id
                FROM produits p
                LEFT JOIN categories c ON p.category_id = c.id
                WHERE LOWER(TRIM(p.nom)) = LOWER(?)
                  AND (LOWER(COALESCE(c.nom, p.categorie)) LIKE '%tabac%' 
                       OR LOWER(COALESCE(c.nom, p.categorie)) LIKE '%puff%'
                       OR LOWER(COALESCE(c.nom, p.categorie)) LIKE '%terrea%')
                  AND p.id != ?
                LIMIT 1
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nomRecherche);
                stmt.setInt(2, produitFrak.getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int produitId = rs.getInt("id");
                        return produitDAO.findById(produitId);
                    }
                }
            }
            
            // Si pas trouvé, chercher avec LIKE (nom contient le nom recherché)
            sql = """
                SELECT p.id
                FROM produits p
                LEFT JOIN categories c ON p.category_id = c.id
                WHERE LOWER(TRIM(p.nom)) LIKE LOWER(?)
                  AND (LOWER(COALESCE(c.nom, p.categorie)) LIKE '%tabac%' 
                       OR LOWER(COALESCE(c.nom, p.categorie)) LIKE '%puff%'
                       OR LOWER(COALESCE(c.nom, p.categorie)) LIKE '%terrea%')
                  AND p.id != ?
                LIMIT 1
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "%" + nomRecherche + "%");
                stmt.setInt(2, produitFrak.getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int produitId = rs.getInt("id");
                        return produitDAO.findById(produitId);
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Erreur lors de la recherche du produit tabac associé: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Récupère les meilleurs produits de tabac (par quantité vendue)
     * @param limit Nombre de produits à retourner
     * @return Liste des statistiques des produits (nom, quantité totale, CA total)
     */
    public java.util.List<java.util.Map<String, Object>> getTopProduitsTabac(int limit) {
        java.util.List<java.util.Map<String, Object>> topProduits = new java.util.ArrayList<>();
        String sql = """
            SELECT p.nom, 
                   SUM(dv.quantite) as quantite_totale, 
                   SUM(dv.prix_vente_unitaire * dv.quantite) as ca_total
            FROM detailsvente dv
            INNER JOIN ventes v ON dv.id_vente = v.id
            INNER JOIN produits p ON dv.id_produit = p.id
            WHERE LOWER(p.categorie) LIKE '%tabac%' 
               OR LOWER(p.categorie) LIKE '%puff%' 
               OR LOWER(p.categorie) LIKE '%terrea%'
               OR LOWER(p.categorie) LIKE '%cigarette%'
               OR (LOWER(p.categorie) LIKE '%frak%' AND LOWER(p.categorie) LIKE '%cigarette%')
            GROUP BY p.id, p.nom
            ORDER BY quantite_totale DESC
            LIMIT ?
            """;
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                java.util.Map<String, Object> produit = new java.util.HashMap<>();
                produit.put("nom", rs.getString("nom"));
                produit.put("quantite", rs.getInt("quantite_totale"));
                produit.put("ca", rs.getBigDecimal("ca_total") != null ? rs.getBigDecimal("ca_total") : BigDecimal.ZERO);
                topProduits.add(produit);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des meilleurs produits de tabac: " + e.getMessage());
        }
        
        return topProduits;
    }
    
    /**
     * Mapping ResultSet → Vente
     */
    private Vente mapResultSetToVente(ResultSet rs) throws SQLException {
        LocalDateTime date = rs.getTimestamp("date_vente") != null
                ? rs.getTimestamp("date_vente").toLocalDateTime()
                : null;

        return new Vente(
                rs.getInt("id"),
                date,
                rs.getBigDecimal("total_vente"),
                rs.getInt("id_utilisateur")
        );
    }
}

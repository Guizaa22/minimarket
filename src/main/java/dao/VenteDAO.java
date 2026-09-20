package dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(VenteDAO.class);


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
                LOG.error("Erreur: Impossible d'obtenir l'ID de la vente créée");
                return false;
            }
            vente.setId(venteId);

            // Vérifier qu'il y a des détails AVANT de commencer
            if (vente.getDetails() == null || vente.getDetails().isEmpty()) {
                conn.rollback();
                LOG.error("========================================");
                LOG.error("ERREUR: Aucun détail de vente à enregistrer - le panier est vide");
                LOG.error("========================================");
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
            // unite_vente enregistre si la ligne est en paquets ou en cigarettes.
            // Sans elle, une ligne de 7 « Marlboro » est ambiguë au rechargement,
            // et les statistiques tabac comptaient 7 cigarettes comme 7 paquets.
            String sqlDetail = "INSERT INTO detailsvente "
                             + "(id_vente, id_produit, quantite, prix_vente_unitaire, "
                             + " prix_achat_unitaire, unite_vente) "
                             + "VALUES (?, ?, ?, ?, ?, ?)";
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
                            // Comparé en paquets, unité dans laquelle le stock
                            // est tenu — pas en cigarettes.
                            int requis = paquetsConsommes(d);
                            if (stockActuel < requis) {
                                // Exception métier : le contrôleur peut afficher
                                // un message utile sans analyser un texte SQL.
                                throw new exception.StockInsuffisantException(
                                        produitVendu.getNom(), stockActuel, requis);
                            }
                        }
                    }
                }

                stmtDetail.setInt(1, venteId);
                stmtDetail.setInt(2, d.getProduitId());
                stmtDetail.setInt(3, d.getQuantite());
                stmtDetail.setBigDecimal(4, d.getPrixVenteUnitaire());
                stmtDetail.setBigDecimal(5, d.getPrixAchatUnitaire());
                stmtDetail.setString(6, d.getTypeVenteTabac() != null
                        ? d.getTypeVenteTabac() : "unite");
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
                            int paquetsADecrémenter =
                                    (totalCigarettes + model.TypeCategorie.CIGARETTES_PAR_PAQUET - 1) / model.TypeCategorie.CIGARETTES_PAR_PAQUET;
                            
                            if (paquetsADecrémenter > 0) {
                                // Vérifier le stock du produit tabac associé
                                try (PreparedStatement checkStockTabac = conn.prepareStatement("SELECT quantite_stock FROM produits WHERE id = ?")) {
                                    checkStockTabac.setInt(1, produitTabacAssocie.getId());
                                    try (ResultSet rs = checkStockTabac.executeQuery()) {
                                        if (rs.next()) {
                                            int stockTabac = rs.getInt("quantite_stock");
                                            if (stockTabac < paquetsADecrémenter) {
                                                throw new exception.StockInsuffisantException(
                                                        produitTabacAssocie.getNom() + " (paquets)",
                                                        stockTabac, paquetsADecrémenter);
                                            }
                                        }
                                    }
                                }
                                
                                // Décrémenter le stock du produit tabac associé
                                stmtStock.setInt(1, paquetsADecrémenter);
                                stmtStock.setInt(2, produitTabacAssocie.getId());
                                stmtStock.setInt(3, paquetsADecrémenter);
                                stmtStock.addBatch();
                                
                                LOG.info("✓ Frak cigarette: " + totalCigarettes + " cigarettes vendues -> " + paquetsADecrémenter + " paquet(s) décrémenté(s) du produit '" + produitTabacAssocie.getNom() + "'");
                            }
                        } else {
                            LOG.error("⚠ ATTENTION: Produit tabac associé ID " + produitTabacAssocieId + " introuvable pour le produit 'frak cigarette' '" + produitVendu.getNom() + "'.");
                        }
                    } else {
                        LOG.error("⚠ ATTENTION: Produit 'frak cigarette' '" + produitVendu.getNom() + "' vendu, mais aucun produit tabac associé spécifié. Le stock du produit tabac ne sera pas décrémenté.");
                    }
                } else {
                    // Le stock est tenu en paquets. Une vente à la cigarette
                    // n'en consomme donc pas autant d'unités que de cigarettes :
                    // 7 cigarettes entament 1 paquet, 25 en entament 2.
                    // Sans cette conversion, vendre 7 cigarettes retirait
                    // 7 paquets du stock.
                    int unitesADecrementer = paquetsConsommes(d);

                    stmtStock.setInt(1, unitesADecrementer);
                    stmtStock.setInt(2, d.getProduitId());
                    stmtStock.setInt(3, unitesADecrementer); // Vérification dans WHERE
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
            try (PreparedStatement verifyStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM detailsvente WHERE id_vente = ?")) {
                verifyStmt.setInt(1, venteId);
                try (ResultSet rs = verifyStmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        if (count != vente.getDetails().size()) {
                            LOG.error("ATTENTION: Nombre de détails sauvegardés (" + count + ") ne correspond pas au nombre attendu (" + vente.getDetails().size() + ")");
                        } else {
                            LOG.info("✓ Vente sauvegardée avec succès: ID=" + venteId + ", Détails=" + count);
                        }
                    }
                }
            }
            
            return true;

        } catch (exception.ApplicationException e) {
            // Erreur métier (stock insuffisant...) : on annule et on laisse
            // remonter. Elle porte un message destiné au caissier ; l'avaler
            // ici ferait échouer la vente sans aucune explication à l'écran.
            LOG.warn("Vente annulée : {}", e.getMessage());
            rollbackSilencieux(conn);
            throw e;

        } catch (SQLException e) {
            LOG.error("Échec de création de la vente (utilisateur={}, total={}, {} détail(s))",
                    vente != null ? vente.getUtilisateurId() : "null",
                    vente != null ? vente.getTotalVente() : "null",
                    vente != null && vente.getDetails() != null ? vente.getDetails().size() : 0,
                    e);
            if (vente != null && vente.getDetails() != null) {
                for (DetailVente d : vente.getDetails()) {
                    LOG.debug("  détail : produit={} qte={} pv={} pa={}",
                            d.getProduitId(), d.getQuantite(),
                            d.getPrixVenteUnitaire(), d.getPrixAchatUnitaire());
                }
            }
            rollbackSilencieux(conn);
            throw new exception.DatabaseException("Échec de l'enregistrement de la vente", e);

        } catch (Exception e) {
            LOG.error("Erreur inattendue lors de la création de la vente", e);
            rollbackSilencieux(conn);
            throw new exception.DatabaseException(
                    "Erreur inattendue lors de l'enregistrement de la vente", e);

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
                LOG.error("Erreur lors de la fermeture des ressources", e);
            }
        }
    }

    /**
     * Nombre d'unités de stock consommées par une ligne de vente.
     *
     * Le stock des produits de tabac est tenu en paquets. Une ligne vendue à la
     * cigarette consomme donc le nombre de paquets entamés, arrondi au
     * supérieur : 7 cigarettes entament 1 paquet, 25 en entament 2. Pour toute
     * autre ligne, la quantité vendue est directement l'unité de stock.
     */
    private int paquetsConsommes(DetailVente detail) {
        if (!"cigarette".equals(detail.getTypeVenteTabac())) {
            return detail.getQuantite();
        }
        int parPaquet = model.TypeCategorie.CIGARETTES_PAR_PAQUET;
        return (detail.getQuantite() + parPaquet - 1) / parPaquet;
    }

    /**
     * Annule la transaction en cours sans masquer l'erreur d'origine.
     * Un échec de rollback est journalisé mais ne remplace pas l'exception
     * qui a conduit à l'annulation.
     */
    private void rollbackSilencieux(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            if (!conn.isClosed()) {
                conn.rollback();
                LOG.debug("Transaction annulée");
            }
        } catch (SQLException e) {
            LOG.error("Échec du rollback", e);
        }
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
            LOG.error("Erreur findAll: " + e.getMessage(), e);
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
            LOG.error("Erreur findRecent: " + e.getMessage(), e);
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
            LOG.error("Erreur findByUtilisateur: " + e.getMessage(), e);
        }

        return ventes;
    }
    
    /**
     * Ventes par utilisateur pour une date spécifique
     */
    /**
     * Ventes d'une journée, tous employés confondus.
     * Utilisée par le tableau de bord administrateur, qui doit voir l'activité
     * du magasin et non celle d'un seul poste.
     */
    public List<Vente> findByDate(LocalDateTime jour) {
        List<Vente> ventes = new ArrayList<>();
        String sql = "SELECT * FROM ventes WHERE " + DayRange.where("date_vente")
                   + " ORDER BY date_vente DESC";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            DayRange.bind(stmt, 1, jour);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ventes.add(mapResultSetToVente(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Lecture des ventes du jour impossible", e);
            throw new exception.DatabaseException("Impossible de charger les ventes du jour", e);
        }
        return ventes;
    }

    public List<Vente> findByUtilisateurAndDate(int utilisateurId, LocalDateTime dateDebut, LocalDateTime dateFin) {
        List<Vente> ventes = new ArrayList<>();
        // Intervalle semi-ouvert [jour de début, lendemain du jour de fin[ : couvre
        // toute la plage demandée et reste compatible index, contrairement à
        // DATE(date_vente) qui empêchait toute utilisation de l'index.
        String sql = "SELECT * FROM ventes WHERE id_utilisateur = ? AND "
                   + DayRange.where("date_vente") + " ORDER BY date_vente DESC";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, utilisateurId);
            DayRange.bindRange(stmt, 2, dateDebut, dateFin);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }

            LOG.info("findByUtilisateurAndDate: {} vente(s) pour l'utilisateur {} du {} au {}",
                    ventes.size(), utilisateurId, dateDebut.toLocalDate(), dateFin.toLocalDate());

        } catch (SQLException e) {
            LOG.error("Erreur findByUtilisateurAndDate: " + e.getMessage(), e);
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
                DetailVente detail = new DetailVente(
                        rs.getInt("id"),
                        rs.getInt("id_vente"),
                        rs.getInt("id_produit"),
                        rs.getInt("quantite"),
                        rs.getBigDecimal("prix_vente_unitaire"),
                        rs.getBigDecimal("prix_achat_unitaire")
                );

                // « unite » correspond à un produit ordinaire : on laisse null
                // pour que les traitements tabac ne s'y appliquent pas.
                String unite = rs.getString("unite_vente");
                if (unite != null && !"unite".equals(unite)) {
                    detail.setTypeVenteTabac(unite);
                }
                details.add(detail);
            }

        } catch (SQLException e) {
            LOG.error("Erreur détails vente: " + e.getMessage(), e);
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
            LOG.error("Erreur total recettes: " + e.getMessage(), e);
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
            LOG.error("Erreur nombre ventes: " + e.getMessage(), e);
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
            LOG.error("Erreur profit: " + e.getMessage(), e);
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
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE (c.type IN ('Tabac', 'FrakCigarette')
                   OR (c.type IS NULL AND (
                          LOWER(p.categorie) LIKE '%tabac%'
                       OR LOWER(p.categorie) LIKE '%puff%'
                       OR LOWER(p.categorie) LIKE '%terrea%'
                       OR LOWER(p.categorie) LIKE '%cigarette%')))
            ORDER BY v.date_vente DESC
            """;
        
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des ventes de tabac: " + e.getMessage(), e);
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
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE v.date_vente BETWEEN ? AND ?
              AND (c.type IN ('Tabac', 'FrakCigarette')
                   OR (c.type IS NULL AND (
                          LOWER(p.categorie) LIKE '%tabac%'
                       OR LOWER(p.categorie) LIKE '%puff%'
                       OR LOWER(p.categorie) LIKE '%terrea%'
                       OR LOWER(p.categorie) LIKE '%cigarette%')))
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
            LOG.error("Erreur lors du calcul du total des ventes de tabac: " + e.getMessage(), e);
        }
        
        return BigDecimal.ZERO;
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
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE (c.type IN ('Tabac', 'FrakCigarette')
                   OR (c.type IS NULL AND (
                          LOWER(p.categorie) LIKE '%tabac%'
                       OR LOWER(p.categorie) LIKE '%puff%'
                       OR LOWER(p.categorie) LIKE '%terrea%'
                       OR LOWER(p.categorie) LIKE '%cigarette%')))
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
            LOG.error("Erreur lors de la récupération des meilleurs produits de tabac: " + e.getMessage(), e);
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

package dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import model.Produit;

/**
 * DAO pour les opérations CRUD sur la table Produits
 */
public class ProduitDAO {
    private static final Logger LOG = LoggerFactory.getLogger(ProduitDAO.class);


    /**
     * Projection commune des produits.
     *
     * La catégorie affichée provient de la table {@code categories} lorsqu'un
     * {@code category_id} est renseigné, et retombe sur l'ancienne colonne texte
     * sinon. Toutes les lectures doivent passer par là : sans cela, une méthode
     * renvoyant la colonne brute et une autre la valeur résolue classent le même
     * produit différemment.
     */
    private static final String SELECT_PRODUIT =
            "SELECT p.*, COALESCE(c.nom, p.categorie) AS categorie_affiche, "
          + "c.type AS categorie_type "
          + "FROM produits p LEFT JOIN categories c ON c.id = p.category_id";


    /**
     * Récupère tous les produits
     * @return Liste de tous les produits
     */
    public List<Produit> findAll() {
        List<Produit> produits = new ArrayList<>();

        // Projection commune : charge aussi categories.type, dont dépend
        // findProduitsTabac() via isTabac().
        String sql = SELECT_PRODUIT + " ORDER BY p.nom";

        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                produits.add(mapResultSetToProduit(rs));
            }
        } catch (SQLException e) {
            // Plus de repli sur une requête sans jointure : elle renvoyait les
            // produits sans categories.type, donc mal classés vis-à-vis du tabac,
            // en masquant au passage la véritable cause de l'échec.
            LOG.error("Récupération des produits impossible", e);
            throw new exception.DatabaseException("Impossible de charger les produits", e);
        }

        return produits;
    }
    
    /**
     * Récupère un produit par son ID
     * @param id L'ID du produit
     * @return Le produit trouvé, null sinon
     */
    public Produit findById(int id) {
        // La catégorie est résolue via la table categories, comme dans findAll().
        // Avec un simple "SELECT *", cette méthode renvoyait la colonne texte brute :
        // après le renommage d'une catégorie, la caisse (qui passe par findById)
        // classait le produit autrement que la liste des produits, ce qui faussait
        // la logique tabac / frak cigarette lors de l'encaissement.
        String sql = SELECT_PRODUIT + " WHERE p.id = ?";

        // La connexion vient d'un pool : elle doit être rendue après usage.
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProduit(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la recherche de produit: " + e.getMessage(), e);
        }

        return null;
    }
    
    /**
     * Récupère un produit par son code-barres
     * @param codeBarre Le code-barres du produit
     * @return Le produit trouvé, null sinon
     */
    public Produit findByCodeBarre(String codeBarre) {
        String sql = SELECT_PRODUIT + " WHERE p.code_barre = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, codeBarre);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToProduit(rs);
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la recherche de produit par code-barres: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Récupère un produit par son nom (recherche exacte)
     * @param nom Le nom du produit
     * @return Le produit trouvé, null sinon
     */
    public Produit findByNomExact(String nom) {
        String sql = SELECT_PRODUIT + " WHERE p.nom = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, nom);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToProduit(rs);
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la recherche de produit par nom: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Recherche intelligente : essaie d'abord par code-barres, puis par nom exact
     * @param recherche Le terme de recherche (code-barres ou nom)
     * @return Le produit trouvé, null sinon
     */
    public Produit rechercherProduit(String recherche) {
        // Essayer d'abord par code-barres
        Produit produit = findByCodeBarre(recherche);
        if (produit != null) {
            return produit;
        }
        
        // Ensuite par nom exact
        produit = findByNomExact(recherche);
        return produit;
    }
    
    /**
     * Récupère les produits avec stock faible
     * @return Liste des produits avec stock faible
     */
    public List<Produit> findStockFaible() {
        List<Produit> produits = new ArrayList<>();
        String sql = SELECT_PRODUIT
                   + " WHERE p.quantite_stock <= p.seuil_alerte ORDER BY p.quantite_stock ASC";

        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                produits.add(mapResultSetToProduit(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des produits à stock faible: " + e.getMessage(), e);
        }
        
        return produits;
    }
    
    /**
     * Crée un nouveau produit
     * @param produit Le produit à créer
     * @return true si la création réussit, false sinon
     */
    public boolean create(Produit produit) {
        // Requête fixe : le schéma garantit la présence de category_id et unite.
        // Elle était auparavant assemblée dynamiquement après deux appels à
        // columnExists(), soit deux interrogations d'information_schema avant
        // chaque insertion, pour gérer d'anciennes bases qui n'existent plus.
        String sql = "INSERT INTO produits "
                   + "(code_barre, nom, categorie, category_id, prix_achat_actuel, "
                   + " prix_vente_defaut, quantite_stock, unite, seuil_alerte, "
                   + " prix_vente_cigarette, image, image_mime) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, produit.getCodeBarre());
            stmt.setString(2, produit.getNom());
            stmt.setString(3, produit.getCategorie() != null ? produit.getCategorie() : "");

            Integer categoryId = findCategoryIdByName(conn, produit.getCategorie());
            if (categoryId != null) {
                stmt.setInt(4, categoryId);
            } else {
                stmt.setNull(4, java.sql.Types.INTEGER);
            }

            stmt.setBigDecimal(5, produit.getPrixAchatActuel());
            stmt.setBigDecimal(6, produit.getPrixVenteDefaut());
            stmt.setInt(7, produit.getQuantiteStock());
            stmt.setString(8, produit.getUnite() != null ? produit.getUnite() : "unité");
            stmt.setInt(9, produit.getSeuilAlerte());
            lierPrixCigaretteEtImage(stmt, 10, produit);

            LOG.debug("Création du produit {} ({})", produit.getNom(), produit.getCodeBarre());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        produit.setId(rs.getInt(1));
                        LOG.info("Produit créé : {} (id={})", produit.getNom(), produit.getId());
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            LOG.error("========================================");
            LOG.error("ERREUR lors de la création de produit:");
            LOG.error("Message: " + e.getMessage(), e);
            LOG.error("Code SQL: " + e.getSQLState());
            LOG.error("Code erreur SGBD: " + e.getErrorCode());
            LOG.error("SQL: " + sql);
            LOG.error("Produit: " + produit);
            LOG.error("========================================");
        }
        
        return false;
    }
    
    /**
     * Met à jour un produit
     * @param produit Le produit à mettre à jour
     * @return true si la mise à jour réussit, false sinon
     */
    public boolean update(Produit produit) {
        // Requête fixe : voir le commentaire de create().
        String sql = "UPDATE produits SET code_barre = ?, nom = ?, categorie = ?, "
                   + "category_id = ?, prix_achat_actuel = ?, prix_vente_defaut = ?, "
                   + "quantite_stock = ?, unite = ?, seuil_alerte = ?, "
                   + "prix_vente_cigarette = ?, image = ?, image_mime = ?, "
                   + "date_derniere_maj = CURRENT_TIMESTAMP "
                   + "WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, produit.getCodeBarre());
            stmt.setString(2, produit.getNom());
            stmt.setString(3, produit.getCategorie() != null ? produit.getCategorie() : "");

            Integer categoryId = findCategoryIdByName(conn, produit.getCategorie());
            if (categoryId != null) {
                stmt.setInt(4, categoryId);
            } else {
                stmt.setNull(4, java.sql.Types.INTEGER);
            }

            stmt.setBigDecimal(5, produit.getPrixAchatActuel());
            stmt.setBigDecimal(6, produit.getPrixVenteDefaut());
            stmt.setInt(7, produit.getQuantiteStock());
            stmt.setString(8, produit.getUnite() != null ? produit.getUnite() : "unité");
            stmt.setInt(9, produit.getSeuilAlerte());
            lierPrixCigaretteEtImage(stmt, 10, produit);
            stmt.setInt(13, produit.getId());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOG.info("Produit mis à jour : {} (id={})", produit.getNom(), produit.getId());
                return true;
            }
        } catch (SQLException e) {
            LOG.error("========================================");
            LOG.error("ERREUR lors de la mise à jour de produit:");
            LOG.error("Message: " + e.getMessage(), e);
            LOG.error("Code SQL: " + e.getSQLState());
            LOG.error("Code erreur SGBD: " + e.getErrorCode());
            LOG.error("SQL: " + sql);
            LOG.error("Produit ID: " + produit.getId());
            LOG.error("========================================");
        }
        
        return false;
    }
    
    /**
     * Vérifie si un produit est utilisé dans des ventes ou ajouts de stock
     * @param id L'ID du produit
     * @return true si le produit est utilisé, false sinon
     */
    public boolean isProduitUtilise(int id) {
        try (Connection conn = DBConnector.getConnection()) {
            return isProduitUtilise(conn, id);
        } catch (SQLException e) {
            LOG.error("Erreur lors de la vérification d'utilisation du produit: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Vérifie si un produit est utilisé dans des ventes ou ajouts de stock (avec connexion fournie)
     * @param conn La connexion à utiliser
     * @param id L'ID du produit
     * @return true si le produit est utilisé, false sinon
     */
    private boolean isProduitUtilise(Connection conn, int id) throws SQLException {
        // Vérifier dans detailsvente
        String sqlDetails = "SELECT COUNT(*) FROM detailsvente WHERE id_produit = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sqlDetails)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return true;
                }
            }
        }
        
        // Vérifier dans ajouts_stock
        String sqlAjouts = "SELECT COUNT(*) FROM ajouts_stock WHERE produit_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sqlAjouts)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Supprime un produit
     * @param id L'ID du produit à supprimer
     * @param forceDelete Si true, supprime même si le produit est utilisé (admin uniquement)
     * @return true si la suppression réussit, false sinon
     * @throws SQLException si le produit est utilisé dans des ventes ou ajouts de stock (sauf si forceDelete = true)
     */
    public boolean delete(int id, boolean forceDelete) throws SQLException {
        // Ne pas utiliser try-with-resources car la connexion est un singleton partagé
        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);
            
            // Si forceDelete est activé (admin), supprimer d'abord les références
            if (forceDelete) {
                // Supprimer les détails de vente associés
                String sqlDeleteDetails = "DELETE FROM detailsvente WHERE id_produit = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sqlDeleteDetails)) {
                    stmt.setInt(1, id);
                    int detailsDeleted = stmt.executeUpdate();
                    if (detailsDeleted > 0) {
                        LOG.info("✓ " + detailsDeleted + " détail(s) de vente supprimé(s) pour le produit ID " + id);
                    }
                }
                
                // Supprimer les ajouts de stock associés
                String sqlDeleteAjouts = "DELETE FROM ajouts_stock WHERE produit_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sqlDeleteAjouts)) {
                    stmt.setInt(1, id);
                    int ajoutsDeleted = stmt.executeUpdate();
                    if (ajoutsDeleted > 0) {
                        LOG.info("✓ " + ajoutsDeleted + " ajout(s) de stock supprimé(s) pour le produit ID " + id);
                    }
                }
            } else {
                // Vérifier d'abord si le produit est utilisé (utiliser la même connexion)
                if (isProduitUtilise(conn, id)) {
                    conn.rollback();
                    throw new SQLException("Impossible de supprimer ce produit car il est utilisé dans des ventes ou des ajouts de stock.");
                }
            }
            
            // Supprimer le produit
            String sql = "DELETE FROM produits WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    conn.commit();
                    LOG.info("✓ Produit ID " + id + " supprimé avec succès" + (forceDelete ? " (suppression forcée)" : ""));
                    return true;
                } else {
                    conn.rollback();
                    LOG.error("✗ Aucun produit trouvé avec l'ID " + id);
                    return false;
                }
            }
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    LOG.error("Erreur lors du rollback: " + rollbackEx.getMessage(), rollbackEx);
                }
            }
            LOG.error("========================================");
            LOG.error("ERREUR lors de la suppression de produit:");
            LOG.error("Message: " + e.getMessage(), e);
            LOG.error("Code SQL: " + e.getSQLState());
            LOG.error("Code erreur SGBD: " + e.getErrorCode());
            LOG.error("Produit ID: " + id);
            LOG.error("Force Delete: " + forceDelete);
            LOG.error("========================================");
            throw e; // Re-lancer l'exception pour que le contrôleur puisse l'afficher
        } finally {
            // Restaurer autoCommit puis rendre la connexion au pool.
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    LOG.error("Erreur lors de la réinitialisation de autoCommit: " + e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * Supprime un produit (méthode de compatibilité, sans forceDelete)
     * @param id L'ID du produit à supprimer
     * @return true si la suppression réussit, false sinon
     * @throws SQLException si le produit est utilisé dans des ventes ou ajouts de stock
     */
    public boolean delete(int id) throws SQLException {
        return delete(id, false);
    }
    
    /**
     * Augmente le stock d'un produit de façon atomique.
     *
     * À préférer à {@link #updateStock(int, int)} : l'incrément est calculé par la
     * base et non à partir d'une quantité lue au préalable en mémoire, ce qui
     * évite d'écraser une vente encaissée entre-temps.
     *
     * @param produitId L'ID du produit
     * @param delta     Quantité à ajouter (peut être négative pour retirer)
     * @return true si une ligne a été mise à jour
     */
    public boolean augmenterStock(int produitId, int delta) {
        try (Connection conn = DBConnector.getConnection()) {
            return augmenterStock(conn, produitId, delta);
        } catch (SQLException e) {
            LOG.error("✗ Erreur lors de la mise à jour du stock : " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Fait varier le stock sur une connexion fournie par l'appelant, afin que
     * l'incrément tienne dans la même transaction que la trace de l'ajout et
     * le mouvement de crédit fournisseur.
     *
     * La connexion n'est ni validée ni fermée ici.
     *
     * @throws SQLException pour laisser l'appelant annuler la transaction
     */
    public boolean augmenterStock(Connection conn, int produitId, int delta) throws SQLException {
        String sql = "UPDATE produits SET quantite_stock = quantite_stock + ?, "
                   + "date_derniere_maj = CURRENT_TIMESTAMP "
                   + "WHERE id = ? AND quantite_stock + ? >= 0";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, delta);
            stmt.setInt(2, produitId);
            stmt.setInt(3, delta);

            if (stmt.executeUpdate() > 0) {
                return true;
            }
            LOG.error("✗ Stock non modifié pour le produit ID " + produitId
                    + " (produit introuvable ou stock insuffisant pour un retrait de " + (-delta) + ")");
            return false;
        }
    }

    /**
     * Fixe la quantité en stock d'un produit à une valeur absolue.
     * Réservé à la correction manuelle d'inventaire ; pour un réapprovisionnement,
     * utiliser {@link #augmenterStock(int, int)}.
     *
     * @param produitId L'ID du produit
     * @param quantite La nouvelle quantité
     * @return true si la mise à jour réussit, false sinon
     */
    public boolean updateStock(int produitId, int quantite) {
        String sql = "UPDATE produits SET quantite_stock = ? WHERE id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, quantite);
            stmt.setInt(2, produitId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Vérifier que la mise à jour a bien été effectuée
                try (PreparedStatement verifyStmt = conn.prepareStatement("SELECT quantite_stock FROM produits WHERE id = ?")) {
                    verifyStmt.setInt(1, produitId);
                    try (ResultSet rs = verifyStmt.executeQuery()) {
                        if (rs.next()) {
                            int stockVerifie = rs.getInt("quantite_stock");
                            if (stockVerifie == quantite) {
                                LOG.info("✓ Stock mis à jour: Produit ID=" + produitId + ", Nouveau stock=" + quantite);
                                return true;
                            } else {
                                LOG.error("✗ Erreur: Stock vérifié (" + stockVerifie + ") ne correspond pas au stock attendu (" + quantite + ")");
                                return false;
                            }
                        } else {
                            LOG.error("✗ Erreur: Produit ID " + produitId + " introuvable après mise à jour");
                            return false;
                        }
                    }
                }
            } else {
                LOG.error("✗ Erreur: Aucune ligne mise à jour pour produit ID " + produitId);
                return false;
            }
        } catch (SQLException e) {
            LOG.error("✗ Erreur lors de la mise à jour du stock: " + e.getMessage(), e);
            LOG.error("  Produit ID: " + produitId);
            LOG.error("  Nouvelle quantité: " + quantite);
            return false;
        }
    }
    
    /**
     * Vérifie si un code-barres existe déjà
     * @param codeBarre Le code-barres à vérifier
     * @return true si le code-barres existe, false sinon
     */
    public boolean codeBarreExists(String codeBarre) {
        String sql = "SELECT COUNT(*) FROM produits WHERE code_barre = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, codeBarre);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la vérification du code-barres: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Récupère le nom de toutes les catégories.
     *
     * La table {@code categories} fait référence : une catégorie qui vient d'être
     * créée doit apparaître immédiatement, même si aucun produit ne lui est encore
     * rattaché. L'ancienne version partait des produits
     * ({@code categories INNER JOIN produits}), si bien qu'une catégorie vide
     * restait invisible côté caisse tant qu'on ne lui avait pas ajouté un produit.
     *
     * Les valeurs de l'ancienne colonne texte {@code produits.categorie} sont
     * ajoutées ensuite, pour les produits importés dont la catégorie n'a pas
     * d'équivalent dans la table.
     *
     * @return Liste triée des noms de catégories
     */
    public List<String> findAllCategories() {
        java.util.Set<String> noms = new java.util.LinkedHashSet<>();

        // 1. Référentiel : toutes les catégories déclarées, y compris les vides.
        String sqlCategories = "SELECT nom FROM categories ORDER BY nom";
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlCategories)) {

            while (rs.next()) {
                String nom = rs.getString("nom");
                if (nom != null && !nom.isBlank()) {
                    noms.add(nom.trim());
                }
            }
        } catch (SQLException e) {
            LOG.error("Lecture de la table categories impossible : " + e.getMessage(), e);
        }

        // 2. Rattrapage : catégories présentes uniquement dans la colonne texte.
        String sqlTexte = "SELECT DISTINCT TRIM(categorie) AS categorie FROM produits "
                        + "WHERE categorie IS NOT NULL AND TRIM(categorie) <> '' "
                        + "ORDER BY 1";
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlTexte)) {

            while (rs.next()) {
                String nom = rs.getString("categorie");
                if (nom != null && !nom.isBlank()) {
                    noms.add(nom.trim());
                }
            }
        } catch (SQLException e) {
            LOG.error("Lecture des catégories depuis produits impossible : " + e.getMessage(), e);
        }

        List<String> categories = new ArrayList<>(noms);
        categories.sort(String.CASE_INSENSITIVE_ORDER);

        LOG.info("✓ " + categories.size() + " catégorie(s) : " + categories);
        return categories;
    }
    
    /**
     * Récupère les produits d'une catégorie (par nom de catégorie)
     * Affiche TOUS les produits de la catégorie (même avec stock 0)
     * @param categorieNom Le nom de la catégorie
     * @return Liste des produits de la catégorie
     */
    public List<Produit> findByCategorie(String categorieNom) {
        List<Produit> produits = new ArrayList<>();
        
        if (categorieNom == null || categorieNom.trim().isEmpty()) {
            LOG.error("Avertissement: Nom de catégorie vide ou null");
            return produits;
        }
        
        String categorieTrim = categorieNom.trim();
        LOG.info("Recherche de produits pour catégorie: '" + categorieTrim + "'");
        
        // Essayer d'abord avec la table categories (via category_id)
        try {
            String sql = "SELECT p.*, COALESCE(c.nom, p.categorie) AS categorie_affiche FROM produits p " +
                       "LEFT JOIN categories c ON p.category_id = c.id " +
                       "WHERE (c.nom = ? OR p.categorie = ?) ORDER BY p.nom";
            
            try (Connection conn = DBConnector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, categorieTrim);
                stmt.setString(2, categorieTrim);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    produits.add(mapResultSetToProduit(rs));
                }
                
                if (!produits.isEmpty()) {
                    LOG.info("✓ Trouvé " + produits.size() + " produit(s) via JOIN pour catégorie: " + categorieTrim);
                return produits;
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur avec requête JOIN (peut être normal si table categories n'existe pas): " + e.getMessage(), e);
        }
        
        // Si aucune méthode avec JOIN n'a fonctionné, utiliser l'ancienne méthode avec juste la colonne categorie
        // Recherche insensible à la casse et avec trim
        String sql = SELECT_PRODUIT + " WHERE LOWER(TRIM(COALESCE(c.nom, p.categorie))) = LOWER(?) ORDER BY p.nom";
            
            try (Connection conn = DBConnector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
            stmt.setString(1, categorieTrim);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    produits.add(mapResultSetToProduit(rs));
                }
            
            LOG.info("✓ Trouvé " + produits.size() + " produit(s) via colonne categorie pour: " + categorieTrim);
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des produits par catégorie: " + e.getMessage(), e);
        }
        
        return produits;
    }
    
    /**
     * Récupère les produits d'une catégorie (par ID de catégorie).
     *
     * Renvoie tous les produits, y compris ceux à stock nul, comme
     * {@link #findByCategorie(String)}. Cette méthode filtrait auparavant sur
     * {@code quantite_stock > 0} : une même catégorie affichait donc un contenu
     * différent selon qu'on l'ouvrait par son nom ou par son identifiant, et les
     * produits en rupture disparaissaient de l'inventaire sans explication.
     *
     * @param categoryId L'ID de la catégorie
     * @return Liste des produits de la catégorie
     */
    public List<Produit> findByCategoryId(int categoryId) {
        List<Produit> produits = new ArrayList<>();
        String sql = "SELECT p.*, COALESCE(c.nom, p.categorie) AS categorie_affiche "
                   + "FROM produits p LEFT JOIN categories c ON c.id = p.category_id "
                   + "WHERE p.category_id = ? ORDER BY p.nom";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    produits.add(mapResultSetToProduit(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des produits par category_id: " + e.getMessage(), e);
        }

        return produits;
    }
    
    /**
     * Récupère tous les produits appartenant à la catégorie Tabac (et variantes)
     * Cette méthode filtre côté Java pour garantir la compatibilité avec les bases anciennes.
     *
     * @return Liste des produits considérés comme tabac (tabac, puff, terrea, cigarette)
     */
    public List<Produit> findProduitsTabac() {
        List<Produit> produitsTabac = new ArrayList<>();
        
        for (Produit produit : findAll()) {
            if (produit != null && produit.isTabac()) {
                produitsTabac.add(produit);
            }
        }
        
        return produitsTabac;
    }
    
    /**
     * Renseigne prix cigarette, image et type MIME à partir de l'index donné.
     * Regroupé ici pour que create() et update() ne divergent pas.
     */
    private void lierPrixCigaretteEtImage(PreparedStatement stmt, int index, Produit produit)
            throws SQLException {
        if (produit.getPrixVenteCigarette() != null) {
            stmt.setBigDecimal(index, produit.getPrixVenteCigarette());
        } else {
            stmt.setNull(index, java.sql.Types.NUMERIC);
        }

        if (produit.hasImage()) {
            stmt.setBytes(index + 1, produit.getImage());
            stmt.setString(index + 2, produit.getImageMime());
        } else {
            stmt.setNull(index + 1, java.sql.Types.BINARY);
            stmt.setNull(index + 2, java.sql.Types.VARCHAR);
        }
    }

    private Produit mapResultSetToProduit(ResultSet rs) throws SQLException {
        // La base de données utilise snake_case
        String categorie = null;
        try {
            categorie = rs.getString("categorie_affiche");
        } catch (SQLException ignored) { }
        if (categorie == null) {
            try {
                categorie = rs.getString("categorie");
            } catch (SQLException ignored) { }
        }
        
        String unite = null;
        try {
            unite = rs.getString("unite");
        } catch (SQLException e) {
            // Colonne unite peut ne pas exister dans certaines bases
        }
        
        Produit produit = new Produit(
            rs.getInt("id"),
            rs.getString("code_barre"),
            rs.getString("nom"),
            categorie != null ? categorie : "",
            rs.getBigDecimal("prix_achat_actuel"),
            rs.getBigDecimal("prix_vente_defaut"),
            rs.getInt("quantite_stock"),
            unite,
            rs.getInt("seuil_alerte")
        );

        // Photo et prix cigarette : absents des projections qui ne les
        // sélectionnent pas, d'où la lecture défensive.
        try {
            produit.setImage(rs.getBytes("image"));
            produit.setImageMime(rs.getString("image_mime"));
        } catch (SQLException ignored) {
            // colonne absente de cette projection
        }
        try {
            produit.setPrixVenteCigarette(rs.getBigDecimal("prix_vente_cigarette"));
        } catch (SQLException ignored) {
            // colonne absente de cette projection
        }

        // Type explicite issu du référentiel. Absent (produit sans catégorie ou
        // requête sans jointure), Produit retombe sur la déduction par libellé.
        try {
            String type = rs.getString("categorie_type");
            if (type != null) {
                produit.setTypeCategorie(model.TypeCategorie.depuisBase(type));
            }
        } catch (SQLException ignored) {
            // colonne absente de cette projection
        }

        return produit;
    }
    
    /**
     * Trouve l'ID d'une catégorie par son nom
     * @param categoryName Le nom de la catégorie
     * @return L'ID de la catégorie, ou null si non trouvée
     */
    private Integer findCategoryIdByName(Connection conn, String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }
        
        String sql = "SELECT id FROM categories WHERE nom = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, categoryName.trim());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            // La table categories n'existe peut-être pas, c'est OK
            LOG.error("Impossible de trouver la catégorie: " + e.getMessage(), e);
        }
        
        return null;
    }
}


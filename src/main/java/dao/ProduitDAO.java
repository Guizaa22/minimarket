package dao;

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
    
    /**
     * Récupère tous les produits
     * @return Liste de tous les produits
     */
    public List<Produit> findAll() {
        List<Produit> produits = new ArrayList<>();
        
        // Essayer d'abord avec la table categories (si elle existe)
        String sql = """
            SELECT p.*, COALESCE(c.nom, p.categorie) AS categorie_affiche
            FROM produits p
            LEFT JOIN categories c ON p.category_id = c.id
            ORDER BY p.nom
        """;
        
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                produits.add(mapResultSetToProduit(rs));
            }
        } catch (SQLException e) {
            // Si la requête avec JOIN échoue (table categories n'existe pas ou category_id n'existe pas),
            // essayer une requête simple sans JOIN
            System.err.println("Erreur lors de la récupération des produits (tentative avec JOIN): " + e.getMessage());
            System.err.println("Tentative avec requête simple...");
            
            try {
                sql = "SELECT *, categorie AS categorie_affiche FROM produits ORDER BY nom";
                try (Connection conn = DBConnector.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    
                    while (rs.next()) {
                        produits.add(mapResultSetToProduit(rs));
                    }
                }
            } catch (SQLException e2) {
                System.err.println("Erreur lors de la récupération des produits (requête simple): " + e2.getMessage());
                e2.printStackTrace();
            }
        }
        
        return produits;
    }
    
    /**
     * Récupère un produit par son ID
     * @param id L'ID du produit
     * @return Le produit trouvé, null sinon
     */
    public Produit findById(int id) {
        String sql = "SELECT * FROM produits WHERE id = ?";
        
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
            System.err.println("Erreur lors de la recherche de produit: " + e.getMessage());
        }

        return null;
    }
    
    /**
     * Récupère un produit par son code-barres
     * @param codeBarre Le code-barres du produit
     * @return Le produit trouvé, null sinon
     */
    public Produit findByCodeBarre(String codeBarre) {
        String sql = "SELECT * FROM produits WHERE code_barre = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, codeBarre);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToProduit(rs);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la recherche de produit par code-barres: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Récupère un produit par son nom (recherche exacte)
     * @param nom Le nom du produit
     * @return Le produit trouvé, null sinon
     */
    public Produit findByNomExact(String nom) {
        String sql = "SELECT * FROM produits WHERE nom = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, nom);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToProduit(rs);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la recherche de produit par nom: " + e.getMessage());
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
        String sql = """
            SELECT p.*, COALESCE(c.nom, p.categorie) AS categorie_affiche
            FROM produits p
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE quantite_stock <= seuil_alerte
            ORDER BY quantite_stock ASC
        """;
        
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                produits.add(mapResultSetToProduit(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des produits à stock faible: " + e.getMessage());
        }
        
        return produits;
    }
    
    /**
     * Crée un nouveau produit
     * @param produit Le produit à créer
     * @return true si la création réussit, false sinon
     */
    public boolean create(Produit produit) {
        // Vérifier si la colonne unite existe
        boolean hasUnite = columnExists("unite");
        boolean hasCategoryId = columnExists("category_id");
        
        // Construire la requête SQL dynamiquement
        // Pour compatibilité avec anciennes bases de données
        StringBuilder sqlBuilder = new StringBuilder("INSERT INTO produits (code_barre, nom, categorie");
        if (hasCategoryId) {
            sqlBuilder.append(", category_id");
        }
        sqlBuilder.append(", prix_achat_actuel, prix_vente_defaut, quantite_stock");
        if (hasUnite) {
            sqlBuilder.append(", unite");
        }
        sqlBuilder.append(", seuil_alerte) VALUES (?, ?, ?");
        
        // Compter les paramètres correctement
        int paramCount = 3; // code_barre, nom, categorie
        if (hasCategoryId) {
            sqlBuilder.append(", ?");
            paramCount++;
        }
        sqlBuilder.append(", ?, ?, ?"); // prix_achat, prix_vente, quantite_stock
        paramCount += 3;
        if (hasUnite) {
            sqlBuilder.append(", ?");
            paramCount++;
        }
        sqlBuilder.append(", ?"); // seuil_alerte
        paramCount++;
        sqlBuilder.append(")");
        
        String sql = sqlBuilder.toString();
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            int paramIndex = 1;
            stmt.setString(paramIndex++, produit.getCodeBarre());
            stmt.setString(paramIndex++, produit.getNom());
            stmt.setString(paramIndex++, produit.getCategorie() != null ? produit.getCategorie() : "");
            
            // Si category_id existe, essayer de trouver l'ID de la catégorie
            if (hasCategoryId) {
                Integer categoryId = findCategoryIdByName(conn, produit.getCategorie());
                if (categoryId != null) {
                    stmt.setInt(paramIndex++, categoryId);
                } else {
                    stmt.setNull(paramIndex++, java.sql.Types.INTEGER);
                }
            }
            
            stmt.setBigDecimal(paramIndex++, produit.getPrixAchatActuel());
            stmt.setBigDecimal(paramIndex++, produit.getPrixVenteDefaut());
            stmt.setInt(paramIndex++, produit.getQuantiteStock());
            if (hasUnite) {
                stmt.setString(paramIndex++, produit.getUnite() != null ? produit.getUnite() : "unité");
            }
            stmt.setInt(paramIndex++, produit.getSeuilAlerte());
            
            System.out.println("SQL: " + sql);
            System.out.println("Paramètres: code_barre=" + produit.getCodeBarre() + ", nom=" + produit.getNom());
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        produit.setId(rs.getInt(1));
                        System.out.println("Produit créé avec succès, ID: " + produit.getId());
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("========================================");
            System.err.println("ERREUR lors de la création de produit:");
            System.err.println("Message: " + e.getMessage());
            System.err.println("Code SQL: " + e.getSQLState());
            System.err.println("Erreur SQLite: " + e.getErrorCode());
            System.err.println("SQL: " + sql);
            System.err.println("Produit: " + produit);
            e.printStackTrace();
            System.err.println("========================================");
        }
        
        return false;
    }
    
    /**
     * Met à jour un produit
     * @param produit Le produit à mettre à jour
     * @return true si la mise à jour réussit, false sinon
     */
    public boolean update(Produit produit) {
        // Vérifier si les colonnes existent
        boolean hasUnite = columnExists("unite");
        boolean hasCategoryId = columnExists("category_id");
        
        // Construire la requête SQL dynamiquement
        StringBuilder sqlBuilder = new StringBuilder("UPDATE produits SET code_barre = ?, nom = ?, categorie = ?");
        if (hasCategoryId) {
            sqlBuilder.append(", category_id = ?");
        }
        sqlBuilder.append(", prix_achat_actuel = ?, prix_vente_defaut = ?, quantite_stock = ?");
        if (hasUnite) {
            sqlBuilder.append(", unite = ?");
        }
        sqlBuilder.append(", seuil_alerte = ? WHERE id = ?");
        
        String sql = sqlBuilder.toString();
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int paramIndex = 1;
            stmt.setString(paramIndex++, produit.getCodeBarre());
            stmt.setString(paramIndex++, produit.getNom());
            stmt.setString(paramIndex++, produit.getCategorie() != null ? produit.getCategorie() : "");
            
            // Si category_id existe, essayer de trouver l'ID de la catégorie
            if (hasCategoryId) {
                Integer categoryId = findCategoryIdByName(conn, produit.getCategorie());
                if (categoryId != null) {
                    stmt.setInt(paramIndex++, categoryId);
                } else {
                    stmt.setNull(paramIndex++, java.sql.Types.INTEGER);
                }
            }
            
            stmt.setBigDecimal(paramIndex++, produit.getPrixAchatActuel());
            stmt.setBigDecimal(paramIndex++, produit.getPrixVenteDefaut());
            stmt.setInt(paramIndex++, produit.getQuantiteStock());
            if (hasUnite) {
                stmt.setString(paramIndex++, produit.getUnite() != null ? produit.getUnite() : "unité");
            }
            stmt.setInt(paramIndex++, produit.getSeuilAlerte());
            stmt.setInt(paramIndex++, produit.getId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Produit mis à jour avec succès, ID: " + produit.getId());
                return true;
            }
        } catch (SQLException e) {
            System.err.println("========================================");
            System.err.println("ERREUR lors de la mise à jour de produit:");
            System.err.println("Message: " + e.getMessage());
            System.err.println("Code SQL: " + e.getSQLState());
            System.err.println("Erreur SQLite: " + e.getErrorCode());
            System.err.println("SQL: " + sql);
            System.err.println("Produit ID: " + produit.getId());
            e.printStackTrace();
            System.err.println("========================================");
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
            System.err.println("Erreur lors de la vérification d'utilisation du produit: " + e.getMessage());
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
                        System.out.println("✓ " + detailsDeleted + " détail(s) de vente supprimé(s) pour le produit ID " + id);
                    }
                }
                
                // Supprimer les ajouts de stock associés
                String sqlDeleteAjouts = "DELETE FROM ajouts_stock WHERE produit_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sqlDeleteAjouts)) {
                    stmt.setInt(1, id);
                    int ajoutsDeleted = stmt.executeUpdate();
                    if (ajoutsDeleted > 0) {
                        System.out.println("✓ " + ajoutsDeleted + " ajout(s) de stock supprimé(s) pour le produit ID " + id);
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
                    System.out.println("✓ Produit ID " + id + " supprimé avec succès" + (forceDelete ? " (suppression forcée)" : ""));
                    return true;
                } else {
                    conn.rollback();
                    System.err.println("✗ Aucun produit trouvé avec l'ID " + id);
                    return false;
                }
            }
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Erreur lors du rollback: " + rollbackEx.getMessage());
                }
            }
            System.err.println("========================================");
            System.err.println("ERREUR lors de la suppression de produit:");
            System.err.println("Message: " + e.getMessage());
            System.err.println("Code SQL: " + e.getSQLState());
            System.err.println("Erreur SQLite: " + e.getErrorCode());
            System.err.println("Produit ID: " + id);
            System.err.println("Force Delete: " + forceDelete);
            e.printStackTrace();
            System.err.println("========================================");
            throw e; // Re-lancer l'exception pour que le contrôleur puisse l'afficher
        } finally {
            // Restaurer autoCommit puis rendre la connexion au pool.
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Erreur lors de la réinitialisation de autoCommit: " + e.getMessage());
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
        String sql = "UPDATE produits SET quantite_stock = quantite_stock + ?, "
                   + "date_derniere_maj = CURRENT_TIMESTAMP "
                   + "WHERE id = ? AND quantite_stock + ? >= 0";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, delta);
            stmt.setInt(2, produitId);
            stmt.setInt(3, delta);

            if (stmt.executeUpdate() > 0) {
                return true;
            }
            System.err.println("✗ Stock non modifié pour le produit ID " + produitId
                    + " (produit introuvable ou stock insuffisant pour un retrait de " + (-delta) + ")");
            return false;

        } catch (SQLException e) {
            System.err.println("✗ Erreur lors de la mise à jour du stock : " + e.getMessage());
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
                                System.out.println("✓ Stock mis à jour: Produit ID=" + produitId + ", Nouveau stock=" + quantite);
                                return true;
                            } else {
                                System.err.println("✗ Erreur: Stock vérifié (" + stockVerifie + ") ne correspond pas au stock attendu (" + quantite + ")");
                                return false;
                            }
                        } else {
                            System.err.println("✗ Erreur: Produit ID " + produitId + " introuvable après mise à jour");
                            return false;
                        }
                    }
                }
            } else {
                System.err.println("✗ Erreur: Aucune ligne mise à jour pour produit ID " + produitId);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("✗ Erreur lors de la mise à jour du stock: " + e.getMessage());
            System.err.println("  Produit ID: " + produitId);
            System.err.println("  Nouvelle quantité: " + quantite);
            e.printStackTrace();
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
            System.err.println("Erreur lors de la vérification du code-barres: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Mappe un ResultSet vers un objet Produit
     */
    /**
     * Récupère toutes les catégories distinctes (méthode de compatibilité)
     * Utilise la table categories si disponible, sinon la colonne categorie
     * @return Liste des noms de catégories
     */
    public List<String> findAllCategories() {
        List<String> categories = new ArrayList<>();
        java.util.Set<String> categoriesSet = new java.util.HashSet<>(); // Pour éviter les doublons
        
        // Essayer d'abord avec la table categories (si elle existe)
        try {
            String sql = "SELECT DISTINCT c.nom FROM categories c " +
                         "INNER JOIN produits p ON p.category_id = c.id " +
                         "ORDER BY c.nom";
            try (Connection conn = DBConnector.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    String cat = rs.getString("nom");
                    if (cat != null && !cat.trim().isEmpty()) {
                        categoriesSet.add(cat.trim());
                    }
                }
            }
        } catch (SQLException e) {
            // Table categories n'existe pas ou category_id n'existe pas - c'est normal
            System.out.println("Table categories non disponible, utilisation de la colonne categorie: " + e.getMessage());
        }
        
        // Toujours récupérer aussi depuis la colonne categorie (pour compatibilité)
        try {
            String sql = "SELECT DISTINCT TRIM(categorie) AS categorie FROM produits " +
                         "WHERE categorie IS NOT NULL AND TRIM(categorie) != '' " +
                         "ORDER BY categorie";
            
            try (Connection conn = DBConnector.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    String categorie = rs.getString("categorie");
                    if (categorie != null && !categorie.trim().isEmpty()) {
                        categoriesSet.add(categorie.trim());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des catégories depuis colonne categorie: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Convertir le Set en List triée
        categories.addAll(categoriesSet);
        java.util.Collections.sort(categories);
        
        System.out.println("✓ " + categories.size() + " catégorie(s) trouvée(s): " + categories);
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
            System.err.println("Avertissement: Nom de catégorie vide ou null");
            return produits;
        }
        
        String categorieTrim = categorieNom.trim();
        System.out.println("Recherche de produits pour catégorie: '" + categorieTrim + "'");
        
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
                    System.out.println("✓ Trouvé " + produits.size() + " produit(s) via JOIN pour catégorie: " + categorieTrim);
                return produits;
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur avec requête JOIN (peut être normal si table categories n'existe pas): " + e.getMessage());
        }
        
        // Si aucune méthode avec JOIN n'a fonctionné, utiliser l'ancienne méthode avec juste la colonne categorie
        // Recherche insensible à la casse et avec trim
        String sql = "SELECT *, categorie AS categorie_affiche FROM produits WHERE LOWER(TRIM(categorie)) = LOWER(?) ORDER BY nom";
            
            try (Connection conn = DBConnector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
            stmt.setString(1, categorieTrim);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    produits.add(mapResultSetToProduit(rs));
                }
            
            System.out.println("✓ Trouvé " + produits.size() + " produit(s) via colonne categorie pour: " + categorieTrim);
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des produits par catégorie: " + e.getMessage());
            e.printStackTrace();
        }
        
        return produits;
    }
    
    /**
     * Récupère les produits d'une catégorie (par ID de catégorie)
     * @param categoryId L'ID de la catégorie
     * @return Liste des produits de la catégorie
     */
    public List<Produit> findByCategoryId(int categoryId) {
        List<Produit> produits = new ArrayList<>();
        String sql = "SELECT *, categorie AS categorie_affiche FROM produits WHERE category_id = ? AND quantite_stock > 0 ORDER BY nom";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, categoryId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                produits.add(mapResultSetToProduit(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des produits par category_id: " + e.getMessage());
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
        
        return new Produit(
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
    }
    
    /**
     * Vérifie si une colonne existe dans la table produits.
     * Utilise information_schema (standard SQL) au lieu de PRAGMA (SQLite).
     */
    private boolean columnExists(String columnName) {
        String sql = "SELECT 1 FROM information_schema.columns "
                   + "WHERE table_name = 'produits' AND lower(column_name) = lower(?)";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, columnName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Vérification de la colonne " + columnName + " impossible : " + e.getMessage());
            return false;
        }
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
            System.err.println("Impossible de trouver la catégorie: " + e.getMessage());
        }
        
        return null;
    }
}


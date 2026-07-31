package dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.Categorie;
import model.TypeCategorie;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour les opérations CRUD sur la table Categories
 */
public class CategorieDAO {
    private static final Logger LOG = LoggerFactory.getLogger(CategorieDAO.class);

    
    /**
     * Récupère toutes les catégories
     * @return Liste de toutes les catégories
     */
    public List<Categorie> findAll() {
        List<Categorie> categories = new ArrayList<>();
        String sql = "SELECT * FROM categories ORDER BY nom";
        
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                categories.add(mapResultSetToCategorie(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des catégories: " + e.getMessage(), e);
        }
        
        return categories;
    }
    
    /**
     * Récupère une catégorie par son ID
     * @param id L'ID de la catégorie
     * @return La catégorie trouvée, null sinon
     */
    public Categorie findById(int id) {
        String sql = "SELECT * FROM categories WHERE id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToCategorie(rs);
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la recherche de catégorie: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Récupère une catégorie par son nom
     * @param nom Le nom de la catégorie
     * @return La catégorie trouvée, null sinon
     */
    public Categorie findByNom(String nom) {
        String sql = "SELECT * FROM categories WHERE nom = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, nom);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToCategorie(rs);
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la recherche de catégorie par nom: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Crée une nouvelle catégorie
     * @param categorie La catégorie à créer
     * @return true si la création réussit, false sinon
     */
    public boolean create(Categorie categorie) {
        String sql = "INSERT INTO categories (nom, description, type) VALUES (?, ?, ?)";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, categorie.getNom());
            stmt.setString(2, categorie.getDescription());
            stmt.setString(3, categorie.getType().name());

            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                int categorieId = -1;
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                            categorieId = rs.getInt(1);
                        }
                    }
                
                if (categorieId > 0) {
                    categorie.setId(categorieId);
                }
                return true;
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la création de catégorie: " + e.getMessage(), e);
            
            // Vérifier si c'est une erreur de contrainte unique (nom déjà existant)
            if (e.getMessage() != null && (e.getMessage().contains("UNIQUE constraint") || 
                e.getMessage().contains("unique constraint"))) {
                LOG.error("ERREUR: Une catégorie avec ce nom existe déjà.");
            }
        }
        
        return false;
    }
    
    /**
     * Met à jour une catégorie
     * @param categorie La catégorie à mettre à jour
     * @return true si la mise à jour réussit, false sinon
     */
    /**
     * Met à jour une catégorie et propage le nouveau nom aux produits.
     *
     * La colonne dénormalisée {@code produits.categorie} doit suivre le renommage :
     * sans cela les produits gardent l'ancien libellé et se retrouvent classés sous
     * deux noms selon la requête utilisée. Pour le tabac, cela casse également la
     * détection « tabac » / « frak cigarette », qui repose sur ce libellé.
     *
     * Les deux écritures sont faites dans une seule transaction.
     */
    public boolean update(Categorie categorie) {
        String sqlCategorie = "UPDATE categories SET nom = ?, description = ?, type = ? WHERE id = ?";
        String sqlProduits  = "UPDATE produits SET categorie = ? WHERE category_id = ?";

        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);

            int misAJour;
            try (PreparedStatement stmt = conn.prepareStatement(sqlCategorie)) {
                stmt.setString(1, categorie.getNom());
                stmt.setString(2, categorie.getDescription());
                stmt.setString(3, categorie.getType().name());
                stmt.setInt(4, categorie.getId());
                misAJour = stmt.executeUpdate();
            }

            if (misAJour == 0) {
                conn.rollback();
                return false;
            }

            try (PreparedStatement stmt = conn.prepareStatement(sqlProduits)) {
                stmt.setString(1, categorie.getNom());
                stmt.setInt(2, categorie.getId());
                int produits = stmt.executeUpdate();
                if (produits > 0) {
                    LOG.info("✓ " + produits + " produit(s) reclassé(s) sous « "
                            + categorie.getNom() + " »");
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            LOG.error("Erreur lors de la mise à jour de catégorie: " + e.getMessage(), e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOG.error("Erreur lors du rollback: " + ex.getMessage(), ex);
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    LOG.error("Erreur lors de la libération de la connexion: " + e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * Supprime une catégorie
     * @param id L'ID de la catégorie à supprimer
     * @return true si la suppression réussit, false sinon
     */
    /**
     * Supprime une catégorie, à condition qu'aucun produit ne l'utilise.
     *
     * La contrainte de la base est {@code ON DELETE SET NULL} : sans ce contrôle,
     * la suppression réussissait en silence et laissait les produits avec un
     * {@code category_id} vide mais l'ancien libellé dans la colonne texte. Ils
     * réapparaissaient alors sous une catégorie fantôme, impossible à retrouver
     * dans le référentiel.
     *
     * @return true si la catégorie a été supprimée
     * @throws SQLException si des produits l'utilisent encore, avec leur nombre
     */
    public boolean delete(int id) throws SQLException {
        String sqlCompte = "SELECT COUNT(*) FROM produits WHERE category_id = ?";
        String sqlDelete = "DELETE FROM categories WHERE id = ?";

        try (Connection conn = DBConnector.getConnection()) {

            try (PreparedStatement stmt = conn.prepareStatement(sqlCompte)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        int nb = rs.getInt(1);
                        throw new SQLException("Impossible de supprimer cette catégorie : "
                                + nb + " produit(s) l'utilisent encore. "
                                + "Reclassez-les avant de la supprimer.");
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(sqlDelete)) {
                stmt.setInt(1, id);
                return stmt.executeUpdate() > 0;
            }
        }
    }
    
    /**
     * Vérifie si un nom de catégorie existe déjà
     * @param nom Le nom de la catégorie à vérifier
     * @return true si le nom existe, false sinon
     */
    public boolean nomExists(String nom) {
        String sql = "SELECT COUNT(*) FROM categories WHERE nom = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, nom);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la vérification du nom de catégorie: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Mappe un ResultSet vers un objet Categorie
     */
    private Categorie mapResultSetToCategorie(ResultSet rs) throws SQLException {
        String type = null;
        try {
            type = rs.getString("type");
        } catch (SQLException ignored) {
            // colonne absente sur une base non encore migrée
        }

        return new Categorie(
            rs.getInt("id"),
            rs.getString("nom"),
            rs.getString("description"),
            TypeCategorie.depuisBase(type)
        );
    }
}


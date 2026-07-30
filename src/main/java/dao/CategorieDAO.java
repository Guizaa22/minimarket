package dao;

import model.Categorie;

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
            System.err.println("Erreur lors de la récupération des catégories: " + e.getMessage());
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
            System.err.println("Erreur lors de la recherche de catégorie: " + e.getMessage());
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
            System.err.println("Erreur lors de la recherche de catégorie par nom: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Crée une nouvelle catégorie
     * @param categorie La catégorie à créer
     * @return true si la création réussit, false sinon
     */
    public boolean create(Categorie categorie) {
        String sql = "INSERT INTO categories (nom, description) VALUES (?, ?)";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, categorie.getNom());
            stmt.setString(2, categorie.getDescription());
            
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
            System.err.println("Erreur lors de la création de catégorie: " + e.getMessage());
            e.printStackTrace();
            
            // Vérifier si c'est une erreur de contrainte unique (nom déjà existant)
            if (e.getMessage() != null && (e.getMessage().contains("UNIQUE constraint") || 
                e.getMessage().contains("unique constraint"))) {
                System.err.println("ERREUR: Une catégorie avec ce nom existe déjà.");
            }
        }
        
        return false;
    }
    
    /**
     * Met à jour une catégorie
     * @param categorie La catégorie à mettre à jour
     * @return true si la mise à jour réussit, false sinon
     */
    public boolean update(Categorie categorie) {
        String sql = "UPDATE categories SET nom = ?, description = ? WHERE id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, categorie.getNom());
            stmt.setString(2, categorie.getDescription());
            stmt.setInt(3, categorie.getId());
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur lors de la mise à jour de catégorie: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Supprime une catégorie
     * @param id L'ID de la catégorie à supprimer
     * @return true si la suppression réussit, false sinon
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM categories WHERE id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur lors de la suppression de catégorie: " + e.getMessage());
        }
        
        return false;
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
            System.err.println("Erreur lors de la vérification du nom de catégorie: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Mappe un ResultSet vers un objet Categorie
     */
    private Categorie mapResultSetToCategorie(ResultSet rs) throws SQLException {
        return new Categorie(
            rs.getInt("id"),
            rs.getString("nom"),
            rs.getString("description")
        );
    }
}


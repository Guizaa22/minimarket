package dao;

import model.Fournisseur;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour les opérations CRUD sur la table fournisseurs
 */
public class FournisseurDAO {
    
    /**
     * Récupère tous les fournisseurs
     */
    public List<Fournisseur> findAll() {
        List<Fournisseur> fournisseurs = new ArrayList<>();
        String sql = "SELECT * FROM fournisseurs ORDER BY nom";
        
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                fournisseurs.add(mapResultSetToFournisseur(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des fournisseurs: " + e.getMessage());
        }
        
        return fournisseurs;
    }
    
    /**
     * Récupère un fournisseur par son ID
     */
    public Fournisseur findById(int id) {
        String sql = "SELECT * FROM fournisseurs WHERE id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToFournisseur(rs);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la recherche de fournisseur: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Crée un nouveau fournisseur
     */
    public boolean create(Fournisseur fournisseur) {
        String sql = "INSERT INTO fournisseurs (nom, telephone, email, adresse) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, fournisseur.getNom());
            stmt.setString(2, fournisseur.getTelephone());
            stmt.setString(3, fournisseur.getEmail());
            stmt.setString(4, fournisseur.getAdresse());
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    fournisseur.setId(rs.getInt(1));
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création de fournisseur: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Met à jour un fournisseur
     */
    public boolean update(Fournisseur fournisseur) {
        String sql = "UPDATE fournisseurs SET nom = ?, telephone = ?, email = ?, adresse = ? WHERE id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, fournisseur.getNom());
            stmt.setString(2, fournisseur.getTelephone());
            stmt.setString(3, fournisseur.getEmail());
            stmt.setString(4, fournisseur.getAdresse());
            stmt.setInt(5, fournisseur.getId());
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur lors de la mise à jour de fournisseur: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Supprime un fournisseur
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM fournisseurs WHERE id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur lors de la suppression de fournisseur: " + e.getMessage());
        }
        
        return false;
    }
    
    private Fournisseur mapResultSetToFournisseur(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("date_creation");
        LocalDateTime dateCreation = ts != null ? ts.toLocalDateTime() : null;
        
        return new Fournisseur(
            rs.getInt("id"),
            rs.getString("nom"),
            rs.getString("telephone"),
            rs.getString("email"),
            rs.getString("adresse"),
            dateCreation
        );
    }
}


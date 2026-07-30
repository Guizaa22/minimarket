package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import model.CreditFournisseur;

/**
 * DAO pour les opérations sur les crédits fournisseur
 */
public class CreditFournisseurDAO {
    
    /**
     * Récupère le crédit d'un fournisseur
     */
    public CreditFournisseur findByFournisseurId(int idFournisseur) {
        String sql = "SELECT * FROM credits_fournisseur WHERE fournisseur_id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idFournisseur);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToCredit(rs);
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la recherche de crédit: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Met à jour le crédit d'un fournisseur
     */
    public boolean updateCredit(int idFournisseur, BigDecimal nouveauMontant) {
        String sql = "UPDATE credits_fournisseur SET montant = ?, date_maj = CURRENT_TIMESTAMP WHERE fournisseur_id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, nouveauMontant);
            stmt.setInt(2, idFournisseur);
            
            int rowsAffected = stmt.executeUpdate();
            
            // Si aucun crédit n'existe, en créer un
            if (rowsAffected == 0) {
                return createCredit(idFournisseur, nouveauMontant);
            }
            
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Erreur lors de la mise à jour du crédit: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Crée un crédit pour un fournisseur
     */
    public boolean createCredit(int idFournisseur, BigDecimal montant) {
        String sql = "INSERT INTO credits_fournisseur (fournisseur_id, montant) VALUES (?, ?)";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idFournisseur);
            stmt.setBigDecimal(2, montant);
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création de crédit: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Ajoute du crédit à un fournisseur
     */
    public boolean ajouterCredit(int idFournisseur, BigDecimal montant) {
        CreditFournisseur credit = findByFournisseurId(idFournisseur);
        BigDecimal nouveauMontant = credit != null ? 
            credit.getMontant().add(montant) : montant;
        return updateCredit(idFournisseur, nouveauMontant);
    }
    
    /**
     * Utilise du crédit d'un fournisseur
     */
    public boolean utiliserCredit(int idFournisseur, BigDecimal montant) {
        CreditFournisseur credit = findByFournisseurId(idFournisseur);
        if (credit == null || credit.getMontant().compareTo(montant) < 0) {
            return false; // Pas assez de crédit
        }
        BigDecimal nouveauMontant = credit.getMontant().subtract(montant);
        return updateCredit(idFournisseur, nouveauMontant);
    }
    
    private CreditFournisseur mapResultSetToCredit(ResultSet rs) throws SQLException {
        Timestamp tsCreation = rs.getTimestamp("date_creation");
        Timestamp tsModif = rs.getTimestamp("date_modification");
        
        return new CreditFournisseur(
            rs.getInt("id"),
            rs.getInt("fournisseur_id"),
            rs.getBigDecimal("montant"),
            tsCreation != null ? tsCreation.toLocalDateTime() : null,
            tsModif != null ? tsModif.toLocalDateTime() : null
        );
    }
}


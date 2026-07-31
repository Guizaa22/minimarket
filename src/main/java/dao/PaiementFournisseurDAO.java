package dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.PaiementFournisseur;
import util.DayRange;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour les paiements aux fournisseurs
 */
public class PaiementFournisseurDAO {
    private static final Logger LOG = LoggerFactory.getLogger(PaiementFournisseurDAO.class);

    
    /**
     * Crée un nouveau paiement
     */
    public boolean create(PaiementFournisseur paiement) {
        String sql = "INSERT INTO paiements_fournisseur (fournisseur_id, employe_id, montant, notes, date_paiement) " +
                     "VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, paiement.getIdFournisseur());
            stmt.setInt(2, paiement.getIdEmploye());
            stmt.setBigDecimal(3, paiement.getMontant());
            stmt.setString(4, paiement.getNotes());
            stmt.setTimestamp(5, Timestamp.valueOf(paiement.getDatePaiement()));
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                int paiementId = -1;
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            paiementId = rs.getInt(1);
                        }
                    }
                
                if (paiementId > 0) {
                    paiement.setId(paiementId);
                }
                return true;
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la création de paiement: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Récupère tous les paiements d'une date
     */
    public List<PaiementFournisseur> findByDate(LocalDateTime date) {
        List<PaiementFournisseur> paiements = new ArrayList<>();
        String sql = "SELECT * FROM paiements_fournisseur WHERE "
                   + DayRange.where("date_paiement") + " ORDER BY date_paiement";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            DayRange.bind(stmt, 1, date);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                paiements.add(mapResultSetToPaiement(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des paiements: " + e.getMessage(), e);
        }
        
        return paiements;
    }
    
    /**
     * Récupère tous les paiements d'un employé pour une date
     */
    public List<PaiementFournisseur> findByEmployeAndDate(int idEmploye, LocalDateTime date) {
        List<PaiementFournisseur> paiements = new ArrayList<>();
        // Utiliser employe_id (nom correct dans la base de données)
        String sql = "SELECT * FROM paiements_fournisseur WHERE employe_id = ? AND "
                   + DayRange.where("date_paiement") + " ORDER BY date_paiement";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idEmploye);
            DayRange.bind(stmt, 2, date);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                paiements.add(mapResultSetToPaiement(rs));
            }
            LOG.info("Paiements trouvés pour l'employé " + idEmploye + " le " + date.toLocalDate() + ": " + paiements.size());
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des paiements: " + e.getMessage(), e);
        }
        
        return paiements;
    }
    
    /**
     * Calcule le total des paiements pour une date
     */
    public BigDecimal getTotalByDate(LocalDateTime date) {
        String sql = "SELECT SUM(montant) FROM paiements_fournisseur WHERE "
                   + DayRange.where("date_paiement");

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            DayRange.bind(stmt, 1, date);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal(1);
                return total != null ? total : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors du calcul du total: " + e.getMessage(), e);
        }
        
        return BigDecimal.ZERO;
    }
    
    private PaiementFournisseur mapResultSetToPaiement(ResultSet rs) throws SQLException {
        Timestamp tsPaiement = rs.getTimestamp("date_paiement");
        
        // Utiliser employe_id (nom correct dans la base de données)
        int employeId = rs.getInt("employe_id");
        
        return new PaiementFournisseur(
            rs.getInt("id"),
            rs.getInt("fournisseur_id"),
            employeId,
            rs.getBigDecimal("montant"),
            rs.getString("notes"),
            tsPaiement != null ? tsPaiement.toLocalDateTime() : null,
            null  // date_creation column doesn't exist in schema
        );
    }
}


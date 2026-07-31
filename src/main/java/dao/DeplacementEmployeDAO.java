package dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.DeplacementEmploye;
import util.DayRange;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour le suivi des déplacements des employés
 */
public class DeplacementEmployeDAO {
    private static final Logger LOG = LoggerFactory.getLogger(DeplacementEmployeDAO.class);

    
    /**
     * Crée un nouveau déplacement
     */
    public boolean create(DeplacementEmploye deplacement) {
        String sql = "INSERT INTO deplacements_employe (employe_id, date_debut, date_fin, destination, notes, heures_travaillees) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, deplacement.getIdEmploye());
            stmt.setTimestamp(2, Timestamp.valueOf(deplacement.getDateDebut()));
            stmt.setTimestamp(3, deplacement.getDateFin() != null ? Timestamp.valueOf(deplacement.getDateFin()) : null);
            stmt.setString(4, deplacement.getDestination());
            stmt.setString(5, deplacement.getNotes());
            stmt.setBigDecimal(6, deplacement.getHeuresTravaillees());
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                int deplacementId = -1;
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            deplacementId = rs.getInt(1);
                        }
                    }
                
                if (deplacementId > 0) {
                    deplacement.setId(deplacementId);
                }
                return true;
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la création de déplacement: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Met à jour un déplacement (pour fermer le déplacement avec date_fin)
     */
    public boolean update(DeplacementEmploye deplacement) {
        String sql = "UPDATE deplacements_employe SET date_fin = ?, heures_travaillees = ?, notes = ? WHERE id = ?";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, deplacement.getDateFin() != null ? Timestamp.valueOf(deplacement.getDateFin()) : null);
            stmt.setBigDecimal(2, deplacement.getHeuresTravaillees());
            stmt.setString(3, deplacement.getNotes());
            stmt.setInt(4, deplacement.getId());
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Erreur lors de la mise à jour de déplacement: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Récupère tous les déplacements d'un employé pour une date
     */
    public List<DeplacementEmploye> findByEmployeAndDate(int idEmploye, LocalDateTime date) {
        List<DeplacementEmploye> deplacements = new ArrayList<>();
        // Utiliser employe_id (nom correct dans la base de données)
        String sql = "SELECT * FROM deplacements_employe WHERE employe_id = ? AND "
                   + DayRange.where("date_debut") + " ORDER BY date_debut";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idEmploye);
            DayRange.bind(stmt, 2, date);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                deplacements.add(mapResultSetToDeplacement(rs));
            }
            LOG.info("Déplacements trouvés pour l'employé " + idEmploye + " le " + date.toLocalDate() + ": " + deplacements.size());
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des déplacements: " + e.getMessage(), e);
        }
        
        return deplacements;
    }
    
    /**
     * Calcule le total des heures travaillées pour un employé à une date
     */
    public BigDecimal getTotalHeuresByDate(int idEmploye, LocalDateTime date) {
        String sql = "SELECT SUM(heures_travaillees) FROM deplacements_employe WHERE employe_id = ? AND "
                   + DayRange.where("date_debut");
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idEmploye);
            DayRange.bind(stmt, 2, date);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal(1);
                return total != null ? total : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors du calcul des heures: " + e.getMessage(), e);
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Récupère tous les déplacements pour une date (tous les employés)
     */
    public List<DeplacementEmploye> findByDate(LocalDateTime date) {
        List<DeplacementEmploye> deplacements = new ArrayList<>();
        String sql = "SELECT * FROM deplacements_employe WHERE "
                   + DayRange.where("date_debut") + " ORDER BY date_debut";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            DayRange.bind(stmt, 1, date);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                deplacements.add(mapResultSetToDeplacement(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des déplacements: " + e.getMessage(), e);
        }
        
        return deplacements;
    }
    
    private DeplacementEmploye mapResultSetToDeplacement(ResultSet rs) throws SQLException {
        Timestamp tsDebut = rs.getTimestamp("date_debut");
        Timestamp tsFin = rs.getTimestamp("date_fin");
        
        // Utiliser employe_id (nom correct dans la base de données)
        int employeId = rs.getInt("employe_id");
        
        return new DeplacementEmploye(
            rs.getInt("id"),
            employeId,
            tsDebut != null ? tsDebut.toLocalDateTime() : null,
            tsFin != null ? tsFin.toLocalDateTime() : null,
            rs.getString("destination"),
            rs.getString("notes"),
            rs.getBigDecimal("heures_travaillees"),
            null  // date_creation column doesn't exist in schema
        );
    }
}


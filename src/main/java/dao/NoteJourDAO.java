package dao;

import model.NoteJour;
import util.DayRange;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour les notes du jour
 */
public class NoteJourDAO {
    
    /**
     * Crée une nouvelle note
     */
    public boolean create(NoteJour note) {
        // Vérifier que l'employé existe avant d'insérer la note
        try (Connection conn = DBConnector.getConnection()) {
            // Vérifier que l'utilisateur existe
            try (PreparedStatement checkUser = conn.prepareStatement("SELECT id FROM utilisateurs WHERE id = ?")) {
                checkUser.setInt(1, note.getIdEmploye());
                try (ResultSet rs = checkUser.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("ERREUR: L'utilisateur avec l'ID " + note.getIdEmploye() + " n'existe pas dans la base de données.");
                        return false;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la vérification de l'utilisateur: " + e.getMessage());
            return false;
        }
        
        String sql = "INSERT INTO notes_jour (employe_id, type_note, montant, description, date_note) " +
                     "VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, note.getIdEmploye());
            stmt.setString(2, note.getTypeNote().name());
            stmt.setBigDecimal(3, note.getMontant());
            stmt.setString(4, note.getDescription());
            stmt.setTimestamp(5, Timestamp.valueOf(note.getDateNote()));
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                int noteId = -1;
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            noteId = rs.getInt(1);
                        }
                    }
                
                if (noteId > 0) {
                    note.setId(noteId);
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création de note: " + e.getMessage());
            e.printStackTrace();
            
            // Vérifier si c'est une erreur de contrainte de clé étrangère
            if (e.getMessage() != null && e.getMessage().contains("FOREIGN KEY")) {
                System.err.println("ERREUR: L'employé avec l'ID " + note.getIdEmploye() + " n'existe pas dans la base de données.");
            }
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors de la création de note: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Récupère toutes les notes d'un employé pour une date
     */
    public List<NoteJour> findByEmployeAndDate(int idEmploye, LocalDateTime date) {
        List<NoteJour> notes = new ArrayList<>();
        String sql = "SELECT * FROM notes_jour WHERE employe_id = ? AND "
                   + DayRange.where("date_note") + " ORDER BY date_note";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idEmploye);
            DayRange.bind(stmt, 2, date);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                notes.add(mapResultSetToNote(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des notes: " + e.getMessage());
        }
        
        return notes;
    }
    
    /**
     * Récupère toutes les notes pour une date
     */
    public List<NoteJour> findByDate(LocalDateTime date) {
        List<NoteJour> notes = new ArrayList<>();
        String sql = "SELECT * FROM notes_jour WHERE "
                   + DayRange.where("date_note") + " ORDER BY date_note";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            DayRange.bind(stmt, 1, date);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                notes.add(mapResultSetToNote(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des notes: " + e.getMessage());
        }
        
        return notes;
    }
    
    /**
     * Calcule le total des montants pour un type de note et une date
     */
    public BigDecimal getTotalByTypeAndDate(NoteJour.TypeNote type, LocalDateTime date) {
        String sql = "SELECT SUM(montant) FROM notes_jour WHERE type_note = ? AND "
                   + DayRange.where("date_note");

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, type.name());
            DayRange.bind(stmt, 2, date);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal(1);
                return total != null ? total : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors du calcul du total: " + e.getMessage());
        }
        
        return BigDecimal.ZERO;
    }
    
    private NoteJour mapResultSetToNote(ResultSet rs) throws SQLException {
        Timestamp tsNote = rs.getTimestamp("date_note");
        
        NoteJour.TypeNote type = NoteJour.TypeNote.valueOf(rs.getString("type_note"));
        
        return new NoteJour(
            rs.getInt("id"),
            rs.getInt("employe_id"),
            type,
            rs.getBigDecimal("montant"),
            rs.getString("description"),
            tsNote != null ? tsNote.toLocalDateTime() : null,
            null  // date_creation column doesn't exist in schema
        );
    }
}


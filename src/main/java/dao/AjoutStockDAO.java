package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import model.AjoutStock;
import util.DayRange;

/**
 * DAO pour les ajouts de stock avec informations fournisseur
 */
public class AjoutStockDAO {
    
    /**
     * Crée un nouvel ajout de stock
     */
    public boolean create(AjoutStock ajoutStock) {
        // Vérifier si les colonnes tabac existent
        boolean hasTabacColumns = checkTabacColumnsExist();
        String sql;
        if (hasTabacColumns) {
            sql = "INSERT INTO ajouts_stock (produit_id, employe_id, fournisseur_id, quantite, montant_paiement, credit_utilise, notes, date_ajout, type_ajout_tabac, quantite_cigarettes) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sql = "INSERT INTO ajouts_stock (produit_id, employe_id, fournisseur_id, quantite, montant_paiement, credit_utilise, notes, date_ajout) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        }
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, ajoutStock.getIdProduit());
            stmt.setInt(2, ajoutStock.getIdEmploye());
            if (ajoutStock.getIdFournisseur() != null) {
                stmt.setInt(3, ajoutStock.getIdFournisseur());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setInt(4, ajoutStock.getQuantite());
            stmt.setBigDecimal(5, ajoutStock.getMontantPaiement());
            stmt.setBigDecimal(6, ajoutStock.getCreditUtilise());
            stmt.setString(7, ajoutStock.getNotes());
            stmt.setTimestamp(8, Timestamp.valueOf(ajoutStock.getDateAjout()));
            if (hasTabacColumns) {
                stmt.setString(9, ajoutStock.getTypeAjoutTabac());
                if (ajoutStock.getQuantiteCigarettes() != null) {
                    stmt.setInt(10, ajoutStock.getQuantiteCigarettes());
                } else {
                    stmt.setNull(10, Types.INTEGER);
                }
            }
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                int ajoutId = -1;
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            ajoutId = rs.getInt(1);
                        }
                    }
                
                if (ajoutId > 0) {
                    ajoutStock.setId(ajoutId);
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création d'ajout de stock: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Récupère tous les ajouts de stock d'un employé pour une date
     */
    public List<AjoutStock> findByEmployeAndDate(int idEmploye, LocalDateTime date) {
        List<AjoutStock> ajouts = new ArrayList<>();
        String sql = "SELECT * FROM ajouts_stock WHERE employe_id = ? AND "
                   + DayRange.where("date_ajout") + " ORDER BY date_ajout";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idEmploye);
            DayRange.bind(stmt, 2, date);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                ajouts.add(mapResultSetToAjoutStock(rs));
            }
            System.out.println("Ajouts de stock trouvés pour l'employé " + idEmploye + " le " + date.toLocalDate() + ": " + ajouts.size());
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des ajouts: " + e.getMessage());
            e.printStackTrace();
        }
        
        return ajouts;
    }
    
    /**
     * Récupère tous les ajouts de stock pour une date (tous les employés)
     */
    public List<AjoutStock> findByDate(LocalDateTime date) {
        List<AjoutStock> ajouts = new ArrayList<>();
        String sql = "SELECT * FROM ajouts_stock WHERE "
                   + DayRange.where("date_ajout") + " ORDER BY date_ajout";
        
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            DayRange.bind(stmt, 1, date);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                ajouts.add(mapResultSetToAjoutStock(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des ajouts: " + e.getMessage());
        }
        
        return ajouts;
    }
    
    /**
     * Récupère tous les ajouts de stock
     */
    public List<AjoutStock> findAll() {
        List<AjoutStock> ajouts = new ArrayList<>();
        String sql = "SELECT * FROM ajouts_stock ORDER BY date_ajout DESC";
        
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ajouts.add(mapResultSetToAjoutStock(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération de tous les ajouts: " + e.getMessage());
        }
        
        return ajouts;
    }
    
    private AjoutStock mapResultSetToAjoutStock(ResultSet rs) throws SQLException {
        Timestamp tsAjout = rs.getTimestamp("date_ajout");
        
        Integer idFournisseur = rs.getObject("fournisseur_id", Integer.class);
        
        // Vérifier si les colonnes tabac existent
        String typeAjoutTabac = null;
        Integer quantiteCigarettes = null;
        try {
            typeAjoutTabac = rs.getString("type_ajout_tabac");
            quantiteCigarettes = rs.getObject("quantite_cigarettes", Integer.class);
        } catch (SQLException e) {
            // Colonnes n'existent pas encore, utiliser null
        }
        
        AjoutStock ajoutStock = new AjoutStock(
            rs.getInt("id"),
            rs.getInt("produit_id"),
            rs.getInt("employe_id"),
            idFournisseur,
            rs.getInt("quantite"),
            rs.getBigDecimal("montant_paiement"),
            rs.getBigDecimal("credit_utilise"),
            rs.getString("notes"),
            tsAjout != null ? tsAjout.toLocalDateTime() : null,
            null  // date_creation column doesn't exist in schema
        );
        ajoutStock.setTypeAjoutTabac(typeAjoutTabac);
        ajoutStock.setQuantiteCigarettes(quantiteCigarettes);
        return ajoutStock;
    }
    
    /**
     * Vérifie si les colonnes tabac existent dans la table ajouts_stock
     */
    private boolean checkTabacColumnsExist() {
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement()) {
            // Essayer de lire les colonnes
            try (ResultSet rs = stmt.executeQuery("SELECT type_ajout_tabac, quantite_cigarettes FROM ajouts_stock LIMIT 1")) {
                return true; // Les colonnes existent
            }
        } catch (SQLException e) {
            // Les colonnes n'existent pas, les créer
            try (Connection conn = DBConnector.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE ajouts_stock ADD COLUMN type_ajout_tabac TEXT");
                stmt.execute("ALTER TABLE ajouts_stock ADD COLUMN quantite_cigarettes INTEGER");
                System.out.println("Colonnes tabac ajoutées à la table ajouts_stock");
                return true;
            } catch (SQLException e2) {
                System.err.println("Erreur lors de l'ajout des colonnes tabac: " + e2.getMessage());
                return false;
            }
        }
    }
}


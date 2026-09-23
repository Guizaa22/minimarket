package dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(AjoutStockDAO.class);

    
    /**
     * Crée un nouvel ajout de stock
     */
    public boolean create(AjoutStock ajoutStock) {
        try (Connection conn = DBConnector.getConnection()) {
            return create(conn, ajoutStock);
        } catch (SQLException e) {
            LOG.error("Erreur lors de la création d'ajout de stock: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Crée un ajout de stock sur une connexion fournie par l'appelant.
     *
     * Permet d'inscrire l'ajout dans la même transaction que l'incrément de
     * stock et le mouvement de crédit fournisseur : ces trois écritures
     * doivent être validées ou annulées ensemble.
     *
     * La connexion n'est ni validée ni fermée ici : c'est à l'appelant, qui
     * possède la transaction, de le faire.
     *
     * @throws SQLException pour que l'appelant puisse annuler la transaction ;
     *         avaler l'erreur laisserait le stock incrémenté sans trace.
     */
    public boolean create(Connection conn, AjoutStock ajoutStock) throws SQLException {
        // Les colonnes tabac font partie du schéma (voir schema_postgres.sql) :
        // une seule requête, sans détection ni ALTER TABLE à chaud.
        String sql = "INSERT INTO ajouts_stock (produit_id, employe_id, fournisseur_id, quantite, "
                   + "montant_paiement, credit_utilise, notes, date_ajout, type_ajout_tabac, quantite_cigarettes) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
            stmt.setString(9, ajoutStock.getTypeAjoutTabac());
            if (ajoutStock.getQuantiteCigarettes() != null) {
                stmt.setInt(10, ajoutStock.getQuantiteCigarettes());
            } else {
                stmt.setNull(10, Types.INTEGER);
            }

            if (stmt.executeUpdate() == 0) {
                return false;
            }

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    ajoutStock.setId(rs.getInt(1));
                }
            }
            return true;
        }
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
            LOG.info("Ajouts de stock trouvés pour l'employé " + idEmploye + " le " + date.toLocalDate() + ": " + ajouts.size());
        } catch (SQLException e) {
            LOG.error("Erreur lors de la récupération des ajouts: " + e.getMessage(), e);
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
            LOG.error("Erreur lors de la récupération des ajouts: " + e.getMessage(), e);
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
            LOG.error("Erreur lors de la récupération de tous les ajouts: " + e.getMessage(), e);
        }
        
        return ajouts;
    }
    
    private AjoutStock mapResultSetToAjoutStock(ResultSet rs) throws SQLException {
        Timestamp tsAjout = rs.getTimestamp("date_ajout");
        
        Integer idFournisseur = rs.getObject("fournisseur_id", Integer.class);
        
        // Colonnes garanties par le schéma : elles restent nulles pour un ajout
        // qui ne concerne pas le tabac.
        String typeAjoutTabac = rs.getString("type_ajout_tabac");
        Integer quantiteCigarettes = rs.getObject("quantite_cigarettes", Integer.class);
        
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
    
}

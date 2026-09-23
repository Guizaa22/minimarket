package dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(CreditFournisseurDAO.class);

    
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
            LOG.error("Erreur lors de la recherche de crédit: " + e.getMessage(), e);
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
            LOG.error("Erreur lors de la mise à jour du crédit: " + e.getMessage(), e);
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
            LOG.error("Erreur lors de la création de crédit: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Ajoute du crédit à un fournisseur.
     *
     * Écriture atomique : le nouveau montant est calculé par la base, pas en
     * Java. La forme précédente lisait le solde, l'additionnait puis écrivait
     * le résultat ; deux caisses créditant le même fournisseur en même temps
     * lisaient la même valeur de départ et l'une des deux additions était
     * perdue.
     *
     * Le fournisseur peut ne pas encore avoir de ligne de crédit : l'insertion
     * et la mise à jour sont donc faites en une seule requête (ON CONFLICT sur
     * fournisseur_id, qui est unique).
     */
    public boolean ajouterCredit(int idFournisseur, BigDecimal montant) {
        try (Connection conn = DBConnector.getConnection()) {
            return ajouterCredit(conn, idFournisseur, montant);
        } catch (SQLException e) {
            LOG.error("Erreur lors de l'ajout de crédit: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Ajoute du crédit sur une connexion fournie par l'appelant, afin que
     * l'opération tienne dans la même transaction que l'ajout de stock.
     *
     * La connexion n'est ni validée ni fermée ici.
     *
     * @throws SQLException pour laisser l'appelant annuler la transaction
     */
    public boolean ajouterCredit(Connection conn, int idFournisseur, BigDecimal montant) throws SQLException {
        String sql = "INSERT INTO credits_fournisseur (fournisseur_id, montant) VALUES (?, ?) "
                   + "ON CONFLICT (fournisseur_id) DO UPDATE "
                   + "SET montant = credits_fournisseur.montant + EXCLUDED.montant, "
                   + "    date_maj = CURRENT_TIMESTAMP";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idFournisseur);
            stmt.setBigDecimal(2, montant);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Utilise du crédit d'un fournisseur.
     *
     * La condition « montant >= ? » fait partie de l'UPDATE : le solde est
     * vérifié et débité en une seule instruction, la base refusant d'elle-même
     * un débit supérieur au disponible. Un contrôle lu séparément puis appliqué
     * plus tard pouvait être invalidé entre-temps par une autre caisse.
     *
     * @return false si le crédit disponible est insuffisant, ou si le
     *         fournisseur n'a pas de ligne de crédit
     */
    public boolean utiliserCredit(int idFournisseur, BigDecimal montant) {
        try (Connection conn = DBConnector.getConnection()) {
            return utiliserCredit(conn, idFournisseur, montant);
        } catch (SQLException e) {
            LOG.error("Erreur lors de l'utilisation de crédit: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Utilise du crédit sur une connexion fournie par l'appelant, afin que
     * l'opération tienne dans la même transaction que l'ajout de stock.
     *
     * La connexion n'est ni validée ni fermée ici.
     *
     * @return false si le crédit disponible est insuffisant
     * @throws SQLException pour laisser l'appelant annuler la transaction
     */
    public boolean utiliserCredit(Connection conn, int idFournisseur, BigDecimal montant) throws SQLException {
        String sql = "UPDATE credits_fournisseur SET montant = montant - ?, "
                   + "date_maj = CURRENT_TIMESTAMP "
                   + "WHERE fournisseur_id = ? AND montant >= ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBigDecimal(1, montant);
            stmt.setInt(2, idFournisseur);
            stmt.setBigDecimal(3, montant);

            if (stmt.executeUpdate() > 0) {
                return true;
            }
            LOG.warn("Crédit insuffisant ou inexistant pour le fournisseur {} (débit demandé : {})",
                    idFournisseur, montant);
            return false;
        }
    }

    private CreditFournisseur mapResultSetToCredit(ResultSet rs) throws SQLException {
        Timestamp tsCreation = rs.getTimestamp("date_creation");
        // La colonne s'appelle date_maj, comme dans l'UPDATE plus haut : la
        // lecture visait date_modification, qui n'existe pas. Toute consultation
        // d'un crédit fournisseur échouait donc, sans effet visible à l'écran
        // puisque l'erreur partait sur System.err.
        Timestamp tsModif = rs.getTimestamp("date_maj");
        
        return new CreditFournisseur(
            rs.getInt("id"),
            rs.getInt("fournisseur_id"),
            rs.getBigDecimal("montant"),
            tsCreation != null ? tsCreation.toLocalDateTime() : null,
            tsModif != null ? tsModif.toLocalDateTime() : null
        );
    }
}


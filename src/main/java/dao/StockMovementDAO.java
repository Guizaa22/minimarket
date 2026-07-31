package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exception.DatabaseException;
import model.StockMovement;
import util.DayRange;

/**
 * Accès à la table {@code stock_movements}.
 *
 * Certaines méthodes acceptent une {@link Connection} : l'enregistrement d'un
 * mouvement doit pouvoir participer à la transaction de la vente, afin qu'une
 * vente annulée n'en laisse pas la trace.
 */
public class StockMovementDAO {

    private static final Logger LOG = LoggerFactory.getLogger(StockMovementDAO.class);

    private static final String INSERT =
            "INSERT INTO stock_movements "
          + "(product_id, user_id, quantity_change, stock_apres, type, reference, created_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";

    private static final String SELECT =
            "SELECT id, product_id, user_id, quantity_change, stock_apres, type, reference, created_at "
          + "FROM stock_movements";

    /** Enregistre un mouvement avec sa propre connexion. */
    public boolean create(StockMovement mouvement) {
        try (Connection conn = DBConnector.getConnection()) {
            return create(conn, mouvement);
        } catch (SQLException e) {
            LOG.error("Enregistrement du mouvement de stock impossible", e);
            throw new DatabaseException("Impossible d'enregistrer le mouvement de stock", e);
        }
    }

    /**
     * Enregistre un mouvement sur une connexion fournie, pour participer à une
     * transaction existante.
     */
    public boolean create(Connection conn, StockMovement mouvement) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT)) {
            stmt.setInt(1, mouvement.getProductId());

            if (mouvement.getUserId() != null) {
                stmt.setInt(2, mouvement.getUserId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            stmt.setInt(3, mouvement.getQuantityChange());

            if (mouvement.getStockApres() != null) {
                stmt.setInt(4, mouvement.getStockApres());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }

            stmt.setString(5, mouvement.getType().name());
            stmt.setString(6, mouvement.getReference());
            stmt.setTimestamp(7, Timestamp.valueOf(
                    mouvement.getCreatedAt() != null ? mouvement.getCreatedAt() : LocalDateTime.now()));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    mouvement.setId(rs.getInt(1));
                    return true;
                }
            }
            return false;
        }
    }

    /** Historique d'un produit, du plus récent au plus ancien. */
    public List<StockMovement> findByProduit(int produitId, int limite) {
        String sql = SELECT + " WHERE product_id = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produitId);
            stmt.setInt(2, limite);
            return lire(stmt);
        } catch (SQLException e) {
            LOG.error("Lecture des mouvements de stock impossible", e);
            throw new DatabaseException("Impossible de lire l'historique du stock", e);
        }
    }

    /** Mouvements d'une journée. */
    public List<StockMovement> findByDate(LocalDateTime jour) {
        String sql = SELECT + " WHERE " + DayRange.where("created_at") + " ORDER BY created_at DESC";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DayRange.bind(stmt, 1, jour);
            return lire(stmt);
        } catch (SQLException e) {
            LOG.error("Lecture des mouvements de stock impossible", e);
            throw new DatabaseException("Impossible de lire les mouvements du jour", e);
        }
    }

    /** Mouvements d'une origine donnée sur une journée (ex. saisies mobiles). */
    public List<StockMovement> findByTypeAndDate(StockMovement.Type type, LocalDateTime jour) {
        String sql = SELECT + " WHERE type = ? AND " + DayRange.where("created_at")
                   + " ORDER BY created_at DESC";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, type.name());
            DayRange.bind(stmt, 2, jour);
            return lire(stmt);
        } catch (SQLException e) {
            LOG.error("Lecture des mouvements de stock impossible", e);
            throw new DatabaseException("Impossible de lire les mouvements du jour", e);
        }
    }

    private List<StockMovement> lire(PreparedStatement stmt) throws SQLException {
        List<StockMovement> mouvements = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                mouvements.add(mapper(rs));
            }
        }
        return mouvements;
    }

    private StockMovement mapper(ResultSet rs) throws SQLException {
        StockMovement m = new StockMovement();
        m.setId(rs.getInt("id"));
        m.setProductId(rs.getInt("product_id"));

        int userId = rs.getInt("user_id");
        m.setUserId(rs.wasNull() ? null : userId);

        m.setQuantityChange(rs.getInt("quantity_change"));

        int stockApres = rs.getInt("stock_apres");
        m.setStockApres(rs.wasNull() ? null : stockApres);

        m.setType(StockMovement.Type.depuisBase(rs.getString("type")));
        m.setReference(rs.getString("reference"));

        Timestamp ts = rs.getTimestamp("created_at");
        m.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        return m;
    }
}

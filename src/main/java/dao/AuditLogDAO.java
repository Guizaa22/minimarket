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
import model.AuditLog;
import util.DayRange;

/**
 * Accès à la table {@code audit_logs}.
 */
public class AuditLogDAO {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogDAO.class);

    private static final String SELECT =
            "SELECT id, user_id, action, entity_name, entity_id, details, timestamp FROM audit_logs";

    public boolean create(AuditLog log) {
        String sql = "INSERT INTO audit_logs (user_id, action, entity_name, entity_id, details, timestamp) "
                   + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (log.getUserId() != null) {
                stmt.setInt(1, log.getUserId());
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            stmt.setString(2, log.getAction());
            stmt.setString(3, log.getEntityName());
            if (log.getEntityId() != null) {
                stmt.setInt(4, log.getEntityId());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setString(5, log.getDetails());
            stmt.setTimestamp(6, Timestamp.valueOf(
                    log.getTimestamp() != null ? log.getTimestamp() : LocalDateTime.now()));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    log.setId(rs.getInt(1));
                    return true;
                }
            }
            return false;

        } catch (SQLException e) {
            LOG.error("Écriture du journal d'audit impossible", e);
            throw new DatabaseException("Impossible d'enregistrer l'action dans le journal", e);
        }
    }

    /** Dernières entrées, les plus récentes d'abord. */
    public List<AuditLog> findRecent(int limite) {
        String sql = SELECT + " ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limite);
            return lire(stmt);
        } catch (SQLException e) {
            LOG.error("Lecture du journal d'audit impossible", e);
            throw new DatabaseException("Impossible de lire le journal d'audit", e);
        }
    }

    /** Entrées d'une journée. */
    public List<AuditLog> findByDate(LocalDateTime jour) {
        String sql = SELECT + " WHERE " + DayRange.where("timestamp") + " ORDER BY timestamp DESC";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DayRange.bind(stmt, 1, jour);
            return lire(stmt);
        } catch (SQLException e) {
            LOG.error("Lecture du journal d'audit impossible", e);
            throw new DatabaseException("Impossible de lire le journal d'audit", e);
        }
    }

    /** Historique d'une entité précise. */
    public List<AuditLog> findByEntity(String entite, int entiteId) {
        String sql = SELECT + " WHERE entity_name = ? AND entity_id = ? ORDER BY timestamp DESC";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entite);
            stmt.setInt(2, entiteId);
            return lire(stmt);
        } catch (SQLException e) {
            LOG.error("Lecture du journal d'audit impossible", e);
            throw new DatabaseException("Impossible de lire le journal d'audit", e);
        }
    }

    private List<AuditLog> lire(PreparedStatement stmt) throws SQLException {
        List<AuditLog> logs = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                logs.add(mapper(rs));
            }
        }
        return logs;
    }

    private AuditLog mapper(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setId(rs.getInt("id"));

        int userId = rs.getInt("user_id");
        log.setUserId(rs.wasNull() ? null : userId);

        log.setAction(rs.getString("action"));
        log.setEntityName(rs.getString("entity_name"));

        int entityId = rs.getInt("entity_id");
        log.setEntityId(rs.wasNull() ? null : entityId);

        log.setDetails(rs.getString("details"));
        Timestamp ts = rs.getTimestamp("timestamp");
        log.setTimestamp(ts != null ? ts.toLocalDateTime() : null);
        return log;
    }
}

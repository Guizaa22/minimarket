package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import dao.DBConnector;

/**
 * Crée et met à jour le schéma PostgreSQL au démarrage.
 *
 * Contrairement à l'ancienne version, ce code exécute réellement le fichier
 * {@code /database/schema_postgres.sql}. L'ancien schéma SQL n'était jamais
 * chargé, ce qui explique l'absence totale d'index dans la base de production.
 */
public final class DatabaseSetup {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseSetup.class);


    private static final String SCHEMA_RESOURCE = "/database/schema_postgres.sql";

    private DatabaseSetup() {
    }

    /**
     * Applique le schéma. Idempotent : peut être appelé à chaque démarrage.
     *
     * @return true si la base est prête à l'emploi
     */
    public static boolean initializeDatabase() {
        String script = readSchemaScript();
        if (script == null) {
            LOG.error("✗ Script de schéma introuvable : " + SCHEMA_RESOURCE);
            return false;
        }

        try (Connection conn = DBConnector.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // PostgreSQL accepte plusieurs instructions séparées par ';'
                // dans un seul execute().
                stmt.execute(script);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

            LOG.info("✓ Schéma vérifié : " + Config.describe());
            reportState();
            return true;

        } catch (SQLException e) {
            LOG.error("✗ Initialisation de la base échouée : " + DBConnector.diagnose(e));
            return false;
        }
    }

    private static String readSchemaScript() {
        try (InputStream in = DatabaseSetup.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("✗ Lecture du schéma impossible : " + e.getMessage(), e);
            return null;
        }
    }

    /** Affiche un résumé de l'état de la base, utile au démarrage. */
    private static void reportState() {
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT (SELECT COUNT(*) FROM utilisateurs), " +
                 "       (SELECT COUNT(*) FROM produits), " +
                 "       (SELECT COUNT(*) FROM ventes)")) {
            if (rs.next()) {
                LOG.info("  Utilisateurs : " + rs.getInt(1)
                        + " | Produits : " + rs.getInt(2)
                        + " | Ventes : " + rs.getInt(3));
            }
        } catch (SQLException e) {
            LOG.error("  (état de la base indisponible : " + e.getMessage() + ")", e);
        }
    }

    /** Indique si au moins un compte administrateur existe. */
    public static boolean hasAdminAccount() {
        String sql = "SELECT COUNT(*) FROM utilisateurs WHERE role = 'Admin'";
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            LOG.error("Vérification du compte admin impossible : " + e.getMessage(), e);
            return true; // ne pas proposer de créer un compte si l'état est inconnu
        }
    }

    /**
     * Crée le tout premier compte administrateur.
     * Utilisé uniquement quand la base ne contient encore aucun Admin :
     * aucun mot de passe par défaut n'est codé en dur dans l'application.
     */
    public static boolean createInitialAdmin(String username, String password) {
        String sql = "INSERT INTO utilisateurs (username, password_hash, role) VALUES (?, ?, 'Admin')";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, SecurityUtil.hashPassword(password));
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Création du compte administrateur impossible : "
                    + DBConnector.diagnose(e));
            return false;
        }
    }
}

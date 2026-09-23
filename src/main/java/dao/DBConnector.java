package dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import util.Config;

/**
 * Fournit les connexions à PostgreSQL via un pool HikariCP.
 *
 * Chaque appel à {@link #getConnection()} retourne une connexion distincte du pool.
 * Les appelants DOIVENT la refermer (try-with-resources) : la fermeture la rend
 * simplement au pool, elle ne coupe rien.
 *
 * C'est le point important par rapport à l'ancienne implémentation, qui partageait
 * une unique connexion statique : la refermer dans un try-with-resources cassait
 * toute transaction en cours ailleurs dans l'application.
 */
public final class DBConnector {
    private static final Logger LOG = LoggerFactory.getLogger(DBConnector.class);


    private static volatile HikariDataSource dataSource;

    private DBConnector() {
    }

    private static HikariDataSource dataSource() throws SQLException {
        HikariDataSource local = dataSource;
        if (local != null && !local.isClosed()) {
            return local;
        }

        synchronized (DBConnector.class) {
            if (dataSource != null && !dataSource.isClosed()) {
                return dataSource;
            }

            if (!Config.isConfigured()) {
                throw new SQLException(
                    "Base de données non configurée.\n" +
                    "Renseignez DATABASE_URL (ou PGHOST/PGDATABASE/PGUSER/PGPASSWORD),\n" +
                    "ou créez le fichier : " + Config.getConfigFilePath());
            }

            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(Config.getJdbcUrl());
                config.setUsername(Config.getDbUser());
                config.setPassword(Config.getDbPassword());
                config.setDriverClassName("org.postgresql.Driver");

                config.setMaximumPoolSize(Config.getPoolSize());
                config.setMinimumIdle(1);
                config.setConnectionTimeout(10_000);
                config.setIdleTimeout(600_000);
                config.setMaxLifetime(1_800_000);
                config.setPoolName("2M-Market-Pool");

                // Signale toute connexion non rendue au pool, avec la pile
                // d'appel fautive. Sans cela, une fuite se manifeste seulement
                // par un blocage de 10 s puis un échec difficile à diagnostiquer.
                //
                // Le seuil dépasse statement_timeout : à 5 s, une requête lente
                // mais légitime — toujours interrompue au bout de 15 s — était
                // signalée comme une fuite, et l'avertissement, faux la plupart
                // du temps, finissait par être ignoré.
                config.setLeakDetectionThreshold(20_000);

                // Les transactions sont gérées explicitement par les DAO.
                config.setAutoCommit(true);

                // Garde-fou : une requête bloquée ne doit pas figer la caisse.
                // Le search_path fixe le schéma cible (les tests en utilisent un dédié).
                config.setConnectionInitSql(
                        "SET statement_timeout = 15000; "
                      + "SET search_path TO " + Config.getDbSchema());

                ensureSchemaExists();

                dataSource = new HikariDataSource(config);

                LOG.info("✓ Pool de connexions initialisé : " + Config.describe());
                avertirSiConnexionNonChiffree();
            } catch (RuntimeException e) {
                throw new SQLException("Impossible d'initialiser le pool de connexions : "
                        + e.getMessage(), e);
            }
            return dataSource;
        }
    }

    /**
     * Crée le schéma cible s'il n'existe pas encore.
     * Utilise une connexion directe : le pool n'est pas encore construit,
     * et son search_path pointerait sur un schéma inexistant.
     */
    private static void ensureSchemaExists() throws SQLException {
        if (Config.isProductionSchema()) {
            return; // le schéma "public" existe toujours
        }
        try (Connection conn = java.sql.DriverManager.getConnection(
                     Config.getJdbcUrl(), Config.getDbUser(), Config.getDbPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + Config.getDbSchema());
            LOG.info("✓ Schéma dédié : " + Config.getDbSchema());
        }
    }

    /**
     * Emprunte une connexion au pool.
     * L'appelant doit la refermer (elle retourne alors au pool).
     */
    public static Connection getConnection() throws SQLException {
        return dataSource().getConnection();
    }

    /** Ferme le pool. À appeler une seule fois, à l'arrêt de l'application. */
    public static void closeConnection() {
        synchronized (DBConnector.class) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                LOG.info("✓ Pool de connexions fermé");
            }
            dataSource = null;
        }
    }

    /** Vérifie que la base est joignable. */
    /**
     * Avertit lorsque la base est jointe à distance sans chiffrement.
     *
     * PGSSLMODE est facultatif : une caisse configurée à la main peut donc
     * dialoguer en clair avec un PostgreSQL situé sur le réseau, mots de passe
     * et montants compris. Le cas est signalé une fois au démarrage plutôt
     * qu'imposé, changer la valeur par défaut pouvant empêcher une
     * installation existante de se connecter.
     */
    private static void avertirSiConnexionNonChiffree() {
        String url = Config.getJdbcUrl();
        if (url == null || url.contains("sslmode=")) {
            return;
        }
        // Une base locale n'emprunte pas le réseau : l'avertissement n'aurait
        // aucun sens sur un poste unique, cas le plus courant.
        boolean locale = url.contains("//localhost") || url.contains("//127.0.0.1")
                || url.contains("//[::1]");
        if (!locale) {
            LOG.warn("Connexion à une base distante sans chiffrement : "
                    + "renseignez PGSSLMODE (par exemple « require ») pour protéger "
                    + "les identifiants et les montants en transit.");
        }
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            LOG.error("✗ Test de connexion échoué : " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Message d'erreur lisible pour l'utilisateur final, à afficher dans l'interface.
     */
    public static String diagnose(SQLException e) {
        String state = e.getSQLState();
        if (state == null) {
            return e.getMessage();
        }
        switch (state) {
            case "28P01":
                return "Mot de passe ou utilisateur PostgreSQL incorrect.";
            case "3D000":
                return "La base de données n'existe pas. Exécutez le script d'installation.";
            case "08001":
            case "08006":
                return "Serveur PostgreSQL injoignable. Vérifiez la connexion réseau.";
            case "42P01":
                return "Table manquante. La base n'a pas été initialisée.";
            default:
                return e.getMessage();
        }
    }
}

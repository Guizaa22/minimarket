package util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration de l'application (PostgreSQL).
 *
 * Ordre de résolution, du plus prioritaire au moins prioritaire :
 *   1. Variable d'environnement DATABASE_URL (format Railway/Heroku)
 *   2. Variables d'environnement PGHOST / PGPORT / PGDATABASE / PGUSER / PGPASSWORD
 *   3. Fichier de configuration utilisateur : %APPDATA%\2M-Market\database.properties
 *   4. Valeurs par défaut (PostgreSQL local)
 *
 * Aucun mot de passe n'est stocké dans le code source ni dans le dépôt Git.
 */
public final class Config {

    /** Dossier de données de l'application (base de données locale, tickets, exports). */
    private static final Path APP_DATA_DIR = resolveAppDataDir();

    private static final String CONFIG_FILE_NAME = "database.properties";

    private static String jdbcUrl;
    private static String dbUser;
    private static String dbPassword;
    private static String dbSchema = "public";
    private static int poolSize = 5;

    static {
        load();
    }

    private Config() {
    }

    // ------------------------------------------------------------------
    // Chargement
    // ------------------------------------------------------------------

    private static void load() {
        Properties fileProps = readConfigFile();

        String databaseUrl = firstNonBlank(System.getProperty("DATABASE_URL"),
                                           System.getenv("DATABASE_URL"),
                                           fileProps.getProperty("DATABASE_URL"));

        if (databaseUrl != null) {
            applyDatabaseUrl(databaseUrl);
        } else {
            String host = resolve("PGHOST", fileProps, "localhost");
            String port = resolve("PGPORT", fileProps, "5432");
            String name = resolve("PGDATABASE", fileProps, "market2m");
            dbUser = resolve("PGUSER", fileProps, "market_app");
            dbPassword = resolve("PGPASSWORD", fileProps, "");
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + name;
        }

        String sslMode = resolve("PGSSLMODE", fileProps, null);
        if (sslMode != null && !jdbcUrl.contains("sslmode=")) {
            jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=" + sslMode;
        }

        // Schéma cible. Les tests utilisent un schéma dédié pour ne jamais
        // écrire dans les données d'exploitation.
        String schema = resolve("PGSCHEMA", fileProps, null);
        if (schema != null && schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            dbSchema = schema;
        }

        String size = resolve("DB_POOL_SIZE", fileProps, null);
        if (size != null) {
            try {
                poolSize = Math.max(1, Integer.parseInt(size.trim()));
            } catch (NumberFormatException ignored) {
                // conserver la valeur par défaut
            }
        }
    }

    /**
     * Convertit une URL de type {@code postgresql://user:pass@host:port/db}
     * (fournie par Railway, Heroku, Neon, Supabase...) en URL JDBC.
     */
    private static void applyDatabaseUrl(String databaseUrl) {
        String raw = databaseUrl.trim();

        if (raw.startsWith("jdbc:postgresql://")) {
            jdbcUrl = raw;
            dbUser = firstNonBlank(System.getenv("PGUSER"), "");
            dbPassword = firstNonBlank(System.getenv("PGPASSWORD"), "");
            return;
        }

        URI uri = URI.create(raw);
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int sep = userInfo.indexOf(':');
            if (sep >= 0) {
                dbUser = urlDecode(userInfo.substring(0, sep));
                dbPassword = urlDecode(userInfo.substring(sep + 1));
            } else {
                dbUser = urlDecode(userInfo);
                dbPassword = "";
            }
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getPath() == null ? "" : uri.getPath();
        jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + path;

        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbcUrl += "?" + uri.getQuery();
        }
    }

    private static Properties readConfigFile() {
        Properties props = new Properties();

        // 1. Fichier dans le dossier de données utilisateur (recommandé en production)
        Path userConfig = APP_DATA_DIR.resolve(CONFIG_FILE_NAME);
        if (Files.isReadable(userConfig)) {
            try (InputStream in = Files.newInputStream(userConfig)) {
                props.load(in);
                return props;
            } catch (IOException e) {
                System.err.println("Impossible de lire " + userConfig + " : " + e.getMessage());
            }
        }

        // 2. Fichier à la racine du projet (pratique en développement)
        Path devConfig = Paths.get(System.getProperty("user.dir"), CONFIG_FILE_NAME);
        if (Files.isReadable(devConfig)) {
            try (InputStream in = Files.newInputStream(devConfig)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Impossible de lire " + devConfig + " : " + e.getMessage());
            }
        }

        return props;
    }

    /**
     * Priorité : propriété système (-Dcle=...) &gt; variable d'environnement &gt;
     * fichier de configuration &gt; valeur par défaut.
     *
     * La propriété système permet aux tests de viser une base dédiée sans
     * modifier la configuration de l'utilisateur.
     */
    private static String resolve(String key, Properties fileProps, String defaultValue) {
        return firstNonBlank(System.getProperty(key),
                             System.getenv(key),
                             fileProps.getProperty(key),
                             defaultValue);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Path resolveAppDataDir() {
        String appData = System.getenv("APPDATA");
        Path base = (appData != null && !appData.isBlank())
                ? Paths.get(appData)
                : Paths.get(System.getProperty("user.home"), ".config");

        Path dir = base.resolve("2M-Market");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("Impossible de créer le dossier de données " + dir + " : " + e.getMessage());
            return Paths.get(System.getProperty("user.dir"));
        }
        return dir;
    }

    // ------------------------------------------------------------------
    // Accès
    // ------------------------------------------------------------------

    public static String getJdbcUrl() {
        return jdbcUrl;
    }

    public static String getDbUser() {
        return dbUser == null ? "" : dbUser;
    }

    public static String getDbPassword() {
        return dbPassword == null ? "" : dbPassword;
    }

    public static int getPoolSize() {
        return poolSize;
    }

    /** Schéma PostgreSQL utilisé ("public" par défaut). */
    public static String getDbSchema() {
        return dbSchema;
    }

    /** true si l'application vise le schéma d'exploitation. */
    public static boolean isProductionSchema() {
        return "public".equalsIgnoreCase(dbSchema);
    }

    /** Dossier où écrire tickets, exports PDF et journaux. */
    public static Path getAppDataDir() {
        return APP_DATA_DIR;
    }

    /** Dossier où écrire les tickets de caisse. */
    public static Path getTicketsDir() {
        Path dir = APP_DATA_DIR.resolve("tickets");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("Impossible de créer le dossier tickets : " + e.getMessage());
            return APP_DATA_DIR;
        }
        return dir;
    }

    public static Path getConfigFilePath() {
        return APP_DATA_DIR.resolve(CONFIG_FILE_NAME);
    }

    /** Description non sensible de la connexion, sûre à afficher dans les logs. */
    public static String describe() {
        String safeUrl = jdbcUrl == null ? "(non configuré)" : jdbcUrl;
        return safeUrl + " (utilisateur: " + getDbUser() + ")";
    }

    /** Vérifie que la configuration minimale est présente. */
    public static boolean isConfigured() {
        return jdbcUrl != null && !jdbcUrl.isBlank() && !getDbUser().isBlank();
    }

    /** Ancien emplacement de la base SQLite, utilisé uniquement par la migration. */
    public static File legacySqliteFile() {
        return new File(System.getProperty("user.dir"), "MarketDB.db");
    }
}

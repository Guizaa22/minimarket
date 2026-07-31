package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Charge les paramètres de l'application depuis plusieurs sources.
 *
 * Ordre de priorité, du plus fort au plus faible :
 *   1. propriété système   -Dcle=valeur
 *   2. variable d'environnement
 *   3. %APPDATA%\2M-Market\database.properties      (secrets, hors dépôt)
 *   4. config.properties à la racine du projet      (développement)
 *   5. /config.properties dans le classpath         (valeurs non sensibles)
 *
 * Les mots de passe ne doivent jamais figurer dans les sources 4 et 5 : elles
 * finissent dans Git et dans le jar livré. Un modèle {@code config.properties.example}
 * est versionné pour documenter les clés attendues.
 */
public final class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String NOM_FICHIER = "database.properties";
    private static final String RESSOURCE_CLASSPATH = "/config.properties";

    private final Properties proprietes = new Properties();

    public ConfigLoader(Path dossierDonnees) {
        chargerClasspath();
        chargerFichier(Paths.get(System.getProperty("user.dir"), "config.properties"));
        chargerFichier(Paths.get(System.getProperty("user.dir"), NOM_FICHIER));
        chargerFichier(dossierDonnees.resolve(NOM_FICHIER));
    }

    // ------------------------------------------------------------------
    // Chargement
    // ------------------------------------------------------------------

    private void chargerClasspath() {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(RESSOURCE_CLASSPATH)) {
            if (in != null) {
                lire(new InputStreamReader(in, StandardCharsets.UTF_8), RESSOURCE_CLASSPATH);
            }
        } catch (IOException e) {
            LOG.warn("Lecture de {} impossible", RESSOURCE_CLASSPATH, e);
        }
    }

    private void chargerFichier(Path chemin) {
        if (!Files.isReadable(chemin)) {
            return;
        }
        // UTF-8 explicite : Properties.load(InputStream) suppose ISO-8859-1 et
        // corromprait un mot de passe contenant des caractères accentués.
        try (Reader reader = Files.newBufferedReader(chemin, StandardCharsets.UTF_8)) {
            lire(reader, chemin.toString());
        } catch (IOException e) {
            LOG.warn("Lecture de {} impossible", chemin, e);
        }
    }

    private void lire(Reader reader, String source) throws IOException {
        Properties chargees = new Properties();
        chargees.load(reader);
        // Les sources lues en dernier écrasent les précédentes.
        chargees.stringPropertyNames().forEach(
                cle -> proprietes.setProperty(nettoyer(cle), chargees.getProperty(cle)));
        LOG.debug("Configuration chargée depuis {} ({} clé(s))", source, chargees.size());
    }

    /**
     * Retire un éventuel BOM UTF-8 en tête de clé.
     * PowerShell écrit ses fichiers avec BOM ; sans ce nettoyage, la première
     * clé du fichier serait ignorée silencieusement.
     */
    private static String nettoyer(String cle) {
        return cle.startsWith("﻿") ? cle.substring(1) : cle;
    }

    // ------------------------------------------------------------------
    // Accès
    // ------------------------------------------------------------------

    /**
     * Valeur d'une clé, en respectant l'ordre de priorité.
     *
     * @param cle          nom de la clé (ex. {@code PGHOST})
     * @param valeurDefaut valeur si la clé est absente partout
     */
    public String get(String cle, String valeurDefaut) {
        String valeur = System.getProperty(cle);
        if (estRenseigne(valeur)) {
            return valeur.trim();
        }
        valeur = System.getenv(cle);
        if (estRenseigne(valeur)) {
            return valeur.trim();
        }
        valeur = proprietes.getProperty(cle);
        if (estRenseigne(valeur)) {
            return valeur.trim();
        }
        return valeurDefaut;
    }

    public String get(String cle) {
        return get(cle, null);
    }

    public int getInt(String cle, int valeurDefaut) {
        String valeur = get(cle);
        if (valeur == null) {
            return valeurDefaut;
        }
        try {
            return Integer.parseInt(valeur);
        } catch (NumberFormatException e) {
            LOG.warn("{} = « {} » n'est pas un entier, valeur par défaut {} utilisée",
                    cle, valeur, valeurDefaut);
            return valeurDefaut;
        }
    }

    private static boolean estRenseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}

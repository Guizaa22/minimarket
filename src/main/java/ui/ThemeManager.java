package ui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Scene;
import javafx.stage.Window;
import util.Config;

/**
 * Thème visuel de l'application, commutable à chaud.
 *
 * Le réglage est mémorisé par poste, dans le dossier de données de
 * l'application : la caisse près de la vitrine peut rester en clair pendant
 * que celle du fond est en sombre. Aucune colonne n'est ajoutée en base, et
 * le réglage survit à un changement d'employé.
 *
 * Les scènes ouvertes sont enregistrées ici afin que le basculement
 * s'applique immédiatement à toutes, sans rouvrir les écrans.
 */
public final class ThemeManager {

    private static final Logger LOG = LoggerFactory.getLogger(ThemeManager.class);

    public enum Theme {
        SOMBRE("/styles/theme-dark.css", "Sombre", "🌙"),
        CLAIR("/styles/theme-light.css", "Clair", "☀");

        private final String feuille;
        private final String libelle;
        private final String icone;

        Theme(String feuille, String libelle, String icone) {
            this.feuille = feuille;
            this.libelle = libelle;
            this.icone = icone;
        }

        public String getLibelle() {
            return libelle;
        }

        public String getIcone() {
            return icone;
        }

        public Theme inverse() {
            return this == SOMBRE ? CLAIR : SOMBRE;
        }
    }

    private static final String FICHIER = "preferences.properties";
    private static final String CLE = "theme";

    /** Sombre par défaut : un écran de caisse reste allumé toute la journée. */
    private static Theme courant = Theme.SOMBRE;

    /**
     * Scènes à retoucher lors d'un basculement.
     * LinkedHashSet : pas de doublon si une scène est enregistrée deux fois,
     * et ordre d'insertion conservé pour un rendu prévisible.
     */
    private static final Set<Scene> scenes = new LinkedHashSet<>();

    static {
        courant = lirePreference();
    }

    private ThemeManager() {
    }

    // ------------------------------------------------------------------

    public static Theme getTheme() {
        return courant;
    }

    /**
     * Enregistre une scène et lui applique le thème courant.
     * À appeler à chaque création de scène.
     */
    public static void enregistrer(Scene scene) {
        if (scene == null) {
            return;
        }
        scenes.add(scene);
        appliquer(scene, courant);
    }

    /** Bascule sombre / clair et met à jour toutes les scènes ouvertes. */
    public static void basculer() {
        definir(courant.inverse());
    }

    public static void definir(Theme theme) {
        if (theme == null || theme == courant) {
            return;
        }
        courant = theme;

        // Les scènes fermées sont retirées au passage, pour ne pas retenir
        // indéfiniment des fenêtres détruites.
        scenes.removeIf(s -> s.getWindow() == null);
        scenes.forEach(s -> appliquer(s, theme));

        ecrirePreference(theme);
        LOG.info("Thème appliqué : {}", theme.getLibelle());
    }

    // ------------------------------------------------------------------

    /**
     * Remplace la feuille de thème sans toucher aux autres.
     *
     * Retire d'abord toutes les feuilles de thème connues : sans cela, les
     * deux resteraient chargées et la dernière appliquée l'emporterait de
     * façon imprévisible.
     */
    private static void appliquer(Scene scene, Theme theme) {
        for (Theme t : Theme.values()) {
            String url = ressource(t.feuille);
            if (url != null) {
                scene.getStylesheets().remove(url);
            }
        }

        String url = ressource(theme.feuille);
        if (url == null) {
            LOG.warn("Feuille de thème introuvable : {}", theme.feuille);
            return;
        }
        // Ajoutée en dernier pour primer sur global.css et modern.css.
        scene.getStylesheets().add(url);
    }

    private static String ressource(String chemin) {
        java.net.URL url = ThemeManager.class.getResource(chemin);
        return url != null ? url.toExternalForm() : null;
    }

    // ------------------------------------------------------------------
    // Persistance
    // ------------------------------------------------------------------

    private static Path fichierPreferences() {
        return Config.getAppDataDir().resolve(FICHIER);
    }

    private static Theme lirePreference() {
        Path chemin = fichierPreferences();
        if (!Files.isReadable(chemin)) {
            return Theme.SOMBRE;
        }
        try (Reader reader = Files.newBufferedReader(chemin, StandardCharsets.UTF_8)) {
            Properties props = new Properties();
            props.load(reader);
            String valeur = props.getProperty(CLE);
            if (valeur != null) {
                return Theme.valueOf(valeur.trim().toUpperCase());
            }
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Préférence de thème illisible, retour au thème sombre", e);
        }
        return Theme.SOMBRE;
    }

    private static void ecrirePreference(Theme theme) {
        Path chemin = fichierPreferences();
        Properties props = new Properties();

        // Relecture préalable : ce fichier accueillera d'autres préférences,
        // les écraser à chaque changement de thème les perdrait.
        if (Files.isReadable(chemin)) {
            try (Reader reader = Files.newBufferedReader(chemin, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                LOG.debug("Préférences existantes illisibles, fichier recréé", e);
            }
        }

        props.setProperty(CLE, theme.name());
        try (Writer writer = Files.newBufferedWriter(chemin, StandardCharsets.UTF_8)) {
            props.store(writer, "Préférences 2M Market (propres à ce poste)");
        } catch (IOException e) {
            // Non bloquant : le thème reste appliqué pour la session en cours.
            LOG.warn("Enregistrement de la préférence de thème impossible", e);
        }
    }

    /** Applique le thème courant à toutes les fenêtres actuellement ouvertes. */
    public static void rafraichirFenetresOuvertes() {
        Window.getWindows().stream()
                .filter(Window::isShowing)
                .map(Window::getScene)
                .filter(java.util.Objects::nonNull)
                .forEach(ThemeManager::enregistrer);
    }
}

package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Classe utilitaire pour charger les vues FXML
 */
public class FXMLUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FXMLUtils.class);

    /**
     * Historique de navigation, alimenté à chaque {@link #changeScene}. Permet au
     * bouton « Retour » de la barre supérieure de revenir à l'écran précédent sans
     * que chaque contrôleur ait à connaître d'où l'on vient.
     */
    private static final java.util.Deque<String[]> HISTORIQUE = new java.util.ArrayDeque<>();

    /** Écran actuellement affiché : {chemin FXML, titre}. */
    private static String[] courant;


    /**
     * Charge une vue FXML et retourne le Parent
     * @param fxmlPath Le chemin vers le fichier FXML (ex: "/view/Connexion.fxml")
     * @return Le Parent chargé
     * @throws IOException Si le fichier FXML ne peut pas être chargé
     */
    public static Parent loadFXML(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(FXMLUtils.class.getResource(fxmlPath));
        return loader.load();
    }
    
    /**
     * Charge une vue FXML et change la scène d'une fenêtre
     * @param stage La fenêtre dont la scène doit être changée
     * @param fxmlPath Le chemin vers le fichier FXML
     * @param title Le titre de la fenêtre
     * @throws IOException Si le fichier FXML ne peut pas être chargé
     */
    public static void changeScene(Stage stage, String fxmlPath, String title) throws IOException {
        naviguer(stage, fxmlPath, title, true);
    }

    /**
     * Revient à l'écran précédent, s'il y en a un. Sans effet sinon.
     */
    public static void retour(Stage stage) throws IOException {
        if (HISTORIQUE.isEmpty()) {
            return;
        }
        String[] cible = HISTORIQUE.pop();
        naviguer(stage, cible[0], cible[1], false);
    }

    /** true s'il existe un écran vers lequel {@link #retour} peut revenir. */
    public static boolean peutRevenir() {
        return !HISTORIQUE.isEmpty();
    }

    /**
     * Retourne à l'écran d'accueil correspondant au rôle : tableau de bord pour un
     * administrateur, interface caisse pour un employé. Vide l'historique.
     */
    public static void accueil(Stage stage) throws IOException {
        HISTORIQUE.clear();
        courant = null;
        boolean admin = service.SessionContext.get().estAdmin();
        if (admin) {
            naviguer(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur", false);
        } else {
            naviguer(stage, "/view/CaisseCategories.fxml", "Catégories", false);
        }
    }

    /** Oublie tout l'historique (à la déconnexion, avant de revenir à la connexion). */
    public static void reinitialiserHistorique() {
        HISTORIQUE.clear();
        courant = null;
    }

    private static void naviguer(Stage stage, String fxmlPath, String title, boolean historiser)
            throws IOException {
        // L'écran de connexion est un point de départ : on n'y revient jamais par
        // « Retour », donc il remet l'historique à zéro.
        if (fxmlPath.contains("Connexion")) {
            HISTORIQUE.clear();
            courant = null;
        } else if (historiser && courant != null && !courant[0].equals(fxmlPath)) {
            HISTORIQUE.push(courant);
        }

        Parent contenu = loadFXML(fxmlPath);

        // La barre supérieure est ajoutée ici, autour du contenu FXML : la
        // recopier dans quinze fichiers FXML garantirait qu'ils divergent dès
        // la première retouche. L'écran de connexion en est exempté, il n'a
        // ni employé connecté ni navigation.
        Parent root = contenu;
        if (!fxmlPath.contains("Connexion")) {
            javafx.scene.layout.HBox barre = ui.BarreHaut.creer(title);

            // Empilement vertical strict : la barre puis le contenu, qui prend
            // toute la hauteur restante. Les racines FXML sont des AnchorPane
            // aux ancrages absolus ; posées dans un BorderPane elles pouvaient
            // déborder de la zone disponible et passer sous la barre.
            javafx.scene.layout.VBox cadre = new javafx.scene.layout.VBox(barre, contenu);
            javafx.scene.layout.VBox.setVgrow(contenu, javafx.scene.layout.Priority.ALWAYS);
            barre.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            barre.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            root = cadre;
        }
        root.setOpacity(0);

        // Get screen dimensions for proper full screen sizing
        javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();

        // Create scene with screen dimensions to ensure full screen works properly
        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());

        // Feuilles communes, puis la feuille propre à l'écran, puis le thème.
        //
        // Ces feuilles étaient auparavant déclarées par l'attribut stylesheets
        // du nœud racine de chaque FXML. Or une feuille posée sur un nœud prime
        // sur celles de la scène : le thème était donc systématiquement battu,
        // quel que soit son ordre de chargement. Chargées ici au niveau de la
        // scène, elles rentrent dans la hiérarchie normale et le thème, ajouté
        // en dernier, l'emporte.
        appliquerFeuille(scene, "/styles/global.css");
        appliquerFeuille(scene, "/styles/modern.css");
        for (String feuille : feuillesDeLEcran(fxmlPath)) {
            appliquerFeuille(scene, feuille);
        }

        // Enregistre la scène et applique le thème retenu sur ce poste.
        ui.ThemeManager.enregistrer(scene);

        stage.setScene(scene);
        stage.setTitle(title);

        // Mémorise l'écran affiché pour que le prochain changement puisse
        // l'empiler dans l'historique de « Retour ».
        if (!fxmlPath.contains("Connexion")) {
            courant = new String[]{fxmlPath, title};
        }

        // Full screen mode for all interfaces - clear and professional display
        stage.setResizable(true);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("Appuyez sur Échap pour quitter le mode plein écran");
        
        // Ensure full screen is applied after showing
        javafx.application.Platform.runLater(() -> {
            stage.setFullScreen(true);
        });
        
        // Fondu combiné à une légère remontée : le changement d'écran est perçu
        // comme un enchaînement plutôt que comme un clignotement.
        root.setOpacity(0);
        root.setTranslateY(18);

        FadeTransition apparition = new FadeTransition(Duration.millis(260), root);
        apparition.setFromValue(0);
        apparition.setToValue(1);

        javafx.animation.TranslateTransition remontee =
                new javafx.animation.TranslateTransition(Duration.millis(260), root);
        remontee.setFromY(18);
        remontee.setToY(0);
        remontee.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

        new javafx.animation.ParallelTransition(apparition, remontee).play();
    }
    
    /**
     * Feuille spécifique à un écran, le cas échéant.
     *
     * Reprend les correspondances qui figuraient dans l'attribut stylesheets
     * de chaque FXML, désormais chargées au niveau de la scène pour que le
     * thème puisse primer.
     */
    private static java.util.List<String> feuillesDeLEcran(String fxmlPath) {
        if (fxmlPath == null) {
            return java.util.List.of();
        }
        if (fxmlPath.contains("AdminDashboard")) {
            return java.util.List.of("/styles/dashboard.css");
        }
        if (fxmlPath.contains("Caisse.fxml")) {
            return java.util.List.of("/styles/caisse.css");
        }
        if (fxmlPath.contains("CaisseCategories")) {
            return java.util.List.of("/styles/caissecategories.css");
        }
        if (fxmlPath.contains("GestionStock")) {
            return java.util.List.of("/styles/gestionstock.css");
        }
        if (fxmlPath.contains("GestionUtilisateurs")) {
            return java.util.List.of("/styles/gestionutilisateurs.css");
        }
        if (fxmlPath.contains("GestionVentes")) {
            return java.util.List.of("/styles/gestion-ventes.css");
        }
        if (fxmlPath.contains("CategorieProduits") || fxmlPath.contains("GestionTabac")
                || fxmlPath.contains("AjoutStock") || fxmlPath.contains("VisualisationProduits")) {
            return java.util.List.of("/styles/product-card.css");
        }
        if (fxmlPath.contains("Connexion")) {
            // Chargée au niveau de la scène (et non sur le nœud racine dans le
            // contrôleur) : ainsi les variables du thème se résolvent et la
            // feuille de thème, ajoutée en dernier, prime là où login.css ne
            // fixe pas la couleur.
            return java.util.List.of("/styles/login.css");
        }
        return java.util.List.of();
    }

    /**
     * Applique les feuilles communes (global, modern) et le thème courant à une
     * scène de dialogue construite à la main.
     *
     * Les fenêtres créées directement avec {@code new Scene(...)} (recherche
     * caisse, fiche produit, note…) s'ouvraient sinon avec l'apparence système
     * par défaut — fond gris clair, insensible au mode sombre — en décalage avec
     * le reste de l'application. À appeler juste après la création de la scène.
     */
    public static void appliquerStylesDialogue(Scene scene) {
        if (scene == null) {
            return;
        }
        appliquerFeuille(scene, "/styles/global.css");
        appliquerFeuille(scene, "/styles/modern.css");
        ui.ThemeManager.enregistrer(scene);
    }

    /**
     * Ouvre une vue FXML dans une fenêtre modale, centrée sur l'écran et thémée.
     *
     * Factorise l'ouverture des boîtes (note du jour…) qui était recopiée
     * intégralement dans les contrôleurs : dimensions, styles de dialogue,
     * modalité et centrage y étaient répétés à l'identique.
     *
     * @param owner fenêtre propriétaire (pour la modalité), ou {@code null}
     */
    public static void ouvrirModal(String fxmlPath, String titre, javafx.stage.Window owner)
            throws IOException {
        Parent contenu = loadFXML(fxmlPath);

        javafx.stage.Screen ecran = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D bornes = ecran.getVisualBounds();
        double largeur = Math.min(600, bornes.getWidth() * 0.5);
        double hauteur = Math.min(500, bornes.getHeight() * 0.6);

        Scene scene = new Scene(contenu, largeur, hauteur);
        appliquerStylesDialogue(scene);

        Stage dialogue = new Stage();
        dialogue.setTitle(titre);
        dialogue.setScene(scene);
        dialogue.setResizable(true);
        dialogue.initModality(javafx.stage.Modality.WINDOW_MODAL);
        if (owner != null) {
            dialogue.initOwner(owner);
        }
        dialogue.setOnShown(e -> {
            dialogue.setX(bornes.getMinX() + (bornes.getWidth() - largeur) / 2);
            dialogue.setY(bornes.getMinY() + (bornes.getHeight() - hauteur) / 2);
        });
        dialogue.showAndWait();
    }

    /**
     * Ajoute une feuille de style à la scène si la ressource existe.
     * Une feuille absente est signalée mais n'empêche pas l'affichage.
     */
    private static void appliquerFeuille(Scene scene, String chemin) {
        try {
            java.net.URL url = FXMLUtils.class.getResource(chemin);
            if (url == null) {
                LOG.warn("Feuille de style introuvable : {}", chemin);
                return;
            }
            String feuille = url.toExternalForm();
            if (!scene.getStylesheets().contains(feuille)) {
                scene.getStylesheets().add(feuille);
            }
        } catch (Exception e) {
            LOG.warn("Chargement de {} impossible", chemin, e);
        }
    }

    /**
     * Charge une vue FXML avec un contrôleur personnalisé
     * @param fxmlPath Le chemin vers le fichier FXML
     * @param controller Le contrôleur à utiliser
     * @return Le Parent chargé
     * @throws IOException Si le fichier FXML ne peut pas être chargé
     */
    public static Parent loadFXML(String fxmlPath, Object controller) throws IOException {
        FXMLLoader loader = new FXMLLoader(FXMLUtils.class.getResource(fxmlPath));
        loader.setController(controller);
        return loader.load();
    }
}


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
        Parent contenu = loadFXML(fxmlPath);

        // La barre supérieure est ajoutée ici, autour du contenu FXML : la
        // recopier dans quinze fichiers FXML garantirait qu'ils divergent dès
        // la première retouche. L'écran de connexion en est exempté, il n'a
        // ni employé connecté ni navigation.
        Parent root = contenu;
        if (!fxmlPath.contains("Connexion")) {
            javafx.scene.layout.BorderPane cadre = new javafx.scene.layout.BorderPane();
            cadre.setTop(ui.BarreHaut.creer(title));
            cadre.setCenter(contenu);
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
        return java.util.List.of();
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


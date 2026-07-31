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
        Parent root = loadFXML(fxmlPath);
        root.setOpacity(0);
        
        // Get screen dimensions for proper full screen sizing
        javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();
        
        // Create scene with screen dimensions to ensure full screen works properly
        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());
        
        // Feuilles communes à tous les écrans. L'ordre compte : modern.css
        // affine ce que global.css a posé, il doit donc être chargé après.
        appliquerFeuille(scene, "/styles/global.css");
        appliquerFeuille(scene, "/styles/modern.css");
        
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


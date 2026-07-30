package util;

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
        
        // Apply global CSS to all scenes
        try {
            java.net.URL cssUrl = FXMLUtils.class.getResource("/styles/global.css");
            if (cssUrl != null) {
                String globalCss = cssUrl.toExternalForm();
                scene.getStylesheets().add(globalCss);
            } else {
                System.err.println("Warning: CSS file /styles/global.css not found, continuing without styles");
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load CSS file: " + e.getMessage());
        }
        
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
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(320), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
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


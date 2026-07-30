package view;

import javafx.scene.Parent;
import javafx.stage.Stage;
import util.FXMLUtils;

import java.io.IOException;

/**
 * Classe pour gérer le changement de scène/vue
 */
public class MainView {
    
    /**
     * Change la scène de la fenêtre principale
     * @param stage La fenêtre
     * @param fxmlPath Le chemin vers le fichier FXML
     * @param title Le titre de la fenêtre
     * @throws IOException Si le fichier FXML ne peut pas être chargé
     */
    public static void changeScene(Stage stage, String fxmlPath, String title) throws IOException {
        FXMLUtils.changeScene(stage, fxmlPath, title);
    }
    
    /**
     * Charge une vue FXML et retourne le Parent
     * @param fxmlPath Le chemin vers le fichier FXML
     * @return Le Parent chargé
     * @throws IOException Si le fichier FXML ne peut pas être chargé
     */
    public static Parent loadFXML(String fxmlPath) throws IOException {
        return FXMLUtils.loadFXML(fxmlPath);
    }
}


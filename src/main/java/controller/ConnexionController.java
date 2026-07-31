package controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Utilisateur;
import service.AuthService;
import util.FXMLUtils;
import util.SessionManager;

/**
 * Contrôleur pour l'interface de connexion
 */
public class ConnexionController {

    private static final Logger LOG = LoggerFactory.getLogger(ConnexionController.class);

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    private final AuthService authService;
    private static Utilisateur utilisateurConnecte;

    public ConnexionController() {
        authService = new AuthService();
    }
    
    @FXML
    private void initialize() {
        // Focus sur le champ username au démarrage
        usernameField.requestFocus();
        
        // Ajouter le CSS programmatiquement et l'image de fond
        javafx.application.Platform.runLater(() -> {
            if (usernameField != null && usernameField.getScene() != null) {
                javafx.scene.Parent root = usernameField.getScene().getRoot();
                if (root != null) {
                    // Ajouter le CSS
                    String cssUrl = getClass().getResource("/styles/login.css").toExternalForm();
                    if (!root.getStylesheets().contains(cssUrl)) {
                        root.getStylesheets().add(cssUrl);
                    }
                    
                    // S'assurer que l'image de fond est appliquée
                    if (root instanceof javafx.scene.layout.AnchorPane) {
                        javafx.scene.layout.AnchorPane anchorPane = (javafx.scene.layout.AnchorPane) root;
                        String bgImage = getClass().getResource("/background/backgroundlogin.jpg").toExternalForm();
                        anchorPane.setStyle(
                            "-fx-background-image: url('" + bgImage + "'); " +
                            "-fx-background-size: cover; " +
                            "-fx-background-position: center; " +
                            "-fx-background-repeat: no-repeat;"
                        );
                    }
                }
            }
        });
    }
    
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champs vides", 
                     "Veuillez remplir tous les champs.");
            return;
        }
        
        // L'ouverture de session est portée par AuthService : le contrôleur ne
        // manipule plus l'état de session directement. C'est l'oubli d'un de ces
        // appels qui faisait renvoyer -1 à getCurrentUserId() dans toute
        // l'application, et attribuait toutes les ventes au compte admin.
        java.util.Optional<Utilisateur> connecte = authService.connecter(username, password);

        if (connecte.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Échec de connexion",
                     "Nom d'utilisateur ou mot de passe incorrect.");
            passwordField.clear();
            return;
        }

        Utilisateur utilisateur = connecte.get();
        utilisateurConnecte = utilisateur;
        SessionManager.startSession(utilisateur);

        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();

            if (utilisateur.isAdmin()) {
                FXMLUtils.changeScene(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur");
            } else {
                FXMLUtils.changeScene(stage, "/view/CaisseCategories.fxml", "Catégories");
            }
        } catch (IOException e) {
            LOG.error("Chargement de l'interface impossible après connexion", e);
            showAlert(Alert.AlertType.ERROR, "Erreur",
                     "Erreur lors du chargement de l'interface : " + e.getMessage());
        }
    }
    
    @FXML
    private void handleEnterKey() {
        handleLogin();
    }
    
    public static Utilisateur getUtilisateurConnecte() {
        return utilisateurConnecte;
    }
    
    /**
     * Ferme la session : vide le panier, journalise et efface l'utilisateur des
     * trois emplacements qui le mémorisent encore (SessionContext, SessionManager
     * et ce champ statique, conservés le temps de la migration des contrôleurs).
     */
    public static void deconnecter() {
        new AuthService().deconnecter();
        utilisateurConnecte = null;
        SessionManager.endSession();
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


package controller;

import java.io.IOException;

import dao.UtilisateurDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Utilisateur;
import util.FXMLUtils;
import util.SessionManager;

/**
 * Contrôleur pour l'interface de connexion
 */
public class ConnexionController {
    
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    private final UtilisateurDAO utilisateurDAO;
    private static Utilisateur utilisateurConnecte;
    
    public ConnexionController() {
        utilisateurDAO = new UtilisateurDAO();
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
        
        Utilisateur utilisateur = utilisateurDAO.authenticate(username, password);

        if (utilisateur != null) {
            utilisateurConnecte = utilisateur;
            // Sans cet appel, SessionManager.getCurrentUserId() renvoie -1 partout
            // dans l'application : les ventes étaient attribuées à un utilisateur
            // par défaut et les ajouts de stock n'étaient jamais enregistrés.
            SessionManager.startSession(utilisateur);

            try {
                Stage stage = (Stage) usernameField.getScene().getWindow();
                
                if (utilisateur.isAdmin()) {
                    // Rediriger vers le dashboard admin
                    FXMLUtils.changeScene(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur");
                } else {
                    // Rediriger vers les catégories pour les employés (page principale)
                    FXMLUtils.changeScene(stage, "/view/CaisseCategories.fxml", "Catégories");
                }
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Erreur", 
                         "Erreur lors du chargement de l'interface: " + e.getMessage());
            }
        } else {
            showAlert(Alert.AlertType.ERROR, "Échec de connexion", 
                     "Nom d'utilisateur ou mot de passe incorrect.");
            passwordField.clear();
        }
    }
    
    @FXML
    private void handleEnterKey() {
        handleLogin();
    }
    
    public static Utilisateur getUtilisateurConnecte() {
        return utilisateurConnecte;
    }
    
    public static void deconnecter() {
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


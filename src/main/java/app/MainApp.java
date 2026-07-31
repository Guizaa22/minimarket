package app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import dao.DBConnector;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import util.Config;
import util.DatabaseSetup;
import util.FXMLUtils;

/**
 * Point d'entrée de l'application.
 */
public class MainApp extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(MainApp.class);


    @Override
    public void start(Stage primaryStage) {
        try {
            LOG.info("Démarrage de 2M Market...");

            if (!DBConnector.testConnection()) {
                showFatalError(
                    "Connexion à la base de données impossible",
                    "L'application n'a pas pu joindre PostgreSQL.\n\n"
                        + "Configuration utilisée :\n" + Config.describe() + "\n\n"
                        + "Vérifiez :\n"
                        + "  • que le serveur PostgreSQL est démarré,\n"
                        + "  • le fichier " + Config.getConfigFilePath() + ",\n"
                        + "  • ou la variable d'environnement DATABASE_URL.");
                return;
            }

            if (!DatabaseSetup.initializeDatabase()) {
                showFatalError(
                    "Initialisation de la base échouée",
                    "Le schéma de la base n'a pas pu être créé ou mis à jour.\n"
                        + "Consultez la console pour le détail de l'erreur.");
                return;
            }

            // Aucun mot de passe par défaut n'est livré avec l'application :
            // le premier administrateur est créé ici, par l'utilisateur.
            if (!DatabaseSetup.hasAdminAccount() && !promptForInitialAdmin()) {
                showFatalError(
                    "Aucun compte administrateur",
                    "L'application ne peut pas démarrer sans compte administrateur.");
                return;
            }

            FXMLUtils.changeScene(primaryStage, "/view/Connexion.fxml", "Connexion - 2M Market");
            primaryStage.setResizable(true);
            primaryStage.setWidth(1200);
            primaryStage.setHeight(800);
            primaryStage.centerOnScreen();
            primaryStage.show();

            javafx.application.Platform.runLater(() -> {
                try {
                    primaryStage.setFullScreen(true);
                    primaryStage.setFullScreenExitHint("Appuyez sur Échap pour quitter le plein écran");
                } catch (Exception e) {
                    LOG.error("Plein écran indisponible : " + e.getMessage(), e);
                }
            });

            LOG.info("Application démarrée.");

        } catch (Exception e) {
            showFatalError("L'application n'a pas pu démarrer",
                    e.getClass().getSimpleName() + " : " + e.getMessage());
        }
    }

    /**
     * Ferme proprement le pool de connexions.
     * Cette méthode manquait : le pool n'était jamais libéré à l'arrêt.
     */
    @Override
    public void stop() {
        ui.TacheFond.arreter();
        DBConnector.closeConnection();
    }

    /** Demande la création du premier compte administrateur au tout premier lancement. */
    private boolean promptForInitialAdmin() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Première configuration");
        dialog.setHeaderText("Créer le compte administrateur");

        TextField username = new TextField("admin");
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        Label error = new Label();
        error.setStyle("-fx-text-fill: #D32F2F;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Nom d'utilisateur :"), username);
        grid.addRow(1, new Label("Mot de passe :"), password);
        grid.addRow(2, new Label("Confirmation :"), confirm);
        grid.add(error, 0, 3, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        while (true) {
            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return false;
            }

            String user = username.getText().trim();
            String pass = password.getText();

            if (user.isEmpty()) {
                error.setText("Le nom d'utilisateur est obligatoire.");
                continue;
            }
            if (pass.length() < 8) {
                error.setText("Le mot de passe doit contenir au moins 8 caractères.");
                continue;
            }
            if (!pass.equals(confirm.getText())) {
                error.setText("Les deux mots de passe ne correspondent pas.");
                continue;
            }
            if (DatabaseSetup.createInitialAdmin(user, pass)) {
                return true;
            }
            error.setText("Création impossible. Consultez la console.");
        }
    }

    private void showFatalError(String header, String content) {
        LOG.error("ERREUR : " + header + "\n" + content);
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("2M Market");
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.getDialogPane().setMinWidth(560);
            alert.showAndWait();
        } catch (Exception ignored) {
            // Interface graphique indisponible : le message console suffit.
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package util;

import java.util.Optional;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Gestionnaire centralisé des popups et notifications pour l'application
 * Fournit des popups modernes, élégants et bien positionnés
 */
public class PopupManager {

    // ========================================
    // TOAST NOTIFICATIONS (In-App)
    // ========================================

    /**
     * Affiche un toast de succès
     */
    public static void showSuccess(String message, StackPane rootPane) {
        showToast(message, "SUCCESS", rootPane);
    }

    /**
     * Affiche un toast d'information
     */
    public static void showInfo(String message, StackPane rootPane) {
        showToast(message, "INFO", rootPane);
    }

    /**
     * Affiche un toast d'avertissement
     */
    public static void showWarning(String message, StackPane rootPane) {
        showToast(message, "WARNING", rootPane);
    }

    /**
     * Affiche un toast d'erreur
     */
    public static void showError(String message, StackPane rootPane) {
        showToast(message, "ERROR", rootPane);
    }

    /**
     * Méthode générique pour afficher un toast
     */
    private static void showToast(String message, String type, StackPane rootPane) {
        if (rootPane == null) {
            // Fallback to standard alert if no root pane
            showStandardAlert(message, type);
            return;
        }

        // Créer le conteneur du toast
        HBox toast = new HBox(15);
        toast.setAlignment(Pos.CENTER);
        toast.setPadding(new Insets(20, 30, 20, 30));
        toast.setMaxWidth(600);
        toast.setStyle(getToastStyle(type));

        // Icône selon le type
        Label iconLabel = new Label(getIcon(type));
        iconLabel.setStyle("-fx-font-size: 28px;");

        // Message
        Label messageLabel = new Label(message);
        messageLabel.setStyle(
            "-fx-font-size: 16px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: white; " +
            "-fx-wrap-text: true;"
        );
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(450);

        // Bouton fermer
        Button closeButton = new Button("✕");
        closeButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 20px; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0 8 0 8;"
        );
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(
            "-fx-background-color: rgba(255,255,255,0.2); " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 20px; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0 8 0 8; " +
            "-fx-background-radius: 5;"
        ));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 20px; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0 8 0 8;"
        ));

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        toast.getChildren().addAll(iconLabel, messageLabel, spacer, closeButton);

        // Positionner le toast en haut centre
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(80, 20, 0, 20));

        rootPane.getChildren().add(toast);

        // Animation d'entrée
        toast.setOpacity(0);
        toast.setTranslateY(-50);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), toast);
        scaleIn.setFromX(0.8);
        scaleIn.setFromY(0.8);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);

        fadeIn.play();
        scaleIn.play();

        toast.setTranslateY(0);

        // Auto-fermeture après 5 secondes
        PauseTransition pause = new PauseTransition(Duration.seconds(5));
        pause.setOnFinished(e -> removeToast(toast, rootPane));

        // Fermeture manuelle
        closeButton.setOnAction(e -> removeToast(toast, rootPane));

        pause.play();
    }

    /**
     * Retire un toast avec animation
     */
    private static void removeToast(HBox toast, StackPane rootPane) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> rootPane.getChildren().remove(toast));
        fadeOut.play();
    }

    /**
     * Retourne le style CSS selon le type de toast
     */
    private static String getToastStyle(String type) {
        String baseStyle = 
            "-fx-background-radius: 12; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 15, 0, 0, 5);";

        switch (type) {
            case "SUCCESS":
                return baseStyle + "-fx-background-color: linear-gradient(to right, #43A047, #66BB6A);";
            case "INFO":
                return baseStyle + "-fx-background-color: linear-gradient(to right, #1E88E5, #42A5F5);";
            case "WARNING":
                return baseStyle + "-fx-background-color: linear-gradient(to right, #FB8C00, #FFB74D);";
            case "ERROR":
                return baseStyle + "-fx-background-color: linear-gradient(to right, #E53935, #EF5350);";
            default:
                return baseStyle + "-fx-background-color: linear-gradient(to right, #424242, #616161);";
        }
    }

    /**
     * Retourne l'icône selon le type
     */
    private static String getIcon(String type) {
        switch (type) {
            case "SUCCESS": return "✅";
            case "INFO": return "ℹ️";
            case "WARNING": return "⚠️";
            case "ERROR": return "❌";
            default: return "📢";
        }
    }

    // ========================================
    // DIALOG ALERTS (Modal Windows)
    // ========================================

    /**
     * Affiche une alerte de confirmation
     */
    public static boolean showConfirmation(String title, String message, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.CONFIRMATION, title, message, owner);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Affiche une alerte d'information
     */
    public static void showInformationAlert(String title, String message, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.INFORMATION, title, message, owner);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'erreur
     */
    public static void showErrorAlert(String title, String message, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.ERROR, title, message, owner);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'avertissement
     */
    public static void showWarningAlert(String title, String message, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.WARNING, title, message, owner);
        alert.showAndWait();
    }

    /**
     * Crée une alerte stylée
     */
    private static Alert createStyledAlert(Alert.AlertType type, String title, String message, Window owner) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Positionner l'alerte au centre du propriétaire
        if (owner != null) {
            alert.initOwner(owner);
            alert.initModality(Modality.APPLICATION_MODAL);
            
            // Centrer sur la fenêtre parente
            alert.setOnShown(e -> {
                alert.setX(owner.getX() + (owner.getWidth() - alert.getWidth()) / 2);
                alert.setY(owner.getY() + (owner.getHeight() - alert.getHeight()) / 2);
            });
        }

        // Appliquer le style
        alert.getDialogPane().setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: " + getBorderColor(type) + "; " +
            "-fx-border-width: 3; " +
            "-fx-border-radius: 10; " +
            "-fx-background-radius: 10; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 3);"
        );

        // Styliser les boutons
        alert.getDialogPane().lookupAll(".button").forEach(node -> {
            if (node instanceof Button) {
                Button button = (Button) node;
                button.setStyle(
                    "-fx-background-color: " + getButtonColor(type) + "; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-weight: bold; " +
                    "-fx-font-size: 14px; " +
                    "-fx-padding: 10 20; " +
                    "-fx-background-radius: 8; " +
                    "-fx-cursor: hand;"
                );
            }
        });

        return alert;
    }

    /**
     * Retourne la couleur de bordure selon le type
     */
    private static String getBorderColor(Alert.AlertType type) {
        switch (type) {
            case CONFIRMATION: return "#1E88E5";
            case INFORMATION: return "#42A5F5";
            case WARNING: return "#FB8C00";
            case ERROR: return "#E53935";
            default: return "#757575";
        }
    }

    /**
     * Retourne la couleur de bouton selon le type
     */
    private static String getButtonColor(Alert.AlertType type) {
        switch (type) {
            case CONFIRMATION: return "linear-gradient(to bottom, #1E88E5, #1976D2)";
            case INFORMATION: return "linear-gradient(to bottom, #42A5F5, #1E88E5)";
            case WARNING: return "linear-gradient(to bottom, #FB8C00, #F57C00)";
            case ERROR: return "linear-gradient(to bottom, #E53935, #D32F2F)";
            default: return "linear-gradient(to bottom, #757575, #616161)";
        }
    }

    /**
     * Fallback standard alert (si StackPane non disponible)
     */
    private static void showStandardAlert(String message, String type) {
        Alert.AlertType alertType;
        String title;

        switch (type) {
            case "SUCCESS":
                alertType = Alert.AlertType.INFORMATION;
                title = "Succès";
                break;
            case "WARNING":
                alertType = Alert.AlertType.WARNING;
                title = "Avertissement";
                break;
            case "ERROR":
                alertType = Alert.AlertType.ERROR;
                title = "Erreur";
                break;
            default:
                alertType = Alert.AlertType.INFORMATION;
                title = "Information";
        }

        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


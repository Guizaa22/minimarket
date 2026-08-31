package controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import dao.NoteJourDAO;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.NoteJour;
import util.SessionManager;

/**
 * Contrôleur pour le dialogue d'ajout de notes
 */
public class NoteDialogController {
    private static final Logger LOG = LoggerFactory.getLogger(NoteDialogController.class);

    
    @FXML
    private ComboBox<NoteJour.TypeNote> typeNoteComboBox;
    
    @FXML
    private TextField montantField;
    
    @FXML
    private TextArea descriptionField;
    
    @FXML
    private Button enregistrerButton;
    
    @FXML
    private Button annulerButton;
    
    @FXML
    private VBox montantContainer;
    
    @FXML
    private Label charCountLabel;
    
    @FXML
    private StackPane rootPane;
    
    private NoteJourDAO noteDAO;
    
    @FXML
    @SuppressWarnings("unused")
    private void initialize() {
        noteDAO = new NoteJourDAO();
        
        // Animation d'entrée pour le dialogue
        Platform.runLater(() -> animerEntree());
        
        // Remplir le ComboBox avec les types de notes
        typeNoteComboBox.getItems().addAll(NoteJour.TypeNote.values());
        typeNoteComboBox.setValue(NoteJour.TypeNote.Autre);
        
        // Configurer le formatage des cellules du ComboBox
        typeNoteComboBox.setCellFactory(listView -> new ListCell<NoteJour.TypeNote>() {
            @Override
            protected void updateItem(NoteJour.TypeNote item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getLibelle());
                    setStyle("-fx-font-size: 14px; -fx-padding: 10;");
                }
            }
        });
        
        // Afficher/masquer le champ montant selon le type avec animation
        typeNoteComboBox.setOnAction(e -> {
            NoteJour.TypeNote type = typeNoteComboBox.getValue();
            if (type == NoteJour.TypeNote.Credit || type == NoteJour.TypeNote.Sortie_Caisse) {
                afficherMontantAvecAnimation();
            } else {
                masquerMontantAvecAnimation();
            }
        });
        
        // Compteur de caractères pour la description
        descriptionField.textProperty().addListener((observable, oldValue, newValue) -> {
            int length = newValue != null ? newValue.length() : 0;
            charCountLabel.setText(length + " / 500 caractères");
            
            // Changer la couleur si proche de la limite
            if (length > 450) {
                charCountLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            } else if (length > 400) {
                charCountLabel.setStyle("-fx-font-size: 11px;");
            } else {
                charCountLabel.setStyle("-fx-font-size: 11px;");
            }
        });
        
        // Limiter la longueur de la description
        descriptionField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > 500) {
                descriptionField.setText(oldValue);
            }
        });
        
        // Masquer le champ montant par défaut
        montantContainer.setVisible(false);
        montantContainer.setManaged(false);
        montantContainer.setOpacity(0);
    }
    
    /**
     * Animation d'entrée pour le dialogue
     */
    private void animerEntree() {
        if (rootPane != null) {
            rootPane.setOpacity(0);
            rootPane.setScaleX(0.9);
            rootPane.setScaleY(0.9);
            
            ParallelTransition entree = new ParallelTransition(
                new FadeTransition(Duration.millis(300), rootPane),
                new ScaleTransition(Duration.millis(300), rootPane)
            );
            ((FadeTransition) entree.getChildren().get(0)).setFromValue(0);
            ((FadeTransition) entree.getChildren().get(0)).setToValue(1);
            ((ScaleTransition) entree.getChildren().get(1)).setFromX(0.9);
            ((ScaleTransition) entree.getChildren().get(1)).setFromY(0.9);
            ((ScaleTransition) entree.getChildren().get(1)).setToX(1.0);
            ((ScaleTransition) entree.getChildren().get(1)).setToY(1.0);
            
            entree.play();
        }
    }
    
    /**
     * Affiche le champ montant avec animation smooth
     */
    private void afficherMontantAvecAnimation() {
        montantContainer.setManaged(true);
        montantContainer.setVisible(true);
        montantContainer.setTranslateY(-10);
        montantContainer.setOpacity(0);
        
        ParallelTransition show = new ParallelTransition(
            new FadeTransition(Duration.millis(300), montantContainer),
            new TranslateTransition(Duration.millis(300), montantContainer)
        );
        ((FadeTransition) show.getChildren().get(0)).setFromValue(0);
        ((FadeTransition) show.getChildren().get(0)).setToValue(1);
        ((TranslateTransition) show.getChildren().get(1)).setFromY(-10);
        ((TranslateTransition) show.getChildren().get(1)).setToY(0);
        
        show.play();
        
        // Focus sur le champ montant après l'animation
        show.setOnFinished(e -> montantField.requestFocus());
    }
    
    /**
     * Masque le champ montant avec animation smooth
     */
    private void masquerMontantAvecAnimation() {
        FadeTransition hide = new FadeTransition(Duration.millis(250), montantContainer);
        hide.setFromValue(1);
        hide.setToValue(0);
        hide.setOnFinished(e -> {
            montantContainer.setVisible(false);
            montantContainer.setManaged(false);
            montantField.clear();
        });
        hide.play();
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleEnregistrer() {
        NoteJour.TypeNote type = typeNoteComboBox.getValue();
        String description = descriptionField.getText().trim();
        
        if (description.isEmpty()) {
            afficherAlerteAvecAnimation(Alert.AlertType.WARNING, "Attention", "Veuillez entrer une description.");
            animerErreur(descriptionField);
            return;
        }
        
        BigDecimal montant = BigDecimal.ZERO;
        if (type == NoteJour.TypeNote.Credit || type == NoteJour.TypeNote.Sortie_Caisse) {
            if (montantField.getText().trim().isEmpty()) {
                afficherAlerteAvecAnimation(Alert.AlertType.WARNING, "Attention", "Veuillez entrer un montant.");
                animerErreur(montantField);
                return;
            }
            try {
                montant = new BigDecimal(montantField.getText().trim());
                if (montant.compareTo(BigDecimal.ZERO) < 0) {
                    afficherAlerteAvecAnimation(Alert.AlertType.WARNING, "Attention", "Le montant ne peut pas être négatif.");
                    animerErreur(montantField);
                    return;
                }
            } catch (NumberFormatException e) {
                afficherAlerteAvecAnimation(Alert.AlertType.ERROR, "Erreur", "Montant invalide.");
                animerErreur(montantField);
                return;
            }
        }
        
        // La note doit être rattachée à l'employé réellement connecté.
        int idEmploye = SessionManager.getCurrentUserId();
        if (idEmploye <= 0) {
            afficherAlerteAvecAnimation(Alert.AlertType.ERROR, "Session expirée",
                "Aucun utilisateur connecté. Reconnectez-vous pour enregistrer une note.");
            return;
        }
        
        // Animation de chargement sur le bouton
        String texteOriginal = enregistrerButton.getText();
        enregistrerButton.setText("⏳ Enregistrement...");
        enregistrerButton.setDisable(true);
        
        try {
            NoteJour note = new NoteJour(idEmploye, type, montant, description, LocalDateTime.now());
            
            // Animation de succès
            if (noteDAO.create(note)) {
            // Animation de succès
            ScaleTransition success = new ScaleTransition(Duration.millis(200), enregistrerButton);
            success.setFromX(1.0);
            success.setFromY(1.0);
            success.setToX(1.1);
            success.setToY(1.1);
            success.setAutoReverse(true);
            success.setCycleCount(2);
            success.setOnFinished(e -> {
                afficherAlerteAvecAnimation(Alert.AlertType.INFORMATION, "Succès", "Note enregistrée avec succès.");
                fermerAvecAnimation();
            });
                success.play();
            } else {
                enregistrerButton.setText(texteOriginal);
                enregistrerButton.setDisable(false);
                afficherAlerteAvecAnimation(Alert.AlertType.ERROR, "Erreur", 
                    "Erreur lors de l'enregistrement de la note.\n\nVérifiez:\n" +
                    "- Que l'employé existe dans la base de données\n" +
                    "- La console pour plus de détails");
                animerErreur(enregistrerButton);
            }
        } catch (Exception e) {
            enregistrerButton.setText(texteOriginal);
            enregistrerButton.setDisable(false);
            String errorMsg = "Erreur lors de l'enregistrement de la note:\n\n" + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += "\n\nCause: " + e.getCause().getMessage();
            }
            afficherAlerteAvecAnimation(Alert.AlertType.ERROR, "Erreur", errorMsg);
            animerErreur(enregistrerButton);
            LOG.error("Erreur détaillée lors de l'enregistrement de la note:");
        }
    }
    
    /**
     * Anime une erreur sur un champ
     */
    private void animerErreur(javafx.scene.Node node) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(100), node);
        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.play();
        
        // Changer temporairement la couleur de bordure
        String styleOriginal = node.getStyle();
        node.setStyle(styleOriginal + "-fx-border-width: 2;");
        
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                Platform.runLater(() -> {
                    node.setStyle(styleOriginal);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * Ferme le dialogue avec animation
     */
    private void fermerAvecAnimation() {
        if (rootPane != null) {
            ParallelTransition sortie = new ParallelTransition(
                new FadeTransition(Duration.millis(200), rootPane),
                new ScaleTransition(Duration.millis(200), rootPane)
            );
            ((FadeTransition) sortie.getChildren().get(0)).setFromValue(1);
            ((FadeTransition) sortie.getChildren().get(0)).setToValue(0);
            ((ScaleTransition) sortie.getChildren().get(1)).setFromX(1.0);
            ((ScaleTransition) sortie.getChildren().get(1)).setFromY(1.0);
            ((ScaleTransition) sortie.getChildren().get(1)).setToX(0.9);
            ((ScaleTransition) sortie.getChildren().get(1)).setToY(0.9);
            
            sortie.setOnFinished(e -> fermer());
            sortie.play();
        } else {
            fermer();
        }
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleAnnuler() {
        fermerAvecAnimation();
    }
    
    private void fermer() {
        Stage stage = (Stage) annulerButton.getScene().getWindow();
        stage.close();
    }
    
    private void afficherAlerteAvecAnimation(Alert.AlertType type, String titre, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        // Style personnalisé pour l'alerte
        javafx.scene.Node alertNode = alert.getDialogPane();
        alertNode.setStyle("-fx-background-radius: 12;");
        
        ui.Dialogues.preparer(alert.getDialogPane(), null);
        alert.showAndWait();
    }
}

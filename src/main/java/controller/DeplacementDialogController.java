package controller;

import java.time.LocalDateTime;

import dao.DeplacementEmployeDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.DeplacementEmploye;
import util.SessionManager;

/**
 * Contrôleur pour le dialogue de suivi des déplacements
 */
public class DeplacementDialogController {
    
    @FXML
    private TextField destinationField;
    
    @FXML
    private TextArea notesField;
    
    @FXML
    private Button enregistrerButton;
    
    @FXML
    private Button annulerButton;
    
    private DeplacementEmployeDAO deplacementDAO;
    private DeplacementEmploye deplacementActuel;
    private boolean estEnCours = false;
    
    @FXML
    @SuppressWarnings("unused")
    private void initialize() {
        deplacementDAO = new DeplacementEmployeDAO();
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleEnregistrer() {
        String destination = destinationField.getText().trim();
        String notes = notesField.getText().trim();
        
        if (destination.isEmpty()) {
            afficherAlerte(Alert.AlertType.WARNING, "Attention", "Veuillez entrer une destination.");
            return;
        }
        
        // Le déplacement doit être rattaché à l'employé réellement connecté.
        int idEmploye = SessionManager.getCurrentUserId();
        if (idEmploye <= 0) {
            afficherAlerte(Alert.AlertType.ERROR, "Session expirée",
                "Aucun utilisateur connecté. Reconnectez-vous pour enregistrer un déplacement.");
            return;
        }
        
        if (!estEnCours) {
            // Démarrer un nouveau déplacement
            deplacementActuel = new DeplacementEmploye(idEmploye, LocalDateTime.now(), destination, notes);
            if (deplacementDAO.create(deplacementActuel)) {
                estEnCours = true;
                enregistrerButton.setText("Terminer Déplacement");
                destinationField.setEditable(false);
                afficherAlerte(Alert.AlertType.INFORMATION, "Succès", "Déplacement démarré.");
            } else {
                afficherAlerte(Alert.AlertType.ERROR, "Erreur", "Erreur lors de l'enregistrement.");
            }
        } else {
            // Terminer le déplacement
            if (deplacementActuel != null) {
                deplacementActuel.setDateFin(LocalDateTime.now());
                deplacementActuel.setDestination(destination);
                deplacementActuel.setNotes(notes);
                deplacementActuel.calculerHeuresTravaillees();
                
                if (deplacementDAO.update(deplacementActuel)) {
                    afficherAlerte(Alert.AlertType.INFORMATION, "Succès", 
                        "Déplacement terminé. Heures travaillées: " + 
                        String.format("%.2f h", deplacementActuel.getHeuresTravaillees()));
                    fermer();
                } else {
                    afficherAlerte(Alert.AlertType.ERROR, "Erreur", "Erreur lors de la mise à jour.");
                }
            }
        }
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleAnnuler() {
        fermer();
    }
    
    private void fermer() {
        Stage stage = (Stage) annulerButton.getScene().getWindow();
        stage.close();
    }
    
    private void afficherAlerte(Alert.AlertType type, String titre, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


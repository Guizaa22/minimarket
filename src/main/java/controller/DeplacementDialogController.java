package controller;

import java.time.LocalDateTime;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.DeplacementEmploye;

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
    
    private service.JourneeService journeeService;
    private DeplacementEmploye deplacementActuel;
    private boolean estEnCours = false;
    
    @FXML
    @SuppressWarnings("unused")
    private void initialize() {
        journeeService = new service.JourneeService();
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
        int idEmploye = service.SessionContext.get().getUtilisateurId();
        if (idEmploye <= 0) {
            afficherAlerte(Alert.AlertType.ERROR, "Session expirée",
                "Aucun utilisateur connecté. Reconnectez-vous pour enregistrer un déplacement.");
            return;
        }
        
        // L'écriture passe par JourneeService : les heures d'un déplacement
        // alimentent la paie et doivent laisser une trace dans audit_logs, ce
        // que l'appel direct au DAO ne faisait pas.
        try {
            if (!estEnCours) {
                deplacementActuel = journeeService.demarrerDeplacement(idEmploye, destination, notes);
                estEnCours = true;
                enregistrerButton.setText("Terminer Déplacement");
                destinationField.setEditable(false);
                afficherAlerte(Alert.AlertType.INFORMATION, "Succès", "Déplacement démarré.");

            } else if (deplacementActuel != null) {
                deplacementActuel.setDestination(destination);
                deplacementActuel.setNotes(notes);
                journeeService.terminerDeplacement(deplacementActuel, idEmploye);

                afficherAlerte(Alert.AlertType.INFORMATION, "Succès",
                    "Déplacement terminé. Heures travaillées: " +
                    String.format("%.2f h", deplacementActuel.getHeuresTravaillees()));
                fermer();
            }
        } catch (exception.ApplicationException e) {
            afficherAlerte(Alert.AlertType.ERROR, "Erreur", e.getMessage());
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
        ui.Dialogues.preparer(alert.getDialogPane(), null);
        alert.showAndWait();
    }
}


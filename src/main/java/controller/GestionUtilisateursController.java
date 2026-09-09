package controller;

import service.AuthService;
import exception.ApplicationException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import model.Utilisateur;

/**
 * Contrôleur pour la gestion des utilisateurs (Admin uniquement)
 * Version moderne avec interface améliorée
 */
public class GestionUtilisateursController {

    // ========================================
    // LABELS & TITRE
    // ========================================
    @FXML
    private Label formTitleLabel;

    @FXML
    private Label totalUsersLabel;

    // ========================================
    // CHAMPS DE FORMULAIRE
    // ========================================
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ComboBox<Utilisateur.Role> roleComboBox;

    // ========================================
    // TABLEVIEW & COLONNES
    // ========================================
    @FXML
    private TableView<Utilisateur> utilisateursTable;

    @FXML
    private TableColumn<Utilisateur, String> usernameColumn;

    @FXML
    private TableColumn<Utilisateur, String> roleColumn;

    @FXML
    private TableColumn<Utilisateur, Void> actionsColumn;

    // ========================================
    // BOUTONS
    // ========================================
    @FXML
    private Button ajouterButton;

    @FXML
    private Button modifierButton;

    @FXML
    private Button annulerButton;

    @FXML
    private HBox editButtonsBox;

    @FXML
    private Button retourButton;

    // ========================================
    // DONNÉES & DAO
    // ========================================
    private AuthService authService;
    private ObservableList<Utilisateur> utilisateursList;
    private Utilisateur utilisateurSelectionne;
    private boolean modeEdition = false;

    /**
     * Initialisation du contrôleur
     */
    @FXML
    private void initialize() {
        authService = new AuthService();
        utilisateursList = FXCollections.observableArrayList();

        // Configuration du ComboBox des rôles
        roleComboBox.setItems(FXCollections.observableArrayList(Utilisateur.Role.values()));
        roleComboBox.setValue(Utilisateur.Role.Employé);

        // Configuration des colonnes
        configureTableColumns();

        // Configuration de la colonne Actions
        configureActionsColumn();

        utilisateursTable.setItems(utilisateursList);

        // Listener de sélection (optionnel)
        utilisateursTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    utilisateurSelectionne = newSelection;
                }
        );

        // Charger les utilisateurs
        chargerUtilisateurs();

        // Mise à jour du compteur
        updateUserCount();
    }

    /**
     * Configuration des colonnes du tableau
     */
    private void configureTableColumns() {
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));

        // Pour la colonne role, on doit gérer l'enum différemment
        roleColumn.setCellValueFactory(cellData -> {
            Utilisateur user = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                    user.getRole() != null ? user.getRole().toString() : ""
            );
        });

        // Formatage de la colonne rôle avec badges colorés
        roleColumn.setCellFactory(column -> new TableCell<Utilisateur, String>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(role);
                    badge.getStyleClass().add("role-badge");

                    // Adapter selon les rôles de votre enum
                    if (role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("Administrateur")) {
                        badge.getStyleClass().add("role-badge-admin");
                        badge.setText("👑 " + role);
                    } else {
                        badge.getStyleClass().add("role-badge-caissier");
                        badge.setText("💼 " + role);
                    }

                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    /**
     * Configuration de la colonne Actions avec boutons Modifier et Supprimer
     */
    private void configureActionsColumn() {
        actionsColumn.setCellFactory(column -> new TableCell<Utilisateur, Void>() {
            private final Button btnEdit = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox actionBox = new HBox(8, btnEdit, btnDelete);

            {
                // Style des boutons
                btnEdit.getStyleClass().add("action-button-edit");
                btnDelete.getStyleClass().add("action-button-delete");

                btnEdit.setTooltip(new Tooltip("Modifier l'utilisateur"));
                btnDelete.setTooltip(new Tooltip("Supprimer l'utilisateur"));

                actionBox.setAlignment(Pos.CENTER);

                // Actions des boutons
                btnEdit.setOnAction(event -> {
                    Utilisateur utilisateur = getTableView().getItems().get(getIndex());
                    editerUtilisateur(utilisateur);
                });

                btnDelete.setOnAction(event -> {
                    Utilisateur utilisateur = getTableView().getItems().get(getIndex());
                    supprimerUtilisateur(utilisateur);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(actionBox);
                }
            }
        });
    }

    /**
     * Éditer un utilisateur depuis le tableau
     */
    private void editerUtilisateur(Utilisateur utilisateur) {
        utilisateurSelectionne = utilisateur;
        remplirFormulaire(utilisateur);
        activerModeEdition();
    }

    /**
     * Supprimer un utilisateur depuis le tableau
     */
    private void supprimerUtilisateur(Utilisateur utilisateur) {
        // Empêcher la suppression de l'utilisateur actuellement connecté
        Utilisateur utilisateurConnecte = ConnexionController.getUtilisateurConnecte();
        if (utilisateurConnecte != null && utilisateurConnecte.getId() == utilisateur.getId()) {
            showAlert(Alert.AlertType.WARNING, "Action interdite",
                    "Vous ne pouvez pas supprimer votre propre compte.");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation de suppression");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Êtes-vous sûr de vouloir supprimer l'utilisateur \"" +
                utilisateur.getUsername() + "\" ?\n\nCette action est irréversible.");

        ui.Dialogues.preparer(confirmAlert.getDialogPane(), null);
        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            // AuthService centralise les garde-fous (dernier administrateur,
            // compte de la session) et le journal d'audit.
            try {
                authService.supprimerCompte(utilisateur);
                showAlert(Alert.AlertType.INFORMATION, "Succès",
                        "Utilisateur supprimé avec succès.");
                chargerUtilisateurs();
                updateUserCount();
            } catch (ApplicationException e) {
                showAlert(Alert.AlertType.ERROR, "Suppression impossible", e.getMessage());
            }
        }
    }

    /**
     * Activer le mode édition
     */
    private void activerModeEdition() {
        modeEdition = true;
        formTitleLabel.setText("Modifier l'Utilisateur");
        ajouterButton.setVisible(false);
        ajouterButton.setManaged(false);
        editButtonsBox.setVisible(true);
        editButtonsBox.setManaged(true);
    }

    /**
     * Désactiver le mode édition
     */
    private void desactiverModeEdition() {
        modeEdition = false;
        formTitleLabel.setText("Nouvel Utilisateur");
        ajouterButton.setVisible(true);
        ajouterButton.setManaged(true);
        editButtonsBox.setVisible(false);
        editButtonsBox.setManaged(false);
        utilisateurSelectionne = null;
    }

    /**
     * Handler pour ajouter un utilisateur
     */
    @FXML
    private void handleAjouter() {
        if (!validerFormulaire()) {
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        Utilisateur.Role role = roleComboBox.getValue();

        // Création, validation, unicité, hachage BCrypt et audit sont portés
        // par AuthService.
        try {
            authService.creerCompte(username, password, role);
            showAlert(Alert.AlertType.INFORMATION, "Succès",
                    "Utilisateur ajouté avec succès.");
            viderFormulaire();
            chargerUtilisateurs();
            updateUserCount();
        } catch (ApplicationException e) {
            showAlert(Alert.AlertType.WARNING, "Ajout impossible", e.getMessage());
        }
    }

    /**
     * Handler pour modifier un utilisateur
     */
    @FXML
    private void handleModifier() {
        if (utilisateurSelectionne == null) {
            showAlert(Alert.AlertType.WARNING, "Aucune sélection",
                    "Veuillez sélectionner un utilisateur à modifier.");
            return;
        }

        if (!validerFormulaire()) {
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        Utilisateur.Role role = roleComboBox.getValue();

        // Modification (nom, rôle, mot de passe optionnel), unicité et audit
        // sont portés par AuthService.
        try {
            authService.modifierCompte(utilisateurSelectionne, username, role, password);
            showAlert(Alert.AlertType.INFORMATION, "Succès",
                    "Utilisateur modifié avec succès.");
            viderFormulaire();
            desactiverModeEdition();
            chargerUtilisateurs();
        } catch (ApplicationException e) {
            showAlert(Alert.AlertType.WARNING, "Modification impossible", e.getMessage());
        }
    }

    /**
     * Handler pour annuler l'édition
     */
    @FXML
    public void handleAnnuler(ActionEvent actionEvent) {
        viderFormulaire();
        desactiverModeEdition();
    }

    /**
     * Handler pour le retour au dashboard
     */
    @FXML
    private void handleRetour() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) retourButton.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Erreur lors du retour: " + e.getMessage());
        }
    }

    /**
     * Charger tous les utilisateurs depuis la base de données
     */
    private void chargerUtilisateurs() {
        utilisateursList.clear();
        utilisateursList.addAll(authService.listerUtilisateurs());
    }

    /**
     * Remplir le formulaire avec les données d'un utilisateur
     */
    private void remplirFormulaire(Utilisateur utilisateur) {
        usernameField.setText(utilisateur.getUsername());
        passwordField.clear(); // Ne pas afficher le mot de passe hashé
        roleComboBox.setValue(utilisateur.getRole());
    }

    /**
     * Vider tous les champs du formulaire
     */
    private void viderFormulaire() {
        usernameField.clear();
        passwordField.clear();
        roleComboBox.setValue(Utilisateur.Role.Employé);
        utilisateursTable.getSelectionModel().clearSelection();
    }

    /**
     * Valider les données du formulaire
     */
    private boolean validerFormulaire() {
        if (usernameField.getText().trim().isEmpty() ||
                roleComboBox.getValue() == null) {

            showAlert(Alert.AlertType.WARNING, "Champs vides",
                    "Veuillez remplir tous les champs obligatoires.");
            return false;
        }

        // Pour la création, le mot de passe est obligatoire
        if (utilisateurSelectionne == null && passwordField.getText().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Mot de passe requis",
                    "Veuillez entrer un mot de passe pour créer un nouvel utilisateur.");
            return false;
        }

        return true;
    }

    /**
     * Mettre à jour le compteur d'utilisateurs
     */
    private void updateUserCount() {
        if (totalUsersLabel != null) {
            int total = utilisateursList.size();

            // Compter les admins selon les valeurs possibles de votre enum
            long adminCount = utilisateursList.stream()
                    .filter(u -> {
                        String role = u.getRole().toString();
                        return role.equalsIgnoreCase("ADMIN") ||
                                role.equalsIgnoreCase("Administrateur");
                    })
                    .count();

            long autresCount = total - adminCount;

            totalUsersLabel.setText(String.format("Total : %d (👑 %d • 💼 %d)",
                    total, adminCount, autresCount));
        }
    }

    /**
     * Afficher une alerte
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        ui.Dialogues.preparer(alert.getDialogPane(), null);
        alert.showAndWait();
    }
}
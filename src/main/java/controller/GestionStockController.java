package controller;

import java.math.BigDecimal;

import dao.CategorieDAO;
import dao.ProduitDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import model.Categorie;
import model.Produit;
import util.SessionManager;

/**
 * Contrôleur pour la gestion de stock (Admin uniquement)
 * Version moderne avec interface améliorée
 */
public class GestionStockController {

    // ========================================
    // LABELS & TITRE
    // ========================================
    @FXML
    private Label formTitleLabel;

    // ========================================
    // CHAMPS DE FORMULAIRE
    // ========================================
    @FXML
    private TextField codeBarreField;

    @FXML
    private TextField nomField;

    @FXML
    private ComboBox<Categorie> categorieComboBox;
    
    @FXML
    private Button ajouterCategorieButton;

    @FXML
    private TextField prixAchatField;

    @FXML
    private TextField prixVenteField;

    @FXML
    private TextField quantiteStockField;

    @FXML
    private TextField seuilAlerteField;

    @FXML
    private TextField rechercheField;

    // ========================================
    // TABLEVIEW & COLONNES
    // ========================================
    @FXML
    private TableView<Produit> produitsTable;

    @FXML
    private TableColumn<Produit, String> codeBarreColumn;

    @FXML
    private TableColumn<Produit, String> nomColumn;

    @FXML
    private TableColumn<Produit, String> categorieColumn;

    @FXML
    private TableColumn<Produit, BigDecimal> prixAchatColumn;

    @FXML
    private TableColumn<Produit, BigDecimal> prixVenteColumn;

    @FXML
    private TableColumn<Produit, Integer> quantiteStockColumn;

    @FXML
    private TableColumn<Produit, Integer> seuilAlerteColumn;

    @FXML
    private TableColumn<Produit, Void> actionsColumn;

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
    private ProduitDAO produitDAO;
    private CategorieDAO categorieDAO;
    private ObservableList<Produit> produitsList;
    private ObservableList<Categorie> categoriesList;
    private Produit produitSelectionne;

    /**
     * Initialisation du contrôleur
     */
    @FXML
    private void initialize() {
        produitDAO = new ProduitDAO();
        categorieDAO = new CategorieDAO();
        produitsList = FXCollections.observableArrayList();
        categoriesList = FXCollections.observableArrayList();
        
        // Charger les catégories
        chargerCategories();

        // Configuration des colonnes
        configureTableColumns();

        // Configuration de la colonne Actions avec boutons Modifier/Supprimer
        configureActionsColumn();

        produitsTable.setItems(produitsList);

        // Listener de sélection (optionnel, car on utilise les boutons dans la table)
        produitsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    produitSelectionne = newSelection;
                }
        );

        // Charger les produits
        chargerProduits();

        // Configuration de la recherche en temps réel
        rechercheField.textProperty().addListener((observable, oldValue, newValue) -> {
            handleRechercher();
        });
        
        // ✨ SCAN CODE-BARRES: Détection automatique du scan
        if (codeBarreField != null) {
            configurerScanCodeBarres(codeBarreField);
        }
        configurerScanCodeBarres(rechercheField);
    }

    /**
     * Configuration des colonnes du tableau
     */
    private void configureTableColumns() {
        codeBarreColumn.setCellValueFactory(new PropertyValueFactory<>("codeBarre"));
        nomColumn.setCellValueFactory(new PropertyValueFactory<>("nom"));
        categorieColumn.setCellValueFactory(new PropertyValueFactory<>("categorie"));
        prixAchatColumn.setCellValueFactory(new PropertyValueFactory<>("prixAchatActuel"));
        prixVenteColumn.setCellValueFactory(new PropertyValueFactory<>("prixVenteDefaut"));
        quantiteStockColumn.setCellValueFactory(new PropertyValueFactory<>("quantiteStock"));
        seuilAlerteColumn.setCellValueFactory(new PropertyValueFactory<>("seuilAlerte"));

        // Formatage des colonnes de prix
        prixAchatColumn.setCellFactory(column -> new TableCell<Produit, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f DT", item));
                }
            }
        });

        prixVenteColumn.setCellFactory(column -> new TableCell<Produit, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f DT", item));
                }
            }
        });

        // Mise en évidence des produits à stock faible avec badges
        quantiteStockColumn.setCellFactory(column -> new TableCell<Produit, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    setGraphic(null);
                } else {
                    Produit produit = getTableView().getItems().get(getIndex());
                    if (produit != null && produit.isStockFaible()) {
                        // Badge rouge pour stock faible
                        Label badge = new Label("⚠️ " + item);
                        badge.getStyleClass().add("stock-badge");
                        badge.getStyleClass().add("stock-badge-low");
                        setGraphic(badge);
                        setText(null);
                    } else {
                        // Badge vert pour stock normal
                        Label badge = new Label(item.toString());
                        badge.getStyleClass().add("stock-badge");
                        badge.getStyleClass().add("stock-badge-normal");
                        setGraphic(badge);
                        setText(null);
                    }
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    /**
     * Configuration de la colonne Actions avec boutons Modifier et Supprimer
     */
    private void configureActionsColumn() {
        actionsColumn.setCellFactory(column -> new TableCell<Produit, Void>() {
            private final Button btnEdit = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox actionBox = new HBox(8, btnEdit, btnDelete);

            {
                // Style des boutons
                btnEdit.getStyleClass().add("action-button-edit");
                btnDelete.getStyleClass().add("action-button-delete");

                btnEdit.setTooltip(new Tooltip("Modifier"));
                btnDelete.setTooltip(new Tooltip("Supprimer"));

                actionBox.setAlignment(Pos.CENTER);

                // Actions des boutons
                btnEdit.setOnAction(event -> {
                    Produit produit = getTableView().getItems().get(getIndex());
                    editerProduit(produit);
                });

                btnDelete.setOnAction(event -> {
                    Produit produit = getTableView().getItems().get(getIndex());
                    supprimerProduit(produit);
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
     * Éditer un produit depuis le tableau
     */
    private void editerProduit(Produit produit) {
        produitSelectionne = produit;
        remplirFormulaire(produit);
        activerModeEdition();
    }

    /**
     * Supprimer un produit depuis le tableau
     */
    private void supprimerProduit(Produit produit) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation de suppression");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Êtes-vous sûr de vouloir supprimer le produit \"" +
                produit.getNom() + "\" ?");

        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            // Vérifier si l'utilisateur est admin
            boolean isAdmin = SessionManager.isAdmin();
            boolean forceDelete = false;
            
            // Si le produit est utilisé et que l'utilisateur est admin, demander confirmation pour suppression forcée
            if (isAdmin && produitDAO.isProduitUtilise(produit.getId())) {
                Alert forceConfirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                forceConfirmAlert.setTitle("Suppression forcée");
                forceConfirmAlert.setHeaderText("Ce produit est utilisé dans des ventes ou des ajouts de stock");
                forceConfirmAlert.setContentText("En tant qu'administrateur, vous pouvez forcer la suppression.\n\n" +
                        "⚠️ ATTENTION: Cela supprimera également toutes les références à ce produit dans les ventes et ajouts de stock.\n\n" +
                        "Voulez-vous continuer ?");
                
                if (forceConfirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    forceDelete = true;
                } else {
                    return; // L'utilisateur a annulé
                }
            }
            
            try {
                if (produitDAO.delete(produit.getId(), forceDelete)) {
                    String message = "Produit supprimé avec succès.";
                    if (forceDelete) {
                        message += "\n\n⚠️ Les références à ce produit dans les ventes et ajouts de stock ont également été supprimées.";
                    }
                    showAlert(Alert.AlertType.INFORMATION, "Succès", message);
                    // Rafraîchir la liste des produits
                    chargerProduits();
                    // S'assurer que la table est visible et mise à jour
                    produitsTable.setVisible(true);
                    produitsTable.requestFocus();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Erreur",
                            "Erreur lors de la suppression du produit. Le produit n'a pas été trouvé.");
                }
            } catch (java.sql.SQLException e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.contains("utilisé dans des ventes")) {
                    showAlert(Alert.AlertType.WARNING, "Impossible de supprimer",
                            "Ce produit ne peut pas être supprimé car il est utilisé dans des ventes ou des ajouts de stock.\n\n" +
                            "Pour supprimer ce produit, vous devez d'abord supprimer toutes les ventes et ajouts de stock associés.");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Erreur",
                            "Erreur lors de la suppression du produit:\n\n" + errorMessage + "\n\n" +
                            "Vérifiez la console pour plus de détails.");
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Erreur",
                        "Erreur inattendue lors de la suppression du produit:\n\n" + e.getMessage() + "\n\n" +
                        "Vérifiez la console pour plus de détails.");
            }
        }
    }

    /**
     * Activer le mode édition
     */
    private void activerModeEdition() {
        formTitleLabel.setText("Modifier Produit");
        ajouterButton.setVisible(false);
        ajouterButton.setManaged(false);
        editButtonsBox.setVisible(true);
        editButtonsBox.setManaged(true);
    }

    /**
     * Désactiver le mode édition
     */
    private void desactiverModeEdition() {
        formTitleLabel.setText("Nouveau Produit");
        ajouterButton.setVisible(true);
        ajouterButton.setManaged(true);
        editButtonsBox.setVisible(false);
        editButtonsBox.setManaged(false);
        produitSelectionne = null;
    }

    /**
     * Handler pour ajouter un produit
     */
    @FXML
    private void handleAjouter() {
        if (validerFormulaire()) {
            Produit produit = creerProduitDepuisFormulaire();

            if (produitDAO.codeBarreExists(produit.getCodeBarre())) {
                showAlert(Alert.AlertType.WARNING, "Code-barres existant",
                        "Un produit avec ce code-barres existe déjà.");
                return;
            }

            try {
                if (produitDAO.create(produit)) {
                    showAlert(Alert.AlertType.INFORMATION, "Succès",
                            "Produit ajouté avec succès.");
                    viderFormulaire();
                    // Rafraîchir la liste des produits
                    chargerProduits();
                    // S'assurer que la table est visible et mise à jour
                    produitsTable.setVisible(true);
                    produitsTable.requestFocus();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Erreur",
                            "Erreur lors de l'ajout du produit.\n\n" +
                            "Vérifiez:\n" +
                            "- Que tous les champs sont remplis correctement\n" +
                            "- Que le code-barres n'existe pas déjà\n" +
                            "- La console pour les détails de l'erreur SQL");
                }
            } catch (Exception e) {
                String errorMsg = "Erreur lors de l'ajout du produit:\n\n" + e.getMessage();
                if (e.getCause() != null) {
                    errorMsg += "\n\nCause: " + e.getCause().getMessage();
                }
                showAlert(Alert.AlertType.ERROR, "Erreur", errorMsg);
                System.err.println("Erreur détaillée lors de l'ajout:");
                e.printStackTrace();
            }
        }
    }

    /**
     * Handler pour modifier un produit
     */
    @FXML
    private void handleModifier() {
        if (produitSelectionne == null) {
            showAlert(Alert.AlertType.WARNING, "Aucune sélection",
                    "Veuillez sélectionner un produit à modifier.");
            return;
        }

        if (validerFormulaire()) {
            Produit produit = creerProduitDepuisFormulaire();
            produit.setId(produitSelectionne.getId());

            // Vérifier si le code-barres a changé et s'il existe déjà
            if (!produit.getCodeBarre().equals(produitSelectionne.getCodeBarre()) &&
                    produitDAO.codeBarreExists(produit.getCodeBarre())) {
                showAlert(Alert.AlertType.WARNING, "Code-barres existant",
                        "Un produit avec ce code-barres existe déjà.");
                return;
            }

            if (produitDAO.update(produit)) {
                showAlert(Alert.AlertType.INFORMATION, "Succès",
                        "Produit modifié avec succès.");
                viderFormulaire();
                desactiverModeEdition();
                // Rafraîchir la liste des produits
                chargerProduits();
                // S'assurer que la table est visible et mise à jour
                produitsTable.setVisible(true);
                produitsTable.requestFocus();
            } else {
                showAlert(Alert.AlertType.ERROR, "Erreur",
                        "Erreur lors de la modification du produit. Vérifiez la console pour plus de détails.");
            }
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
     * Handler pour la recherche (appelé automatiquement)
     */
    @FXML
    private void handleRechercher() {
        String recherche = rechercheField.getText().trim().toLowerCase();

        if (recherche.isEmpty()) {
            chargerProduits();
            return;
        }

        produitsList.clear();
        produitDAO.findAll().stream()
                .filter(p -> p.getNom().toLowerCase().contains(recherche) ||
                        p.getCodeBarre().contains(recherche) ||
                        p.getCategorie().toLowerCase().contains(recherche))
                .forEach(produitsList::add);
    }

    /**
     * Handler pour le retour au dashboard
     */
    @FXML
    private void handleRetour() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) retourButton.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur");
        } catch (java.io.IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Erreur lors du retour: " + e.getMessage());
        }
    }

    /**
     * Handler pour le scan de code-barres
     */
    @FXML
    private void handleCodeBarreScan() {
        String codeBarre = codeBarreField.getText().trim();
        if (!codeBarre.isEmpty()) {
            Produit produit = produitDAO.findByCodeBarre(codeBarre);
            if (produit != null) {
                remplirFormulaire(produit);
                produitSelectionne = produit;
                activerModeEdition();
            }
        }
    }
    
    /**
     * ✨ Configure le scan de code-barres pour un champ de texte
     * Détecte automatiquement quand un code-barres est scanné
     */
    private void configurerScanCodeBarres(javafx.scene.control.TextField field) {
        final long[] lastKeyTime = {0};
        
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastKeyTime[0];
            
            // Si le texte change rapidement (moins de 50ms entre les caractères), c'est probablement un scan
            if (timeDiff < 50 && newVal.length() > oldVal.length()) {
                lastKeyTime[0] = currentTime;
                
                // Si c'est un code-barres complet (au moins 8 chiffres)
                if (newVal.length() >= 8 && newVal.matches("\\d+")) {
                    javafx.application.Platform.runLater(() -> {
                        try {
                            Thread.sleep(150);
                            if (field.getText().equals(newVal) && newVal.length() >= 8) {
                                // C'est un code-barres scanné - rechercher automatiquement
                                if (field == codeBarreField) {
                                    handleCodeBarreScan();
                                } else if (field == rechercheField) {
                                    handleRechercher();
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
            } else {
                lastKeyTime[0] = currentTime;
            }
        });
        
        // Détecter la touche Enter (scanners envoient souvent Enter à la fin)
        field.setOnAction(e -> {
            String text = field.getText().trim();
            if (text.length() >= 8 && text.matches("\\d+")) {
                if (field == codeBarreField) {
                    handleCodeBarreScan();
                } else if (field == rechercheField) {
                    handleRechercher();
                }
            }
        });
    }

    /**
     * Charger tous les produits depuis la base de données
     */
    private void chargerProduits() {
        produitsList.clear();
        java.util.List<Produit> produits = produitDAO.findAll();
        produitsList.addAll(produits);
        
        // Debug: afficher le nombre de produits chargés
        System.out.println("Produits chargés: " + produits.size());
        
        // Forcer le rafraîchissement de la table
        produitsTable.refresh();
    }

    /**
     * Remplir le formulaire avec les données d'un produit
     */
    private void remplirFormulaire(Produit produit) {
        codeBarreField.setText(produit.getCodeBarre());
        nomField.setText(produit.getNom());
        // Trouver la catégorie correspondante
        String categorieNom = produit.getCategorie();
        if (categorieNom != null && !categorieNom.isEmpty()) {
            Categorie categorie = categorieDAO.findByNom(categorieNom);
            if (categorie != null) {
                categorieComboBox.setValue(categorie);
            } else {
                // Si la catégorie n'existe pas dans la table, créer une option temporaire
                categorieComboBox.setValue(null);
            }
        } else {
            categorieComboBox.setValue(null);
        }
        prixAchatField.setText(produit.getPrixAchatActuel().toString());
        prixVenteField.setText(produit.getPrixVenteDefaut().toString());
        quantiteStockField.setText(String.valueOf(produit.getQuantiteStock()));
        seuilAlerteField.setText(String.valueOf(produit.getSeuilAlerte()));
    }

    /**
     * Vider tous les champs du formulaire
     */
    private void viderFormulaire() {
        codeBarreField.clear();
        nomField.clear();
        categorieComboBox.setValue(null);
        prixAchatField.clear();
        prixVenteField.clear();
        quantiteStockField.clear();
        seuilAlerteField.clear();
        produitsTable.getSelectionModel().clearSelection();
    }

    /**
     * Créer un objet Produit depuis les données du formulaire
     */
    private Produit creerProduitDepuisFormulaire() {
        String codeBarre = codeBarreField.getText().trim();
        String nom = nomField.getText().trim();
        String categorieValeur;
        Categorie categorieSelectionnee = categorieComboBox.getValue();
        if (categorieSelectionnee != null) {
            categorieValeur = categorieSelectionnee.getNom();
        } else {
            categorieValeur = ""; // Valeur par défaut si aucune catégorie sélectionnée
        }
        BigDecimal prixAchat = new BigDecimal(prixAchatField.getText().trim());
        BigDecimal prixVente = new BigDecimal(prixVenteField.getText().trim());
        int quantiteStock = Integer.parseInt(quantiteStockField.getText().trim());
        int seuilAlerte = Integer.parseInt(seuilAlerteField.getText().trim());

        return new Produit(codeBarre, nom, categorieValeur, prixAchat, prixVente, quantiteStock, "unité", seuilAlerte);
    }
    
    /**
     * Charge les catégories dans le ComboBox
     */
    private void chargerCategories() {
        categoriesList.clear();
        categoriesList.addAll(categorieDAO.findAll());
        categorieComboBox.setItems(categoriesList);
    }
    
    /**
     * Handler pour ajouter une nouvelle catégorie
     */
    @FXML
    private void handleAjouterCategorie() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nouvelle Catégorie");
        dialog.setHeaderText("Ajouter une nouvelle catégorie");
        dialog.setContentText("Nom de la catégorie:");
        
        dialog.showAndWait().ifPresent(nomCategorie -> {
            if (nomCategorie != null && !nomCategorie.trim().isEmpty()) {
                // Vérifier si la catégorie existe déjà
                Categorie existante = categorieDAO.findByNom(nomCategorie.trim());
                if (existante != null) {
                    showAlert(Alert.AlertType.WARNING, "Catégorie existante",
                            "Cette catégorie existe déjà.");
                    categorieComboBox.setValue(existante);
                    return;
                }
                
                // Créer la nouvelle catégorie
                Categorie nouvelleCategorie = new Categorie(nomCategorie.trim());
                try {
                    if (categorieDAO.create(nouvelleCategorie)) {
                        showAlert(Alert.AlertType.INFORMATION, "Succès",
                                "Catégorie ajoutée avec succès.");
                        chargerCategories();
                        categorieComboBox.setValue(nouvelleCategorie);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erreur",
                                "Erreur lors de l'ajout de la catégorie.\n\n" +
                                "Vérifiez:\n" +
                                "- Que le nom de la catégorie n'existe pas déjà\n" +
                                "- La console pour plus de détails");
                    }
                } catch (Exception e) {
                    String errorMsg = "Erreur lors de l'ajout de la catégorie:\n\n" + e.getMessage();
                    if (e.getCause() != null) {
                        errorMsg += "\n\nCause: " + e.getCause().getMessage();
                    }
                    showAlert(Alert.AlertType.ERROR, "Erreur", errorMsg);
                    System.err.println("Erreur détaillée lors de l'ajout de catégorie:");
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Valider les données du formulaire
     */
    private boolean validerFormulaire() {
        if (codeBarreField.getText().trim().isEmpty() ||
                nomField.getText().trim().isEmpty() ||
                categorieComboBox.getValue() == null ||
                prixAchatField.getText().trim().isEmpty() ||
                prixVenteField.getText().trim().isEmpty() ||
                quantiteStockField.getText().trim().isEmpty() ||
                seuilAlerteField.getText().trim().isEmpty()) {

            showAlert(Alert.AlertType.WARNING, "Champs vides",
                    "Veuillez remplir tous les champs obligatoires, y compris la catégorie.");
            return false;
        }

        try {
            BigDecimal prixAchat = new BigDecimal(prixAchatField.getText().trim());
            BigDecimal prixVente = new BigDecimal(prixVenteField.getText().trim());
            int quantiteStock = Integer.parseInt(quantiteStockField.getText().trim());
            int seuilAlerte = Integer.parseInt(seuilAlerteField.getText().trim());

            // Validation des valeurs
            if (prixAchat.compareTo(BigDecimal.ZERO) < 0 || prixVente.compareTo(BigDecimal.ZERO) < 0 ||
                    quantiteStock < 0 || seuilAlerte < 0) {
                showAlert(Alert.AlertType.WARNING, "Valeurs invalides",
                        "Les valeurs ne peuvent pas être négatives.");
                return false;
            }

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Format invalide",
                    "Veuillez entrer des valeurs numériques valides.");
            return false;
        }

        return true;
    }

    /**
     * Afficher une alerte
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
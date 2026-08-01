package controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import dao.ProduitDAO;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.DetailVente;
import model.Produit;

/**
 * Contrôleur pour l'interface de sélection de catégories
 */
public class CaisseCategoriesController {
    private static final Logger LOG = LoggerFactory.getLogger(CaisseCategoriesController.class);


    // ============================================
    // CONSTANTES
    // ============================================
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 180;
    private static final int ICON_SIZE = 48;
    private static final int ANIMATION_DURATION = 200;

    // ============================================
    // COMPOSANTS FXML
    // ============================================
    @FXML
    private FlowPane categoriesContainer;

    @FXML
    private Button ajoutStockButton;

    @FXML
    private Button panierButton;

    @FXML
    private Button deconnexionButton;

    @FXML
    private Label panierCountLabel;

    @FXML
    private TextField rechercheField;

    @FXML
    private TextField quantiteField;

    @FXML
    private Button ajouterRapideButton;

    @FXML
    private Button plusButton;

    @FXML
    private Button moinsButton;

    @FXML
    private Label produitInfoLabel;

    // ============================================
    // ATTRIBUTS
    // ============================================
    private ProduitDAO produitDAO;
    private Produit produitTrouve = null;

    // ============================================
    // INITIALISATION
    // ============================================
    @FXML
    private void initialize() {
        produitDAO = new ProduitDAO();
        quantiteField.setText("1");
        rechercheField.requestFocus();

        configurerRecherche();
        updatePanierCount();
        ecouterChangementsPanier();
        chargerCategories();
        
        // Add global styles if not present
        javafx.application.Platform.runLater(() -> {
            if (categoriesContainer.getScene() != null) {
                String globalCss = getClass().getResource("/styles/global.css").toExternalForm();
                if (!categoriesContainer.getScene().getStylesheets().contains(globalCss)) {
                    categoriesContainer.getScene().getStylesheets().add(globalCss);
                }
            }
        });
    }

    /**
     * Configure le listener de recherche avec détection automatique de code-barres
     */
    private void configurerRecherche() {
        final long[] lastKeyTime = {0};
        final java.util.concurrent.atomic.AtomicBoolean ajoutEnCours = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        rechercheField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                produitInfoLabel.setText("");
                produitTrouve = null;
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastKeyTime[0];
            lastKeyTime[0] = currentTime;
            
            String recherche = newVal.trim();
            
            // Si changement rapide (scan) et code-barres complet
            if (timeDiff < 50 && recherche.length() >= 8 && recherche.matches("\\d+")) {
                // C'est probablement un scan - attendre un peu puis ajouter automatiquement
                if (!ajoutEnCours.get()) {
                    ajoutEnCours.set(true);
                    javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            Thread.sleep(200);
                            return null;
                        }
                    };
                    task.setOnSucceeded(e -> {
                        String currentText = rechercheField.getText().trim();
                        if (currentText.equals(recherche) && recherche.length() >= 8) {
                            // Recherche silencieuse sans popup
                            Produit produit = produitDAO.rechercherProduit(recherche);
                            if (produit != null && (produit.getQuantiteStock() > 0 || produit.isFrakCigarette())) {
                                // Si c'est un produit "frak cigarette", afficher ComboBox pour choisir le produit tabac associé
                                if (produit.isFrakCigarette()) {
                                    afficherPopupChoixProduitTabac(produit, 1);
                                }
                                // Si produit tabac normal, afficher popup pour choisir paquet ou cigarette
                                else if (produit.isTabac()) {
                                    afficherPopupChoixTabac(produit, 1);
                                } else {
                                    ajouterAuPanierSilencieux(produit, 1, null);
                                    // Afficher juste un message discret
                                    produitInfoLabel.setText("✓ " + produit.getNom() + " ajouté au panier");
                                    produitInfoLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold; -fx-font-size: 13px;");
                                }
                                // Effacer le champ après 1 seconde
                                javafx.application.Platform.runLater(() -> {
                                    javafx.concurrent.Task<Void> clearTask = new javafx.concurrent.Task<Void>() {
                                        @Override
                                        protected Void call() throws Exception {
                                            Thread.sleep(1000);
                                            return null;
                                        }
                                    };
                                    clearTask.setOnSucceeded(ev -> {
                                        rechercheField.clear();
                                        produitInfoLabel.setText("");
                                    });
                                    new Thread(clearTask).start();
                                });
                            } else if (produit != null) {
                                produitInfoLabel.setText("❌ Stock insuffisant: " + produit.getQuantiteStock());
                                produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
                            } else {
                                produitInfoLabel.setText("❌ Produit introuvable");
                                produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
                            }
                        }
                        ajoutEnCours.set(false);
                    });
                    task.setOnFailed(e -> ajoutEnCours.set(false));
                    new Thread(task).start();
                }
            } else {
                // Recherche normale
                rechercherProduit(recherche);
            }
        });
        
        // Détecter Enter (scanners envoient souvent Enter)
        rechercheField.setOnAction(e -> {
            String recherche = rechercheField.getText().trim();
            if (!recherche.isEmpty()) {
                if (estCodeBarre(recherche)) {
                    // Code-barres scanné - ajout silencieux
                    Produit produit = produitDAO.rechercherProduit(recherche);
                    if (produit != null && produit.getQuantiteStock() > 0) {
                        // Si produit tabac, afficher popup pour choisir paquet ou cigarette
                        if (produit.isTabac()) {
                            afficherPopupChoixTabac(produit, 1);
                        } else {
                            ajouterAuPanierSilencieux(produit, 1, null);
                            produitInfoLabel.setText("✓ " + produit.getNom() + " ajouté");
                            produitInfoLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold; -fx-font-size: 13px;");
                        }
                        rechercheField.clear();
                    } else if (produit != null) {
                        produitInfoLabel.setText("❌ Stock: " + produit.getQuantiteStock());
                        produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
                    } else {
                        produitInfoLabel.setText("❌ Produit introuvable");
                        produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
                    }
                } else {
                    // Recherche normale (nom de produit)
                    rechercherProduit(recherche);
                }
            }
        });
    }

    /**
     * Écoute les changements du panier global
     */
    private void ecouterChangementsPanier() {
        if (service.SessionContext.get().getPanier().getLignes() != null) {
            service.SessionContext.get().getPanier().getLignes().addListener(
                    (javafx.collections.ListChangeListener.Change<? extends DetailVente> c) -> {
                        updatePanierCount();
                    }
            );
        }
    }

    // ============================================
    // GESTION DES CATÉGORIES
    // ============================================

    /**
     * Charge et affiche toutes les catégories depuis la base de données
     */
    private void chargerCategories() {
        try {
            // Configuration du conteneur
            categoriesContainer.getChildren().clear();
            categoriesContainer.setHgap(20);
            categoriesContainer.setVgap(20);
            categoriesContainer.setPadding(new Insets(20));
            categoriesContainer.setAlignment(Pos.TOP_LEFT);

            // Récupération des catégories depuis la base de données
            List<String> categories = produitDAO.findAllCategories();

            // Vérifier si des catégories existent
            if (categories == null || categories.isEmpty()) {
                afficherMessageAucuneCategorie();
                return;
            }

            // Créer un bouton pour chaque catégorie
            for (String categorie : categories) {
                if (categorie != null && !categorie.trim().isEmpty()) {
                    Button categoryButton = createCategoryButton(categorie);
                    categoriesContainer.getChildren().add(categoryButton);
                }
            }

            LOG.info("✓ " + categories.size() + " catégorie(s) chargée(s)");

        } catch (Exception e) {
            LOG.error("Erreur lors du chargement des catégories: " + e.getMessage(), e);
            afficherErreurChargement();
        }
    }

    /**
     * Crée un bouton pour une catégorie
     */
    private Button createCategoryButton(String categorie) {
        // Créer un VBox pour contenir l'icône et le texte
        VBox content = new VBox(8);
        content.setAlignment(Pos.CENTER);

        // Icône selon la catégorie
        String icon = getCategoryIcon(categorie);
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: " + ICON_SIZE + "px;");

        Label textLabel = new Label(categorie);
        textLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");
        textLabel.setWrapText(true);
        textLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        content.getChildren().addAll(iconLabel, textLabel);

        Button button = new Button();
        button.setGraphic(content);
        button.setPrefSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        button.setContentDisplay(javafx.scene.control.ContentDisplay.CENTER);
        
        // Use CSS class instead of hardcoded styles
        button.getStyleClass().add("category-button");
        // Fallback style if CSS not loaded yet or class not found
        if (button.getStyleClass().isEmpty()) {
             button.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #2E7D32); -fx-background-radius: 20;");
        }

        // Effet hover
        configurerEffetsHoverBouton(button);

        button.setOnAction(e -> ouvrirCategorie(categorie));

        return button;
    }
    
    private void configurerEffetsHoverBouton(Button button) {
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(ANIMATION_DURATION), button);
        scaleIn.setToX(1.05);
        scaleIn.setToY(1.05);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(ANIMATION_DURATION), button);
        scaleOut.setToX(1.0);
        scaleOut.setToY(1.0);

        button.setOnMouseEntered(e -> scaleIn.playFromStart());
        button.setOnMouseExited(e -> scaleOut.playFromStart());
    }

    /**
     * Retourne l'icône appropriée pour une catégorie
     */
    private String getCategoryIcon(String categorie) {
        String cat = categorie.toLowerCase();
        if (cat.contains("aliment") || cat.contains("food")) {
            return "🍞";
        } else if (cat.contains("boisson") || cat.contains("drink")) {
            return "🥤";
        } else if (cat.contains("tabac") || cat.contains("tobacco")) {
            return "🚬";
        } else if (cat.contains("hygiene") || cat.contains("hygiène")) {
            return "🧴";
        } else if (cat.contains("divers") || cat.contains("other")) {
            return "📦";
        }
        return "🏷️";
    }

    /**
     * Affiche un message si aucune catégorie n'est disponible
     */
    private void afficherMessageAucuneCategorie() {
        VBox messageBox = new VBox(15);
        messageBox.setAlignment(Pos.CENTER);
        messageBox.setStyle("-fx-padding: 40px;");

        Label iconLabel = new Label("📭");
        iconLabel.setStyle("-fx-font-size: 64px;");

        Label messageLabel = new Label("Aucune catégorie disponible");
        messageLabel.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #999;"
        );

        Label infoLabel = new Label("Ajoutez des produits avec des catégories dans la gestion des stocks");
        infoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(400);
        infoLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        messageBox.getChildren().addAll(iconLabel, messageLabel, infoLabel);
        categoriesContainer.getChildren().add(messageBox);
    }

    /**
     * Affiche un message d'erreur en cas de problème de chargement
     */
    private void afficherErreurChargement() {
        VBox errorBox = new VBox(15);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.setStyle("-fx-padding: 40px;");

        Label iconLabel = new Label("⚠️");
        iconLabel.setStyle("-fx-font-size: 64px;");

        Label errorLabel = new Label("Erreur de chargement");
        errorLabel.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #f44336;"
        );

        Label infoLabel = new Label("Impossible de charger les catégories. Vérifiez la connexion à la base de données.");
        infoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(400);
        infoLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button retryButton = new Button("🔄 Réessayer");
        retryButton.getStyleClass().addAll("btn", "btn-primary");
        retryButton.setOnAction(e -> chargerCategories());

        errorBox.getChildren().addAll(iconLabel, errorLabel, infoLabel, retryButton);
        categoriesContainer.getChildren().add(errorBox);

        showAlert(Alert.AlertType.ERROR, "Erreur",
                "Impossible de charger les catégories depuis la base de données.");
    }

    /**
     * Ouvre la vue des produits d'une catégorie
     */
    private void ouvrirCategorie(String categorie) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/CategorieProduits.fxml"));
            javafx.scene.Parent root = loader.load();

            CategorieProduitsController controller = loader.getController();
            controller.setCategorie(categorie);

            Stage stage = (Stage) panierButton.getScene().getWindow();
            stage.getScene().setRoot(root);
            stage.setTitle("Produits - " + categorie);
        } catch (IOException e) {
            LOG.error("Erreur lors du chargement de la page catégorie: " + e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Impossible d'ouvrir la catégorie: " + e.getMessage());
        }
    }

    // ============================================
    // GESTION DE LA RECHERCHE RAPIDE
    // ============================================

    /**
     * Recherche un produit et affiche les informations
     */
    private void rechercherProduit(String recherche) {
        produitTrouve = produitDAO.rechercherProduit(recherche);

        if (produitTrouve != null) {
            produitInfoLabel.setText("✓ " + produitTrouve.getNom() + " - " +
                    String.format("%.2f DT", produitTrouve.getPrixVenteDefaut()) +
                    " (Stock: " + produitTrouve.getQuantiteStock() + ")");
            produitInfoLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold; -fx-font-size: 13px;");

            // Ajout automatique pour code-barres
            if (estCodeBarre(recherche)) {
                ajouterAutomatiquementApresDelai();
            }
        } else {
            produitInfoLabel.setText("❌ Produit introuvable");
            produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
        }
    }

    /**
     * Vérifie si la recherche est un code-barres
     */
    private boolean estCodeBarre(String recherche) {
        return recherche.matches("\\d+") && recherche.length() >= 8;
    }

    /**
     * Ajoute automatiquement le produit après un court délai (pour code-barres scanné)
     * Cette méthode est appelée depuis rechercherProduit() quand un code-barres est détecté
     */
    private void ajouterAutomatiquementApresDelai() {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Thread.sleep(200);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            if (produitTrouve != null && produitTrouve.getQuantiteStock() > 0) {
                ajouterAuPanier(1);
            }
        });
        new Thread(task).start();
    }

    @FXML
    private void handleRecherche() {
        handleAjouterRapide();
    }

    @FXML
    private void handleAjouterRapide() {
        String recherche = rechercheField.getText().trim();

        if (recherche.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Recherche vide",
                    "Veuillez entrer un nom de produit ou un code-barres.");
            return;
        }

        // Si produit déjà trouvé, utiliser celui-ci, sinon rechercher
        if (produitTrouve == null) {
            produitTrouve = produitDAO.rechercherProduit(recherche);
        }

        if (produitTrouve == null) {
            showAlert(Alert.AlertType.WARNING, "Produit introuvable",
                    "Aucun produit trouvé avec ce nom ou code-barres.");
            reinitialiserRecherche();
            return;
        }

        if (produitTrouve.getQuantiteStock() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Stock insuffisant",
                    "Ce produit n'est plus en stock.");
            reinitialiserRecherche();
            return;
        }

        int quantite = obtenirQuantite();
        if (quantite <= 0) {
            showAlert(Alert.AlertType.WARNING, "Quantité invalide",
                    "Veuillez entrer une quantité valide.");
            return;
        }

        if (quantite > produitTrouve.getQuantiteStock()) {
            showAlert(Alert.AlertType.WARNING, "Stock insuffisant",
                    "Stock disponible: " + produitTrouve.getQuantiteStock());
            return;
        }

        ajouterAuPanier(quantite);
    }

    /**
     * Obtient la quantité saisie
     */
    private int obtenirQuantite() {
        try {
            int quantite = Integer.parseInt(quantiteField.getText().trim());
            return quantite > 0 ? quantite : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Ajoute un produit au panier (avec popups pour erreurs)
     */
    private void ajouterAuPanier(int quantite) {
        if (produitTrouve == null) {
            showAlert(Alert.AlertType.WARNING, "Aucun produit", "Veuillez sélectionner un produit d'abord.");
            return;
        }
        
        // Si c'est un produit "frak cigarette", afficher ComboBox pour choisir le produit tabac associé
        if (produitTrouve.isFrakCigarette()) {
            afficherPopupChoixProduitTabac(produitTrouve, quantite);
        }
        // Si c'est un produit tabac normal, afficher popup pour choisir paquet ou cigarette
        else if (produitTrouve.isTabac()) {
            afficherPopupChoixTabac(produitTrouve, quantite);
        } else {
            ajouterAuPanierSilencieux(produitTrouve, quantite, null);
        }
    }
    
    /**
     * Affiche une popup avec ComboBox pour choisir le produit tabac associé pour les "frak cigarettes"
     */
    private void afficherPopupChoixProduitTabac(Produit produitFrak, int quantite) {
        LOG.info("Popup choix produit tabac pour frak cigarette: " + produitFrak.getNom());
        
        // Récupérer tous les produits tabac disponibles
        List<Produit> produitsTabac = produitDAO.findProduitsTabac();
        
        if (produitsTabac == null || produitsTabac.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Aucun produit tabac", 
                    "Aucun produit tabac disponible. Impossible de vendre des cigarettes.");
            return;
        }
        
        // Créer le dialogue
        javafx.scene.control.Dialog<Produit> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Choix du produit tabac");
        dialog.setHeaderText("Choisissez le produit tabac pour: " + produitFrak.getNom());
        dialog.setContentText("Sélectionnez le produit tabac dont le stock sera décrémenté:");
        
        // Créer le ComboBox
        ComboBox<Produit> produitTabacComboBox = new ComboBox<>(FXCollections.observableArrayList(produitsTabac));
        produitTabacComboBox.setPromptText("Sélectionnez un produit tabac...");
        produitTabacComboBox.setPrefWidth(300);
        
        // Configurer l'affichage du ComboBox
        produitTabacComboBox.setCellFactory(param -> new javafx.scene.control.ListCell<Produit>() {
            @Override
            protected void updateItem(Produit item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getNom() + " (Stock: " + item.getQuantiteStock() + ")");
                }
            }
        });
        
        produitTabacComboBox.setButtonCell(new javafx.scene.control.ListCell<Produit>() {
            @Override
            protected void updateItem(Produit item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getNom() + " (Stock: " + item.getQuantiteStock() + ")");
                }
            }
        });
        
        // Créer le layout
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().addAll(
            new Label("Produit tabac:"),
            produitTabacComboBox
        );
        dialog.getDialogPane().setContent(vbox);
        
        // Ajouter les boutons
        ButtonType buttonTypeOk = new ButtonType("Valider", ButtonBar.ButtonData.OK_DONE);
        ButtonType buttonTypeCancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(buttonTypeOk, buttonTypeCancel);
        
        // Désactiver le bouton OK tant qu'aucun produit n'est sélectionné
        javafx.scene.control.Button okButton = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(buttonTypeOk);
        okButton.setDisable(true);
        
        produitTabacComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            okButton.setDisable(newVal == null);
        });
        
        // Convertir le résultat
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == buttonTypeOk) {
                return produitTabacComboBox.getValue();
            }
            return null;
        });
        
        // Afficher le dialogue et traiter le résultat
        java.util.Optional<Produit> result = dialog.showAndWait();
        result.ifPresent(produitTabacSelectionne -> {
            LOG.info("Produit tabac sélectionné: " + produitTabacSelectionne.getNom());
            ajouterAuPanierSilencieuxAvecTabacAssocie(produitFrak, quantite, produitTabacSelectionne.getId());
            produitInfoLabel.setText("✓ " + produitFrak.getNom() + " -> " + produitTabacSelectionne.getNom());
            produitInfoLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold; -fx-font-size: 13px;");
        });
    }
    
    /**
     * Affiche une popup pour choisir entre paquet et cigarette pour les produits tabac
     */
    /**
     * Ouvre le dialogue de vente tabac : unité, quantité et total avant validation.
     *
     * Remplace l'ancienne Alert à deux boutons, qui ne demandait pas la quantité
     * et proposait « Cigarette » même pour les produits sans prix unitaire.
     */
    private void afficherPopupChoixTabac(Produit produit, int quantite) {
        ui.DialogueVenteTabac.ouvrir(produit).ifPresent(choix -> {
            ajouterAuPanierSilencieux(produit, choix.quantite, choix.uniteVente);

            String libelle = choix.estCigarette()
                    ? choix.quantite + " cigarette(s)"
                    : choix.quantite + " paquet(s)";
            ui.Toast.succes(produitInfoLabel, String.format(
                    "%s — %s (%.3f DT)", produit.getNom(), libelle, choix.total));

            LOG.info("Tabac ajouté : {} x{} {} = {} DT",
                    produit.getNom(), choix.quantite, choix.uniteVente, choix.total);
        });
    }

    /**
     * Ajoute un produit au panier SANS popup (ajout silencieux pour scan code-barres)
     * @param produit Le produit à ajouter
     * @param quantite La quantité
     * @param typeVenteTabac "paquet" ou "cigarette" pour tabac, null pour autres produits
     *                       Si null et produit tabac, ajout direct en paquet (code-barres)
     */
    private void ajouterAuPanierSilencieux(Produit produit, int quantite, String typeVenteTabac) {
        if (produit == null) return;
        
        // Si produit tabac et typeVenteTabac null (code-barres), ajout direct en paquet
        // Créer une variable finale pour utiliser dans la lambda
        final String typeVenteFinal;
        if (produit.isTabac() && typeVenteTabac == null) {
            typeVenteFinal = "paquet";
        } else {
            typeVenteFinal = typeVenteTabac;
        }
        
        javafx.collections.ObservableList<DetailVente> panier = service.SessionContext.get().getPanier().getLignes();
        // Chercher un détail existant avec le même produit ET le même type de vente (pour tabac)
        DetailVente detailExistant = panier.stream()
                .filter(d -> {
                    if (d.getProduitId() == produit.getId()) {
                        // Pour tabac, vérifier aussi le type de vente
                        if (produit.isTabac()) {
                            String typeExistant = d.getTypeVenteTabac();
                            return (typeVenteFinal != null && typeVenteFinal.equals(typeExistant)) ||
                                   (typeVenteFinal == null && typeExistant == null);
                        }
                        return true; // Pour non-tabac, juste vérifier le produit
                    }
                    return false;
                })
                .findFirst()
                .orElse(null);

        if (detailExistant != null) {
            int nouvelleQuantite = detailExistant.getQuantite() + quantite;
            if (nouvelleQuantite > produit.getQuantiteStock()) {
                // Pas de popup, juste un message dans le label
                produitInfoLabel.setText("❌ Stock insuffisant: " + produit.getQuantiteStock());
                produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
                return;
            }
            detailExistant.setQuantite(nouvelleQuantite);
        } else {
            // Vérifier que les prix sont valides
            if (produit.getPrixVenteDefaut() == null) {
                produitInfoLabel.setText("❌ Prix manquant");
                produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
                return;
            }

            // Tarif correspondant à l'unité vendue. Le prix du paquet était
            // appliqué quel que soit le choix : une cigarette à l'unité était
            // donc facturée au prix du paquet entier.
            java.math.BigDecimal prixUnitaire;
            if ("cigarette".equals(typeVenteFinal)) {
                if (!produit.vendableALaCigarette()) {
                    produitInfoLabel.setText("❌ « " + produit.getNom()
                            + " » ne se vend qu'au paquet");
                    produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
                    return;
                }
                prixUnitaire = produit.getPrixVenteCigarette();
            } else {
                prixUnitaire = produit.getPrixVenteDefaut();
            }

            DetailVente detail = new DetailVente();
            detail.setProduitId(produit.getId());
            detail.setQuantite(quantite);
            detail.setPrixVenteUnitaire(prixUnitaire);
            detail.setPrixAchatUnitaire(produit.getPrixAchatActuel() != null ? produit.getPrixAchatActuel() : java.math.BigDecimal.ZERO);
            detail.setProduit(produit);
            detail.setTypeVenteTabac(typeVenteFinal);
            panier.add(detail);
        }

        // Mise à jour silencieuse
        updatePanierCount();
    }
    
    /**
     * Ajoute un produit "frak cigarette" au panier avec le produit tabac associé
     * @param produitFrak Le produit "frak cigarette"
     * @param quantite La quantité de cigarettes
     * @param produitTabacAssocieId L'ID du produit tabac associé
     */
    private void ajouterAuPanierSilencieuxAvecTabacAssocie(Produit produitFrak, int quantite, int produitTabacAssocieId) {
        if (produitFrak == null) return;
        
        javafx.collections.ObservableList<DetailVente> panier = service.SessionContext.get().getPanier().getLignes();
        
        // Chercher un détail existant avec le même produit "frak" et le même produit tabac associé
        DetailVente detailExistant = panier.stream()
                .filter(d -> {
                    if (d.getProduitId() == produitFrak.getId()) {
                        // Pour "frak cigarette", vérifier aussi le produit tabac associé
                        Integer tabacAssocieExistant = d.getProduitTabacAssocieId();
                        return tabacAssocieExistant != null && tabacAssocieExistant == produitTabacAssocieId;
                    }
                    return false;
                })
                .findFirst()
                .orElse(null);

        if (detailExistant != null) {
            // Ajouter à la quantité existante
            detailExistant.setQuantite(detailExistant.getQuantite() + quantite);
        } else {
            // Vérifier que les prix sont valides
            if (produitFrak.getPrixVenteDefaut() == null) {
                produitInfoLabel.setText("❌ Prix manquant");
                produitInfoLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold; -fx-font-size: 13px;");
                return;
            }
            
            DetailVente detail = new DetailVente();
            detail.setProduitId(produitFrak.getId());
            detail.setQuantite(quantite);
            detail.setPrixVenteUnitaire(produitFrak.getPrixVenteDefaut());
            detail.setPrixAchatUnitaire(produitFrak.getPrixAchatActuel() != null ? produitFrak.getPrixAchatActuel() : java.math.BigDecimal.ZERO);
            detail.setProduit(produitFrak);
            detail.setProduitTabacAssocieId(produitTabacAssocieId);
            detail.setTypeVenteTabac("cigarette"); // Les "frak cigarettes" sont toujours vendues en cigarettes
            panier.add(detail);
        }

        // Mise à jour silencieuse
        updatePanierCount();
    }

    /**
     * Réinitialise les champs de recherche
     */
    private void reinitialiserRecherche() {
        rechercheField.clear();
        quantiteField.setText("1");
        produitInfoLabel.setText("");
        produitTrouve = null;
        rechercheField.requestFocus();
    }

    // ============================================
    // GESTION DE LA QUANTITÉ
    // ============================================

    @FXML
    private void handlePlusQuantite() {
        try {
            int quantite = Integer.parseInt(quantiteField.getText().trim());
            quantite++;
            quantiteField.setText(String.valueOf(quantite));
        } catch (NumberFormatException e) {
            quantiteField.setText("1");
        }
    }

    @FXML
    private void handleMoinsQuantite() {
        try {
            int quantite = Integer.parseInt(quantiteField.getText().trim());
            if (quantite > 1) {
                quantite--;
                quantiteField.setText(String.valueOf(quantite));
            }
        } catch (NumberFormatException e) {
            quantiteField.setText("1");
        }
    }

    // ============================================
    // NAVIGATION
    // ============================================

    @FXML
    private void handleVoirPanier() {
        try {
            Stage stage = (Stage) panierButton.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/Caisse.fxml", "Caisse - Point de Vente");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Erreur lors de l'ouverture du panier: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeconnexion() {
        ConnexionController.deconnecter();
        try {
            Stage stage = (Stage) deconnexionButton.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/Connexion.fxml", "Connexion");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Erreur lors de la déconnexion: " + e.getMessage());
        }
    }

    // ============================================
    // UTILITAIRES
    // ============================================

    /**
     * Met à jour le compteur du panier
     */
    private void updatePanierCount() {
        int count = 0;
        if (service.SessionContext.get().getPanier().getLignes() != null) {
            count = service.SessionContext.get().getPanier().getLignes().size();
        }
        if (panierCountLabel != null) {
            panierCountLabel.setText("Panier: " + count);
        }
    }

    /**
     * Ouvre l'interface d'ajout de stock pour les employés
     */
    @FXML
    private void handleAjoutStock() {
        try {
            Stage stage = (Stage) ajoutStockButton.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/AjoutStockEmploye.fxml", "Ajout de Stock - Employé");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors du chargement de l'ajout de stock: " + e.getMessage());
        }
    }

    /**
     * Affiche une alerte
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
package controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import dao.CreditFournisseurDAO;
import dao.FournisseurDAO;
import dao.ProduitDAO;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.CreditFournisseur;
import model.Fournisseur;
import model.Produit;
import util.FXMLUtils;
import util.PopupManager;

/**
 * Contrôleur pour l'ajout de stock par les employés
 * Permet uniquement de voir et ajouter du stock (pas de modification/suppression)
 */
@SuppressWarnings("unused") // FXML-bound methods appear unused to static analysis
public class AjoutStockEmployeController {
    private static final Logger LOG = LoggerFactory.getLogger(AjoutStockEmployeController.class);


    @FXML
    private StackPane rootPane;

    @FXML
    private TextField rechercheField;
    
    @FXML
    private Button rechercherButton;
    
    @FXML
    private VBox produitInfoBox;
    
    @FXML
    private Label nomLabel;
    
    @FXML
    private Label codeBarreLabel;
    
    @FXML
    private Label stockActuelLabel;
    
    @FXML
    private Label prixVenteLabel;
    
    @FXML
    private Label categorieLabel;
    
    @FXML
    private Label uniteLabel;
    
    @FXML
    private Label prixAchatLabel;
    
    @FXML
    private Label seuilAlerteLabel;
    
    @FXML
    private TextArea descriptionProduitField;
    
    @FXML
    private TextField quantiteField;
    
    @FXML
    private Button moinsButton;
    
    @FXML
    private Button plusButton;
    
    @FXML
    private Button ajouterStockButton;
    
    @FXML
    private Button ajouterRapideButton;
    
    @FXML
    private Label messageLabel;
    
    @FXML
    private FlowPane produitsContainer;
    
    @FXML
    private TextField filtreField;
    
    @FXML
    private ComboBox<Fournisseur> fournisseurComboBox;
    
    @FXML
    private TextField montantPaiementField;
    
    @FXML
    private TextField creditUtiliseField;
    
    @FXML
    private TextArea notesField;
    
    @FXML
    private Label creditDisponibleLabel;

    // Lecture seule : l'affichage des produits, des fournisseurs et du crédit
    // disponible. Toute écriture passe par ApprovisionnementService, qui la
    // place dans une transaction unique.
    private ProduitDAO produitDAO;
    private FournisseurDAO fournisseurDAO;
    private CreditFournisseurDAO creditDAO;
    private service.ApprovisionnementService approvisionnementService;
    private Produit produitSelectionne;
    private ObservableList<Produit> tousProduits;
    private ObservableList<Produit> produitsFiltres;
    private ObservableList<Fournisseur> fournisseurs;

    @FXML
    private void initialize() {
        produitDAO = new ProduitDAO();
        fournisseurDAO = new FournisseurDAO();
        creditDAO = new CreditFournisseurDAO();
        approvisionnementService = new service.ApprovisionnementService();
        
        tousProduits = FXCollections.observableArrayList();
        produitsFiltres = FXCollections.observableArrayList();
        fournisseurs = FXCollections.observableArrayList();
        
        // Configurer le stage pour plein écran
        configurerPleinEcran();
        
        // Animation d'entrée pour les panneaux
        animerEntree();
        
        chargerTousProduits();
        chargerFournisseurs();
        afficherProduits();
        
        // Configurer le ComboBox des fournisseurs
        fournisseurComboBox.setItems(fournisseurs);
        fournisseurComboBox.setOnAction(e -> mettreAJourCreditDisponible());
        
        // ✨ LIVE SEARCH: Recherche automatique pendant que vous tapez
        rechercheField.textProperty().addListener((observable, oldValue, newValue) -> {
            rechercheAutomatique(newValue);
        });
        
        // ✨ SCAN CODE-BARRES: Détection automatique du scan
        configurerScanCodeBarres(rechercheField);
        
        // S'assurer que tous les boutons sont visibles
        if (moinsButton != null) {
            moinsButton.setVisible(true);
            moinsButton.setManaged(true);
        }
        if (plusButton != null) {
            plusButton.setVisible(true);
            plusButton.setManaged(true);
        }
        if (ajouterStockButton != null) {
            ajouterStockButton.setVisible(true);
            ajouterStockButton.setManaged(true);
        }
        if (ajouterRapideButton != null) {
            ajouterRapideButton.setVisible(true);
            ajouterRapideButton.setManaged(true);
        }
    }
    
    /**
     * Configure la fenêtre en plein écran
     */
    private void configurerPleinEcran() {
        javafx.application.Platform.runLater(() -> {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            if (stage != null) {
                // Get screen dimensions
                javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
                javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();
                
                // Set stage dimensions to match screen
                stage.setX(bounds.getMinX());
                stage.setY(bounds.getMinY());
                stage.setWidth(bounds.getWidth());
                stage.setHeight(bounds.getHeight());
                stage.setResizable(true);
                stage.setFullScreen(true);
                stage.setFullScreenExitHint("Appuyez sur Échap pour quitter le mode plein écran");
                
                // Ensure full screen is maintained
                stage.setOnShown(e -> {
                    stage.setFullScreen(true);
                });
            }
        });
    }
    
    /**
     * Animation d'entrée pour les panneaux principaux
     */
    private void animerEntree() {
        // Fade in pour le root pane
        rootPane.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), rootPane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
        
        // Animation pour les panneaux gauche et droit
        javafx.application.Platform.runLater(() -> {
            // Trouver les panneaux par leur style
            if (rootPane.getChildren().size() > 0) {
                javafx.scene.Node anchorPane = rootPane.getChildren().get(0);
                if (anchorPane instanceof javafx.scene.layout.AnchorPane) {
                    javafx.scene.Node vbox = ((javafx.scene.layout.AnchorPane) anchorPane).getChildren().get(1);
                    if (vbox instanceof VBox) {
                        VBox mainVBox = (VBox) vbox;
                        if (mainVBox.getChildren().size() > 1) {
                            javafx.scene.Node hbox = mainVBox.getChildren().get(1);
                            if (hbox instanceof HBox) {
                                HBox contentHBox = (HBox) hbox;
                                animerPanneaux(contentHBox);
                            }
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Anime les panneaux gauche et droit
     */
    private void animerPanneaux(HBox hbox) {
        if (hbox.getChildren().size() >= 2) {
            // Panneau gauche - slide from left
            javafx.scene.Node leftPanel = hbox.getChildren().get(0);
            leftPanel.setTranslateX(-50);
            leftPanel.setOpacity(0);
            TranslateTransition slideLeft = new TranslateTransition(Duration.millis(500), leftPanel);
            slideLeft.setToX(0);
            FadeTransition fadeLeft = new FadeTransition(Duration.millis(500), leftPanel);
            fadeLeft.setToValue(1);
            ParallelTransition leftAnim = new ParallelTransition(slideLeft, fadeLeft);
            leftAnim.setDelay(Duration.millis(100));
            leftAnim.play();
            
            // Panneau droit - slide from right
            javafx.scene.Node rightPanel = hbox.getChildren().get(1);
            rightPanel.setTranslateX(50);
            rightPanel.setOpacity(0);
            TranslateTransition slideRight = new TranslateTransition(Duration.millis(500), rightPanel);
            slideRight.setToX(0);
            FadeTransition fadeRight = new FadeTransition(Duration.millis(500), rightPanel);
            fadeRight.setToValue(1);
            ParallelTransition rightAnim = new ParallelTransition(slideRight, fadeRight);
            rightAnim.setDelay(Duration.millis(200));
            rightAnim.play();
        }
    }
    
    /**
     * Charge tous les fournisseurs
     */
    private void chargerFournisseurs() {
        fournisseurs.clear();
        List<Fournisseur> liste = fournisseurDAO.findAll();
        fournisseurs.addAll(liste);
    }
    
    /**
     * Met à jour l'affichage du crédit disponible pour le fournisseur sélectionné
     */
    private void mettreAJourCreditDisponible() {
        Fournisseur fournisseur = fournisseurComboBox.getValue();
        if (fournisseur != null) {
            CreditFournisseur credit = creditDAO.findByFournisseurId(fournisseur.getId());
            BigDecimal montant = credit != null ? credit.getMontant() : BigDecimal.ZERO;
            creditDisponibleLabel.setText("Crédit disponible: " + String.format("%.2f DT", montant));
            
            // Animation smooth pour afficher le crédit
            if (!creditDisponibleLabel.isVisible()) {
                creditDisponibleLabel.setOpacity(0);
                creditDisponibleLabel.setVisible(true);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), creditDisponibleLabel);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                fadeIn.play();
            } else {
                // Animation de pulse pour la mise à jour
                ScaleTransition pulse = new ScaleTransition(Duration.millis(150), creditDisponibleLabel);
                pulse.setFromX(1.0);
                pulse.setFromY(1.0);
                pulse.setToX(1.1);
                pulse.setToY(1.1);
                pulse.setAutoReverse(true);
                pulse.setCycleCount(2);
                pulse.play();
            }
        } else {
            // Animation fade out
            if (creditDisponibleLabel.isVisible()) {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(200), creditDisponibleLabel);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(e -> creditDisponibleLabel.setVisible(false));
                fadeOut.play();
            }
        }
    }

    /**
     * Charge tous les produits depuis la base de données
     */
    private void chargerTousProduits() {
        tousProduits.clear();
        List<Produit> produits = produitDAO.findAll();
        // Charger TOUS les produits (même avec stock 0) pour pouvoir ajouter du stock
        tousProduits.addAll(produits);
        produitsFiltres.setAll(tousProduits);
        
        // Debug: afficher le nombre de produits chargés
        LOG.info("Produits chargés pour ajout stock: " + tousProduits.size());
    }

    /**
     * Affiche les produits dans le FlowPane avec animation
     */
    private void afficherProduits() {
        produitsContainer.getChildren().clear();
        
        if (produitsFiltres.isEmpty()) {
            // Message si aucun produit
            VBox emptyState = new VBox(20);
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(60));
            emptyState.getStyleClass().add("empty-state");
            emptyState.setOpacity(0);
            
            Label emptyIcon = new Label("📦");
            emptyIcon.getStyleClass().add("empty-state-icon");
            emptyIcon.setStyle("-fx-font-size: 64px;");
            
            Label emptyTitle = new Label("Aucun produit trouvé");
            emptyTitle.getStyleClass().add("empty-state-title");
            emptyTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
            
            Label emptyDesc = new Label("Essayez de modifier votre recherche ou ajoutez des produits à la base de données.");
            emptyDesc.getStyleClass().add("empty-state-description");
            emptyDesc.setStyle("-fx-font-size: 14px; -fx-wrap-text: true; -fx-text-alignment: center;");
            emptyDesc.setWrapText(true);
            emptyDesc.setMaxWidth(400);
            
            emptyState.getChildren().addAll(emptyIcon, emptyTitle, emptyDesc);
            produitsContainer.getChildren().add(emptyState);
            
            // Animation fade in
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), emptyState);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        } else {
            // Afficher les cartes produit avec animation séquentielle
            for (int i = 0; i < produitsFiltres.size(); i++) {
                Produit produit = produitsFiltres.get(i);
                VBox card = creerCarteProduit(produit);
                card.setOpacity(0);
                card.setScaleX(0.8);
                card.setScaleY(0.8);
                produitsContainer.getChildren().add(card);
                
                // Animation séquentielle pour chaque carte
                SequentialTransition cardAnim = new SequentialTransition();
                FadeTransition fade = new FadeTransition(Duration.millis(200), card);
                fade.setFromValue(0);
                fade.setToValue(1);
                ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
                scale.setFromX(0.8);
                scale.setFromY(0.8);
                scale.setToX(1.0);
                scale.setToY(1.0);
                cardAnim.getChildren().addAll(fade, scale);
                cardAnim.setDelay(Duration.millis(i * 30)); // Délai progressif
                cardAnim.play();
            }
        }
    }

    /**
     * Crée une carte produit pour l'affichage
     */
    private VBox creerCarteProduit(Produit produit) {
        VBox card = new VBox(12);
        card.getStyleClass().add("product-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(18));
        // Largeur fixe pour les cartes pour un meilleur affichage
        card.setPrefWidth(280);
        card.setMinWidth(280);
        card.setMaxWidth(280);

        // ============ NOM du produit (GRAND et CLAIR) ============
        Label nomLabel = new Label(produit.getNom());
        nomLabel.getStyleClass().add("product-name");
        nomLabel.setWrapText(true);
        nomLabel.setMaxWidth(Double.MAX_VALUE);
        nomLabel.setAlignment(Pos.TOP_LEFT);
        nomLabel.setStyle("-fx-padding: 0 0 8 0;");

        // ============ PRICE: Prix de vente (TRÈS VISIBLE) ============
        HBox priceContainer = new HBox();
        priceContainer.setAlignment(Pos.CENTER_LEFT);
        priceContainer.setStyle("-fx-padding: 5 0;");
        
        Label prixLabel = new Label(String.format("%.2f DT", produit.getPrixVenteDefaut()));
        prixLabel.getStyleClass().add("product-price");
        priceContainer.getChildren().add(prixLabel);

        // ============ DETAILS: Informations ============
        VBox detailsBox = new VBox(5);
        detailsBox.setAlignment(Pos.TOP_LEFT);
        detailsBox.setStyle("-fx-padding: 8 0;");
        
        // Code-barres
        Label codeLabel = new Label("📋 " + produit.getCodeBarre());
        codeLabel.getStyleClass().add("product-code");
        codeLabel.setAlignment(Pos.CENTER_LEFT);
        detailsBox.getChildren().add(codeLabel);
        
        // Unité (si disponible)
        if (produit.getUnite() != null && !produit.getUnite().isEmpty()) {
            Label uniteLabel = new Label("📦 " + produit.getUnite());
            uniteLabel.getStyleClass().add("product-unite");
            uniteLabel.setAlignment(Pos.CENTER_LEFT);
            detailsBox.getChildren().add(uniteLabel);
        }

        // ============ Spacer pour pousser le footer en bas ============
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // ============ FOOTER: Stock + Button ============
        VBox footerBox = new VBox(10);
        footerBox.setAlignment(Pos.BOTTOM_CENTER);
        footerBox.setStyle("-fx-padding: 10 0 0 0;");

        // STOCK: Badge coloré
        HBox stockContainer = new HBox();
        stockContainer.setAlignment(Pos.CENTER);
        stockContainer.setStyle("-fx-padding: 5 0;");
        
        Label stockLabel = new Label("📊 Stock: " + produit.getQuantiteStock());
        stockLabel.getStyleClass().add("product-stock");
        
        // Couleur selon le niveau de stock
        if (produit.getQuantiteStock() == 0) {
            stockLabel.getStyleClass().add("stock-critical");
        } else if (produit.isStockFaible() || produit.getQuantiteStock() <= produit.getSeuilAlerte()) {
            stockLabel.getStyleClass().add("stock-low");
        } else if (produit.getQuantiteStock() > 50) {
            stockLabel.getStyleClass().add("stock-high");
        } else {
            stockLabel.getStyleClass().add("stock-medium");
        }
        
        stockContainer.getChildren().add(stockLabel);

        // BUTTON: Sélectionner (fixé en bas)
        Button selectButton = new Button("✅ Sélectionner");
        selectButton.getStyleClass().addAll("btn", "btn-primary");
        selectButton.setMaxWidth(Double.MAX_VALUE);
        selectButton.setMinHeight(40);
        selectButton.setAlignment(Pos.CENTER);
        selectButton.setOnAction(e -> selectionnerProduit(produit));

        footerBox.getChildren().addAll(stockContainer, selectButton);

        // Assembler la carte dans l'ordre
        card.getChildren().addAll(
            nomLabel,
            priceContainer,
            detailsBox,
            spacer,
            footerBox
        );

        // Effet hover smooth
        configurerEffetHover(card);

        return card;
    }

    /**
     * Configure l'effet hover sur la carte avec animations smooth
     */
    private void configurerEffetHover(VBox card) {
        // Animation d'entrée - scale + shadow
        card.setOnMouseEntered(e -> {
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), card);
            scaleIn.setToX(1.05);
            scaleIn.setToY(1.05);
            
            // Ajouter une ombre pour l'effet de profondeur
            card.setStyle(card.getStyle() + 
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);");
            
            scaleIn.play();
        });
        
        // Animation de sortie
        card.setOnMouseExited(e -> {
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), card);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);
            
            // Retirer l'ombre
            card.setStyle(card.getStyle().replace(
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);", ""));
            
            scaleOut.play();
        });
        
        // Animation de clic
        card.setOnMousePressed(e -> {
            ScaleTransition press = new ScaleTransition(Duration.millis(100), card);
            press.setToX(0.98);
            press.setToY(0.98);
            press.play();
        });
        
        card.setOnMouseReleased(e -> {
            ScaleTransition release = new ScaleTransition(Duration.millis(100), card);
            release.setToX(1.0);
            release.setToY(1.0);
            release.play();
        });
    }

    /**
     * Sélectionne un produit pour ajouter du stock
     */
    private void selectionnerProduit(Produit produit) {
        produitSelectionne = produit;
        afficherInfoProduit(produit);
        quantiteField.setText("1");
        
        // Animation smooth pour afficher le panneau d'info
        if (!produitInfoBox.isVisible()) {
            produitInfoBox.setOpacity(0);
            produitInfoBox.setTranslateY(-20);
            produitInfoBox.setVisible(true);
            
            ParallelTransition showInfo = new ParallelTransition(
                new FadeTransition(Duration.millis(300), produitInfoBox),
                new TranslateTransition(Duration.millis(300), produitInfoBox)
            );
            ((FadeTransition) showInfo.getChildren().get(0)).setFromValue(0);
            ((FadeTransition) showInfo.getChildren().get(0)).setToValue(1);
            ((TranslateTransition) showInfo.getChildren().get(1)).setFromY(-20);
            ((TranslateTransition) showInfo.getChildren().get(1)).setToY(0);
            showInfo.play();
        } else {
            // Animation de mise à jour
            ScaleTransition pulse = new ScaleTransition(Duration.millis(200), produitInfoBox);
            pulse.setFromX(1.0);
            pulse.setFromY(1.0);
            pulse.setToX(1.02);
            pulse.setToY(1.02);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(2);
            pulse.play();
        }
        
        messageLabel.setVisible(false);
    }

    /**
     * Affiche les informations du produit sélectionné (en lecture seule)
     */
    private void afficherInfoProduit(Produit produit) {
        nomLabel.setText(produit.getNom() != null ? produit.getNom() : "N/A");
        codeBarreLabel.setText(produit.getCodeBarre() != null ? produit.getCodeBarre() : "N/A");
        categorieLabel.setText(produit.getCategorie() != null ? produit.getCategorie() : "N/A");
        stockActuelLabel.setText(String.valueOf(produit.getQuantiteStock()));
        uniteLabel.setText(produit.getUnite() != null ? produit.getUnite() : "unité");
        prixAchatLabel.setText(produit.getPrixAchatActuel() != null ? 
            String.format("%.2f DT", produit.getPrixAchatActuel()) : "N/A");
        prixVenteLabel.setText(produit.getPrixVenteDefaut() != null ? 
            String.format("%.2f DT", produit.getPrixVenteDefaut()) : "N/A");
        seuilAlerteLabel.setText(String.valueOf(produit.getSeuilAlerte()));
        
        // Réinitialiser le champ description (peut être rempli par l'utilisateur)
        if (descriptionProduitField != null) {
            descriptionProduitField.clear();
        }
    }

    /**
     * ✨ LIVE SEARCH: Recherche automatique pendant que vous tapez
     */
    private void rechercheAutomatique(String texte) {
        String recherche = texte.trim().toLowerCase();
        
        if (recherche.isEmpty()) {
            // Si le champ est vide, réinitialiser
            produitsFiltres.setAll(tousProduits);
            produitInfoBox.setVisible(false);
            messageLabel.setVisible(false);
            afficherProduits();
            return;
        }
        
        // Filtrer les produits en temps réel
        List<Produit> resultats = tousProduits.stream()
            .filter(p -> p.getNom().toLowerCase().contains(recherche) ||
                        p.getCodeBarre().toLowerCase().contains(recherche))
            .collect(Collectors.toList());
        
        produitsFiltres.setAll(resultats);
        afficherProduits();
        
        // Si recherche exacte (code-barres ou nom complet), sélectionner automatiquement
        if (resultats.size() == 1) {
            selectionnerProduit(resultats.get(0));
        } else if (recherche.length() >= 8 && recherche.matches("\\d+")) {
            // Si c'est un code-barres complet, chercher exact match
            Produit exact = produitDAO.rechercherProduit(recherche);
            if (exact != null) {
                selectionnerProduit(exact);
            }
        }
    }
    
    @FXML
    private void handleRecherche() {
        String recherche = rechercheField.getText().trim();
        
        if (recherche.isEmpty()) {
            afficherMessage("Veuillez entrer un code-barres ou un nom de produit.", Alert.AlertType.WARNING);
            return;
        }

        // Chercher le produit exact
        Produit produit = produitDAO.rechercherProduit(recherche);
        
        if (produit != null) {
            selectionnerProduit(produit);
        } else {
            produitInfoBox.setVisible(false);
            afficherMessage("Produit non trouvé. Vérifiez le code-barres ou le nom.", Alert.AlertType.WARNING);
        }
    }

    @FXML
    private void handleMoins() {
        try {
            int quantite = Integer.parseInt(quantiteField.getText());
            if (quantite > 1) {
                quantiteField.setText(String.valueOf(quantite - 1));
            }
        } catch (NumberFormatException e) {
            quantiteField.setText("1");
        }
    }

    @FXML
    @SuppressWarnings("unused") // Method is used via FXML binding (onAction="#handlePlus")
    private void handlePlus() {
        try {
            int quantite = Integer.parseInt(quantiteField.getText());
            quantiteField.setText(String.valueOf(quantite + 1));
        } catch (NumberFormatException e) {
            quantiteField.setText("1");
        }
    }

    @FXML
    private void handleAjouterRapide() {
        if (produitSelectionne == null) {
            afficherMessage("Veuillez sélectionner un produit d'abord.", Alert.AlertType.WARNING);
            return;
        }

        // Vérifié avant de toucher au stock : sans employé identifié, l'ajout ne
        // serait pas traçable. L'ancien code modifiait le stock puis ignorait
        // silencieusement l'écriture dans ajouts_stock.
        int idEmploye = service.SessionContext.get().getUtilisateurId();
        if (idEmploye <= 0) {
            afficherMessage("Aucun utilisateur connecté. Reconnectez-vous.", Alert.AlertType.ERROR);
            return;
        }

        try {
            int quantiteAjouter = Integer.parseInt(quantiteField.getText().trim());

            if (quantiteAjouter <= 0) {
                afficherMessage("La quantité doit être supérieure à 0.", Alert.AlertType.WARNING);
                return;
            }

            // Ajout rapide sans fournisseur
            String noteRapide = "Ajout rapide";
            if (descriptionProduitField != null && !descriptionProduitField.getText().trim().isEmpty()) {
                noteRapide += " | Description produit: " + descriptionProduitField.getText().trim();
            }

            // Stock et historique validés ensemble : le stock n'est jamais
            // incrémenté sans sa trace dans ajouts_stock.
            int nouveauStock = approvisionnementService.enregistrerAjout(
                    produitSelectionne.getId(), idEmploye,
                    null,                       // pas de fournisseur pour un ajout rapide
                    quantiteAjouter,
                    BigDecimal.ZERO,            // pas de paiement
                    BigDecimal.ZERO,            // pas de crédit
                    noteRapide, null, null);

            afficherMessage("✅ Stock ajouté rapidement! Nouveau stock: " + nouveauStock, Alert.AlertType.INFORMATION);
            produitSelectionne.setQuantiteStock(nouveauStock);
            afficherInfoProduit(produitSelectionne);

            // Recharger la liste
            chargerTousProduits();
            afficherProduits();

            // Réinitialiser seulement la quantité
            quantiteField.setText("1");

        } catch (NumberFormatException e) {
            afficherMessage("Veuillez entrer un nombre valide.", Alert.AlertType.WARNING);
        } catch (exception.ApplicationException e) {
            afficherMessage(e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleAjouterStock() {
        if (produitSelectionne == null) {
            afficherMessage("Veuillez sélectionner un produit d'abord.", Alert.AlertType.WARNING);
            return;
        }

        // Vérifié avant de toucher au stock ou aux comptes fournisseur.
        int idEmploye = service.SessionContext.get().getUtilisateurId();
        if (idEmploye <= 0) {
            afficherMessage("Aucun utilisateur connecté. Reconnectez-vous.", Alert.AlertType.ERROR);
            return;
        }

        try {
            int quantiteAjouter = Integer.parseInt(quantiteField.getText().trim());

            if (quantiteAjouter <= 0) {
                afficherMessage("La quantité doit être supérieure à 0.", Alert.AlertType.WARNING);
                return;
            }

            // Si le produit est du tabac, afficher un popup pour choisir le type
            String typeAjoutTabac = null;
            Integer quantiteCigarettes = null;
            if (produitSelectionne.isTabac()) {
                java.util.Map<String, Object> resultatTabac = afficherPopupChoixTabac();
                if (resultatTabac == null) {
                    return; // L'utilisateur a annulé
                }
                typeAjoutTabac = (String) resultatTabac.get("type");
                quantiteCigarettes = (Integer) resultatTabac.get("quantiteCigarettes");
            }

            // Récupérer les informations fournisseur
            Fournisseur fournisseur = fournisseurComboBox.getValue();
            Integer idFournisseur = fournisseur != null ? fournisseur.getId() : null;
            
            BigDecimal montantPaiement = BigDecimal.ZERO;
            BigDecimal creditUtilise = BigDecimal.ZERO;
            String notes = notesField.getText().trim();
            
            // Traiter le montant de paiement
            if (!montantPaiementField.getText().trim().isEmpty()) {
                try {
                    montantPaiement = new BigDecimal(montantPaiementField.getText().trim());
                    if (montantPaiement.compareTo(BigDecimal.ZERO) < 0) {
                        afficherMessage("Le montant de paiement ne peut pas être négatif.", Alert.AlertType.WARNING);
                        return;
                    }
                } catch (NumberFormatException e) {
                    afficherMessage("Montant de paiement invalide.", Alert.AlertType.WARNING);
                    return;
                }
            }
            
            // Traiter le crédit utilisé
            if (!creditUtiliseField.getText().trim().isEmpty()) {
                try {
                    creditUtilise = new BigDecimal(creditUtiliseField.getText().trim());
                    if (creditUtilise.compareTo(BigDecimal.ZERO) < 0) {
                        afficherMessage("Le crédit utilisé ne peut pas être négatif.", Alert.AlertType.WARNING);
                        return;
                    }
                    
                    // Vérifier que le crédit est disponible
                    if (idFournisseur != null) {
                        CreditFournisseur credit = creditDAO.findByFournisseurId(idFournisseur);
                        BigDecimal creditDisponible = credit != null ? credit.getMontant() : BigDecimal.ZERO;
                        if (creditUtilise.compareTo(creditDisponible) > 0) {
                            afficherMessage("Crédit insuffisant. Disponible: " + String.format("%.2f DT", creditDisponible), Alert.AlertType.WARNING);
                            return;
                        }
                    }
                } catch (NumberFormatException e) {
                    afficherMessage("Crédit utilisé invalide.", Alert.AlertType.WARNING);
                    return;
                }
            }
            
            // Combiner les notes du fournisseur et la description du produit
            String notesCompletes = notes;
            if (descriptionProduitField != null && !descriptionProduitField.getText().trim().isEmpty()) {
                if (!notesCompletes.isEmpty()) {
                    notesCompletes += " | ";
                }
                notesCompletes += "Description produit: " + descriptionProduitField.getText().trim();
            }

            // Stock, historique, paiement et crédit dans une seule transaction :
            // soit les quatre écritures aboutissent, soit aucune. Auparavant,
            // une panne au milieu pouvait laisser le stock augmenté sans que le
            // crédit fournisseur ait été débité.
            int nouveauStock = approvisionnementService.enregistrerAjout(
                    produitSelectionne.getId(), idEmploye, idFournisseur, quantiteAjouter,
                    montantPaiement, creditUtilise, notesCompletes,
                    typeAjoutTabac, quantiteCigarettes);

            afficherMessage("Stock mis à jour avec succès! Nouveau stock: " + nouveauStock, Alert.AlertType.INFORMATION);
            produitSelectionne.setQuantiteStock(nouveauStock);
            afficherInfoProduit(produitSelectionne);

            // Recharger la liste
            chargerTousProduits();
            afficherProduits();

            // Réinitialiser les champs
            quantiteField.setText("1");
            montantPaiementField.clear();
            creditUtiliseField.clear();
            notesField.clear();
            fournisseurComboBox.setValue(null);
            creditDisponibleLabel.setVisible(false);

        } catch (NumberFormatException e) {
            afficherMessage("Veuillez entrer un nombre valide.", Alert.AlertType.WARNING);
        } catch (exception.ApplicationException e) {
            afficherMessage(e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    private void handleFiltre() {
        String filtre = filtreField.getText().toLowerCase().trim();
        
        if (filtre.isEmpty()) {
            produitsFiltres.setAll(tousProduits);
        } else {
            List<Produit> filtres = tousProduits.stream()
                .filter(p -> p.getNom().toLowerCase().contains(filtre) ||
                           p.getCodeBarre().toLowerCase().contains(filtre))
                .collect(Collectors.toList());
            produitsFiltres.setAll(filtres);
        }
        
        afficherProduits();
    }

    /**
     * Affiche un message moderne avec PopupManager
     */
    private void afficherMessage(String message, Alert.AlertType type) {
        // Utiliser le nouveau système de toast moderne
        if (type == Alert.AlertType.ERROR) {
            PopupManager.showError(message, rootPane);
        } else if (type == Alert.AlertType.WARNING) {
            PopupManager.showWarning(message, rootPane);
        } else if (type == Alert.AlertType.INFORMATION) {
            PopupManager.showSuccess(message, rootPane);
        } else {
            PopupManager.showInfo(message, rootPane);
        }
        
        // Aussi afficher dans le label pour compatibilité
        messageLabel.setText(message);
        messageLabel.setVisible(true);
        
        if (type == Alert.AlertType.ERROR) {
        } else if (type == Alert.AlertType.WARNING) {
        } else {
        }
        
        // Masquer le label après 5 secondes
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                javafx.application.Platform.runLater(() -> messageLabel.setVisible(false));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * Affiche un popup pour choisir le type d'ajout de tabac (paquet ou cigarette)
     * @return Map avec "type" (String) et "quantiteCigarettes" (Integer), ou null si annulé
     */
    private java.util.Map<String, Object> afficherPopupChoixTabac() {
        Dialog<java.util.Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Type d'ajout - Tabac");
        dialog.setHeaderText("Choisir le type d'ajout pour le produit tabac");
        
        ButtonType okButtonType = new ButtonType("OK", ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Annuler", ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label label = new Label("Sélectionnez le type d'ajout:");
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        ComboBox<String> typeComboBox = new ComboBox<>();
        typeComboBox.getItems().addAll("Paquet", "Cigarette");
        typeComboBox.setValue("Paquet");
        
        Label infoLabel = new Label("Pour les cigarettes, vous pouvez spécifier le nombre exact.");
        infoLabel.setStyle("-fx-font-size: 11px;");
        infoLabel.setWrapText(true);
        
        CheckBox cigaretteCheckBox = new CheckBox("Spécifier le nombre de cigarettes");
        TextField quantiteCigarettesField = new TextField();
        quantiteCigarettesField.setPromptText("Nombre de cigarettes (ex: 50)");
        quantiteCigarettesField.setDisable(true);
        
        typeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCigarette = "Cigarette".equals(newVal);
            cigaretteCheckBox.setDisable(!isCigarette);
            if (!isCigarette) {
                cigaretteCheckBox.setSelected(false);
                quantiteCigarettesField.setDisable(true);
                quantiteCigarettesField.clear();
            } else {
                // Si cigarette est sélectionné, activer la checkbox par défaut
                cigaretteCheckBox.setSelected(true);
                quantiteCigarettesField.setDisable(false);
            }
        });
        
        cigaretteCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            quantiteCigarettesField.setDisable(!newVal);
            if (!newVal) {
                quantiteCigarettesField.clear();
            }
        });
        
        content.getChildren().addAll(label, typeComboBox, infoLabel, cigaretteCheckBox, quantiteCigarettesField);
        dialog.getDialogPane().setContent(content);
        
        // Convertir le résultat
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                String typeSelectionne = typeComboBox.getValue();
                if (typeSelectionne == null) {
                    return null;
                }
                
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                if ("Paquet".equals(typeSelectionne)) {
                    result.put("type", "paquet");
                    result.put("quantiteCigarettes", null);
                } else {
                    // Type "Cigarette"
                    result.put("type", "cigarette");
                    if (cigaretteCheckBox.isSelected() && !quantiteCigarettesField.getText().trim().isEmpty()) {
                        try {
                            int quantite = Integer.parseInt(quantiteCigarettesField.getText().trim());
                            if (quantite <= 0) {
                                afficherMessage("La quantité de cigarettes doit être supérieure à 0.", Alert.AlertType.WARNING);
                                return null;
                            }
                            result.put("quantiteCigarettes", quantite);
                        } catch (NumberFormatException e) {
                            afficherMessage("Veuillez entrer un nombre valide de cigarettes.", Alert.AlertType.WARNING);
                            return null;
                        }
                    } else {
                        // Si pas de quantité spécifiée, utiliser la quantité normale du produit
                        result.put("quantiteCigarettes", null);
                    }
                }
                return result;
            }
            return null;
        });
        
        java.util.Optional<java.util.Map<String, Object>> result = dialog.showAndWait();
        return result.orElse(null);
    }
    
    /**
     * ✨ Configure le scan de code-barres pour un champ de texte
     * Détecte automatiquement quand un code-barres est scanné (texte entré rapidement)
     */
    private void configurerScanCodeBarres(javafx.scene.control.TextField field) {
        final long[] lastKeyTime = {0};
        
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastKeyTime[0];
            
            // Si le texte change rapidement (moins de 50ms entre les caractères), c'est probablement un scan
            if (timeDiff < 50 && newVal.length() > oldVal.length()) {
                // C'est probablement un scan en cours
                lastKeyTime[0] = currentTime;
                
                // Si c'est un code-barres complet (au moins 8 chiffres)
                if (newVal.length() >= 8 && newVal.matches("\\d+")) {
                    // Attendre un peu pour voir si c'est vraiment un scan complet
                    javafx.application.Platform.runLater(() -> {
                        try {
                            Thread.sleep(150);
                            if (field.getText().equals(newVal) && newVal.length() >= 8) {
                                // C'est un code-barres scanné - rechercher automatiquement
                                Produit produit = produitDAO.rechercherProduit(newVal);
                                if (produit != null) {
                                    selectionnerProduit(produit);
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
                Produit produit = produitDAO.rechercherProduit(text);
                if (produit != null) {
                    selectionnerProduit(produit);
                }
            }
        });
    }
}


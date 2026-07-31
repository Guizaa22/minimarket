package controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.ProduitDAO;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.DetailVente;
import model.Produit;

/**
 * Contrôleur pour afficher les produits d'une catégorie
 */
public class CategorieProduitsController {
    private static final Logger LOG = LoggerFactory.getLogger(CategorieProduitsController.class);


    // ============================================
    // CONSTANTES
    // ============================================
    private static final int CARD_WIDTH = 240;
    private static final int CARD_HEIGHT = 300;
    private static final int ANIMATION_DURATION = 200;

    // ============================================
    // COMPOSANTS FXML
    // ============================================
    @FXML
    private Label categorieLabel;

    @FXML
    private FlowPane produitsContainer;

    @FXML
    private Button retourButton;

    @FXML
    private Button panierButton;

    @FXML
    private Label panierCountLabel;

    // ============================================
    // ATTRIBUTS
    // ============================================
    private ProduitDAO produitDAO;
    private String categorie;
    private static javafx.collections.ObservableList<DetailVente> panierGlobal;

    // ============================================
    // INITIALISATION
    // ============================================
    @FXML
    private void initialize() {
        produitDAO = new ProduitDAO();
        initialiserPanierGlobal();
        updatePanierCount();
        
        // Add global styles if not present
        javafx.application.Platform.runLater(() -> {
            if (produitsContainer.getScene() != null) {
                String globalCss = getClass().getResource("/styles/global.css").toExternalForm();
                String cardCss = getClass().getResource("/styles/product-card.css").toExternalForm();
                if (!produitsContainer.getScene().getStylesheets().contains(globalCss)) {
                    produitsContainer.getScene().getStylesheets().add(globalCss);
                }
                if (!produitsContainer.getScene().getStylesheets().contains(cardCss)) {
                    produitsContainer.getScene().getStylesheets().add(cardCss);
                }
            }
        });
    }

    /**
     * Initialise le panier global s'il n'existe pas
     */
    private void initialiserPanierGlobal() {
        if (panierGlobal == null) {
            panierGlobal = javafx.collections.FXCollections.observableArrayList();
        }
    }

    /**
     * Définit la catégorie et charge les produits
     */
    public void setCategorie(String categorie) {
        this.categorie = categorie;
        categorieLabel.setText(categorie);
        chargerProduits();
    }

    // ============================================
    // GESTION DES PRODUITS
    // ============================================

    /**
     * Charge et affiche tous les produits de la catégorie
     */
    private void chargerProduits() {
        try {
            // Configuration du conteneur
            produitsContainer.getChildren().clear();
            produitsContainer.setHgap(20);
            produitsContainer.setVgap(20);
            produitsContainer.setPadding(new Insets(25));
            produitsContainer.setAlignment(Pos.TOP_LEFT);

            LOG.info("========================================");
            LOG.info("Chargement produits pour catégorie: '" + categorie + "'");
            
            // Récupération des produits
            java.util.List<Produit> produits = produitDAO.findByCategorie(categorie);
            
            LOG.info("Nombre de produits trouvés: " + (produits != null ? produits.size() : 0));
            if (produits != null && !produits.isEmpty()) {
                LOG.info("Premiers produits:");
                for (int i = 0; i < Math.min(3, produits.size()); i++) {
                    Produit p = produits.get(i);
                    LOG.info("  - " + p.getNom() + " (ID: " + p.getId() + ", Catégorie: '" + p.getCategorie() + "', Stock: " + p.getQuantiteStock() + ")");
                }
            }
            LOG.info("========================================");

            // Vérifier si des produits existent
            if (produits == null || produits.isEmpty()) {
                LOG.error("AUCUN PRODUIT TROUVÉ pour catégorie: '" + categorie + "'");
                afficherMessageAucunProduit();
                return;
            }

            // Créer une carte pour chaque produit
            for (Produit produit : produits) {
                VBox card = createProductCard(produit);
                produitsContainer.getChildren().add(card);
            }

            LOG.info("✓ " + produits.size() + " produit(s) chargé(s) et affiché(s) pour la catégorie: " + categorie);

        } catch (Exception e) {
            LOG.error("========================================");
            LOG.error("ERREUR lors du chargement des produits:");
            LOG.error("Catégorie: '" + categorie + "'");
            LOG.error("Message: " + e.getMessage(), e);
            LOG.error("========================================");
            afficherErreurChargement();
        }
    }

    /**
     * Crée une carte produit complète avec tous les détails
     * Enhanced UI/UX with clear hierarchy: Name (big), Details (medium), Stock (small)
     */
    private VBox createProductCard(Produit produit) {
        VBox card = new VBox(0);
        card.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        card.getStyleClass().add("product-card");
        card.setAlignment(Pos.TOP_LEFT);

        // ============================================
        // HEADER: Category Badge (Small, Top Right)
        // ============================================
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.getStyleClass().add("product-card-header");
        headerBox.setPadding(new Insets(0, 0, 8, 0));
        
        Label catLabel = new Label(produit.getCategorie() != null && !produit.getCategorie().isEmpty() 
            ? produit.getCategorie() : "Non catégorisé");
        catLabel.getStyleClass().add("product-category");
        headerBox.getChildren().add(catLabel);

        // ============================================
        // CONTENT: Main Product Information
        // ============================================
        VBox contentBox = new VBox(10);
        contentBox.getStyleClass().add("product-card-content");
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.setPadding(new Insets(8, 0, 0, 0));

        // PRODUCT NAME - BIG & CLEAR (Primary Focus)
        Label nomLabel = new Label(produit.getNom());
        nomLabel.getStyleClass().add("product-name");
        nomLabel.setWrapText(true);
        nomLabel.setMaxWidth(Double.MAX_VALUE);
        nomLabel.setAlignment(Pos.TOP_LEFT);

        // PRICE - LARGE & PROMINENT
        HBox priceContainer = new HBox();
        priceContainer.getStyleClass().add("product-price-container");
        priceContainer.setAlignment(Pos.CENTER_LEFT);
        priceContainer.setPadding(new Insets(5, 0, 5, 0));
        
        Label prixLabel = new Label(String.format("%.2f DT", produit.getPrixVenteDefaut()));
        prixLabel.getStyleClass().add("product-price");
        priceContainer.getChildren().add(prixLabel);

        // DETAILS CONTAINER - Medium size, clear info
        VBox detailsContainer = new VBox(5);
        detailsContainer.getStyleClass().add("product-details-container");
        detailsContainer.setAlignment(Pos.TOP_LEFT);

        // Code barre
        if (produit.getCodeBarre() != null && !produit.getCodeBarre().isEmpty()) {
            Label codeLabel = new Label("📋 " + produit.getCodeBarre());
            codeLabel.getStyleClass().add("product-code");
            codeLabel.setAlignment(Pos.CENTER_LEFT);
            detailsContainer.getChildren().add(codeLabel);
        }

        // Unité
        if (produit.getUnite() != null && !produit.getUnite().isEmpty()) {
            Label uniteLabel = new Label("📦 " + produit.getUnite());
            uniteLabel.getStyleClass().add("product-unite");
            uniteLabel.setAlignment(Pos.CENTER_LEFT);
            detailsContainer.getChildren().add(uniteLabel);
        }

        contentBox.getChildren().addAll(nomLabel, priceContainer, detailsContainer);

        // Spacer to push footer to bottom
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // ============================================
        // FOOTER: Stock (Small) & Action Button
        // ============================================
        VBox footerBox = new VBox(10);
        footerBox.getStyleClass().add("product-card-footer");
        footerBox.setAlignment(Pos.BOTTOM_CENTER);
        footerBox.setPadding(new Insets(10, 0, 0, 0));

        // Stock Container - Small but visible
        HBox stockContainer = new HBox(6);
        stockContainer.getStyleClass().add("product-stock-container");
        stockContainer.setAlignment(Pos.CENTER);
        stockContainer.setStyle("-fx-padding: 5 0;");

        // Stock value badge
        String stockText = "📊 Stock: " + produit.getQuantiteStock();
        Label stockLabel = new Label(stockText);
        stockLabel.getStyleClass().add("product-stock");
        
        // Stock color class based on quantity
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

        // Action Button - Prominent and at bottom
        Button ajouterButton = new Button("✅ Ajouter");
        ajouterButton.getStyleClass().addAll("btn", "btn-primary");
        ajouterButton.setMaxWidth(Double.MAX_VALUE);
        ajouterButton.setMinHeight(40);
        ajouterButton.setAlignment(Pos.CENTER);
        ajouterButton.setOnAction(e -> ajouterAuPanier(produit));

        footerBox.getChildren().addAll(stockContainer, ajouterButton);

        // ============================================
        // ASSEMBLE CARD
        // ============================================
        card.getChildren().addAll(headerBox, contentBox, spacer, footerBox);

        // Effets hover sur la carte
        configurerEffetsHoverCarte(card);

        // ============================================
        // DOUBLE-CLICK to add to cart
        // ============================================
        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ajouterAuPanier(produit);
            }
        });

        return card;
    }

    /**
     * Configure les effets hover avec animation pour la carte
     */
    private void configurerEffetsHoverCarte(VBox card) {
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(ANIMATION_DURATION), card);
        scaleIn.setToX(1.03);
        scaleIn.setToY(1.03);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(ANIMATION_DURATION), card);
        scaleOut.setToX(1.0);
        scaleOut.setToY(1.0);

        card.setOnMouseEntered(e -> {
            scaleIn.playFromStart();
        });

        card.setOnMouseExited(e -> {
            scaleOut.playFromStart();
        });
    }

    /**
     * Affiche un message si aucun produit n'est disponible
     */
    private void afficherMessageAucunProduit() {
        VBox messageBox = creerBoiteMessage(
                "📭",
                "Aucun produit disponible",
                "Cette catégorie ne contient aucun produit en stock"
        );
        produitsContainer.getChildren().add(messageBox);
    }

    /**
     * Affiche un message d'erreur de chargement
     */
    private void afficherErreurChargement() {
        VBox errorBox = creerBoiteMessage(
                "⚠️",
                "Erreur de chargement",
                "Impossible de charger les produits. Vérifiez la connexion à la base de données."
        );

        Button retryButton = new Button("🔄 Réessayer");
        retryButton.getStyleClass().addAll("btn", "btn-primary");
        retryButton.setOnAction(e -> chargerProduits());
        errorBox.getChildren().add(retryButton);

        produitsContainer.getChildren().add(errorBox);
    }

    /**
     * Crée une boîte de message centrée
     */
    private VBox creerBoiteMessage(String icone, String titre, String description) {
        VBox box = new VBox(15);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-padding: 40px;");

        Label iconLabel = new Label(icone);
        iconLabel.setStyle("-fx-font-size: 64px;");

        Label titleLabel = new Label(titre);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -fx-text-secondary;");

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -fx-text-muted;");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(400);
        descLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        box.getChildren().addAll(iconLabel, titleLabel, descLabel);
        return box;
    }

    // ============================================
    // GESTION DU PANIER
    // ============================================

    /**
     * Ajoute un produit au panier ou incrémente la quantité
     */
    private void ajouterAuPanier(Produit produit) {
        // Vérifier si le produit est déjà dans le panier
        DetailVente detailExistant = rechercherProduitDansPanier(produit.getId());

        if (detailExistant != null) {
            incrementerQuantitePanier(detailExistant, produit);
        } else {
            ajouterNouveauProduitAuPanier(produit);
        }

        updatePanierCount();
    }

    /**
     * Recherche un produit dans le panier par son ID
     */
    private DetailVente rechercherProduitDansPanier(int produitId) {
        return panierGlobal.stream()
                .filter(d -> d.getProduitId() == produitId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Incrémente la quantité d'un produit existant dans le panier
     */
    private void incrementerQuantitePanier(DetailVente detail, Produit produit) {
        int nouvelleQuantite = detail.getQuantite() + 1;

        if (nouvelleQuantite > produit.getQuantiteStock()) {
            afficherAlerte(
                    Alert.AlertType.WARNING,
                    "Stock insuffisant",
                    "Stock disponible: " + produit.getQuantiteStock()
            );
            return;
        }

        detail.setQuantite(nouvelleQuantite);
    }

    /**
     * Ajoute un nouveau produit au panier
     */
    private void ajouterNouveauProduitAuPanier(Produit produit) {
        // Vérifier que les prix sont valides
        if (produit.getPrixVenteDefaut() == null) {
            LOG.error("ERREUR: Prix de vente manquant pour produit ID " + produit.getId());
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Erreur");
            alert.setHeaderText(null);
            alert.setContentText("Le produit \"" + produit.getNom() + "\" n'a pas de prix de vente défini.");
            alert.showAndWait();
            return;
        }
        
        DetailVente detail = new DetailVente();
        detail.setProduitId(produit.getId());
        detail.setQuantite(1);
        detail.setPrixVenteUnitaire(produit.getPrixVenteDefaut());
        detail.setPrixAchatUnitaire(produit.getPrixAchatActuel() != null ? produit.getPrixAchatActuel() : java.math.BigDecimal.ZERO);
        detail.setProduit(produit);
        panierGlobal.add(detail);
    }

    /**
     * Met à jour le compteur d'articles dans le panier
     */
    private void updatePanierCount() {
        int count = panierGlobal != null ? panierGlobal.size() : 0;
        if (panierCountLabel != null) {
            panierCountLabel.setText("Panier: " + count + " article(s)");
        }
    }

    /**
     * Retourne le panier global
     */
    public static javafx.collections.ObservableList<DetailVente> getPanierGlobal() {
        if (panierGlobal == null) {
            panierGlobal = javafx.collections.FXCollections.observableArrayList();
        }
        return panierGlobal;
    }

    // ============================================
    // NAVIGATION
    // ============================================

    @FXML
    private void handleRetour() {
        try {
            Stage stage = (Stage) retourButton.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/CaisseCategories.fxml", "Catégories");
        } catch (Exception e) {
            LOG.error("Erreur lors du retour: " + e.getMessage(), e);
            afficherAlerte(
                    Alert.AlertType.ERROR,
                    "Erreur",
                    "Erreur lors du retour: " + e.getMessage()
            );
        }
    }

    @FXML
    private void handleVoirPanier() {
        try {
            Stage stage = (Stage) panierButton.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/Caisse.fxml", "Caisse - Point de Vente");
        } catch (Exception e) {
            afficherAlerte(
                    Alert.AlertType.ERROR,
                    "Erreur",
                    "Erreur lors de l'ouverture du panier: " + e.getMessage()
            );
        }
    }

    // ============================================
    // UTILITAIRES
    // ============================================

    /**
     * Affiche une alerte
     */
    private void afficherAlerte(Alert.AlertType type, String titre, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
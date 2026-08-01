package controller;

import java.util.List;

import dao.ProduitDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Produit;

/**
 * Contrôleur pour la visualisation des produits avec filtrage et recherche
 * Interface améliorée avec effets 3D et hover
 */
public class VisualisationProduitsController {
    
    @FXML
    private FlowPane produitsContainer;
    
    @FXML
    private TextField rechercheField;
    
    @FXML
    private ComboBox<String> categorieComboBox;
    
    @FXML
    private TextField codeBarreRechercheField;
    
    @FXML
    private Label totalProduitsLabel;
    
    private ProduitDAO produitDAO;
    private ObservableList<Produit> tousProduits;
    private FilteredList<Produit> produitsFiltres;
    
    @FXML
    private void initialize() {
        produitDAO = new ProduitDAO();
        tousProduits = FXCollections.observableArrayList();
        produitsFiltres = new FilteredList<>(tousProduits, p -> true);
        
        // Configuration du conteneur
        produitsContainer.setHgap(20);
        produitsContainer.setVgap(20);
        produitsContainer.setPadding(new Insets(20));
        
        // Charger les catégories
        chargerCategories();
        
        // Charger tous les produits
        chargerProduits();
        
        // Écouter les changements de recherche
        rechercheField.textProperty().addListener((obs, oldVal, newVal) -> filtrerProduits());
        
        // ✨ SCAN CODE-BARRES: Détection automatique du scan
        configurerScanCodeBarres(rechercheField);
        
        // Écouter les changements de catégorie
        categorieComboBox.valueProperty().addListener((obs, oldVal, newVal) -> filtrerProduits());
        
        // Recherche par code-barres avec auto-ajout
        codeBarreRechercheField.setOnAction(e -> rechercherParCodeBarre());
        
        // ✨ SCAN CODE-BARRES: Détection automatique du scan pour le champ code-barres
        configurerScanCodeBarres(codeBarreRechercheField);
    }
    
    /**
     * Charge toutes les catégories disponibles
     */
    private void chargerCategories() {
        List<String> categories = produitDAO.findAllCategories();
        categorieComboBox.getItems().clear();
        categorieComboBox.getItems().add("Toutes les catégories");
        categorieComboBox.getItems().addAll(categories);
        categorieComboBox.setValue("Toutes les catégories");
    }
    
    /**
     * Charge tous les produits depuis la base de données
     */
    private void chargerProduits() {
        tousProduits.clear();
        List<Produit> produits = produitDAO.findAll();
        tousProduits.addAll(produits);
        afficherProduits();
        mettreAJourTotal();
    }
    
    /**
     * Affiche les produits filtrés dans le conteneur
     */
    private void afficherProduits() {
        produitsContainer.getChildren().clear();
        
        for (Produit produit : produitsFiltres) {
            VBox card = creerCarteProduit(produit);
            produitsContainer.getChildren().add(card);
        }
    }
    
    /**
     * Crée une carte produit avec effets 3D et hover - IMPROVED LAYOUT
     */
    private VBox creerCarteProduit(Produit produit) {
        VBox card = new VBox(12);
        card.getStyleClass().add("product-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(18));

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

        // PRICE BOX - LARGE & PROMINENT with both prices
        VBox priceBox = new VBox(5);
        priceBox.setAlignment(Pos.CENTER_LEFT);
        priceBox.setPadding(new Insets(8, 0, 8, 0));
        priceBox.setStyle("-fx-background-radius: 8; -fx-padding: 10;");
        
        HBox prixVenteBox = new HBox(8);
        prixVenteBox.setAlignment(Pos.CENTER_LEFT);
        Label prixVenteIcon = new Label("💰");
        prixVenteIcon.setStyle("-fx-font-size: 18px;");
        Label prixVenteLabel = new Label(String.format("Prix Vente: %.2f DT", produit.getPrixVenteDefaut()));
        prixVenteLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        prixVenteBox.getChildren().addAll(prixVenteIcon, prixVenteLabel);
        
        HBox prixAchatBox = new HBox(8);
        prixAchatBox.setAlignment(Pos.CENTER_LEFT);
        Label prixAchatIcon = new Label("🏷️");
        prixAchatIcon.setStyle("-fx-font-size: 14px;");
        Label prixAchatLabel = new Label(String.format("Prix Achat: %.2f DT", produit.getPrixAchatActuel()));
        prixAchatLabel.setStyle("-fx-font-size: 14px;");
        prixAchatBox.getChildren().addAll(prixAchatIcon, prixAchatLabel);
        
        priceBox.getChildren().addAll(prixVenteBox, prixAchatBox);

        // DETAILS CONTAINER - Clear and comprehensive info
        VBox detailsContainer = new VBox(8);
        detailsContainer.setAlignment(Pos.TOP_LEFT);
        detailsContainer.setPadding(new Insets(8, 0, 8, 0));

        // Code barre
        if (produit.getCodeBarre() != null && !produit.getCodeBarre().isEmpty()) {
            HBox codeBox = new HBox(8);
            codeBox.setAlignment(Pos.CENTER_LEFT);
            Label codeIcon = new Label("📋");
            codeIcon.setStyle("-fx-font-size: 16px;");
            Label codeLabel = new Label("Code: " + produit.getCodeBarre());
            codeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            codeBox.getChildren().addAll(codeIcon, codeLabel);
            detailsContainer.getChildren().add(codeBox);
        }

        // Unité
        if (produit.getUnite() != null && !produit.getUnite().isEmpty()) {
            HBox uniteBox = new HBox(8);
            uniteBox.setAlignment(Pos.CENTER_LEFT);
            Label uniteIcon = new Label("📦");
            uniteIcon.setStyle("-fx-font-size: 16px;");
            Label uniteLabel = new Label("Unité: " + produit.getUnite());
            uniteLabel.setStyle("-fx-font-size: 14px;");
            uniteBox.getChildren().addAll(uniteIcon, uniteLabel);
            detailsContainer.getChildren().add(uniteBox);
        }
        
        // Seuil d'alerte
        HBox seuilBox = new HBox(8);
        seuilBox.setAlignment(Pos.CENTER_LEFT);
        Label seuilIcon = new Label("⚠️");
        seuilIcon.setStyle("-fx-font-size: 16px;");
        Label seuilLabel = new Label("Seuil alerte: " + produit.getSeuilAlerte());
        seuilLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        seuilBox.getChildren().addAll(seuilIcon, seuilLabel);
        detailsContainer.getChildren().add(seuilBox);

        // Stock Container - Inside the card, integrated with details
        VBox stockContainerBox = new VBox(5);
        stockContainerBox.setAlignment(Pos.CENTER);
        stockContainerBox.setPadding(new Insets(10));
        
        // Determine stock color and message
        String stockColor;
        String stockIcon;
        String stockMessage;
        
        if (produit.getQuantiteStock() == 0) {
            stockColor = "";
            stockIcon = "❌";
            stockMessage = "RUPTURE";
        } else if (produit.isStockFaible() || produit.getQuantiteStock() <= produit.getSeuilAlerte()) {
            stockColor = "";
            stockIcon = "⚠️";
            stockMessage = "FAIBLE";
        } else if (produit.getQuantiteStock() > 50) {
            stockColor = "";
            stockIcon = "✅";
            stockMessage = "BON";
        } else {
            stockColor = "";
            stockIcon = "📦";
            stockMessage = "MOYEN";
        }
        
        stockContainerBox.setStyle(stockColor + "-fx-background-radius: 8;");
        
        HBox stockQtyBox = new HBox(8);
        stockQtyBox.setAlignment(Pos.CENTER);
        Label stockIconLabel = new Label(stockIcon);
        stockIconLabel.setStyle("-fx-font-size: 20px;");
        Label stockQtyLabel = new Label("Stock: " + produit.getQuantiteStock());
        stockQtyLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        stockQtyBox.getChildren().addAll(stockIconLabel, stockQtyLabel);
        Label stockStatusLabel = new Label(stockMessage);
        stockStatusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        stockContainerBox.getChildren().addAll(stockQtyBox, stockStatusLabel);

        // Add Stock button - Inside the card
        Button actionButton = new Button("➕ Ajouter au Stock");
        actionButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10; -fx-cursor: hand;");
        actionButton.setOnMouseEntered(e -> actionButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10; -fx-cursor: hand;"));
        actionButton.setOnMouseExited(e -> actionButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10; -fx-cursor: hand;"));
        actionButton.setOnAction(e -> ouvrirAjoutStock(produit));
        actionButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(actionButton, Priority.ALWAYS);

        // Add stock info and button to details container
        detailsContainer.getChildren().addAll(stockContainerBox, actionButton);

        contentBox.getChildren().addAll(nomLabel, priceBox, detailsContainer);

        // ============================================
        // ASSEMBLE CARD
        // ============================================
        card.getChildren().addAll(
            headerBox,
            contentBox
        );

        // ✨ DOUBLE-CLICK: Afficher popup avec tous les détails
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                afficherPopupDetailsProduit(produit);
            }
        });

        return card;
    }
    
    /**
     * Filtre les produits selon la recherche et la catégorie
     */
    private void filtrerProduits() {
        String recherche = rechercheField.getText().toLowerCase();
        String categorie = categorieComboBox.getValue();
        
        produitsFiltres.setPredicate(produit -> {
            boolean matchRecherche = recherche.isEmpty() || 
                produit.getNom().toLowerCase().contains(recherche) ||
                produit.getCodeBarre().toLowerCase().contains(recherche);
            
            boolean matchCategorie = categorie == null || 
                categorie.equals("Toutes les catégories") ||
                (produit.getCategorie() != null && produit.getCategorie().equals(categorie));
            
            return matchRecherche && matchCategorie;
        });
        
        afficherProduits();
        mettreAJourTotal();
    }
    
    /**
     * Recherche un produit par code-barres et l'ajoute automatiquement s'il n'existe pas
     */
    @FXML
    private void rechercherParCodeBarre() {
        String codeBarre = codeBarreRechercheField.getText().trim();
        
        if (codeBarre.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Code-barres vide", 
                     "Veuillez entrer un code-barres.");
            return;
        }
        
        // Rechercher le produit
        Produit produit = produitDAO.findByCodeBarre(codeBarre);
        
        if (produit != null) {
            // Produit trouvé - ouvrir l'ajout de stock
            ouvrirAjoutStock(produit);
            codeBarreRechercheField.clear();
        } else {
            // Produit non trouvé - proposer de le créer
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Produit introuvable");
            confirmAlert.setHeaderText(null);
            confirmAlert.setContentText("Aucun produit trouvé avec le code-barres: " + codeBarre + 
                                       "\n\nVoulez-vous créer un nouveau produit avec ce code-barres ?");
            
            if (confirmAlert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK) {
                ouvrirCreationProduit(codeBarre);
            }
        }
    }
    
    /**
     * Ouvre une fenêtre pour ajouter du stock à un produit existant
     */
    private void ouvrirAjoutStock(Produit produit) {
        // Créer une boîte de dialogue pour ajouter du stock
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("0");
        dialog.setTitle("Ajouter au Stock");
        dialog.setHeaderText("Produit: " + produit.getNom());
        dialog.setContentText("Quantité à ajouter (" + produit.getUnite() + "):");
        
        dialog.showAndWait().ifPresent(quantiteStr -> {
            try {
                int quantite = Integer.parseInt(quantiteStr);
                if (quantite > 0) {
                    produit.setQuantiteStock(produit.getQuantiteStock() + quantite);
                    if (produitDAO.update(produit)) {
                        showAlert(Alert.AlertType.INFORMATION, "Succès", 
                                 quantite + " " + produit.getUnite() + " ajouté(s) au stock.");
                        chargerProduits();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erreur", 
                                 "Erreur lors de la mise à jour du stock.");
                    }
                } else {
                    showAlert(Alert.AlertType.WARNING, "Quantité invalide", 
                             "La quantité doit être supérieure à 0.");
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Erreur", 
                         "Veuillez entrer un nombre valide.");
            }
        });
    }
    
    /**
     * Ouvre une fenêtre pour créer un nouveau produit
     */
    private void ouvrirCreationProduit(String codeBarre) {
        // Rediriger vers la gestion de stock avec le code-barres pré-rempli
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) produitsContainer.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/GestionStock.fxml", "Gestion de Stock");
            // Note: Pour pré-remplir le champ, il faudrait passer des paramètres ou utiliser une variable statique
        } catch (java.io.IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors de l'ouverture de la gestion de stock: " + e.getMessage());
        }
    }
    
    /**
     * Met à jour le label du total de produits
     */
    private void mettreAJourTotal() {
        int total = produitsFiltres.size();
        totalProduitsLabel.setText("Total: " + total + " produit(s)");
    }
    
    @FXML
    private void handleRetour() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) produitsContainer.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur");
        } catch (java.io.IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors du retour: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleRafraichir() {
        chargerCategories();
        chargerProduits();
    }
    
    /**
     * ✨ Configure le scan de code-barres pour un champ de texte
     * Détecte automatiquement quand un code-barres est scanné (texte entré rapidement)
     */
    private void configurerScanCodeBarres(TextField field) {
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
                                if (field == codeBarreRechercheField) {
                                    rechercherParCodeBarre();
                                } else {
                                    // Rechercher dans la liste
                                    rechercheField.setText(newVal);
                                    filtrerProduits();
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
                if (field == codeBarreRechercheField) {
                    rechercherParCodeBarre();
                } else {
                    rechercheField.setText(text);
                    filtrerProduits();
                }
            }
        });
    }
    
    /**
     * ✨ Affiche une popup avec tous les détails du produit et possibilité d'ajout de stock
     */
    private void afficherPopupDetailsProduit(Produit produit) {
        // Créer une nouvelle fenêtre de dialogue
        javafx.stage.Stage dialogStage = new javafx.stage.Stage();
        dialogStage.setTitle("📦 Détails du Produit");
        dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialogStage.initOwner(produitsContainer.getScene().getWindow());
        
        // Conteneur principal
        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-radius: 15;");
        root.setPrefWidth(600);
        root.setMaxWidth(600);
        
        // ============================================
        // HEADER: Titre et catégorie
        // ============================================
        VBox headerBox = new VBox(8);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titreLabel = new Label("📦 " + produit.getNom());
        titreLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        
        Label categorieLabel = new Label("Catégorie: " + (produit.getCategorie() != null && !produit.getCategorie().isEmpty() 
            ? produit.getCategorie() : "Non catégorisé"));
        categorieLabel.setStyle("-fx-font-size: 14px;");
        
        headerBox.getChildren().addAll(titreLabel, categorieLabel);
        
        // Séparateur
        javafx.scene.control.Separator separator1 = new javafx.scene.control.Separator();
        
        // ============================================
        // INFORMATIONS PRINCIPALES
        // ============================================
        VBox infoBox = new VBox(15);
        infoBox.setPadding(new Insets(10, 0, 10, 0));
        
        // Code-barres
        HBox codeBox = new HBox(10);
        codeBox.setAlignment(Pos.CENTER_LEFT);
        Label codeIcon = new Label("📋");
        codeIcon.setStyle("-fx-font-size: 20px;");
        Label codeLabel = new Label("Code-barres: " + (produit.getCodeBarre() != null && !produit.getCodeBarre().isEmpty() 
            ? produit.getCodeBarre() : "N/A"));
        codeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        codeBox.getChildren().addAll(codeIcon, codeLabel);
        
        // Unité
        HBox uniteBox = new HBox(10);
        uniteBox.setAlignment(Pos.CENTER_LEFT);
        Label uniteIcon = new Label("📦");
        uniteIcon.setStyle("-fx-font-size: 20px;");
        Label uniteLabel = new Label("Unité: " + (produit.getUnite() != null && !produit.getUnite().isEmpty() 
            ? produit.getUnite() : "unité"));
        uniteLabel.setStyle("-fx-font-size: 16px;");
        uniteBox.getChildren().addAll(uniteIcon, uniteLabel);
        
        // Prix d'achat
        HBox prixAchatBox = new HBox(10);
        prixAchatBox.setAlignment(Pos.CENTER_LEFT);
        Label prixAchatIcon = new Label("🏷️");
        prixAchatIcon.setStyle("-fx-font-size: 20px;");
        Label prixAchatLabel = new Label("Prix d'achat: " + String.format("%.2f DT", produit.getPrixAchatActuel()));
        prixAchatLabel.setStyle("-fx-font-size: 16px;");
        prixAchatBox.getChildren().addAll(prixAchatIcon, prixAchatLabel);
        
        // Prix de vente
        HBox prixVenteBox = new HBox(10);
        prixVenteBox.setAlignment(Pos.CENTER_LEFT);
        Label prixVenteIcon = new Label("💰");
        prixVenteIcon.setStyle("-fx-font-size: 20px;");
        Label prixVenteLabel = new Label("Prix de vente: " + String.format("%.2f DT", produit.getPrixVenteDefaut()));
        prixVenteLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        prixVenteBox.getChildren().addAll(prixVenteIcon, prixVenteLabel);
        
        // Seuil d'alerte
        HBox seuilBox = new HBox(10);
        seuilBox.setAlignment(Pos.CENTER_LEFT);
        Label seuilIcon = new Label("⚠️");
        seuilIcon.setStyle("-fx-font-size: 20px;");
        Label seuilLabel = new Label("Seuil d'alerte: " + produit.getSeuilAlerte());
        seuilLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        seuilBox.getChildren().addAll(seuilIcon, seuilLabel);
        
        infoBox.getChildren().addAll(codeBox, uniteBox, prixAchatBox, prixVenteBox, seuilBox);
        
        // Séparateur
        javafx.scene.control.Separator separator2 = new javafx.scene.control.Separator();
        
        // ============================================
        // STOCK INFORMATION
        // ============================================
        VBox stockBox = new VBox(10);
        stockBox.setAlignment(Pos.CENTER);
        stockBox.setPadding(new Insets(15));
        
        // Déterminer le statut du stock
        String stockColor;
        String stockIcon;
        String stockMessage;
        
        if (produit.getQuantiteStock() == 0) {
            stockColor = "";
            stockIcon = "❌";
            stockMessage = "RUPTURE DE STOCK";
        } else if (produit.isStockFaible() || produit.getQuantiteStock() <= produit.getSeuilAlerte()) {
            stockColor = "";
            stockIcon = "⚠️";
            stockMessage = "STOCK FAIBLE";
        } else if (produit.getQuantiteStock() > 50) {
            stockColor = "";
            stockIcon = "✅";
            stockMessage = "STOCK BON";
        } else {
            stockColor = "";
            stockIcon = "📦";
            stockMessage = "STOCK MOYEN";
        }
        
        stockBox.setStyle(stockColor + "-fx-background-radius: 10;");
        
        HBox stockQtyBox = new HBox(10);
        stockQtyBox.setAlignment(Pos.CENTER);
        Label stockIconLabel = new Label(stockIcon);
        stockIconLabel.setStyle("-fx-font-size: 32px;");
        Label stockQtyLabel = new Label("Stock actuel: " + produit.getQuantiteStock() + " " + 
            (produit.getUnite() != null && !produit.getUnite().isEmpty() ? produit.getUnite() : "unité(s)"));
        stockQtyLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        stockQtyBox.getChildren().addAll(stockIconLabel, stockQtyLabel);
        
        Label stockStatusLabel = new Label(stockMessage);
        stockStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        stockBox.getChildren().addAll(stockQtyBox, stockStatusLabel);
        
        // Séparateur
        javafx.scene.control.Separator separator3 = new javafx.scene.control.Separator();
        
        // ============================================
        // BOUTONS D'ACTION
        // ============================================
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        // Bouton Ajouter au Stock
        Button ajouterStockButton = new Button("➕ Ajouter au Stock");
        ajouterStockButton.setStyle("-fx-font-size: 16px;" +
            "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 12 24; -fx-cursor: hand;");
        ajouterStockButton.setOnMouseEntered(e -> ajouterStockButton.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 12 24; -fx-cursor: hand;"));
        ajouterStockButton.setOnMouseExited(e -> ajouterStockButton.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 12 24; -fx-cursor: hand;"));
        ajouterStockButton.setOnAction(e -> {
            dialogStage.close();
            ouvrirAjoutStock(produit);
        });
        
        // Bouton Fermer
        Button fermerButton = new Button("✕ Fermer");
        fermerButton.setStyle("-fx-font-size: 16px;" +
            "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 12 24; -fx-cursor: hand;");
        fermerButton.setOnMouseEntered(e -> fermerButton.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 12 24; -fx-cursor: hand;"));
        fermerButton.setOnMouseExited(e -> fermerButton.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 12 24; -fx-cursor: hand;"));
        fermerButton.setOnAction(e -> dialogStage.close());
        
        buttonBox.getChildren().addAll(ajouterStockButton, fermerButton);
        
        // Assembler tous les éléments
        root.getChildren().addAll(headerBox, separator1, infoBox, separator2, stockBox, separator3, buttonBox);
        
        // Créer la scène et afficher
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        dialogStage.setScene(scene);
        dialogStage.setResizable(false);
        
        // Centrer la fenêtre
        dialogStage.setOnShown(e -> {
            javafx.stage.Window owner = dialogStage.getOwner();
            if (owner != null) {
                dialogStage.setX(owner.getX() + (owner.getWidth() - dialogStage.getWidth()) / 2);
                dialogStage.setY(owner.getY() + (owner.getHeight() - dialogStage.getHeight()) / 2);
            }
        });
        
        dialogStage.show();
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


package controller;

import dao.ProduitDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import model.Produit;

/**
 * Contrôleur pour l'ajout rapide de stock via smartphone
 * Interface optimisée pour mobile avec scanner de code-barres
 */
public class AjoutStockMobileController {
    
    @FXML
    private TextField codeBarreField;
    
    @FXML
    private Label produitNomLabel;
    
    @FXML
    private Label produitInfoLabel;
    
    @FXML
    private TextField quantiteField;
    
    @FXML
    private Button ajouterButton;
    
    @FXML
    private Button plusButton;
    
    @FXML
    private Button moinsButton;
    
    @FXML
    private VBox historiqueContainer;
    
    private ProduitDAO produitDAO;
    private Produit produitActuel;
    private ObservableList<String> historique;
    
    @FXML
    private void initialize() {
        produitDAO = new ProduitDAO();
        historique = FXCollections.observableArrayList();
        
        // Focus automatique sur le champ code-barres
        codeBarreField.requestFocus();
        
        // Configuration du champ code-barres pour scanner automatique
        codeBarreField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                rechercherProduit();
            }
        });
        
        // Auto-recherche après saisie (pour scanner)
        codeBarreField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() >= 8) {
                // Délai pour laisser le scanner terminer
                javafx.application.Platform.runLater(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    rechercherProduit();
                });
            }
        });
        
        // Configuration des boutons +/- pour tactile
        configurerBoutonsTactiles();
        
        // Masquer les informations produit au début
        masquerInfosProduit();
    }
    
    /**
     * Configure les boutons +/- pour interface tactile
     */
    private void configurerBoutonsTactiles() {
        plusButton.setPrefSize(60, 60);
        moinsButton.setPrefSize(60, 60);
        plusButton.setStyle(
            "-fx-background-color: #4CAF50; " +
            "-fx-text-fill: white; " +
            "-fx-font-weight: bold; " +
            "-fx-font-size: 24px; " +
            "-fx-background-radius: 30; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 3);"
        );
        moinsButton.setStyle(
            "-fx-background-color: #f44336; " +
            "-fx-text-fill: white; " +
            "-fx-font-weight: bold; " +
            "-fx-font-size: 24px; " +
            "-fx-background-radius: 30; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 3);"
        );
        
        plusButton.setOnMouseEntered(e -> {
            plusButton.setStyle(
                "-fx-background-color: #66BB6A; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 24px; " +
                "-fx-background-radius: 30; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 4); " +
                "-fx-scale-x: 1.1; " +
                "-fx-scale-y: 1.1;"
            );
        });
        
        plusButton.setOnMouseExited(e -> {
            plusButton.setStyle(
                "-fx-background-color: #4CAF50; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 24px; " +
                "-fx-background-radius: 30; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 3); " +
                "-fx-scale-x: 1.0; " +
                "-fx-scale-y: 1.0;"
            );
        });
        
        moinsButton.setOnMouseEntered(e -> {
            moinsButton.setStyle(
                "-fx-background-color: #d32f2f; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 24px; " +
                "-fx-background-radius: 30; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 4); " +
                "-fx-scale-x: 1.1; " +
                "-fx-scale-y: 1.1;"
            );
        });
        
        moinsButton.setOnMouseExited(e -> {
            moinsButton.setStyle(
                "-fx-background-color: #f44336; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 24px; " +
                "-fx-background-radius: 30; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 3); " +
                "-fx-scale-x: 1.0; " +
                "-fx-scale-y: 1.0;"
            );
        });
    }
    
    /**
     * Recherche un produit par code-barres
     */
    @FXML
    private void rechercherProduit() {
        String codeBarre = codeBarreField.getText().trim();
        
        if (codeBarre.isEmpty()) {
            masquerInfosProduit();
            return;
        }
        
        produitActuel = produitDAO.findByCodeBarre(codeBarre);
        
        if (produitActuel != null) {
            afficherInfosProduit(produitActuel);
            quantiteField.setText("1");
            quantiteField.requestFocus();
        } else {
            masquerInfosProduit();
            produitNomLabel.setText("❌ Produit introuvable");
            produitNomLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #f44336;");
            produitInfoLabel.setText("Code-barres: " + codeBarre);
        }
    }
    
    /**
     * Affiche les informations du produit trouvé
     */
    private void afficherInfosProduit(Produit produit) {
        produitNomLabel.setText("✅ " + produit.getNom());
        produitNomLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        
        String info = String.format(
            "📋 Code: %s | 📂 %s | 📦 Stock: %d %s | ⚠️ Seuil: %d",
            produit.getCodeBarre(),
            produit.getCategorie() != null ? produit.getCategorie() : "Non catégorisé",
            produit.getQuantiteStock(),
            produit.getUnite(),
            produit.getSeuilAlerte()
        );
        produitInfoLabel.setText(info);
        produitInfoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        
        // Colorer selon le stock
        if (produit.getQuantiteStock() <= produit.getSeuilAlerte()) {
            produitInfoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #f44336; -fx-font-weight: bold;");
        }
    }
    
    /**
     * Masque les informations produit
     */
    private void masquerInfosProduit() {
        produitNomLabel.setText("🔍 Scannez ou entrez un code-barres");
        produitNomLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #999;");
        produitInfoLabel.setText("");
        produitActuel = null;
    }
    
    /**
     * Augmente la quantité
     */
    @FXML
    private void handlePlus() {
        try {
            int quantite = Integer.parseInt(quantiteField.getText());
            quantite++;
            quantiteField.setText(String.valueOf(quantite));
        } catch (NumberFormatException e) {
            quantiteField.setText("1");
        }
    }
    
    /**
     * Diminue la quantité
     */
    @FXML
    private void handleMoins() {
        try {
            int quantite = Integer.parseInt(quantiteField.getText());
            if (quantite > 1) {
                quantite--;
                quantiteField.setText(String.valueOf(quantite));
            }
        } catch (NumberFormatException e) {
            quantiteField.setText("1");
        }
    }
    
    /**
     * Ajoute le stock au produit
     */
    @FXML
    private void handleAjouter() {
        if (produitActuel == null) {
            showAlert(Alert.AlertType.WARNING, "Aucun produit", 
                     "Veuillez d'abord scanner ou rechercher un produit.");
            return;
        }
        
        try {
            int quantite = Integer.parseInt(quantiteField.getText().trim());
            
            if (quantite <= 0) {
                showAlert(Alert.AlertType.WARNING, "Quantité invalide", 
                         "La quantité doit être supérieure à 0.");
                return;
            }
            
            // Mettre à jour le stock
            produitActuel.setQuantiteStock(produitActuel.getQuantiteStock() + quantite);
            
            if (produitDAO.update(produitActuel)) {
                // Ajouter à l'historique
                String historiqueItem = String.format(
                    "✅ %s: +%d %s (Stock: %d %s)",
                    produitActuel.getNom(),
                    quantite,
                    produitActuel.getUnite(),
                    produitActuel.getQuantiteStock(),
                    produitActuel.getUnite()
                );
                historique.add(0, historiqueItem);
                afficherHistorique();
                
                // Réinitialiser pour le prochain scan
                codeBarreField.clear();
                quantiteField.setText("1");
                masquerInfosProduit();
                codeBarreField.requestFocus();
                
                // Feedback visuel
                afficherInfosProduit(produitActuel);
            } else {
                showAlert(Alert.AlertType.ERROR, "Erreur", 
                         "Erreur lors de la mise à jour du stock.");
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Veuillez entrer un nombre valide.");
        }
    }
    
    /**
     * Affiche l'historique des ajouts
     */
    private void afficherHistorique() {
        historiqueContainer.getChildren().clear();
        
        int maxItems = Math.min(historique.size(), 10);
        for (int i = 0; i < maxItems; i++) {
            Label item = new Label(historique.get(i));
            item.setStyle(
                "-fx-font-size: 14px; " +
                "-fx-padding: 8; " +
                "-fx-background-color: #e8f5e9; " +
                "-fx-background-radius: 5; " +
                "-fx-border-color: #c8e6c9; " +
                "-fx-border-radius: 5;"
            );
            item.setMaxWidth(Double.MAX_VALUE);
            historiqueContainer.getChildren().add(item);
        }
    }
    
    @FXML
    private void handleRetour() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) codeBarreField.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors du retour: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleEffacerHistorique() {
        historique.clear();
        historiqueContainer.getChildren().clear();
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


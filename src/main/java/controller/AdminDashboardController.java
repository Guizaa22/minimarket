package controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import dao.CreditFournisseurDAO;
import dao.FournisseurDAO;
import dao.NoteJourDAO;
import dao.PaiementFournisseurDAO;
import dao.ProduitDAO;
import dao.VenteDAO;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import model.Produit;
import model.Utilisateur;
import util.FXMLUtils;

/**
 * Contrôleur pour le dashboard administrateur
 */
public class AdminDashboardController {
    private static final Logger LOG = LoggerFactory.getLogger(AdminDashboardController.class);

    
    @FXML
    private Label welcomeLabel;

    @FXML
    private Button gestionVentesButton;


    @FXML
    private Button gestionStockButton;
    
    @FXML
    private Button gestionUtilisateursButton;
    
    @FXML
    private Button gestionTabacButton;
    
    @FXML
    private Button visualisationProduitsButton;
    
    @FXML
    private Button deconnexionButton;

    @FXML
    private Button auditButton;
    
    @FXML
    private Label totalProduitsStatLabel;
    
    @FXML
    private Label rupturesStatLabel;
    
    @FXML
    private Label ventesJourStatLabel;
    
    @FXML
    private Label totalFournisseursLabel;
    
    @FXML
    private Label totalCreditsLabel;
    
    @FXML
    private Label paiementsJourLabel;
    
    @FXML
    private Label notesJourLabel;
    
    private Utilisateur utilisateur;
    private ProduitDAO produitDAO;
    private VenteDAO venteDAO;
    private FournisseurDAO fournisseurDAO;
    private CreditFournisseurDAO creditFournisseurDAO;
    private PaiementFournisseurDAO paiementFournisseurDAO;
    private NoteJourDAO noteJourDAO;
    
    /**
     * Initialise le contrôleur après le chargement du FXML.
     * Cette méthode est automatiquement appelée par JavaFX lors du chargement de la vue.
     */
    @FXML
    @SuppressWarnings("unused")
    private void initialize() {
        utilisateur = ConnexionController.getUtilisateurConnecte();
        if (utilisateur != null) {
            welcomeLabel.setText("Bienvenue, " + utilisateur.getUsername() + " (Admin)");
        }
        produitDAO = new ProduitDAO();
        venteDAO = new VenteDAO();
        fournisseurDAO = new FournisseurDAO();
        creditFournisseurDAO = new CreditFournisseurDAO();
        paiementFournisseurDAO = new PaiementFournisseurDAO();
        noteJourDAO = new NoteJourDAO();
        rafraichirStatistiques();
        rendreCartesCliquables();
        
        // 🔒 BLOQUER LE CODE-BARRES DANS LE DASHBOARD ADMIN
        // Empêcher les scans de code-barres de causer des problèmes dans le dashboard
        bloquerScanCodeBarres();
    }
    
    /**
     * 🔒 Bloque la détection automatique des codes-barres dans le dashboard admin
     * pour éviter les crashes et redirections non désirées vers le login
     */
    private void bloquerScanCodeBarres() {
        // Attendre que la scène soit disponible
        javafx.application.Platform.runLater(() -> {
            if (welcomeLabel != null && welcomeLabel.getScene() != null) {
                javafx.scene.Scene scene = welcomeLabel.getScene();
                
                // Buffer pour détecter les scans de code-barres (texte entré rapidement)
                final StringBuilder[] scanBuffer = {new StringBuilder()};
                final long[] lastKeyTime = {0};
                
                // Filtrer les événements clavier pour détecter et bloquer les codes-barres scannés
                scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, event -> {
                    javafx.scene.Node focusedNode = scene.getFocusOwner();
                    
                    // Si un champ de texte a le focus, laisser passer (pour GestionStock, etc.)
                    if (focusedNode instanceof javafx.scene.control.TextInputControl) {
                        return; // Ne pas bloquer, laisser le contrôleur gérer
                    }
                    
                    // Sinon, détecter les scans de code-barres
                    String character = event.getCharacter();
                    if (!character.isEmpty() && Character.isDigit(character.charAt(0))) {
                        long currentTime = System.currentTimeMillis();
                        long timeDiff = currentTime - lastKeyTime[0];
                        
                        // Si texte entré rapidement (moins de 50ms), c'est probablement un scan
                        if (timeDiff < 50) {
                            scanBuffer[0].append(character);
                            lastKeyTime[0] = currentTime;
                            
                            // Si c'est un code-barres complet (au moins 8 chiffres), le bloquer
                            if (scanBuffer[0].length() >= 8) {
                                event.consume();
                                scanBuffer[0].setLength(0); // Reset buffer
                                LOG.info("⚠️ Scan de code-barres bloqué dans le dashboard admin");
                                return;
                            }
                        } else {
                            // Reset si trop de temps entre les caractères
                            scanBuffer[0].setLength(0);
                            scanBuffer[0].append(character);
                            lastKeyTime[0] = currentTime;
                        }
                        
                        // Bloquer les chiffres isolés dans le dashboard (pas de champ de texte)
                        event.consume();
                    }
                });
                
                // Bloquer Enter si aucun champ de texte n'a le focus (scanners envoient souvent Enter)
                scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    javafx.scene.Node focusedNode = scene.getFocusOwner();
                    if (event.getCode() == javafx.scene.input.KeyCode.ENTER && 
                        (focusedNode == null || !(focusedNode instanceof javafx.scene.control.TextInputControl))) {
                        // Pas de champ de texte focusé, probablement un scan de code-barres
                        event.consume();
                        scanBuffer[0].setLength(0); // Reset buffer
                    }
                });
                
                LOG.info("✓ Protection code-barres activée dans le dashboard admin");
            }
        });
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleGestionStock() {
        try {
            Stage stage = (Stage) gestionStockButton.getScene().getWindow();
            FXMLUtils.changeScene(stage, "/view/GestionStock.fxml", "Gestion de Stock");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors du chargement de la gestion de stock: " + e.getMessage());
        }
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleGestionUtilisateurs() {
        try {
            Stage stage = (Stage) gestionUtilisateursButton.getScene().getWindow();
            FXMLUtils.changeScene(stage, "/view/GestionUtilisateurs.fxml", "Gestion des Utilisateurs");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors du chargement de la gestion des utilisateurs: " + e.getMessage());
        }
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleDeconnexion() {
        ConnexionController.deconnecter();
        try {
            Stage stage = (Stage) deconnexionButton.getScene().getWindow();
            FXMLUtils.changeScene(stage, "/view/Connexion.fxml", "Connexion");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors de la déconnexion: " + e.getMessage());
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    @FXML
    @SuppressWarnings("unused")
    private void handleGestionVentes() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/GestionVentes.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) gestionVentesButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Gestion des Ventes");

        } catch (IOException e) {
            LOG.error("Erreur lors du chargement de la gestion des ventes: " + e.getMessage(), e);
            LOG.error("Exception type: " + e.getClass().getName());
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors du chargement de la gestion des ventes: " + e.getMessage());
        }
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleGestionTabac() {
        try {
            Stage stage = (Stage) gestionTabacButton.getScene().getWindow();
            FXMLUtils.changeScene(stage, "/view/GestionTabac.fxml", "Caisse Tabac - Ventes Tabac/Puff/Terrea");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors du chargement de la gestion des ventes de tabac: " + e.getMessage());
        }
    }
    
    @FXML
    @SuppressWarnings("unused")
    private void handleVisualisationProduits() {
        try {
            Stage stage = (Stage) visualisationProduitsButton.getScene().getWindow();
            FXMLUtils.changeScene(stage, "/view/VisualisationProduits.fxml", "Visualisation des Produits");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Erreur lors du chargement de la visualisation des produits: " + e.getMessage());
        }
    }

    // Cartes d'indicateurs : chacune ouvre le détail de ce qu'elle résume.
    @FXML private javafx.scene.layout.VBox carteProduits;
    @FXML private javafx.scene.layout.VBox carteRuptures;
    @FXML private javafx.scene.layout.VBox carteVentes;
    @FXML private javafx.scene.layout.VBox carteFournisseurs;
    @FXML private javafx.scene.layout.VBox carteCredits;
    @FXML private javafx.scene.layout.VBox cartePaiements;
    @FXML private javafx.scene.layout.VBox carteNotes;

    /**
     * Rend les cartes cliquables.
     *
     * Un chiffre seul ne dit pas ce qu'il recouvre : « 3 ruptures » appelle
     * immédiatement la question de savoir lesquelles. Chaque carte ouvre donc
     * le détail correspondant.
     */
    private void rendreCartesCliquables() {
        activer(carteProduits,     "Produits en stock",   this::detailProduits);
        activer(carteRuptures,     "Ruptures de stock",   this::detailRuptures);
        activer(carteVentes,       "Ventes du jour",      this::detailVentes);
        activer(carteFournisseurs, "Fournisseurs",        this::detailFournisseurs);
        activer(carteCredits,      "Crédits fournisseur", this::detailCredits);
        activer(cartePaiements,    "Paiements du jour",   this::detailPaiements);
        activer(carteNotes,        "Notes du jour",       this::detailNotes);
    }

    private void activer(javafx.scene.layout.VBox carte, String titre, Runnable action) {
        if (carte == null) {
            return;
        }
        carte.setCursor(javafx.scene.Cursor.HAND);
        carte.setOnMouseClicked(e -> action.run());
        javafx.scene.control.Tooltip.install(carte,
                new javafx.scene.control.Tooltip("Voir le détail : " + titre));

        // Léger relief au survol : indique que la carte est actionnable.
        carte.setOnMouseEntered(e -> {
            carte.setScaleX(1.03);
            carte.setScaleY(1.03);
        });
        carte.setOnMouseExited(e -> {
            carte.setScaleX(1.0);
            carte.setScaleY(1.0);
        });
    }

    // ------------------------------------------------------------------
    // Détails ouverts depuis les cartes
    // ------------------------------------------------------------------

    private void detailProduits() {
        ui.TacheFond.executer(carteProduits, () -> produitDAO.findAll(),
                produits -> ui.DialogueDetail.produits(carteProduits,
                        "Produits en stock", produits));
    }

    private void detailRuptures() {
        ui.TacheFond.executer(carteRuptures,
                () -> produitDAO.findAll().stream()
                        .filter(p -> p.getQuantiteStock() == 0)
                        .collect(java.util.stream.Collectors.toList()),
                produits -> ui.DialogueDetail.produits(carteRuptures,
                        "Produits en rupture", produits));
    }

    private void detailVentes() {
        ui.TacheFond.executer(carteVentes,
                () -> venteDAO.findByDate(LocalDateTime.now()),
                ventes -> ui.DialogueDetail.ventes(carteVentes, "Ventes du jour", ventes));
    }

    private void detailFournisseurs() {
        ui.TacheFond.executer(carteFournisseurs, () -> fournisseurDAO.findAll(),
                fournisseurs -> ui.DialogueDetail.fournisseurs(carteFournisseurs,
                        "Fournisseurs", fournisseurs, creditFournisseurDAO));
    }

    private void detailCredits() {
        detailFournisseurs();
    }

    private void detailPaiements() {
        ui.TacheFond.executer(cartePaiements,
                () -> paiementFournisseurDAO.findByDate(LocalDateTime.now()),
                paiements -> ui.DialogueDetail.paiements(cartePaiements,
                        "Paiements du jour", paiements));
    }

    private void detailNotes() {
        ui.TacheFond.executer(carteNotes,
                () -> noteJourDAO.findByDate(LocalDateTime.now()),
                notes -> ui.DialogueDetail.notes(carteNotes, "Notes du jour", notes));
    }

    /** Ouvre le journal d'audit, chargé en arrière-plan. */
    @FXML
    private void handleAudit() {
        ui.TacheFond.executer(carteProduits,
                () -> new dao.AuditLogDAO().findRecent(1000),
                logs -> ui.DialogueAudit.ouvrir(carteProduits, logs,
                        new dao.UtilisateurDAO().findAll()));
    }

    /** Chiffres du tableau de bord, collectés en une passe. */
    private static final class Statistiques {
        long produitsDisponibles;
        long ruptures;
        BigDecimal ventesJour = BigDecimal.ZERO;
        int totalFournisseurs;
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal paiementsJour = BigDecimal.ZERO;
        int notesJour;
    }

    /**
     * Rafraîchit les indicateurs.
     *
     * Les requêtes s'exécutent hors du fil JavaFX : elles étaient auparavant
     * enchaînées à l'ouverture de l'écran, qui restait figé le temps de la
     * dizaine d'allers-retours — d'autant plus visible avec la base sur le
     * réseau.
     */
    private void rafraichirStatistiques() {
        ui.TacheFond.executer(welcomeLabel, this::collecterStatistiques, stats -> {
            totalProduitsStatLabel.setText(String.valueOf(stats.produitsDisponibles));
            rupturesStatLabel.setText(String.valueOf(stats.ruptures));
            ventesJourStatLabel.setText(String.format("%.2f DT", stats.ventesJour));
            totalFournisseursLabel.setText(String.valueOf(stats.totalFournisseurs));
            totalCreditsLabel.setText(String.format("%.2f DT", stats.totalCredits));
            paiementsJourLabel.setText(String.format("%.2f DT", stats.paiementsJour));
            notesJourLabel.setText(String.valueOf(stats.notesJour));

            // Les ruptures méritent d'être signalées à l'ouverture : c'est
            // l'information qui appelle une action immédiate.
            if (stats.ruptures > 0) {
                ui.Toast.avertissement(welcomeLabel,
                        stats.ruptures + " produit(s) en rupture de stock.");
            }
        });
    }

    /** Exécuté en arrière-plan : aucune manipulation de l'interface ici. */
    private Statistiques collecterStatistiques() {
        Statistiques stats = new Statistiques();

        List<Produit> produits = produitDAO.findAll();
        stats.produitsDisponibles = produits.stream().filter(p -> p.getQuantiteStock() > 0).count();
        stats.ruptures = produits.stream().filter(p -> p.getQuantiteStock() == 0).count();

        LocalDateTime debutJour = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime finJour = LocalDateTime.now().with(LocalTime.MAX);
        stats.ventesJour = venteDAO.getTotalRecettes(debutJour, finJour);

        // Une seule lecture des fournisseurs : elle était faite deux fois,
        // puis suivie d'une requête de crédit par fournisseur.
        List<model.Fournisseur> fournisseurs = fournisseurDAO.findAll();
        stats.totalFournisseurs = fournisseurs.size();
        for (model.Fournisseur f : fournisseurs) {
            model.CreditFournisseur credit = creditFournisseurDAO.findByFournisseurId(f.getId());
            if (credit != null) {
                stats.totalCredits = stats.totalCredits.add(credit.getMontant());
            }
        }

        stats.paiementsJour = paiementFournisseurDAO.getTotalByDate(LocalDateTime.now());
        stats.notesJour = noteJourDAO.findByDate(LocalDateTime.now()).size();

        return stats;
    }
}


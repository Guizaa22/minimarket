package controller;

import dao.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import model.*;
import util.FXMLUtils;
import util.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Contrôleur pour la vue des recettes du jour
 */
public class RecetteJourController {
    
    @FXML
    private DatePicker datePicker;
    
    @FXML
    private Button rechercherButton;
    
    @FXML
    private Label totalVentesLabel;
    
    @FXML
    private Label totalPaiementsLabel;
    
    @FXML
    private Label totalCreditsLabel;
    
    @FXML
    private Label totalHeuresLabel;
    
    @FXML
    private Label recetteNetLabel;
    
    @FXML
    private TableView<PaiementFournisseur> paiementsTable;
    
    @FXML
    private TableColumn<PaiementFournisseur, String> paiementFournisseurCol;
    
    @FXML
    private TableColumn<PaiementFournisseur, String> paiementMontantCol;
    
    @FXML
    private TableColumn<PaiementFournisseur, String> paiementNotesCol;
    
    @FXML
    private TableColumn<PaiementFournisseur, String> paiementDateCol;
    
    @FXML
    private TableView<AjoutStock> ajoutsStockTable;
    
    @FXML
    private TableColumn<AjoutStock, String> ajoutProduitCol;
    
    @FXML
    private TableColumn<AjoutStock, String> ajoutQuantiteCol;
    
    @FXML
    private TableColumn<AjoutStock, String> ajoutFournisseurCol;
    
    @FXML
    private TableColumn<AjoutStock, String> ajoutPaiementCol;
    
    @FXML
    private TableColumn<AjoutStock, String> ajoutCreditCol;
    
    @FXML
    private TableColumn<AjoutStock, String> ajoutNotesCol;
    
    @FXML
    private TableView<DeplacementEmploye> deplacementsTable;
    
    @FXML
    private TableColumn<DeplacementEmploye, String> deplacementDestinationCol;
    
    @FXML
    private TableColumn<DeplacementEmploye, String> deplacementHeuresCol;
    
    @FXML
    private TableColumn<DeplacementEmploye, String> deplacementNotesCol;
    
    @FXML
    private TableView<NoteJour> notesTable;
    
    @FXML
    private TableColumn<NoteJour, String> noteTypeCol;
    
    @FXML
    private TableColumn<NoteJour, String> noteMontantCol;
    
    @FXML
    private TableColumn<NoteJour, String> noteDescriptionCol;
    
    @FXML
    private TableColumn<NoteJour, String> noteDateCol;
    
    @FXML
    private TableView<Vente> ventesTable;
    
    @FXML
    private TableColumn<Vente, String> venteIdCol;
    
    @FXML
    private TableColumn<Vente, String> venteDateCol;
    
    @FXML
    private TableColumn<Vente, String> venteTotalCol;
    
    @FXML
    private TableColumn<Vente, String> venteDetailsCol;
    
    @FXML
    private Button exporterPDFButton;
    
    @FXML
    private Button retourButton;
    
    private VenteDAO venteDAO;
    private PaiementFournisseurDAO paiementDAO;
    private AjoutStockDAO ajoutStockDAO;
    private DeplacementEmployeDAO deplacementDAO;
    private NoteJourDAO noteDAO;
    private FournisseurDAO fournisseurDAO;
    private ProduitDAO produitDAO;
    private UtilisateurDAO utilisateurDAO;
    
    private ObservableList<PaiementFournisseur> paiementsList;
    private ObservableList<AjoutStock> ajoutsList;
    private ObservableList<DeplacementEmploye> deplacementsList;
    private ObservableList<NoteJour> notesList;
    private ObservableList<Vente> ventesList;
    
    @FXML
    private void initialize() {
        venteDAO = new VenteDAO();
        paiementDAO = new PaiementFournisseurDAO();
        ajoutStockDAO = new AjoutStockDAO();
        deplacementDAO = new DeplacementEmployeDAO();
        noteDAO = new NoteJourDAO();
        fournisseurDAO = new FournisseurDAO();
        produitDAO = new ProduitDAO();
        utilisateurDAO = new UtilisateurDAO();
        
        paiementsList = FXCollections.observableArrayList();
        ajoutsList = FXCollections.observableArrayList();
        deplacementsList = FXCollections.observableArrayList();
        notesList = FXCollections.observableArrayList();
        ventesList = FXCollections.observableArrayList();
        
        // Configurer la date par défaut (aujourd'hui)
        datePicker.setValue(LocalDate.now());
        
        // Configurer les tables
        configurerTables();
        
        // Charger les données pour aujourd'hui
        chargerDonnees();
    }
    
    private void configurerTables() {
        // Table des paiements
        paiementFournisseurCol.setCellValueFactory(cellData -> {
            Fournisseur f = fournisseurDAO.findById(cellData.getValue().getIdFournisseur());
            return new javafx.beans.property.SimpleStringProperty(f != null ? f.getNom() : "N/A");
        });
        paiementMontantCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(String.format("%.2f DT", cellData.getValue().getMontant())));
        paiementNotesCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNotes() != null ? cellData.getValue().getNotes() : ""));
        paiementDateCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDatePaiement().format(DateTimeFormatter.ofPattern("HH:mm"))));
        
        paiementsTable.setItems(paiementsList);
        
        // Table des ajouts de stock
        ajoutProduitCol.setCellValueFactory(cellData -> {
            Produit p = produitDAO.findById(cellData.getValue().getIdProduit());
            return new javafx.beans.property.SimpleStringProperty(p != null ? p.getNom() : "N/A");
        });
        ajoutQuantiteCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(String.valueOf(cellData.getValue().getQuantite())));
        ajoutFournisseurCol.setCellValueFactory(cellData -> {
            Integer idFournisseur = cellData.getValue().getIdFournisseur();
            if (idFournisseur != null) {
                Fournisseur f = fournisseurDAO.findById(idFournisseur);
                return new javafx.beans.property.SimpleStringProperty(f != null ? f.getNom() : "N/A");
            }
            return new javafx.beans.property.SimpleStringProperty("-");
        });
        ajoutPaiementCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(String.format("%.2f DT", cellData.getValue().getMontantPaiement())));
        ajoutCreditCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(String.format("%.2f DT", cellData.getValue().getCreditUtilise())));
        ajoutNotesCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNotes() != null ? cellData.getValue().getNotes() : ""));
        
        ajoutsStockTable.setItems(ajoutsList);
        
        // Table des déplacements
        deplacementDestinationCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDestination() != null ? cellData.getValue().getDestination() : ""));
        deplacementHeuresCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(String.format("%.2f h", cellData.getValue().getHeuresTravaillees())));
        deplacementNotesCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNotes() != null ? cellData.getValue().getNotes() : ""));
        
        deplacementsTable.setItems(deplacementsList);
        
        // Table des notes
        noteTypeCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTypeNote().getLibelle()));
        noteMontantCol.setCellValueFactory(cellData -> {
            BigDecimal montant = cellData.getValue().getMontant();
            if (montant.compareTo(BigDecimal.ZERO) > 0) {
                return new javafx.beans.property.SimpleStringProperty(String.format("%.2f DT", montant));
            }
            return new javafx.beans.property.SimpleStringProperty("-");
        });
        noteDescriptionCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDescription()));
        noteDateCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDateNote().format(DateTimeFormatter.ofPattern("HH:mm"))));
        
        notesTable.setItems(notesList);
        
        // Table des ventes
        if (venteIdCol != null && venteDateCol != null && venteTotalCol != null && venteDetailsCol != null && ventesTable != null) {
            venteIdCol.setCellValueFactory(cellData -> {
                Vente vente = cellData.getValue();
                return new javafx.beans.property.SimpleStringProperty(vente != null ? "#" + vente.getId() : "");
            });
            venteDateCol.setCellValueFactory(cellData -> {
                Vente vente = cellData.getValue();
                if (vente != null && vente.getDateVente() != null) {
                    return new javafx.beans.property.SimpleStringProperty(vente.getDateVente().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                }
                return new javafx.beans.property.SimpleStringProperty("");
            });
            venteTotalCol.setCellValueFactory(cellData -> {
                Vente vente = cellData.getValue();
                if (vente != null && vente.getTotalVente() != null) {
                    return new javafx.beans.property.SimpleStringProperty(String.format("%.2f DT", vente.getTotalVente()));
                }
                return new javafx.beans.property.SimpleStringProperty("");
            });
            venteDetailsCol.setCellValueFactory(cellData -> {
                Vente vente = cellData.getValue();
                if (vente == null) {
                    return new javafx.beans.property.SimpleStringProperty("");
                }
                try {
                    List<DetailVente> details = venteDAO.findDetailsByVente(vente.getId());
                    if (details == null || details.isEmpty()) {
                        return new javafx.beans.property.SimpleStringProperty("Aucun détail");
                    }
                    StringBuilder detailsStr = new StringBuilder();
                    for (int i = 0; i < details.size() && i < 3; i++) {
                        DetailVente detail = details.get(i);
                        Produit produit = produitDAO.findById(detail.getProduitId());
                        if (produit != null) {
                            if (i > 0) detailsStr.append(", ");
                            detailsStr.append(produit.getNom()).append(" (").append(detail.getQuantite()).append(")");
                        }
                    }
                    if (details.size() > 3) {
                        detailsStr.append("... (+").append(details.size() - 3).append(" autres)");
                    }
                    return new javafx.beans.property.SimpleStringProperty(detailsStr.length() > 0 ? detailsStr.toString() : "Aucun détail");
                } catch (Exception e) {
                    System.err.println("Erreur lors de la récupération des détails de la vente " + vente.getId() + ": " + e.getMessage());
                    return new javafx.beans.property.SimpleStringProperty("Erreur");
                }
            });
            
            ventesTable.setItems(ventesList);
            System.out.println("Table des ventes configurée avec " + ventesList.size() + " éléments");
        } else {
            System.err.println("ERREUR: Certains éléments de la table des ventes ne sont pas initialisés dans le FXML!");
        }
    }
    
    @FXML
    private void handleRechercher() {
        chargerDonnees();
    }
    
    private void chargerDonnees() {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            date = LocalDate.now();
            datePicker.setValue(date);
        }
        
        LocalDateTime dateDebut = date.atStartOfDay();
        LocalDateTime dateFin = date.atTime(23, 59, 59);
        
        // La recette affichée est celle de l'employé connecté.
        int idEmploye = SessionManager.getCurrentUserId();
        if (idEmploye <= 0) {
            afficherAlerte(Alert.AlertType.ERROR, "Session expirée",
                "Aucun utilisateur connecté. Reconnectez-vous pour consulter la recette du jour.");
            return;
        }

        System.out.println("Chargement des données pour l'employé ID: " + idEmploye + " le " + date);
        
        // Charger les ventes de l'employé pour la date
        List<Vente> ventes = venteDAO.findByUtilisateurAndDate(idEmploye, dateDebut, dateFin);
        System.out.println("Nombre de ventes trouvées pour l'employé " + idEmploye + " le " + date + ": " + ventes.size());
        ventesList.clear();
        ventesList.addAll(ventes);
        System.out.println("Ventes ajoutées à la liste: " + ventesList.size());
        
        // Calculer le total des ventes de l'employé (depuis la liste filtrée)
        BigDecimal totalVentes = ventes.stream()
            .map(Vente::getTotalVente)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalVentesLabel.setText(String.format("%.2f DT", totalVentes));
        
        // Charger les paiements fournisseur de l'employé
        List<PaiementFournisseur> paiements = paiementDAO.findByEmployeAndDate(idEmploye, dateDebut);
        paiementsList.clear();
        paiementsList.addAll(paiements);
        System.out.println("Paiements chargés: " + paiements.size());
        if (paiementsTable != null) {
            paiementsTable.refresh();
        }
        
        // Calculer le total des paiements de l'employé (depuis la liste filtrée)
        BigDecimal totalPaiements = paiements.stream()
            .map(PaiementFournisseur::getMontant)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalPaiementsLabel.setText(String.format("%.2f DT", totalPaiements));
        
        // Charger les ajouts de stock
        List<AjoutStock> ajouts = ajoutStockDAO.findByEmployeAndDate(idEmploye, dateDebut);
        ajoutsList.clear();
        ajoutsList.addAll(ajouts);
        System.out.println("Ajouts de stock chargés: " + ajouts.size());
        if (ajoutsStockTable != null) {
            ajoutsStockTable.refresh();
        }
        
        // Calculer le total des crédits utilisés
        BigDecimal totalCredits = ajouts.stream()
            .map(AjoutStock::getCreditUtilise)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalCreditsLabel.setText(String.format("%.2f DT", totalCredits));
        
        // Calculer les cigarettes à partir des ajouts de stock de tabac
        int totalCigarettesAjoutees = 0;
        int totalPaquetsAjoutes = 0;
        for (AjoutStock ajout : ajouts) {
            Produit produit = produitDAO.findById(ajout.getIdProduit());
            if (produit != null && produit.isTabac() && ajout.getTypeAjoutTabac() != null) {
                if ("cigarette".equalsIgnoreCase(ajout.getTypeAjoutTabac())) {
                    totalCigarettesAjoutees += ajout.getQuantiteEnCigarettes();
                } else if ("paquet".equalsIgnoreCase(ajout.getTypeAjoutTabac())) {
                    totalPaquetsAjoutes += ajout.getQuantite();
                    totalCigarettesAjoutees += ajout.getQuantiteEnCigarettes();
                }
            }
        }
        
        // Afficher les statistiques tabac dans les notes ou dans un label si disponible
        if (totalCigarettesAjoutees > 0 || totalPaquetsAjoutes > 0) {
            System.out.println(String.format("Ajouts de stock tabac: %d paquets, %d cigarettes", 
                totalPaquetsAjoutes, totalCigarettesAjoutees));
        }
        
        // Charger les déplacements
        List<DeplacementEmploye> deplacements = deplacementDAO.findByEmployeAndDate(idEmploye, dateDebut);
        deplacementsList.clear();
        deplacementsList.addAll(deplacements);
        System.out.println("Déplacements chargés: " + deplacements.size());
        if (deplacementsTable != null) {
            deplacementsTable.refresh();
        }
        
        // Calculer le total des heures
        BigDecimal totalHeures = deplacementDAO.getTotalHeuresByDate(idEmploye, dateDebut);
        totalHeuresLabel.setText(String.format("%.2f h", totalHeures));
        
        // Charger les notes
        List<NoteJour> notes = noteDAO.findByEmployeAndDate(idEmploye, dateDebut);
        notesList.clear();
        notesList.addAll(notes);
        System.out.println("Notes chargées: " + notes.size());
        if (notesTable != null) {
            notesTable.refresh();
        }
        
        // Rafraîchir la table des ventes si elle existe
        if (ventesTable != null) {
            javafx.application.Platform.runLater(() -> {
                ventesTable.refresh();
                System.out.println("Table des ventes rafraîchie. Nombre d'éléments dans la table: " + ventesTable.getItems().size());
            });
        }
        
        // Calculer la recette nette (ventes - paiements)
        BigDecimal recetteNette = totalVentes.subtract(totalPaiements);
        recetteNetLabel.setText(String.format("%.2f DT", recetteNette));
    }
    
    @FXML
    private void handleExporterPDF() {
        try {
            LocalDate date = datePicker.getValue();
            if (date == null) {
                date = LocalDate.now();
            }
            
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Exporter la Recette du Jour en PDF");
            fileChooser.setInitialFileName("recette_" + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
            fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            
            javafx.stage.Stage stage = (javafx.stage.Stage) exporterPDFButton.getScene().getWindow();
            java.io.File file = fileChooser.showSaveDialog(stage);
            
            if (file != null) {
                boolean isAdmin = SessionManager.isAdmin();
                int idEmploye = SessionManager.getCurrentUserId();
                util.PDFExporter.exportRecetteJour(file, date, venteDAO, paiementDAO, ajoutStockDAO, 
                    noteDAO, fournisseurDAO, produitDAO, deplacementDAO, utilisateurDAO, idEmploye, isAdmin);
                afficherAlerte(Alert.AlertType.INFORMATION, "Succès", 
                    "Recette du jour exportée en PDF avec succès:\n" + file.getAbsolutePath());
            }
        } catch (Exception e) {
            afficherAlerte(Alert.AlertType.ERROR, "Erreur", 
                "Erreur lors de l'export PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleRetour() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) retourButton.getScene().getWindow();
            FXMLUtils.changeScene(stage, "/view/AjoutStockEmploye.fxml", "Ajout de Stock - Employé");
        } catch (IOException e) {
            afficherAlerte(Alert.AlertType.ERROR, "Erreur", "Erreur lors du retour: " + e.getMessage());
        }
    }
    
    private void afficherAlerte(Alert.AlertType type, String titre, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


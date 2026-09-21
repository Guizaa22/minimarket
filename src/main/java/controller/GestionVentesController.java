package controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dao.DetailVenteDAO;
import dao.ProduitDAO;
import dao.UtilisateurDAO;
import dao.VenteDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import model.DetailVente;
import model.Produit;
import model.ProduitStats;
import model.Vente;
import util.FXMLUtils;

/**
 * Contrôleur pour la gestion des ventes
 */
public class GestionVentesController {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(GestionVentesController.class);

    // ========================================
    // LABELS & DATE
    // ========================================
    @FXML
    private Button retourButton;

    @FXML
    private Label dateLabel;

    @FXML
    private Label caJourLabel;

    @FXML
    private Label caSemaineLabel;

    @FXML
    private Label caMoisLabel;

    @FXML
    private Label nbVentesLabel;

    @FXML
    private Label panierMoyenLabel;

    @FXML
    private Label beneficeLabel;
    
    @FXML
    private javafx.scene.control.DatePicker datePickerFilter;

    // ========================================
    // BOUTONS DE NAVIGATION
    // ========================================
    @FXML
    private Button btnDashboard;

    @FXML
    private Button btnVentes;

    @FXML
    private Button btnProduits;

    @FXML
    private Button btnRapports;

    // ========================================
    // VUES (CONTAINERS)
    // ========================================
    @FXML
    private javafx.scene.layout.VBox dashboardView;

    @FXML
    private javafx.scene.layout.VBox ventesView;

    @FXML
    private javafx.scene.layout.VBox produitsView;

    @FXML
    private javafx.scene.layout.VBox rapportsView;

    // ========================================
    // GRAPHIQUES
    // ========================================
    @FXML
    private LineChart<String, Number> ventesLineChart;

    @FXML
    private PieChart categoriesPieChart;

    @FXML
    private BarChart<String, Number> caBarChart;

    // ========================================
    // TABLEVIEWS
    // ========================================
    @FXML
    private TableView<VenteDisplay> ventesTable;

    @FXML
    private TableColumn<VenteDisplay, String> colId;

    @FXML
    private TableColumn<VenteDisplay, String> colDate;

    @FXML
    private TableColumn<VenteDisplay, String> colMontant;

    @FXML
    private TableColumn<VenteDisplay, Integer> colArticles;

    @FXML
    private TableColumn<VenteDisplay, String> colCaissier;

    @FXML
    private TableColumn<VenteDisplay, Void> colAction;

    @FXML
    private TableView<ProduitStats> produitsTable;

    @FXML
    private TableColumn<ProduitStats, Integer> colRang;

    @FXML
    private TableColumn<ProduitStats, String> colProduit;

    @FXML
    private TableColumn<ProduitStats, Integer> colQuantite;

    @FXML
    private TableColumn<ProduitStats, String> colCA;

    // ========================================
    // DONNÉES & DAO
    // ========================================
    private VenteDAO venteDAO;
    private DetailVenteDAO detailVenteDAO;
    /** Les rapports passent par le service, seul détenteur des règles de calcul. */
    private service.VenteService venteService;
    private ProduitDAO produitDAO;
    private UtilisateurDAO utilisateurDAO;

    private ObservableList<VenteDisplay> ventesList;
    private ObservableList<ProduitStats> produitStatsList;

    /**
     * Initialisation du contrôleur
     */
    @FXML
    @SuppressWarnings("unused")
    private void initialize() {
        // Initialisation des DAOs
        venteDAO = new VenteDAO();
        detailVenteDAO = new DetailVenteDAO();
        venteService = new service.VenteService();
        produitDAO = new ProduitDAO();
        utilisateurDAO = new UtilisateurDAO();

        // Initialisation des listes
        ventesList = FXCollections.observableArrayList();
        produitStatsList = FXCollections.observableArrayList();

        // Configuration de la date
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)));
        
        // Configuration du DatePicker
        if (datePickerFilter != null) {
            datePickerFilter.setValue(LocalDate.now());
            datePickerFilter.setOnAction(e -> filtrerParDate());
        }

        // Configuration des tables
        configureVentesTable();
        configureProduitsTable();

        // Charger les données
        chargerDonnees();

        // Afficher le dashboard par défaut
        showDashboard();
    }
    
    /**
     * Filtre les ventes par date sélectionnée
     */
    private void filtrerParDate() {
        if (datePickerFilter != null && datePickerFilter.getValue() != null) {
            LocalDate dateSelectionnee = datePickerFilter.getValue();
            LocalDateTime debut = dateSelectionnee.atStartOfDay();
            LocalDateTime fin = dateSelectionnee.plusDays(1).atStartOfDay();
            
            // Recharger les statistiques pour cette date
            chargerStatistiquesPourDate(debut, fin);
            chargerVentesPourDate(debut, fin);
        }
    }
    
    /**
     * Charge les statistiques pour une date spécifique
     */
    private void chargerStatistiquesPourDate(LocalDateTime debut, LocalDateTime fin) {
        BigDecimal caJour = venteDAO.getCAParPeriode(debut, fin);
        caJourLabel.setText(String.format("%.2f DT", caJour));
        
        int nbVentes = venteDAO.getNombreVentesParPeriode(debut, fin);
        nbVentesLabel.setText(String.valueOf(nbVentes));
        
        BigDecimal panierMoyen = nbVentes > 0 ? caJour.divide(BigDecimal.valueOf(nbVentes), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        panierMoyenLabel.setText(String.format("%.2f DT", panierMoyen));
    }
    
    /**
     * Charge les ventes pour une date spécifique
     */
    private void chargerVentesPourDate(LocalDateTime debut, LocalDateTime fin) {
        ventesList.clear();
        
        List<Vente> toutesVentes = venteDAO.findAll();
        List<Vente> ventesFiltrees = toutesVentes.stream()
                .filter(v -> v.getDateVente().isAfter(debut.minusSeconds(1)) && v.getDateVente().isBefore(fin.plusSeconds(1)))
                .collect(java.util.stream.Collectors.toList());
        
        for (Vente vente : ventesFiltrees) {
            List<DetailVente> details = detailVenteDAO.findByVenteId(vente.getId());
            int nbArticles = details.stream().mapToInt(DetailVente::getQuantite).sum();
            
            String caissier = utilisateurDAO.findById(vente.getUtilisateurId()).getUsername();
            
            VenteDisplay display = new VenteDisplay(
                    "#" + vente.getId(),
                    vente.getDateVente().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    String.format("%.2f DT", vente.getTotalVente()),
                    nbArticles,
                    caissier,
                    vente.getId()
            );
            
            ventesList.add(display);
        }
    }

    /**
     * Configuration de la table des ventes
     */
    private void configureVentesTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("dateHeure"));
        colMontant.setCellValueFactory(new PropertyValueFactory<>("montant"));
        colArticles.setCellValueFactory(new PropertyValueFactory<>("nbArticles"));
        colCaissier.setCellValueFactory(new PropertyValueFactory<>("caissier"));

        // Configuration de la colonne Action
        colAction.setCellFactory(column -> new TableCell<VenteDisplay, Void>() {
            private final Button btnDetails = new Button("👁️ Détails");

            {
                btnDetails.getStyleClass().add("btn-details");
                btnDetails.setOnAction(event -> {
                    VenteDisplay vente = getTableView().getItems().get(getIndex());
                    afficherDetailsVente(vente);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btnDetails);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        ventesTable.setItems(ventesList);
    }

    /**
     * Configuration de la table des produits
     */
    private void configureProduitsTable() {
        colRang.setCellValueFactory(new PropertyValueFactory<>("rang"));
        colProduit.setCellValueFactory(new PropertyValueFactory<>("nomProduit"));
        colQuantite.setCellValueFactory(new PropertyValueFactory<>("quantiteVendue"));
        colCA.setCellValueFactory(new PropertyValueFactory<>("caGenere"));

        // Afficher les médailles pour le top 3
        colRang.setCellFactory(column -> new TableCell<ProduitStats, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String emoji = item == 1 ? "🥇" : item == 2 ? "🥈" : item == 3 ? "🥉" : String.valueOf(item);
                    Label label = new Label(emoji);
                    label.setStyle("-fx-font-size: 24px;");
                    setGraphic(label);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Mise en forme de la quantité
        colQuantite.setCellFactory(column -> new TableCell<ProduitStats, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Mise en forme du CA
        colCA.setCellFactory(column -> new TableCell<ProduitStats, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        produitsTable.setItems(produitStatsList);
    }

    /**
     * Charger toutes les données
     */
    private void chargerDonnees() {
        chargerStatistiques();
        chargerGraphiques();
        chargerVentes();
        chargerTopProduits();
    }

    /**
     * Charger les statistiques (KPIs)
     */
    private void chargerStatistiques() {
        LocalDateTime maintenant = LocalDateTime.now();
        LocalDateTime debutJour = maintenant.toLocalDate().atStartOfDay();
        LocalDateTime debutSemaine = maintenant.minusDays(7);
        LocalDateTime debutMois = maintenant.minusDays(30);

        // CA Aujourd'hui
        BigDecimal caJour = venteDAO.getCAParPeriode(debutJour, maintenant);
        caJourLabel.setText(String.format("%.2f DT", caJour));

        // CA Semaine
        BigDecimal caSemaine = venteDAO.getCAParPeriode(debutSemaine, maintenant);
        caSemaineLabel.setText(String.format("%.2f DT", caSemaine));

        // CA Mois
        BigDecimal caMois = venteDAO.getCAParPeriode(debutMois, maintenant);
        caMoisLabel.setText(String.format("%.2f DT", caMois));

        // Nombre de ventes aujourd'hui
        int nbVentes = venteDAO.getNombreVentesParPeriode(debutJour, maintenant);
        nbVentesLabel.setText(String.valueOf(nbVentes));

        // Panier moyen
        BigDecimal panierMoyen = nbVentes > 0 ? caJour.divide(BigDecimal.valueOf(nbVentes), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        panierMoyenLabel.setText(String.format("%.2f DT", panierMoyen));

        // Bénéfice estimé (20% du CA pour cet exemple)
        BigDecimal benefice = caMois.multiply(BigDecimal.valueOf(0.20));
        beneficeLabel.setText(String.format("%.2f DT", benefice));
    }

    /**
     * Charger les données des graphiques
     */
    private void chargerGraphiques() {
        chargerLineChart();
        chargerPieChart();
        chargerBarChart();
    }

    /**
     * Charger le graphique en ligne (Évolution des ventes 7 jours)
     */
    private void chargerLineChart() {
        ventesLineChart.getData().clear();

        XYChart.Series<String, Number> seriesMontant = new XYChart.Series<>();
        seriesMontant.setName("Montant (DT)");

        XYChart.Series<String, Number> seriesNbVentes = new XYChart.Series<>();
        seriesNbVentes.setName("Nombre de ventes");

        // Récupérer les données des 7 derniers jours
        LocalDateTime maintenant = LocalDateTime.now();
        String[] jours = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};

        for (int i = 6; i >= 0; i--) {
            LocalDateTime debut = maintenant.minusDays(i).toLocalDate().atStartOfDay();
            LocalDateTime fin = debut.plusDays(1);

            BigDecimal ca = venteDAO.getCAParPeriode(debut, fin);
            int nbVentes = venteDAO.getNombreVentesParPeriode(debut, fin);

            int jourIndex = debut.getDayOfWeek().getValue() - 1;
            String jour = jours[jourIndex];

            seriesMontant.getData().add(new XYChart.Data<>(jour, ca.doubleValue()));
            seriesNbVentes.getData().add(new XYChart.Data<>(jour, nbVentes));
        }

        ventesLineChart.getData().add(seriesMontant);
        ventesLineChart.getData().add(seriesNbVentes);
    }

    /**
     * Charger le graphique circulaire (Ventes par catégorie)
     */
    private void chargerPieChart() {
        categoriesPieChart.getData().clear();

        Map<String, BigDecimal> ventesParCategorie = detailVenteDAO.getVentesParCategorie();

        for (Map.Entry<String, BigDecimal> entry : ventesParCategorie.entrySet()) {
            PieChart.Data slice = new PieChart.Data(
                    entry.getKey(),
                    entry.getValue().doubleValue()
            );
            categoriesPieChart.getData().add(slice);
        }
    }

    /**
     * Charger le graphique à barres (CA par catégorie)
     */
    private void chargerBarChart() {
        caBarChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("CA (DT)");

        Map<String, BigDecimal> ventesParCategorie = detailVenteDAO.getVentesParCategorie();

        for (Map.Entry<String, BigDecimal> entry : ventesParCategorie.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue().doubleValue()));
        }

        caBarChart.getData().add(series);
    }

    /**
     * Charger la liste des ventes récentes
     */
    private void chargerVentes() {
        ventesList.clear();

        List<Vente> ventes = venteDAO.findRecent(50); // 50 ventes les plus récentes

        // Les caissiers sont relus une fois chacun : une requête par vente
        // faisait cinquante allers-retours pour une poignée de comptes.
        Map<Integer, String> caissiers = new java.util.HashMap<>();

        for (Vente vente : ventes) {
            // Récupérer les détails de la vente
            List<DetailVente> details = detailVenteDAO.findByVenteId(vente.getId());
            int nbArticles = details.stream().mapToInt(DetailVente::getQuantite).sum();

            // Un compte supprimé depuis la vente ne doit pas faire échouer
            // l'affichage de tout l'historique.
            String caissier = caissiers.computeIfAbsent(vente.getUtilisateurId(), id -> {
                model.Utilisateur u = utilisateurDAO.findById(id);
                return u != null ? u.getUsername() : "Compte supprimé (#" + id + ")";
            });

            VenteDisplay display = new VenteDisplay(
                    "#" + vente.getId(),
                    vente.getDateVente().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    String.format("%.2f DT", vente.getTotalVente()),
                    nbArticles,
                    caissier,
                    vente.getId()
            );

            ventesList.add(display);
        }
    }

    /**
     * Charger le top 5 des produits
     */
    private void chargerTopProduits() {
        produitStatsList.clear();

        List<ProduitStats> topProduits = detailVenteDAO.getTopProduits(5);

        int rang = 1;
        for (ProduitStats stats : topProduits) {
            stats.setRang(rang++);
            produitStatsList.add(stats);
        }
    }

    /**
     * Afficher les détails d'une vente
     */
    private void afficherDetailsVente(VenteDisplay venteDisplay) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Détails de la vente");
        alert.setHeaderText("Vente " + venteDisplay.getId());

        // Récupérer les détails de la vente
        List<DetailVente> details = detailVenteDAO.findByVenteId(venteDisplay.getVenteId());

        StringBuilder content = new StringBuilder();
        content.append(String.format("Date: %s\n", venteDisplay.getDateHeure()));
        content.append(String.format("Caissier: %s\n", venteDisplay.getCaissier()));
        content.append(String.format("Montant total: %s\n\n", venteDisplay.getMontant()));
        content.append("Articles:\n");
        content.append("─────────────────────────────────\n");

        for (DetailVente detail : details) {
            Produit produit = produitDAO.findById(detail.getProduitId());
            content.append(String.format("• %s\n", produit.getNom()));
            content.append(String.format("  Quantité: %d × %.2f DT = %.2f DT\n",
                    detail.getQuantite(),
                    detail.getPrixVenteUnitaire(),
                    detail.getSousTotal()));
        }

        alert.setContentText(content.toString());
        ui.Dialogues.preparer(alert.getDialogPane(), null);
        alert.showAndWait();
    }

    /**
     * Afficher le dashboard
     */
    @FXML
    private void showDashboard() {
        activerVue(dashboardView);
        activerBouton(btnDashboard);
        chargerStatistiques();
        chargerGraphiques();
    }

    /**
     * Afficher l'historique des ventes
     */
    @FXML
    @SuppressWarnings("unused")
    private void showVentes() {
        activerVue(ventesView);
        activerBouton(btnVentes);
        chargerVentes();
    }

    /**
     * Afficher le top produits
     */
    @FXML
    @SuppressWarnings("unused")
    private void showProduits() {
        activerVue(produitsView);
        activerBouton(btnProduits);
        chargerTopProduits();
    }

    /**
     * Afficher la page des rapports
     */
    @FXML
    @SuppressWarnings("unused")
    private void showRapports() {
        activerVue(rapportsView);
        activerBouton(btnRapports);
    }

    /**
     * Activer une vue spécifique et masquer les autres
     */
    private void activerVue(javafx.scene.layout.VBox vueAActiver) {
        dashboardView.setVisible(false);
        ventesView.setVisible(false);
        produitsView.setVisible(false);
        rapportsView.setVisible(false);

        vueAActiver.setVisible(true);
    }

    /**
     * Activer un bouton de navigation et désactiver les autres
     */
    private void activerBouton(Button boutonActif) {
        btnDashboard.getStyleClass().remove("active");
        btnVentes.getStyleClass().remove("active");
        btnProduits.getStyleClass().remove("active");
        btnRapports.getStyleClass().remove("active");

        boutonActif.getStyleClass().add("active");
    }

    /**
     * Retour au dashboard principal
     */
    @FXML
    @SuppressWarnings("unused")
    private void handleRetour() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) btnDashboard.getScene().getWindow();
            FXMLUtils.changeScene(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur lors du retour: " + e.getMessage());
        }
    }

    // ========================================
    // GÉNÉRATION DES RAPPORTS
    // ========================================

    /** Nombre de produits repris dans le classement d'un rapport. */
    private static final int TOP_PRODUITS_RAPPORT = 10;

    /**
     * Périodes couvertes par les rapports.
     *
     * Chaque valeur connaît ses propres bornes : le rapport n'a plus qu'à
     * demander la période voulue, et ajouter un rythme de reporting ne touche
     * qu'à cette énumération.
     */
    private enum PeriodeRapport {
        JOURNALIER("RAPPORT JOURNALIER", "journalier"),
        HEBDOMADAIRE("RAPPORT HEBDOMADAIRE", "hebdomadaire"),
        MENSUEL("RAPPORT MENSUEL", "mensuel"),
        ANNUEL("RAPPORT ANNUEL", "annuel");

        private final String titre;
        private final String nomFichier;

        PeriodeRapport(String titre, String nomFichier) {
            this.titre = titre;
            this.nomFichier = nomFichier;
        }

        /** Début de la période, à partir d'aujourd'hui. */
        LocalDateTime debut() {
            LocalDate aujourdhui = LocalDate.now();
            switch (this) {
                case JOURNALIER:   return aujourdhui.atStartOfDay();
                case HEBDOMADAIRE: return aujourdhui.with(java.time.DayOfWeek.MONDAY).atStartOfDay();
                case MENSUEL:      return aujourdhui.withDayOfMonth(1).atStartOfDay();
                case ANNUEL:       return aujourdhui.withDayOfYear(1).atStartOfDay();
                default:           return aujourdhui.atStartOfDay();
            }
        }

        /**
          * Fin de la période, exclue : le début du lendemain.
          * Une borne à 23:59:59 écartait les ventes de la dernière seconde.
          */
        LocalDateTime fin() {
            return LocalDate.now().plusDays(1).atStartOfDay();
        }
    }

    @FXML
    @SuppressWarnings("unused") // Lié par FXML (onAction="#genererRapportJournalier")
    private void genererRapportJournalier() {
        genererRapport(PeriodeRapport.JOURNALIER);
    }

    @FXML
    @SuppressWarnings("unused") // Lié par FXML (onAction="#genererRapportHebdomadaire")
    private void genererRapportHebdomadaire() {
        genererRapport(PeriodeRapport.HEBDOMADAIRE);
    }

    @FXML
    @SuppressWarnings("unused") // Lié par FXML (onAction="#genererRapportMensuel")
    private void genererRapportMensuel() {
        genererRapport(PeriodeRapport.MENSUEL);
    }

    @FXML
    @SuppressWarnings("unused") // Lié par FXML (onAction="#genererRapportAnnuel")
    private void genererRapportAnnuel() {
        genererRapport(PeriodeRapport.ANNUEL);
    }

    /**
     * Produit un rapport PDF pour la période demandée.
     *
     * Les chiffres viennent de {@code VenteService} : chiffre d'affaires,
     * bénéfice, nombre de ventes et classement des produits. Le fichier est
     * écrit dans le dossier de données de l'application, aux côtés des tickets
     * et des journaux, et son chemin est indiqué à l'utilisateur.
     */
    private void genererRapport(PeriodeRapport periode) {
        LocalDateTime debut = periode.debut();
        LocalDateTime fin = periode.fin();

        // Quatre requêtes puis l'écriture d'un PDF : exécuté sur le fil
        // JavaFX, l'écran restait figé le temps de l'export, et d'autant plus
        // longtemps que la base est sur le réseau.
        ui.TacheFond.executer(btnRapports,
                () -> {
                    BigDecimal ca = venteService.chiffreAffaires(debut, fin);
                    BigDecimal benefice = venteService.benefice(debut, fin);
                    int nbVentes = venteService.nombreVentes(debut, fin);
                    List<model.ProduitStats> topProduits =
                            venteService.topProduits(debut, fin, TOP_PRODUITS_RAPPORT);

                    String horodatage = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                    java.io.File fichier = util.Config.getRapportsDir()
                            .resolve("rapport-" + periode.nomFichier + "-" + horodatage + ".pdf")
                            .toFile();

                    try {
                        util.PDFExporter.exportRapportPeriode(fichier, periode.titre, debut, fin,
                                ca, benefice, nbVentes, topProduits);
                    } catch (IOException e) {
                        // Enveloppée : le traitement de fond ne peut pas
                        // propager d'exception contrôlée. Le message reste
                        // celui destiné à l'utilisateur.
                        throw new exception.ApplicationException(
                                "Le rapport n'a pas pu être écrit sur le disque : " + e.getMessage(), e);
                    }

                    return new RapportGenere(fichier, ca, benefice, nbVentes);
                },
                rapport -> showAlert(Alert.AlertType.INFORMATION, "Rapport généré",
                        "Rapport enregistré :\n" + rapport.fichier().getAbsolutePath()
                        + "\n\nChiffre d'affaires : " + String.format("%.2f DT", rapport.chiffreAffaires())
                        + "\nBénéfice : " + String.format("%.2f DT", rapport.benefice())
                        + "\nVentes : " + rapport.nombreVentes()),
                erreur -> {
                    LOG.error("Génération du rapport {} impossible", periode.nomFichier, erreur);
                    showAlert(Alert.AlertType.ERROR, "Erreur",
                            erreur.getMessage() != null
                                ? erreur.getMessage()
                                : "Le rapport n'a pas pu être généré.");
                });
    }

    /** Résultat d'un rapport : le fichier écrit et ses chiffres clés. */
    private record RapportGenere(java.io.File fichier, BigDecimal chiffreAffaires,
                                 BigDecimal benefice, int nombreVentes) {
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

    public void retourDashboard() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) retourButton.getScene().getWindow();
            util.FXMLUtils.changeScene(stage, "/view/AdminDashboard.fxml", "Dashboard Administrateur");
        } catch (java.io.IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Erreur lors du retour: " + e.getMessage());
        }
    }

    // ========================================
    // CLASSES INTERNES POUR L'AFFICHAGE
    // ========================================

    /**
     * Classe pour l'affichage des ventes dans le tableau
     */
    public static class VenteDisplay {
        private final String id;
        private final String dateHeure;
        private final String montant;
        private final int nbArticles;
        private final String caissier;
        private final int venteId;

        public VenteDisplay(String id, String dateHeure, String montant, int nbArticles, String caissier, int venteId) {
            this.id = id;
            this.dateHeure = dateHeure;
            this.montant = montant;
            this.nbArticles = nbArticles;
            this.caissier = caissier;
            this.venteId = venteId;
        }

        public String getId() { return id; }
        public String getDateHeure() { return dateHeure; }
        public String getMontant() { return montant; }
        public int getNbArticles() { return nbArticles; }
        public String getCaissier() { return caissier; }
        public int getVenteId() { return venteId; }
    }

}

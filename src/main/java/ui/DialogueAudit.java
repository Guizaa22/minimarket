package ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.AuditLog;
import model.Utilisateur;

/**
 * Consultation du journal d'audit.
 *
 * Répond à « qui a modifié ce prix », « qui a supprimé ce produit ». Les
 * données existent depuis la mise en place d'AuditService, mais aucun écran
 * ne permettait de les lire.
 *
 * Filtres combinables : type d'action, employé, période et recherche libre.
 * Le filtrage se fait en mémoire sur le jeu chargé — le volume d'un commerce
 * de proximité le permet largement, et cela évite un aller-retour en base à
 * chaque frappe.
 */
public final class DialogueAudit {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Regroupe les actions techniques sous des libellés lisibles. */
    private static final Map<String, List<String>> FAMILLES = new LinkedHashMap<>();
    static {
        FAMILLES.put("Toutes les actions", List.of());
        FAMILLES.put("Ajout",         List.of("CREATION", "AJOUT"));
        FAMILLES.put("Modification",  List.of("MODIFICATION", "CHANGEMENT", "MAJ"));
        FAMILLES.put("Suppression",   List.of("SUPPRESSION"));
        FAMILLES.put("Vente",         List.of("VENTE"));
        FAMILLES.put("Connexion",     List.of("CONNEXION", "DECONNEXION"));
    }

    private DialogueAudit() {
    }

    /** Ligne du tableau, aplatie pour l'affichage. */
    public static final class Ligne {
        final AuditLog source;
        final String utilisateur;

        Ligne(AuditLog source, String utilisateur) {
            this.source = source;
            this.utilisateur = utilisateur;
        }

        public String getDate() {
            return source.getTimestamp() != null ? source.getTimestamp().format(FORMAT) : "";
        }

        public String getUtilisateur() {
            return utilisateur;
        }

        public String getAction() {
            return source.getAction() != null ? source.getAction().replace('_', ' ') : "";
        }

        public String getEntite() {
            String nom = source.getEntityName() != null ? source.getEntityName() : "";
            return source.getEntityId() != null ? nom + " #" + source.getEntityId() : nom;
        }

        public String getDetails() {
            return source.getDetails() != null ? source.getDetails() : "";
        }

        /** Texte concaténé, pour la recherche libre. */
        String rechercheSur() {
            return (getUtilisateur() + " " + getAction() + " " + getEntite() + " "
                    + getDetails()).toLowerCase();
        }
    }

    /**
     * Ouvre la fenêtre.
     *
     * @param logs         entrées à afficher
     * @param utilisateurs pour convertir un identifiant en nom
     */
    public static void ouvrir(javafx.scene.Node source, List<AuditLog> logs,
                              List<Utilisateur> utilisateurs) {

        Map<Integer, String> nomParId = new LinkedHashMap<>();
        for (Utilisateur u : utilisateurs) {
            nomParId.put(u.getId(), u.getUsername());
        }

        List<Ligne> toutes = new ArrayList<>();
        for (AuditLog log : logs) {
            String nom = log.getUserId() != null
                    ? nomParId.getOrDefault(log.getUserId(), "Utilisateur #" + log.getUserId())
                    : "Compte supprimé";
            toutes.add(new Ligne(log, nom));
        }

        ObservableList<Ligne> affichees = FXCollections.observableArrayList(toutes);

        // --- tableau ------------------------------------------------------
        TableView<Ligne> table = new TableView<>(affichees);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucune entrée ne correspond aux filtres."));

        table.getColumns().add(colonne("Date et heure", Ligne::getDate, 165));
        table.getColumns().add(colonne("Utilisateur", Ligne::getUtilisateur, 140));
        table.getColumns().add(colonne("Action", Ligne::getAction, 175));
        table.getColumns().add(colonne("Élément", Ligne::getEntite, 145));
        table.getColumns().add(colonne("Détails", Ligne::getDetails, 380));

        // --- filtres ------------------------------------------------------
        ComboBox<String> filtreAction = new ComboBox<>(
                FXCollections.observableArrayList(FAMILLES.keySet()));
        filtreAction.setValue("Toutes les actions");

        ObservableList<String> noms = FXCollections.observableArrayList();
        noms.add("Tous les utilisateurs");
        noms.addAll(nomParId.values());
        ComboBox<String> filtreUtilisateur = new ComboBox<>(noms);
        filtreUtilisateur.setValue("Tous les utilisateurs");

        DatePicker du = new DatePicker();
        DatePicker au = new DatePicker();
        du.setPromptText("Du");
        au.setPromptText("Au");

        TextField recherche = new TextField();
        recherche.setPromptText("Rechercher dans les détails…");
        HBox.setHgrow(recherche, Priority.ALWAYS);

        Label compteur = new Label();
        compteur.getStyleClass().add("sous-titre");

        Runnable filtrer = () -> {
            String famille = filtreAction.getValue();
            List<String> prefixes = FAMILLES.getOrDefault(famille, List.of());
            String utilisateur = filtreUtilisateur.getValue();
            LocalDate debut = du.getValue();
            LocalDate fin = au.getValue();
            String texte = recherche.getText() == null ? "" : recherche.getText().trim().toLowerCase();

            List<Ligne> retenues = new ArrayList<>();
            for (Ligne l : toutes) {
                if (!prefixes.isEmpty()) {
                    String action = l.source.getAction() == null ? "" : l.source.getAction().toUpperCase();
                    if (prefixes.stream().noneMatch(action::startsWith)) {
                        continue;
                    }
                }
                if (utilisateur != null && !utilisateur.startsWith("Tous")
                        && !utilisateur.equals(l.getUtilisateur())) {
                    continue;
                }
                LocalDateTime quand = l.source.getTimestamp();
                if (quand != null) {
                    if (debut != null && quand.toLocalDate().isBefore(debut)) {
                        continue;
                    }
                    // Comparaison sur la date seule : saisir le même jour en
                    // début et fin doit inclure toute la journée.
                    if (fin != null && quand.toLocalDate().isAfter(fin)) {
                        continue;
                    }
                }
                if (!texte.isEmpty() && !l.rechercheSur().contains(texte)) {
                    continue;
                }
                retenues.add(l);
            }

            affichees.setAll(retenues);
            compteur.setText(retenues.size() + " entrée(s) sur " + toutes.size());
        };

        filtreAction.setOnAction(e -> filtrer.run());
        filtreUtilisateur.setOnAction(e -> filtrer.run());
        du.setOnAction(e -> filtrer.run());
        au.setOnAction(e -> filtrer.run());
        recherche.textProperty().addListener((o, a, b) -> filtrer.run());

        Button reinitialiser = new Button("Réinitialiser");
        reinitialiser.getStyleClass().addAll("btn", "btn-secondary");
        reinitialiser.setOnAction(e -> {
            filtreAction.setValue("Toutes les actions");
            filtreUtilisateur.setValue("Tous les utilisateurs");
            du.setValue(null);
            au.setValue(null);
            recherche.clear();
            filtrer.run();
        });

        HBox ligne1 = new HBox(10, new Label("Action :"), filtreAction,
                new Label("Utilisateur :"), filtreUtilisateur);
        ligne1.setAlignment(Pos.CENTER_LEFT);

        HBox ligne2 = new HBox(10, new Label("Du :"), du, new Label("Au :"), au,
                recherche, reinitialiser);
        ligne2.setAlignment(Pos.CENTER_LEFT);

        VBox contenu = new VBox(12, ligne1, ligne2, table, compteur);
        contenu.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);
        contenu.setPrefSize(1080, 660);

        Dialog<Void> dialogue = new Dialog<>();
        dialogue.setTitle("Journal d'audit");
        dialogue.setHeaderText("Historique des actions");
        dialogue.getDialogPane().setContent(contenu);
        dialogue.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogue.setResizable(true);
        dialogue.setOnShown(e -> ThemeManager.enregistrer(dialogue.getDialogPane().getScene()));

        filtrer.run();
        dialogue.showAndWait();
    }

    private static TableColumn<Ligne, String> colonne(
            String titre, java.util.function.Function<Ligne, String> extracteur, double largeur) {
        TableColumn<Ligne, String> col = new TableColumn<>(titre);
        col.setCellValueFactory(c -> new SimpleStringProperty(extracteur.apply(c.getValue())));
        col.setPrefWidth(largeur);
        return col;
    }
}

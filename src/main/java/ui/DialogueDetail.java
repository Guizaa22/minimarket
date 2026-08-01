package ui;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Fournisseur;
import model.NoteJour;
import model.PaiementFournisseur;
import model.Produit;
import model.Vente;

/**
 * Détail derrière un indicateur du tableau de bord.
 *
 * Un chiffre seul n'est pas actionnable : « 3 ruptures » appelle aussitôt la
 * question de savoir lesquelles. Chaque carte ouvre donc la liste qu'elle
 * résume, avec un filtre de recherche.
 */
public final class DialogueDetail {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DialogueDetail() {
    }

    // ------------------------------------------------------------------
    // Points d'entrée par type
    // ------------------------------------------------------------------

    public static void produits(Node source, String titre, List<Produit> produits) {
        TableView<Produit> table = new TableView<>();
        table.getColumns().add(colonne("Code-barres", Produit::getCodeBarre, 140));
        table.getColumns().add(colonne("Produit", Produit::getNom, 220));
        table.getColumns().add(colonne("Catégorie", Produit::getCategorie, 140));
        table.getColumns().add(colonne("Stock",
                p -> String.valueOf(p.getQuantiteStock()), 80));
        table.getColumns().add(colonne("Seuil",
                p -> String.valueOf(p.getSeuilAlerte()), 80));
        table.getColumns().add(colonne("Prix vente",
                p -> montant(p.getPrixVenteDefaut()), 120));

        afficher(source, titre, table, FXCollections.observableArrayList(produits),
                p -> (p.getNom() + " " + p.getCodeBarre() + " " + p.getCategorie()).toLowerCase());
    }

    public static void ventes(Node source, String titre, List<Vente> ventes) {
        TableView<Vente> table = new TableView<>();
        table.getColumns().add(colonne("N°", v -> "#" + v.getId(), 70));
        table.getColumns().add(colonne("Date",
                v -> v.getDateVente() != null ? v.getDateVente().format(FORMAT) : "", 145));
        table.getColumns().add(colonne("Total", v -> montant(v.getTotalVente()), 130));
        table.getColumns().add(colonne("Paiement", Vente::getTypePaiement, 110));

        ObservableList<Vente> liste = FXCollections.observableArrayList(ventes);
        BigDecimal total = ventes.stream()
                .map(Vente::getTotalVente)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        afficher(source, titre + "  —  total " + montant(total), table, liste,
                v -> ("#" + v.getId() + " " + v.getTypePaiement()).toLowerCase());
    }

    public static void fournisseurs(Node source, String titre, List<Fournisseur> fournisseurs,
                                    dao.CreditFournisseurDAO creditDAO) {
        TableView<Fournisseur> table = new TableView<>();
        table.getColumns().add(colonne("Fournisseur", Fournisseur::getNom, 200));
        table.getColumns().add(colonne("Téléphone", Fournisseur::getTelephone, 150));
        table.getColumns().add(colonne("E-mail", Fournisseur::getEmail, 190));
        table.getColumns().add(colonne("Crédit", f -> {
            model.CreditFournisseur c = creditDAO.findByFournisseurId(f.getId());
            return c != null ? montant(c.getMontant()) : "0.000 DT";
        }, 130));

        afficher(source, titre, table, FXCollections.observableArrayList(fournisseurs),
                f -> (f.getNom() + " " + (f.getTelephone() == null ? "" : f.getTelephone())).toLowerCase());
    }

    public static void paiements(Node source, String titre, List<PaiementFournisseur> paiements) {
        TableView<PaiementFournisseur> table = new TableView<>();
        table.getColumns().add(colonne("Date",
                p -> p.getDatePaiement() != null ? p.getDatePaiement().format(FORMAT) : "", 145));
        table.getColumns().add(colonne("Montant", p -> montant(p.getMontant()), 130));
        table.getColumns().add(colonne("Notes",
                p -> p.getNotes() == null ? "" : p.getNotes(), 340));

        afficher(source, titre, table, FXCollections.observableArrayList(paiements),
                p -> (p.getNotes() == null ? "" : p.getNotes()).toLowerCase());
    }

    public static void notes(Node source, String titre, List<NoteJour> notes) {
        TableView<NoteJour> table = new TableView<>();
        table.getColumns().add(colonne("Date",
                n -> n.getDateNote() != null ? n.getDateNote().format(FORMAT) : "", 145));
        table.getColumns().add(colonne("Type",
                n -> n.getTypeNote() != null ? n.getTypeNote().name().replace('_', ' ') : "", 130));
        table.getColumns().add(colonne("Montant", n -> montant(n.getMontant()), 120));
        table.getColumns().add(colonne("Description", NoteJour::getDescription, 340));

        afficher(source, titre, table, FXCollections.observableArrayList(notes),
                n -> (n.getDescription() == null ? "" : n.getDescription()).toLowerCase());
    }

    // ------------------------------------------------------------------

    private static <T> void afficher(Node source, String titre, TableView<T> table,
                                     ObservableList<T> donnees,
                                     Function<T, String> rechercheSur) {

        table.setItems(donnees);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucun élément."));

        TextField recherche = new TextField();
        recherche.setPromptText("Rechercher…");
        HBox.setHgrow(recherche, Priority.ALWAYS);

        Label compteur = new Label(donnees.size() + " élément(s)");
        compteur.getStyleClass().add("sous-titre");

        java.util.List<T> toutes = new java.util.ArrayList<>(donnees);
        recherche.textProperty().addListener((o, a, texte) -> {
            String q = texte == null ? "" : texte.trim().toLowerCase();
            if (q.isEmpty()) {
                donnees.setAll(toutes);
            } else {
                donnees.setAll(toutes.stream()
                        .filter(t -> rechercheSur.apply(t).contains(q))
                        .collect(java.util.stream.Collectors.toList()));
            }
            compteur.setText(donnees.size() + " élément(s) sur " + toutes.size());
        });

        HBox barre = new HBox(10, recherche);
        barre.setAlignment(Pos.CENTER_LEFT);

        VBox contenu = new VBox(12, barre, table, compteur);
        contenu.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);
        contenu.setPrefSize(920, 560);

        Dialog<Void> dialogue = new Dialog<>();
        dialogue.setTitle(titre);
        dialogue.setHeaderText(titre);
        dialogue.getDialogPane().setContent(contenu);
        dialogue.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogue.setResizable(true);
        dialogue.setOnShown(e -> ThemeManager.enregistrer(dialogue.getDialogPane().getScene()));
        dialogue.showAndWait();
    }

    private static <T> TableColumn<T, String> colonne(
            String titre, Function<T, String> extracteur, double largeur) {
        TableColumn<T, String> col = new TableColumn<>(titre);
        col.setCellValueFactory(c -> new SimpleStringProperty(
                valeurSure(extracteur, c.getValue())));
        col.setPrefWidth(largeur);
        return col;
    }

    /** Un extracteur qui échoue ne doit pas vider tout le tableau. */
    private static <T> String valeurSure(Function<T, String> extracteur, T valeur) {
        try {
            String s = extracteur.apply(valeur);
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    private static String montant(BigDecimal valeur) {
        return valeur == null ? "0.000 DT" : String.format("%.3f DT", valeur);
    }
}

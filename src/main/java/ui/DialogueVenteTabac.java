package ui;

import java.math.BigDecimal;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Produit;
import model.TypeCategorie;

/**
 * Vente d'un produit de tabac : choix de l'unité puis de la quantité.
 *
 * Le choix « Cigarettes » n'est proposé que si le produit a un prix à la
 * cigarette. Tous les paquets ne se vendent pas au détail, et renseigner ce
 * prix est facultatif : sans lui, le produit ne se vend qu'au paquet et le
 * bouton n'apparaît pas — plutôt que d'apparaître puis d'échouer.
 *
 * Le total est recalculé à chaque changement, ainsi que le nombre de paquets
 * effectivement décrémentés du stock : vendre 25 cigarettes entame deux
 * paquets, ce que le caissier doit voir avant de valider.
 */
public final class DialogueVenteTabac {

    /** Unité retenue et quantité saisie. */
    public static final class Choix {
        public final String uniteVente;   // "paquet" ou "cigarette"
        public final int quantite;
        public final BigDecimal prixUnitaire;
        public final BigDecimal total;
        public final int paquetsConsommes;

        Choix(String uniteVente, int quantite, BigDecimal prixUnitaire,
              BigDecimal total, int paquetsConsommes) {
            this.uniteVente = uniteVente;
            this.quantite = quantite;
            this.prixUnitaire = prixUnitaire;
            this.total = total;
            this.paquetsConsommes = paquetsConsommes;
        }

        public boolean estCigarette() {
            return "cigarette".equals(uniteVente);
        }
    }

    private DialogueVenteTabac() {
    }

    /**
     * Ouvre le dialogue.
     *
     * @return le choix validé, ou vide si annulé
     */
    public static Optional<Choix> ouvrir(Produit produit) {
        if (produit == null) {
            return Optional.empty();
        }

        boolean detailPossible = produit.vendableALaCigarette();

        Dialog<Choix> dialogue = new Dialog<>();
        dialogue.setTitle("Vente tabac");
        dialogue.setHeaderText(produit.getNom());

        // --- état courant -------------------------------------------------
        // Tableau d'un élément : les lambdas ne peuvent pas réaffecter une
        // variable locale.
        final String[] unite = { "paquet" };
        final int[] quantite = { 1 };

        Label lignePrix = new Label();
        lignePrix.getStyleClass().add("sous-titre");

        Label ligneQuantite = new Label();
        ligneQuantite.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");

        Label ligneTotal = new Label();
        ligneTotal.getStyleClass().add("total-label");

        Label ligneStock = new Label();
        ligneStock.getStyleClass().add("sous-titre");

        // --- boutons d'unité ---------------------------------------------
        Button btnPaquet = new Button("Paquet");
        btnPaquet.getStyleClass().addAll("btn", "btn-tactile", "btn-primary");
        HBox.setHgrow(btnPaquet, Priority.ALWAYS);
        btnPaquet.setMaxWidth(Double.MAX_VALUE);

        Button btnCigarette = new Button("Cigarettes");
        btnCigarette.getStyleClass().addAll("btn", "btn-tactile", "btn-secondary");
        HBox.setHgrow(btnCigarette, Priority.ALWAYS);
        btnCigarette.setMaxWidth(Double.MAX_VALUE);
        btnCigarette.setDisable(!detailPossible);

        HBox choixUnite = new HBox(12, btnPaquet);
        if (detailPossible) {
            choixUnite.getChildren().add(btnCigarette);
        }
        choixUnite.setAlignment(Pos.CENTER);

        // --- rafraîchissement --------------------------------------------
        Runnable rafraichir = () -> {
            boolean cigarette = "cigarette".equals(unite[0]);

            BigDecimal prix = cigarette
                    ? produit.getPrixVenteCigarette()
                    : produit.getPrixVenteDefaut();
            BigDecimal total = prix.multiply(BigDecimal.valueOf(quantite[0]));

            int paquets = cigarette
                    ? (quantite[0] + TypeCategorie.CIGARETTES_PAR_PAQUET - 1)
                        / TypeCategorie.CIGARETTES_PAR_PAQUET
                    : quantite[0];

            lignePrix.setText(String.format("%.3f DT / %s", prix,
                    cigarette ? "cigarette" : "paquet"));
            ligneQuantite.setText(quantite[0] + (cigarette ? " cigarette(s)" : " paquet(s)"));
            ligneTotal.setText(String.format("%.3f DT", total));

            if (cigarette) {
                ligneStock.setText(String.format(
                        "Décrémente %d paquet(s) du stock — %d en stock",
                        paquets, produit.getQuantiteStock()));
            } else {
                ligneStock.setText(String.format("%d paquet(s) en stock", produit.getQuantiteStock()));
            }

            btnPaquet.getStyleClass().removeAll("btn-primary", "btn-secondary");
            btnCigarette.getStyleClass().removeAll("btn-primary", "btn-secondary");
            btnPaquet.getStyleClass().add(cigarette ? "btn-secondary" : "btn-primary");
            btnCigarette.getStyleClass().add(cigarette ? "btn-primary" : "btn-secondary");
        };

        btnPaquet.setOnAction(e -> {
            unite[0] = "paquet";
            rafraichir.run();
        });
        btnCigarette.setOnAction(e -> {
            unite[0] = "cigarette";
            rafraichir.run();
        });

        // --- quantité -----------------------------------------------------
        Button moins = new Button("−");
        moins.getStyleClass().addAll("btn", "btn-tactile", "btn-secondary");
        moins.setOnAction(e -> {
            if (quantite[0] > 1) {
                quantite[0]--;
                rafraichir.run();
            }
        });

        Button plus = new Button("+");
        plus.getStyleClass().addAll("btn", "btn-tactile", "btn-secondary");
        plus.setOnAction(e -> {
            quantite[0]++;
            rafraichir.run();
        });

        // Saisie directe : atteindre 40 cigarettes au bouton + demanderait
        // quarante appuis.
        Button saisir = new Button("Saisir…");
        saisir.getStyleClass().addAll("btn", "btn-tactile", "btn-secondary");
        saisir.setOnAction(e -> PaveNumerique
                .demanderEntier("Quantité", produit.getNom(), quantite[0])
                .ifPresent(q -> {
                    quantite[0] = q;
                    rafraichir.run();
                }));

        HBox controlesQuantite = new HBox(12, moins, ligneQuantite, plus, saisir);
        controlesQuantite.setAlignment(Pos.CENTER);

        // --- assemblage ----------------------------------------------------
        VBox contenu = new VBox(16,
                lignePrix,
                choixUnite,
                new javafx.scene.control.Separator(),
                controlesQuantite,
                ligneTotal,
                ligneStock);
        contenu.setPadding(new Insets(20));
        contenu.setAlignment(Pos.CENTER);
        contenu.setPrefWidth(430);

        if (!detailPossible && produit.isTabac()) {
            Label note = new Label("Vente à la cigarette indisponible : "
                    + "aucun prix unitaire n'est défini pour ce produit.");
            note.getStyleClass().add("sous-titre");
            note.setWrapText(true);
            contenu.getChildren().add(note);
        }

        ButtonType ajouter = new ButtonType("Ajouter au panier", ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(ajouter, ButtonType.CANCEL);
        ((Button) dialogue.getDialogPane().lookupButton(ajouter)).getStyleClass().add("btn-primary");
        dialogue.getDialogPane().setContent(contenu);

        rafraichir.run();
        dialogue.setOnShown(e -> ThemeManager.enregistrer(dialogue.getDialogPane().getScene()));

        dialogue.setResultConverter(bouton -> {
            if (bouton != ajouter) {
                return null;
            }
            boolean cigarette = "cigarette".equals(unite[0]);
            BigDecimal prix = cigarette
                    ? produit.getPrixVenteCigarette()
                    : produit.getPrixVenteDefaut();
            int paquets = cigarette
                    ? (quantite[0] + TypeCategorie.CIGARETTES_PAR_PAQUET - 1)
                        / TypeCategorie.CIGARETTES_PAR_PAQUET
                    : quantite[0];
            return new Choix(unite[0], quantite[0], prix,
                    prix.multiply(BigDecimal.valueOf(quantite[0])), paquets);
        });

        return Optional.ofNullable(dialogue.showAndWait().orElse(null));
    }
}

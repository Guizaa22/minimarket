package ui;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Pavé numérique tactile.
 *
 * L'application est utilisée sur écran tactile, sans clavier : toute saisie
 * d'un nombre passe par ce pavé. Les touches font 64 px, un doigt en couvrant
 * environ 45 : en dessous, les erreurs de frappe deviennent fréquentes et le
 * caissier finit par saisir de travers en pleine file d'attente.
 *
 * Deux usages :
 * <pre>
 * PaveNumerique.demanderEntier("Quantité", "Marlboro", 1).ifPresent(...);
 * PaveNumerique.demanderMontant("Prix du paquet", "Marlboro", prix).ifPresent(...);
 * </pre>
 */
public final class PaveNumerique {

    private static final double TOUCHE = 64;
    private static final int LONGUEUR_MAX = 9;

    private PaveNumerique() {
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    /** Saisie d'un entier positif (quantité, seuil...). */
    public static Optional<Integer> demanderEntier(String titre, String contexte, Integer valeurInitiale) {
        return demander(titre, contexte,
                valeurInitiale != null ? String.valueOf(valeurInitiale) : "",
                false,
                texte -> {
                    try {
                        int v = Integer.parseInt(texte);
                        return v > 0 ? v : null;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });
    }

    /** Saisie d'un montant décimal (prix, paiement...). */
    public static Optional<BigDecimal> demanderMontant(String titre, String contexte, BigDecimal valeurInitiale) {
        return demander(titre, contexte,
                valeurInitiale != null ? valeurInitiale.stripTrailingZeros().toPlainString() : "",
                true,
                texte -> {
                    try {
                        BigDecimal v = new BigDecimal(texte);
                        return v.signum() >= 0 ? v : null;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });
    }

    // ------------------------------------------------------------------

    private static <T> Optional<T> demander(String titre, String contexte, String valeurInitiale,
                                            boolean decimal, Function<String, T> conversion) {

        Dialog<T> dialogue = new Dialog<>();
        dialogue.setTitle(titre);
        dialogue.setHeaderText(contexte);

        StringBuilder saisie = new StringBuilder(valeurInitiale != null ? valeurInitiale : "");

        Label affichage = new Label(affiche(saisie));
        affichage.setStyle("-fx-font-size: 38px; -fx-font-weight: bold; "
                + "-fx-alignment: center-right; -fx-padding: 14 20;");
        affichage.setMaxWidth(Double.MAX_VALUE);
        affichage.getStyleClass().add("total-container");

        Label aide = new Label();
        aide.getStyleClass().add("sous-titre");

        ButtonType valider = new ButtonType("Valider", ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(valider, ButtonType.CANCEL);
        Button boutonValider = (Button) dialogue.getDialogPane().lookupButton(valider);
        boutonValider.getStyleClass().add("btn-primary");

        Runnable rafraichir = () -> {
            affichage.setText(affiche(saisie));
            boolean valide = conversion.apply(saisie.toString()) != null;
            boutonValider.setDisable(!valide);
            aide.setText(valide || saisie.length() == 0 ? "" : "Valeur invalide");
        };

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(10);
        grille.setAlignment(Pos.CENTER);

        // Disposition téléphone : 1 en haut à gauche. C'est celle des pavés
        // de terminaux de paiement, donc la plus familière en caisse.
        String[][] touches = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {decimal ? "." : "", "0", "⌫"}
        };

        for (int ligne = 0; ligne < touches.length; ligne++) {
            for (int col = 0; col < touches[ligne].length; col++) {
                String libelle = touches[ligne][col];
                if (libelle.isEmpty()) {
                    continue;
                }
                Button touche = new Button(libelle);
                touche.setPrefSize(TOUCHE, TOUCHE);
                touche.setMinSize(TOUCHE, TOUCHE);
                touche.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
                touche.getStyleClass().add("btn");
                touche.setFocusTraversable(false);

                touche.setOnAction(e -> {
                    appliquerTouche(saisie, libelle, decimal);
                    rafraichir.run();
                });

                grille.add(touche, col, ligne);
            }
        }

        Button effacer = new Button("Effacer");
        effacer.getStyleClass().addAll("btn", "btn-secondary");
        effacer.setPrefHeight(TOUCHE);
        effacer.setMaxWidth(Double.MAX_VALUE);
        effacer.setFocusTraversable(false);
        effacer.setOnAction(e -> {
            saisie.setLength(0);
            rafraichir.run();
        });
        HBox.setHgrow(effacer, Priority.ALWAYS);

        VBox contenu = new VBox(14, affichage, aide, grille, new HBox(effacer));
        contenu.setPadding(new Insets(18));
        contenu.setAlignment(Pos.CENTER);
        contenu.setPrefWidth(320);

        dialogue.getDialogPane().setContent(contenu);
        rafraichir.run();

        dialogue.setResultConverter(bouton ->
                bouton == valider ? conversion.apply(saisie.toString()) : null);

        // Applique le thème courant à la boîte de dialogue : sans cela elle
        // s'afficherait avec l'apparence par défaut de JavaFX, en décalage
        // complet avec le reste de l'application.
        if (dialogue.getDialogPane().getScene() != null) {
            ThemeManager.enregistrer(dialogue.getDialogPane().getScene());
        }
        dialogue.setOnShown(e -> ThemeManager.enregistrer(dialogue.getDialogPane().getScene()));

        return Optional.ofNullable(dialogue.showAndWait().orElse(null));
    }

    private static void appliquerTouche(StringBuilder saisie, String touche, boolean decimal) {
        switch (touche) {
            case "⌫":
                if (saisie.length() > 0) {
                    saisie.setLength(saisie.length() - 1);
                }
                return;
            case ".":
                // Un seul séparateur décimal, et jamais en première position.
                if (decimal && saisie.indexOf(".") < 0) {
                    if (saisie.length() == 0) {
                        saisie.append("0");
                    }
                    saisie.append('.');
                }
                return;
            default:
                if (saisie.length() >= LONGUEUR_MAX) {
                    return;
                }
                // Évite « 007 » : le premier chiffre remplace un zéro isolé.
                if (saisie.length() == 1 && saisie.charAt(0) == '0' && !"0".equals(touche)) {
                    saisie.setLength(0);
                }
                saisie.append(touche);
        }
    }

    private static String affiche(StringBuilder saisie) {
        return saisie.length() == 0 ? "0" : saisie.toString();
    }
}

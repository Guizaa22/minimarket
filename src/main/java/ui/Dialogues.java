package ui;

import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Region;

/**
 * Fabrique unique des boîtes de dialogue.
 *
 * Les {@link Alert} étaient créées à vingt-six endroits différents, chacune
 * avec l'apparence par défaut de JavaFX : fond gris clair et boutons système,
 * en décalage complet avec le reste de l'application, et surtout insensibles
 * au mode sombre. Toutes passent désormais par ici, ce qui garantit une seule
 * apparence et l'application du thème courant.
 *
 * Les messages purement informatifs devraient utiliser {@link Toast}, qui
 * n'interrompt pas la caisse. Ces boîtes restent réservées aux confirmations
 * et aux erreurs qui appellent une décision.
 */
public final class Dialogues {

    private Dialogues() {
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    public static void information(javafx.scene.Node source, String titre, String message) {
        afficher(source, Alert.AlertType.INFORMATION, titre, message);
    }

    public static void avertissement(javafx.scene.Node source, String titre, String message) {
        afficher(source, Alert.AlertType.WARNING, titre, message);
    }

    public static void erreur(javafx.scene.Node source, String titre, String message) {
        afficher(source, Alert.AlertType.ERROR, titre, message);
    }

    /**
     * Demande une confirmation.
     *
     * @param actionDestructive place le bouton de confirmation en rouge et en
     *        fait le choix non par défaut : sur une suppression, le bouton
     *        présélectionné doit être l'annulation.
     */
    public static boolean confirmer(javafx.scene.Node source, String titre, String message,
                                    String libelleConfirmation, boolean actionDestructive) {

        Alert alerte = new Alert(Alert.AlertType.CONFIRMATION);
        alerte.setTitle(titre);
        alerte.setHeaderText(titre);
        alerte.setContentText(message);

        ButtonType confirmer = new ButtonType(
                libelleConfirmation != null ? libelleConfirmation : "Confirmer",
                ButtonBar.ButtonData.OK_DONE);
        ButtonType annuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alerte.getButtonTypes().setAll(confirmer, annuler);

        preparer(alerte.getDialogPane(), source);

        Button boutonConfirmer = (Button) alerte.getDialogPane().lookupButton(confirmer);
        boutonConfirmer.getStyleClass().add(actionDestructive ? "btn-danger" : "btn-primary");
        if (actionDestructive) {
            boutonConfirmer.setDefaultButton(false);
            ((Button) alerte.getDialogPane().lookupButton(annuler)).setDefaultButton(true);
        }

        Optional<ButtonType> reponse = alerte.showAndWait();
        return reponse.isPresent() && reponse.get() == confirmer;
    }

    public static boolean confirmer(javafx.scene.Node source, String titre, String message) {
        return confirmer(source, titre, message, "Confirmer", false);
    }

    // ------------------------------------------------------------------

    private static void afficher(javafx.scene.Node source, Alert.AlertType type,
                                 String titre, String message) {
        Alert alerte = new Alert(type);
        alerte.setTitle(titre);
        alerte.setHeaderText(titre);
        alerte.setContentText(message);
        preparer(alerte.getDialogPane(), source);
        alerte.showAndWait();
    }

    /**
     * Applique le thème et l'apparence commune à un DialogPane.
     *
     * Utilisable directement pour les dialogues personnalisés :
     * {@code Dialogues.preparer(monDialogue.getDialogPane(), source);}
     */
    public static void preparer(DialogPane pane, javafx.scene.Node source) {
        if (pane == null) {
            return;
        }

        pane.getStyleClass().add("dialogue");

        // Le texte long doit s'enrouler plutôt que d'étirer la boîte hors écran.
        pane.setMinWidth(420);
        pane.setMaxWidth(640);
        pane.getChildren().stream()
                .filter(n -> n instanceof javafx.scene.control.Label)
                .forEach(n -> ((javafx.scene.control.Label) n).setWrapText(true));
        pane.setMinHeight(Region.USE_PREF_SIZE);

        // Les boutons reçoivent les styles de l'application. Sans cela ils
        // gardent l'apparence système, seule note discordante d'un écran
        // par ailleurs entièrement thémé.
        pane.getButtonTypes().forEach(bt -> {
            javafx.scene.Node bouton = pane.lookupButton(bt);
            if (bouton instanceof Button) {
                bouton.getStyleClass().add("btn");
                ButtonBar.ButtonData role = bt.getButtonData();
                if (role == ButtonBar.ButtonData.OK_DONE
                        || role == ButtonBar.ButtonData.APPLY
                        || role == ButtonBar.ButtonData.YES) {
                    bouton.getStyleClass().add("btn-primary");
                } else {
                    bouton.getStyleClass().add("btn-secondary");
                }
            }
        });

        // Le thème est appliqué dès que la scène existe. showAndWait() la crée
        // juste avant l'affichage, d'où l'écoute plutôt qu'un accès direct.
        if (pane.getScene() != null) {
            ThemeManager.enregistrer(pane.getScene());
        }
        pane.sceneProperty().addListener((o, avant, scene) -> {
            if (scene != null) {
                ThemeManager.enregistrer(scene);
            }
        });

        // Rattache la boîte à la fenêtre d'où elle est ouverte, pour qu'elle
        // s'affiche centrée dessus et non sur l'écran principal.
        if (source != null && source.getScene() != null
                && pane.getScene() != null
                && pane.getScene().getWindow() instanceof javafx.stage.Stage) {
            ((javafx.stage.Stage) pane.getScene().getWindow())
                    .initOwner(source.getScene().getWindow());
        }
    }

    /** Variante pour les dialogues personnalisés. */
    public static void preparer(Dialog<?> dialogue, javafx.scene.Node source) {
        if (dialogue != null) {
            preparer(dialogue.getDialogPane(), source);
        }
    }
}

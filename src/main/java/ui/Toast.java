package ui;

import java.util.ArrayDeque;
import java.util.Deque;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Notification non bloquante, affichée en surimpression puis effacée seule.
 *
 * Remplace les {@code Alert} de JavaFX pour les messages informatifs : une
 * boîte modale interrompt l'encaissement et oblige le caissier à cliquer,
 * alors qu'un simple « Produit ajouté » n'appelle aucune décision.
 *
 * Les toasts s'empilent en bas à droite, du plus récent au plus ancien, et
 * disparaissent au bout de trois secondes. Un clic les ferme immédiatement.
 * Le rendu passe par un {@link Popup} : aucune modification du graphe de
 * scène n'est nécessaire, la méthode fonctionne donc depuis n'importe quel
 * écran, quel que soit son conteneur racine.
 */
public final class Toast {

    /** Durée d'affichage avant disparition automatique. */
    private static final Duration DUREE_AFFICHAGE = Duration.seconds(3);
    private static final Duration DUREE_ANIMATION = Duration.millis(250);

    /** Marge par rapport au bord de la fenêtre. */
    private static final double MARGE = 24;
    private static final double ESPACEMENT = 10;
    private static final double LARGEUR = 360;

    /** Toasts actuellement visibles, du plus récent au plus ancien. */
    private static final Deque<Popup> visibles = new ArrayDeque<>();

    /** Au-delà, les plus anciens sont retirés pour ne pas couvrir l'écran. */
    private static final int MAX_VISIBLES = 4;

    private Toast() {
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    public static void succes(Node source, String message) {
        afficher(source, message, Style.SUCCES);
    }

    public static void info(Node source, String message) {
        afficher(source, message, Style.INFO);
    }

    /** Alerte de stock, de saisie douteuse : attire l'œil sans bloquer. */
    public static void avertissement(Node source, String message) {
        afficher(source, message, Style.AVERTISSEMENT);
    }

    public static void erreur(Node source, String message) {
        afficher(source, message, Style.ERREUR);
    }

    // ------------------------------------------------------------------

    private enum Style {
        SUCCES("#2E7D32", "#E8F5E9", "✓"),
        INFO("#1565C0", "#E3F2FD", "i"),
        AVERTISSEMENT("#EF6C00", "#FFF3E0", "!"),
        ERREUR("#C62828", "#FFEBEE", "✕");

        final String accent;
        final String fond;
        final String icone;

        Style(String accent, String fond, String icone) {
            this.accent = accent;
            this.fond = fond;
            this.icone = icone;
        }
    }

    private static void afficher(Node source, String message, Style style) {
        if (message == null || message.isBlank()) {
            return;
        }
        // Peut être appelé depuis un Task : on repasse sur le fil JavaFX.
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> afficher(source, message, style));
            return;
        }

        Window fenetre = fenetreDe(source);
        if (fenetre == null || !fenetre.isShowing()) {
            return; // écran non encore affiché : rien à surimprimer
        }

        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setHideOnEscape(true);

        HBox carte = construireCarte(message, style);
        carte.setOnMouseClicked(e -> fermer(popup));
        popup.getContent().add(carte);

        popup.show(fenetre);
        visibles.addFirst(popup);

        // Le popup n'a ses dimensions qu'une fois affiché.
        Platform.runLater(() -> {
            repositionner(fenetre);
            animerEntree(carte);
        });

        while (visibles.size() > MAX_VISIBLES) {
            fermer(visibles.peekLast());
        }

        PauseTransition attente = new PauseTransition(DUREE_AFFICHAGE);
        attente.setOnFinished(e -> fermer(popup));
        attente.play();
    }

    private static HBox construireCarte(String message, Style style) {
        Label icone = new Label(style.icone);
        icone.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white; "
                + "-fx-background-color: " + style.accent + "; "
                + "-fx-background-radius: 50%; -fx-min-width: 26px; -fx-min-height: 26px; "
                + "-fx-alignment: center;");

        Label texte = new Label(message);
        texte.setWrapText(true);
        texte.setMaxWidth(LARGEUR - 90);
        texte.setStyle("-fx-font-size: 13.5px; -fx-text-fill: #1a1a1a;");

        VBox contenu = new VBox(texte);
        contenu.setAlignment(Pos.CENTER_LEFT);

        HBox carte = new HBox(12, icone, contenu);
        carte.setAlignment(Pos.CENTER_LEFT);
        carte.setPadding(new Insets(14, 18, 14, 14));
        carte.setMinWidth(LARGEUR);
        carte.setMaxWidth(LARGEUR);
        carte.setStyle(
                "-fx-background-color: " + style.fond + "; "
              + "-fx-background-radius: 10; "
              + "-fx-border-radius: 10; "
              + "-fx-border-width: 0 0 0 4; "
              + "-fx-border-color: " + style.accent + "; "
              + "-fx-cursor: hand;");

        DropShadow ombre = new DropShadow();
        ombre.setRadius(14);
        ombre.setOffsetY(3);
        ombre.setColor(Color.rgb(0, 0, 0, 0.22));
        carte.setEffect(ombre);

        return carte;
    }

    private static void animerEntree(Node carte) {
        carte.setOpacity(0);
        carte.setTranslateX(40);

        FadeTransition apparition = new FadeTransition(DUREE_ANIMATION, carte);
        apparition.setFromValue(0);
        apparition.setToValue(1);

        TranslateTransition glissement = new TranslateTransition(DUREE_ANIMATION, carte);
        glissement.setFromX(40);
        glissement.setToX(0);
        glissement.setInterpolator(Interpolator.EASE_OUT);

        apparition.play();
        glissement.play();
    }

    private static void fermer(Popup popup) {
        if (popup == null || !popup.isShowing()) {
            return;
        }
        visibles.remove(popup);

        Node carte = popup.getContent().isEmpty() ? null : popup.getContent().get(0);
        if (carte == null) {
            popup.hide();
            return;
        }

        FadeTransition disparition = new FadeTransition(DUREE_ANIMATION, carte);
        disparition.setToValue(0);

        TranslateTransition glissement = new TranslateTransition(DUREE_ANIMATION, carte);
        glissement.setToX(40);
        glissement.setInterpolator(Interpolator.EASE_IN);

        SequentialTransition fin = new SequentialTransition();
        fin.getChildren().addAll();
        disparition.setOnFinished(e -> {
            popup.hide();
            // Les toasts restants remontent pour combler le vide.
            Window fenetre = popup.getOwnerWindow();
            if (fenetre != null) {
                repositionner(fenetre);
            }
        });

        disparition.play();
        glissement.play();
    }

    /** Empile les toasts depuis le bas droit de la fenêtre. */
    private static void repositionner(Window fenetre) {
        double x = fenetre.getX() + fenetre.getWidth() - LARGEUR - MARGE;
        double y = fenetre.getY() + fenetre.getHeight() - MARGE;

        for (Popup popup : visibles) {
            if (!popup.isShowing()) {
                continue;
            }
            double hauteur = popup.getHeight() > 0 ? popup.getHeight() : 60;
            y -= hauteur + ESPACEMENT;
            popup.setX(x);
            popup.setY(y);
        }
    }

    private static Window fenetreDe(Node source) {
        if (source != null && source.getScene() != null) {
            return source.getScene().getWindow();
        }
        // Repli : première fenêtre affichée.
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .findFirst()
                .orElse(null);
    }
}

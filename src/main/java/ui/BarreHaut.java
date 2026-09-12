package ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import model.Utilisateur;
import service.AuthService;
import service.SessionContext;
import util.FXMLUtils;

/**
 * Barre supérieure commune à tous les écrans.
 *
 * Porte le nom de l'enseigne, l'employé connecté et le sélecteur de thème.
 * Construite en Java plutôt qu'en FXML : elle doit être ajoutée à quinze
 * écrans existants, et la dupliquer dans autant de fichiers FXML garantirait
 * qu'ils divergent à la première modification.
 */
public final class BarreHaut {

    private BarreHaut() {
    }

    /**
     * Construit la barre.
     *
     * @param titre titre de l'écran courant
     */
    public static HBox creer(String titre) {
        Label enseigne = new Label("2M MARKET");
        enseigne.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label separateur = new Label("│");
        separateur.setStyle("-fx-text-fill: #4A5A95;");

        Label titreEcran = new Label(titre != null ? titre : "");
        titreEcran.setStyle("-fx-font-size: 15px; -fx-text-fill: #C9CFE8;");

        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);

        HBox barre = new HBox(12, enseigne, separateur, titreEcran);
        barre.getStyleClass().add("barre-haut");
        barre.setAlignment(Pos.CENTER_LEFT);
        barre.setMinHeight(52);

        // Boutons de navigation communs, présents uniquement lorsqu'une session
        // est ouverte : sur l'écran de connexion ils n'auraient aucun sens.
        Utilisateur utilisateur = SessionContext.get().getUtilisateurConnecte();
        if (utilisateur != null) {
            Button accueil = boutonNav("🏠  Accueil", "Revenir à l'écran d'accueil");
            accueil.setOnAction(e -> naviguer(accueil, FXMLUtils::accueil));

            Button retour = boutonNav("←  Retour", "Revenir à l'écran précédent");
            retour.setDisable(!FXMLUtils.peutRevenir());
            retour.setOnAction(e -> naviguer(retour, FXMLUtils::retour));

            Button ajoutProduit = boutonNav("➕  Produit", "Ajouter ou modifier un produit");
            ajoutProduit.setOnAction(e -> naviguer(ajoutProduit,
                    st -> FXMLUtils.changeScene(st, "/view/GestionStock.fxml", "Gestion du Stock")));

            barre.getChildren().addAll(accueil, retour, ajoutProduit);

            // Opérations quotidiennes de l'employé : recette du jour et notes,
            // accessibles depuis n'importe quel écran de l'interface caisse.
            if (!utilisateur.isAdmin()) {
                Button recette = boutonNav("📊  Recette", "Recette du jour");
                recette.setOnAction(e -> naviguer(recette,
                        st -> FXMLUtils.changeScene(st, "/view/RecetteJour.fxml", "Recette du Jour")));

                Button notes = boutonNav("📝  Notes", "Ajouter une note");
                notes.setOnAction(e -> naviguer(notes,
                        st -> FXMLUtils.ouvrirModal("/view/NoteDialog.fxml", "Ajouter une Note", st)));

                barre.getChildren().addAll(recette, notes);
            }
        }

        barre.getChildren().add(espace);

        // Employé connecté, s'il y en a un, puis la déconnexion.
        if (utilisateur != null) {
            Label qui = new Label(utilisateur.getUsername() + "  ·  " + utilisateur.getRole());
            qui.setStyle("-fx-font-size: 14px; -fx-text-fill: #C9CFE8;");
            barre.getChildren().add(qui);

            Button deconnexion = boutonNav("⎋  Déconnexion", "Fermer la session");
            deconnexion.setOnAction(e -> naviguer(deconnexion, st -> {
                new AuthService().deconnecter();
                FXMLUtils.reinitialiserHistorique();
                FXMLUtils.changeScene(st, "/view/Connexion.fxml", "Connexion - 2M Market");
            }));
            barre.getChildren().add(deconnexion);
        }

        barre.getChildren().add(boutonTheme());
        return barre;
    }

    /** Fabrique un bouton de navigation homogène pour la barre. */
    private static Button boutonNav(String texte, String infobulle) {
        Button bouton = new Button(texte);
        bouton.getStyleClass().add("bouton-nav");
        bouton.setFocusTraversable(false);
        bouton.setTooltip(new Tooltip(infobulle));
        return bouton;
    }

    /** Action de navigation susceptible de lever {@link java.io.IOException}. */
    @FunctionalInterface
    private interface ActionNav {
        void executer(Stage stage) throws java.io.IOException;
    }

    /**
     * Exécute une navigation en résolvant la fenêtre depuis le bouton lui-même :
     * la barre n'a ainsi pas besoin de connaître le {@code Stage} à l'avance.
     */
    private static void naviguer(Button source, ActionNav action) {
        try {
            Stage stage = (Stage) source.getScene().getWindow();
            action.executer(stage);
        } catch (java.io.IOException ex) {
            Toast.erreur(source, "Navigation impossible : " + ex.getMessage());
        }
    }

    /**
     * Bouton de bascule sombre / clair.
     * Le libellé indique le mode vers lequel on bascule, pas le mode courant :
     * « ☀ Clair » quand on est en sombre. C'est l'action qui est annoncée.
     */
    public static Button boutonTheme() {
        Button bouton = new Button();
        bouton.getStyleClass().add("bouton-theme");
        bouton.setFocusTraversable(false);
        majLibelle(bouton);

        bouton.setOnAction(e -> {
            ThemeManager.basculer();
            majLibelle(bouton);
            Toast.info(bouton, "Thème " + ThemeManager.getTheme().getLibelle().toLowerCase());
        });

        return bouton;
    }

    private static void majLibelle(Button bouton) {
        ThemeManager.Theme cible = ThemeManager.getTheme().inverse();
        bouton.setText(cible.getIcone() + "  " + cible.getLibelle());
    }
}

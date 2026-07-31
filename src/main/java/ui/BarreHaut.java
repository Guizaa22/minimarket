package ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import model.Utilisateur;
import service.SessionContext;

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

        HBox barre = new HBox(12, enseigne, separateur, titreEcran, espace);
        barre.getStyleClass().add("barre-haut");
        barre.setAlignment(Pos.CENTER_LEFT);
        barre.setMinHeight(52);

        // Employé connecté, s'il y en a un.
        Utilisateur utilisateur = SessionContext.get().getUtilisateurConnecte();
        if (utilisateur != null) {
            Label qui = new Label(utilisateur.getUsername() + "  ·  " + utilisateur.getRole());
            qui.setStyle("-fx-font-size: 14px; -fx-text-fill: #C9CFE8;");
            barre.getChildren().add(qui);
        }

        barre.getChildren().add(boutonTheme());
        return barre;
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

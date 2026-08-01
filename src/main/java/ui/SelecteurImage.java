package ui;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import util.ImageUtil;

/**
 * Sélecteur de photo réutilisable, avec aperçu.
 *
 * Employé dans les formulaires de produit et de catégorie. L'image choisie
 * est immédiatement redimensionnée et recompressée par {@link ImageUtil} :
 * l'aperçu montre donc exactement ce qui sera enregistré, taille comprise,
 * plutôt que le fichier d'origine.
 */
public class SelecteurImage extends VBox {

    private static final double COTE_APERCU = 132;

    private byte[] donnees;
    private String mime;

    private final ImageView apercu = new ImageView();
    private final Label indication = new Label();
    private final Label placeholder = new Label("Aucune photo");

    public SelecteurImage(String titre) {
        super(10);
        setAlignment(Pos.CENTER);

        apercu.setFitWidth(COTE_APERCU);
        apercu.setFitHeight(COTE_APERCU);
        apercu.setPreserveRatio(true);
        apercu.setSmooth(true);

        placeholder.getStyleClass().add("sous-titre");

        StackPane cadre = new StackPane(placeholder, apercu);
        cadre.getStyleClass().add("carte");
        cadre.setPrefSize(COTE_APERCU + 16, COTE_APERCU + 16);
        cadre.setMinSize(COTE_APERCU + 16, COTE_APERCU + 16);

        Button choisir = new Button("Choisir une photo");
        choisir.getStyleClass().addAll("btn", "btn-secondary");
        choisir.setOnAction(e -> choisirFichier(getScene() != null ? getScene().getWindow() : null));

        Button retirer = new Button("Retirer");
        retirer.getStyleClass().addAll("btn", "btn-secondary");
        retirer.setOnAction(e -> definirImage(null, null));

        indication.getStyleClass().add("sous-titre");

        Label libelle = new Label(titre);
        libelle.setStyle("-fx-font-weight: bold;");

        javafx.scene.layout.HBox actions = new javafx.scene.layout.HBox(8, choisir, retirer);
        actions.setAlignment(Pos.CENTER);

        getChildren().addAll(libelle, cadre, actions, indication);
        setPadding(new Insets(4));

        rafraichir();
    }

    // ------------------------------------------------------------------

    private void choisirFichier(Window fenetre) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"));

        File fichier = chooser.showOpenDialog(fenetre);
        if (fichier == null) {
            return;
        }

        ImageUtil.ImagePreparee preparee = ImageUtil.preparer(fichier);
        if (preparee == null) {
            Toast.erreur(this, "Fichier illisible ou trop volumineux (max "
                    + (ImageUtil.TAILLE_FICHIER_MAX / (1024 * 1024)) + " Mo).");
            return;
        }

        definirImage(preparee.donnees, preparee.mime);
        Toast.succes(this, "Photo ajoutée (" + preparee.tailleKo() + " Ko après compression)");
    }

    /** Renseigne l'image affichée et retenue. */
    public final void definirImage(byte[] donnees, String mime) {
        this.donnees = donnees;
        this.mime = mime;
        rafraichir();
    }

    private void rafraichir() {
        Image image = ImageUtil.versImageFx(donnees);
        apercu.setImage(image);
        apercu.setVisible(image != null);
        placeholder.setVisible(image == null);

        indication.setText(donnees == null
                ? "Format libre, redimensionnée à " + ImageUtil.TAILLE_MAX + " px"
                : (donnees.length / 1024) + " Ko");
    }

    public byte[] getDonnees() {
        return donnees;
    }

    public String getMime() {
        return mime;
    }

    public boolean aUneImage() {
        return donnees != null && donnees.length > 0;
    }
}

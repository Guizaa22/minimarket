package ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Déconnexion automatique après une période d'inactivité.
 *
 * Un poste de caisse reste allumé toute la journée : une session laissée
 * ouverte pendant la pause permettait à n'importe qui d'encaisser sous le nom
 * de l'employé parti, et les ventes lui étaient imputées.
 *
 * Le minuteur est unique et vit dans la couche JavaFX, installé sur chaque
 * scène par {@code FXMLUtils} : le recopier dans les quinze contrôleurs
 * garantirait qu'un écran finisse par l'oublier. Toute action de l'utilisateur
 * — touche, clic, molette, tactile — le relance ; à son terme, la session est
 * fermée par {@code AuthService} et l'écran de connexion réapparaît.
 */
public final class VerrouInactivite {

    private static final Logger LOG = LoggerFactory.getLogger(VerrouInactivite.class);

    /** Durée d'inactivité au terme de laquelle la session est fermée. */
    public static final Duration DUREE_INACTIVITE = Duration.minutes(10);

    /**
     * Minuteur unique, partagé par toutes les scènes.
     * Créé paresseusement : l'instancier au chargement de la classe supposerait
     * que la boucle JavaFX tourne déjà, ce qui n'est pas le cas dans les tests.
     */
    private static PauseTransition minuteur;

    /** Scène surveillée, utilisée pour retrouver la fenêtre à l'expiration. */
    private static Scene sceneCourante;

    private VerrouInactivite() {
    }

    /**
     * Surveille une scène et (re)lance le décompte.
     *
     * À appeler à chaque changement d'écran : JavaFX construit une nouvelle
     * scène à chaque navigation, les filtres posés sur la précédente
     * disparaissent avec elle.
     */
    public static void installer(Scene scene) {
        if (scene == null) {
            return;
        }
        sceneCourante = scene;

        // Filtres, et non gestionnaires : un filtre voit l'événement à la
        // descente, avant qu'un contrôle ne le consomme. Un clic sur un bouton
        // doit relancer le décompte, même si le bouton absorbe l'événement.
        scene.addEventFilter(MouseEvent.ANY, e -> reinitialiser());
        scene.addEventFilter(KeyEvent.ANY, e -> reinitialiser());
        scene.addEventFilter(ScrollEvent.ANY, e -> reinitialiser());
        scene.addEventFilter(TouchEvent.ANY, e -> reinitialiser());

        reinitialiser();
    }

    /**
     * Suspend la surveillance.
     * Utilisé sur l'écran de connexion : il n'y a alors pas de session à fermer.
     */
    public static void desactiver() {
        if (minuteur != null) {
            minuteur.stop();
        }
        sceneCourante = null;
    }

    /** Relance le décompte depuis zéro. */
    public static void reinitialiser() {
        if (sceneCourante == null) {
            return;
        }
        minuteur().playFromStart();
    }

    private static PauseTransition minuteur() {
        if (minuteur == null) {
            minuteur = new PauseTransition(DUREE_INACTIVITE);
            minuteur.setOnFinished(e -> verrouiller());
        }
        return minuteur;
    }

    /** Ferme la session et ramène à l'écran de connexion. */
    private static void verrouiller() {
        Scene scene = sceneCourante;
        desactiver();

        if (scene == null || !(scene.getWindow() instanceof Stage)) {
            return;
        }
        if (!service.SessionContext.get().estConnecte()) {
            return;
        }

        LOG.info("Session fermée après {} minutes d'inactivité.", (long) DUREE_INACTIVITE.toMinutes());
        new service.AuthService().deconnecter();

        Stage stage = (Stage) scene.getWindow();
        try {
            util.FXMLUtils.reinitialiserHistorique();
            util.FXMLUtils.changeScene(stage, "/view/Connexion.fxml", "Connexion - 2M Market");
            // Le message est posé sur la nouvelle scène : la précédente est
            // détachée, le toast n'y serait jamais visible.
            if (stage.getScene() != null) {
                Toast.info(stage.getScene().getRoot(),
                        "Session fermée après " + (long) DUREE_INACTIVITE.toMinutes()
                        + " minutes d'inactivité.");
            }
        } catch (java.io.IOException ex) {
            LOG.error("Retour à l'écran de connexion impossible après inactivité", ex);
        }
    }
}

package ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exception.ApplicationException;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.Node;

/**
 * Exécute un traitement en arrière-plan et rend le résultat sur le fil JavaFX.
 *
 * Toutes les requêtes partaient auparavant du fil d'affichage : le temps d'un
 * chargement de produits ou d'un export PDF, l'interface se figeait. Sur une
 * base locale cela restait discret ; dès que la base est sur le réseau, chaque
 * aller-retour se voit.
 *
 * Usage :
 * <pre>
 * TacheFond.executer(bouton,
 *         () -&gt; produitService.listerTous(),
 *         produits -&gt; table.setItems(FXCollections.observableArrayList(produits)));
 * </pre>
 *
 * L'échec est traité par défaut : message d'erreur en toast et trace au
 * journal. Le nœud fourni est désactivé et son curseur passe en attente
 * pendant l'exécution.
 */
public final class TacheFond {

    private static final Logger LOG = LoggerFactory.getLogger(TacheFond.class);

    /**
     * Fils démons : l'application doit pouvoir se fermer même si une requête
     * est encore en cours. Les threads créés à la main dans les contrôleurs
     * étaient non démons et retardaient la fermeture de plusieurs secondes.
     */
    private static final ExecutorService EXECUTEUR = Executors.newFixedThreadPool(3,
            new ThreadFactory() {
                private int compteur = 0;

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "2m-market-fond-" + (++compteur));
                    t.setDaemon(true);
                    return t;
                }
            });

    private TacheFond() {
    }

    /**
     * Lance un traitement en arrière-plan.
     *
     * @param source     nœud désactivé pendant le traitement (peut être null)
     * @param traitement travail à exécuter hors du fil JavaFX
     * @param succes     suite à donner, sur le fil JavaFX
     */
    public static <T> void executer(Node source, Supplier<T> traitement, Consumer<T> succes) {
        executer(source, traitement, succes, null);
    }

    /**
     * Variante avec traitement d'erreur explicite.
     *
     * @param echec appelé sur le fil JavaFX ; si null, un toast d'erreur est affiché
     */
    public static <T> void executer(Node source, Supplier<T> traitement,
                                    Consumer<T> succes, Consumer<Throwable> echec) {

        Task<T> tache = new Task<>() {
            @Override
            protected T call() {
                return traitement.get();
            }
        };

        occuper(source, true);

        tache.setOnSucceeded(e -> {
            occuper(source, false);
            if (succes != null) {
                succes.accept(tache.getValue());
            }
        });

        tache.setOnFailed(e -> {
            occuper(source, false);
            Throwable cause = tache.getException();
            LOG.error("Traitement en arrière-plan échoué", cause);

            if (echec != null) {
                echec.accept(cause);
            } else {
                Toast.erreur(source, messageUtilisateur(cause));
            }
        });

        EXECUTEUR.submit(tache);
    }

    /**
     * Variante sans valeur de retour.
     */
    public static void executer(Node source, Runnable traitement, Runnable succes) {
        executer(source, () -> {
            traitement.run();
            return null;
        }, ignore -> {
            if (succes != null) {
                succes.run();
            }
        });
    }

    /** Arrête le pool. Appelé à la fermeture de l'application. */
    public static void arreter() {
        EXECUTEUR.shutdownNow();
    }

    // ------------------------------------------------------------------

    private static void occuper(Node source, boolean enCours) {
        if (source == null) {
            return;
        }
        source.setDisable(enCours);
        if (source.getScene() != null) {
            source.getScene().setCursor(enCours ? Cursor.WAIT : Cursor.DEFAULT);
        }
    }

    /** Extrait un message présentable, en dépliant l'exception de la tâche. */
    private static String messageUtilisateur(Throwable cause) {
        Throwable t = cause;
        while (t != null) {
            if (t instanceof ApplicationException) {
                return ((ApplicationException) t).getMessageUtilisateur();
            }
            t = t.getCause();
        }
        return "Une erreur est survenue. Consultez le journal pour le détail.";
    }
}

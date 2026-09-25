package app;

/**
 * Point d'entrée du jar autonome.
 *
 * La JVM refuse de démarrer une classe qui étend {@code javafx.application.Application}
 * lorsque JavaFX est fourni par le classpath et non par le module-path : elle
 * s'arrête sur « JavaFX runtime components are missing ». Le jar produit par le
 * plugin shade embarque pourtant bien JavaFX, mais en tant que simples classes.
 *
 * Passer par une classe qui n'étend pas {@code Application} contourne ce
 * contrôle, fait au lancement sur la seule classe principale : JavaFX démarre
 * ensuite normalement depuis le classpath.
 *
 * Le défaut ne se voyait pas en développement, {@code mvn javafx:run} montant
 * lui-même le module-path. Il n'apparaissait qu'une fois l'application portée
 * sur un poste de caisse, sous la forme d'une fenêtre qui ne s'ouvre jamais.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}

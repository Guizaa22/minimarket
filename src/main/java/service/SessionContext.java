package service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.Utilisateur;

/**
 * État partagé de la session en cours : utilisateur connecté et panier.
 *
 * Point d'accès unique remplaçant les données statiques éparpillées dans les
 * contrôleurs (le panier vivait dans {@code CategorieProduitsController},
 * l'utilisateur dans {@code ConnexionController} et dans {@code SessionManager},
 * ces deux derniers pouvant diverger).
 *
 * L'instance par défaut est accessible par {@link #get()} pour les contrôleurs
 * FXML, que JavaFX instancie lui-même ; le constructeur reste public afin que
 * les tests puissent créer un contexte isolé.
 */
public class SessionContext {

    private static final Logger LOG = LoggerFactory.getLogger(SessionContext.class);

    private static SessionContext instance;

    private Utilisateur utilisateurConnecte;
    private final Panier panier = new Panier();

    /** Instance partagée par les contrôleurs FXML. */
    public static synchronized SessionContext get() {
        if (instance == null) {
            instance = new SessionContext();
        }
        return instance;
    }

    /** Réinitialise l'instance partagée. Réservé aux tests. */
    public static synchronized void reset() {
        instance = null;
    }

    // ------------------------------------------------------------------

    public Panier getPanier() {
        return panier;
    }

    public Utilisateur getUtilisateurConnecte() {
        return utilisateurConnecte;
    }

    /** Identifiant de l'utilisateur connecté, ou -1 si aucune session. */
    public int getUtilisateurId() {
        return utilisateurConnecte != null ? utilisateurConnecte.getId() : -1;
    }

    public boolean estConnecte() {
        return utilisateurConnecte != null;
    }

    public boolean estAdmin() {
        return utilisateurConnecte != null
                && utilisateurConnecte.getRole() == Utilisateur.Role.Admin;
    }

    void ouvrirSession(Utilisateur utilisateur) {
        this.utilisateurConnecte = utilisateur;
        panier.vider();
        LOG.info("Session ouverte : {} ({})", utilisateur.getUsername(), utilisateur.getRole());
    }

    void fermerSession() {
        if (utilisateurConnecte != null) {
            LOG.info("Session fermée : {}", utilisateurConnecte.getUsername());
        }
        this.utilisateurConnecte = null;
        // Le panier est vidé à la déconnexion : le conserver ferait basculer
        // des articles d'un employé sur la session du suivant.
        panier.vider();
    }
}

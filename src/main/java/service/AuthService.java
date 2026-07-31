package service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.UtilisateurDAO;
import exception.ApplicationException;
import model.Utilisateur;
import util.SecurityUtil;

/**
 * Authentification et gestion du cycle de vie de la session.
 *
 * Centralise l'ouverture de session, qui était auparavant incomplète :
 * {@code ConnexionController} renseignait son propre champ statique mais
 * n'appelait jamais {@code SessionManager.startSession()}, si bien que
 * {@code getCurrentUserId()} renvoyait -1 dans toute l'application. Les
 * contrôleurs retombaient alors sur un « utilisateur par défaut » et toutes
 * les ventes se retrouvaient attribuées au compte admin.
 */
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    /** Longueur minimale imposée à la création ou au changement de mot de passe. */
    public static final int LONGUEUR_MIN_MOT_DE_PASSE = 8;

    private final UtilisateurDAO utilisateurDAO;
    private final SessionContext session;
    private final AuditService audit;

    public AuthService(UtilisateurDAO utilisateurDAO, SessionContext session, AuditService audit) {
        this.utilisateurDAO = utilisateurDAO;
        this.session = session;
        this.audit = audit;
    }

    public AuthService() {
        this(new UtilisateurDAO(), SessionContext.get(), new AuditService());
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    /**
     * Vérifie les identifiants et ouvre la session.
     *
     * @return l'utilisateur connecté, ou vide si les identifiants sont refusés
     */
    public Optional<Utilisateur> connecter(String username, String motDePasse) {
        if (username == null || username.isBlank() || motDePasse == null || motDePasse.isEmpty()) {
            return Optional.empty();
        }

        Utilisateur utilisateur = utilisateurDAO.authenticate(username.trim(), motDePasse);
        if (utilisateur == null) {
            LOG.warn("Échec de connexion pour « {} »", username.trim());
            return Optional.empty();
        }

        session.ouvrirSession(utilisateur);
        audit.enregistrer(utilisateur.getId(), "CONNEXION", "utilisateurs",
                utilisateur.getId(), "Connexion réussie");
        return Optional.of(utilisateur);
    }

    public void deconnecter() {
        Utilisateur utilisateur = session.getUtilisateurConnecte();
        if (utilisateur != null) {
            audit.enregistrer(utilisateur.getId(), "DECONNEXION", "utilisateurs",
                    utilisateur.getId(), "Déconnexion");
        }
        session.fermerSession();
    }

    public boolean estConnecte() {
        return session.estConnecte();
    }

    public boolean estAdmin() {
        return session.estAdmin();
    }

    public Utilisateur getUtilisateurConnecte() {
        return session.getUtilisateurConnecte();
    }

    /**
     * Identifiant de l'utilisateur connecté.
     *
     * @throws ApplicationException si aucune session n'est ouverte. Volontairement
     *         strict : renvoyer -1 conduisait les appelants à inventer un
     *         utilisateur de repli, ou à ignorer silencieusement l'écriture.
     */
    public int exigerUtilisateurConnecte() {
        int id = session.getUtilisateurId();
        if (id <= 0) {
            throw new ApplicationException(
                    "Aucun utilisateur connecté. Reconnectez-vous pour poursuivre.");
        }
        return id;
    }

    // ------------------------------------------------------------------
    // Comptes
    // ------------------------------------------------------------------

    public List<Utilisateur> listerUtilisateurs() {
        return utilisateurDAO.findAll();
    }

    /**
     * Crée un compte. Le mot de passe est haché avec BCrypt avant écriture.
     */
    public Utilisateur creerCompte(String username, String motDePasse, Utilisateur.Role role) {
        validerIdentifiants(username, motDePasse);

        if (utilisateurDAO.usernameExists(username.trim())) {
            throw new ApplicationException("Un compte nommé « " + username.trim() + " » existe déjà.");
        }

        Utilisateur utilisateur = new Utilisateur(
                username.trim(), SecurityUtil.hashPassword(motDePasse), role);

        if (!utilisateurDAO.create(utilisateur)) {
            throw new ApplicationException("La création du compte a échoué.");
        }

        audit.enregistrer(session.getUtilisateurId(), "CREATION_COMPTE", "utilisateurs",
                utilisateur.getId(), "Compte « " + utilisateur.getUsername() + " » (" + role + ")");
        return utilisateur;
    }

    /**
     * Change le mot de passe d'un compte.
     * Les mots de passe étant hachés, ils ne peuvent être que remplacés.
     */
    public void changerMotDePasse(Utilisateur utilisateur, String nouveauMotDePasse) {
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            throw new ApplicationException("Le mot de passe doit contenir au moins "
                    + LONGUEUR_MIN_MOT_DE_PASSE + " caractères.");
        }

        utilisateur.setPasswordHash(SecurityUtil.hashPassword(nouveauMotDePasse));
        if (!utilisateurDAO.update(utilisateur)) {
            throw new ApplicationException("La mise à jour du mot de passe a échoué.");
        }

        audit.enregistrer(session.getUtilisateurId(), "CHANGEMENT_MOT_DE_PASSE", "utilisateurs",
                utilisateur.getId(), "Mot de passe de « " + utilisateur.getUsername() + " » modifié");
    }

    /**
     * Supprime un compte.
     * Un utilisateur ne peut pas supprimer le sien, ni le dernier administrateur.
     */
    public void supprimerCompte(Utilisateur utilisateur) {
        if (utilisateur.getId() == session.getUtilisateurId()) {
            throw new ApplicationException("Vous ne pouvez pas supprimer votre propre compte.");
        }

        if (utilisateur.getRole() == Utilisateur.Role.Admin && compterAdmins() <= 1) {
            throw new ApplicationException(
                    "Impossible de supprimer le dernier administrateur : "
                    + "plus personne ne pourrait gérer l'application.");
        }

        if (!utilisateurDAO.delete(utilisateur.getId())) {
            throw new ApplicationException(
                    "Suppression impossible : ce compte a probablement des ventes associées.");
        }

        audit.enregistrer(session.getUtilisateurId(), "SUPPRESSION_COMPTE", "utilisateurs",
                utilisateur.getId(), "Compte « " + utilisateur.getUsername() + " » supprimé");
    }

    private long compterAdmins() {
        return utilisateurDAO.findAll().stream()
                .filter(u -> u.getRole() == Utilisateur.Role.Admin)
                .count();
    }

    private void validerIdentifiants(String username, String motDePasse) {
        if (username == null || username.isBlank()) {
            throw new ApplicationException("Le nom d'utilisateur est obligatoire.");
        }
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            throw new ApplicationException("Le mot de passe doit contenir au moins "
                    + LONGUEUR_MIN_MOT_DE_PASSE + " caractères.");
        }
    }
}

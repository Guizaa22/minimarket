package service;

import java.util.Optional;

import model.Utilisateur;

/**
 * Issue d'une tentative de connexion.
 *
 * {@code Optional<Utilisateur>} ne distinguait pas un mot de passe erroné d'un
 * identifiant temporairement bloqué : l'écran de connexion affichait « mot de
 * passe incorrect » à un employé qui, en réalité, devait patienter quelques
 * minutes. Le motif du refus accompagne désormais le résultat.
 */
public final class ResultatConnexion {

    /** Motif d'acceptation ou de refus d'une tentative. */
    public enum Statut {
        /** Identifiants reconnus, session ouverte. */
        SUCCES,
        /** Nom d'utilisateur ou mot de passe erroné. */
        IDENTIFIANTS_INVALIDES,
        /** Trop d'échecs consécutifs : l'identifiant est bloqué pour un temps. */
        COMPTE_BLOQUE,
        /** Un des deux champs est vide. */
        SAISIE_INVALIDE
    }

    private final Statut statut;
    private final Utilisateur utilisateur;
    private final long minutesRestantes;

    private ResultatConnexion(Statut statut, Utilisateur utilisateur, long minutesRestantes) {
        this.statut = statut;
        this.utilisateur = utilisateur;
        this.minutesRestantes = minutesRestantes;
    }

    static ResultatConnexion succes(Utilisateur utilisateur) {
        return new ResultatConnexion(Statut.SUCCES, utilisateur, 0);
    }

    static ResultatConnexion identifiantsInvalides() {
        return new ResultatConnexion(Statut.IDENTIFIANTS_INVALIDES, null, 0);
    }

    static ResultatConnexion bloque(long minutesRestantes) {
        return new ResultatConnexion(Statut.COMPTE_BLOQUE, null, minutesRestantes);
    }

    static ResultatConnexion saisieInvalide() {
        return new ResultatConnexion(Statut.SAISIE_INVALIDE, null, 0);
    }

    public Statut statut() {
        return statut;
    }

    /** L'utilisateur connecté, vide dans tous les cas de refus. */
    public Optional<Utilisateur> utilisateur() {
        return Optional.ofNullable(utilisateur);
    }

    /** Minutes restant à patienter, uniquement pour {@link Statut#COMPTE_BLOQUE}. */
    public long minutesRestantes() {
        return minutesRestantes;
    }

    public boolean estReussi() {
        return statut == Statut.SUCCES;
    }

    /** Message destiné à l'écran de connexion. */
    public String message() {
        switch (statut) {
            case SUCCES:
                return "Connexion réussie.";
            case COMPTE_BLOQUE:
                return "Compte temporairement bloqué, réessayez dans "
                        + minutesRestantes + " minute" + (minutesRestantes > 1 ? "s" : "") + ".";
            case SAISIE_INVALIDE:
                return "Veuillez remplir tous les champs.";
            case IDENTIFIANTS_INVALIDES:
            default:
                return "Nom d'utilisateur ou mot de passe incorrect.";
        }
    }
}

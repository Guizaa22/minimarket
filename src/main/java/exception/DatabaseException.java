package exception;

import java.sql.SQLException;

/**
 * Échec d'accès à la base de données.
 *
 * Traduit les {@link SQLException} en un message compréhensible par un
 * commerçant, sans exposer le détail technique — celui-ci part dans le journal.
 */
public class DatabaseException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    private final String messageUtilisateur;

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
        this.messageUtilisateur = traduire(cause);
    }

    public DatabaseException(String message) {
        super(message);
        this.messageUtilisateur = message;
    }

    @Override
    public String getMessageUtilisateur() {
        return messageUtilisateur;
    }

    /**
     * Convertit un SQLState PostgreSQL en message lisible.
     * Les codes proviennent de la norme SQL et de la documentation PostgreSQL.
     */
    private static String traduire(Throwable cause) {
        if (!(cause instanceof SQLException)) {
            return "Erreur d'accès aux données.";
        }

        String etat = ((SQLException) cause).getSQLState();
        if (etat == null) {
            return "Erreur d'accès aux données.";
        }

        switch (etat) {
            case "23505": // unique_violation
                return "Cette valeur existe déjà (code-barres ou nom en double).";
            case "23503": // foreign_key_violation
                return "Cet élément est utilisé ailleurs et ne peut pas être modifié ou supprimé.";
            case "23514": // check_violation
                return "Valeur non autorisée : vérifiez les quantités et les montants.";
            case "23502": // not_null_violation
                return "Un champ obligatoire est vide.";
            case "28P01":
                return "Identifiants PostgreSQL incorrects.";
            case "3D000":
                return "La base de données est introuvable.";
            case "08001":
            case "08006":
                return "Serveur de base de données injoignable. Vérifiez le réseau.";
            case "42P01":
                return "Table manquante : la base n'a pas été initialisée.";
            case "57014": // query_canceled (statement_timeout)
                return "La requête a pris trop de temps et a été interrompue.";
            default:
                return "Erreur d'accès aux données (code " + etat + ").";
        }
    }
}

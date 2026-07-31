package exception;

/**
 * Racine des exceptions métier de l'application.
 *
 * Ces exceptions portent un message destiné à l'utilisateur final, affichable
 * tel quel dans l'interface. Les DAO se contentaient auparavant d'écrire sur
 * {@code System.err} et de renvoyer {@code null} ou {@code false} : l'interface
 * ne pouvait pas distinguer « aucune donnée » d'un « échec de la requête », ce
 * qui a laissé passer plusieurs défauts en production sans le moindre message.
 */
public class ApplicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ApplicationException(String message) {
        super(message);
    }

    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Message à présenter à l'utilisateur.
     * Redéfini par les sous-classes lorsqu'un libellé plus parlant existe.
     */
    public String getMessageUtilisateur() {
        return getMessage();
    }
}

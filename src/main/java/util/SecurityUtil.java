package util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Classe utilitaire pour la sécurité (hachage de mots de passe)
 */
public class SecurityUtil {

    /**
     * Coût du hachage BCrypt (nombre d'itérations : 2^12).
     *
     * Fixé explicitement plutôt que laissé au défaut de la bibliothèque (10) :
     * le coût doit suivre la puissance des machines, et il est écrit dans
     * chaque empreinte produite. Les mots de passe déjà enregistrés avec un
     * coût inférieur restent vérifiables — BCrypt lit le coût dans l'empreinte
     * elle-même — et prennent la nouvelle valeur au prochain changement.
     */
    private static final int COUT_BCRYPT = 12;

    
    /**
     * Hash un mot de passe en utilisant BCrypt
     * @param password Le mot de passe en clair
     * @return Le hash du mot de passe
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(COUT_BCRYPT));
    }
    
    /**
     * Vérifie si un mot de passe correspond à un hash
     * @param password Le mot de passe en clair
     * @param hash Le hash stocké
     * @return true si le mot de passe correspond, false sinon
     */
    public static boolean checkPassword(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (Exception e) {
            return false;
        }
    }
}


package util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Classe utilitaire pour la sécurité (hachage de mots de passe)
 */
public class SecurityUtil {
    
    /**
     * Hash un mot de passe en utilisant BCrypt
     * @param password Le mot de passe en clair
     * @return Le hash du mot de passe
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
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


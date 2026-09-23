package util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hachage des mots de passe.
 *
 * Les mots de passe ne sont jamais stockés en clair ni relisibles : seules
 * l'empreinte et sa vérification comptent.
 */
@DisplayName("SecurityUtil")
class SecurityUtilTest {

    @Test
    @DisplayName("un mot de passe correct est reconnu")
    void motDePasseCorrectReconnu() {
        String hash = SecurityUtil.hashPassword("motdepasse-solide");
        assertTrue(SecurityUtil.checkPassword("motdepasse-solide", hash));
    }

    @Test
    @DisplayName("un mot de passe erroné est rejeté")
    void motDePasseErroneRejete() {
        String hash = SecurityUtil.hashPassword("motdepasse-solide");
        assertFalse(SecurityUtil.checkPassword("motdepasse-solid", hash));
        assertFalse(SecurityUtil.checkPassword("", hash));
    }

    @Test
    @DisplayName("l'empreinte ne contient jamais le mot de passe")
    void empreinteNeContientPasLeClair() {
        String hash = SecurityUtil.hashPassword("café-crème-2024");
        assertFalse(hash.contains("café-crème-2024"));
    }

    @Test
    @DisplayName("deux hachages du même mot de passe diffèrent (sel aléatoire)")
    void selAleatoire() {
        String a = SecurityUtil.hashPassword("identique");
        String b = SecurityUtil.hashPassword("identique");

        assertNotEquals(a, b, "sans sel, deux comptes au même mot de passe se repéreraient");
        assertTrue(SecurityUtil.checkPassword("identique", a));
        assertTrue(SecurityUtil.checkPassword("identique", b));
    }

    @Test
    @DisplayName("le coût du hachage est inscrit dans l'empreinte")
    void coutInscritDansLEmpreinte() {
        String hash = SecurityUtil.hashPassword("peu-importe");

        // Format BCrypt : $2a$<coût>$<sel+empreinte>
        assertTrue(hash.startsWith("$2a$12$"),
                "le coût retenu doit être 12, et non le défaut 10 : " + hash);
    }

    @Test
    @DisplayName("une empreinte produite avec un coût inférieur reste vérifiable")
    void ancienCoutToujoursVerifiable() {
        // Empreinte au coût 10, tel que la version précédente en produisait :
        // les comptes existants doivent continuer de se connecter après le
        // relèvement du coût.
        String ancienne = org.mindrot.jbcrypt.BCrypt.hashpw("ancien-mot-de-passe",
                org.mindrot.jbcrypt.BCrypt.gensalt(10));

        assertTrue(SecurityUtil.checkPassword("ancien-mot-de-passe", ancienne));
    }

    @Test
    @DisplayName("une empreinte invalide est rejetée sans lever d'exception")
    void empreinteInvalideRejetee() {
        assertFalse(SecurityUtil.checkPassword("peu-importe", "pas-une-empreinte"));
        assertFalse(SecurityUtil.checkPassword("peu-importe", ""));
    }
}

package util;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.DBConnector;

/**
 * Outil console pour redéfinir le mot de passe d'un compte de l'application.
 *
 * À utiliser quand le mot de passe d'un compte a été oublié : les mots de passe
 * sont stockés hachés (BCrypt), ils ne peuvent pas être relus, seulement remplacés.
 *
 * Lancement :
 *   java -cp target/2M-market-1.0-SNAPSHOT.jar util.ResetPassword
 *
 * Le mot de passe saisi n'est ni affiché ni écrit sur disque : il est haché
 * puis enregistré directement en base.
 *
 * Le dialogue avec l'utilisateur passe par {@link #sortie()}, et non par le
 * journal : invites sans retour à la ligne, listes alignées, confirmations —
 * c'est l'interface de l'outil, elle doit rester lisible dans le terminal.
 * Les erreurs sont en revanche doublées dans le journal applicatif, pour
 * qu'une réinitialisation ratée laisse une trace exploitable.
 */
public final class ResetPassword {

    private static final Logger LOG = LoggerFactory.getLogger(ResetPassword.class);

    private static final int MIN_LENGTH = 8;

    /**
     * Sortie du terminal.
     * Passe par la console quand il y en a une, afin que le dialogue et les
     * invites de saisie empruntent le même flux et s'affichent dans l'ordre.
     */
    private static PrintWriter sortie;

    /**
     * Lecteur unique sur l'entrée standard.
     * En créer un par appel ferait perdre les données déjà mises en tampon :
     * la deuxième lecture retournerait une chaîne vide.
     */
    private static BufferedReader stdin;

    private ResetPassword() {
    }

    private static PrintWriter sortie() {
        if (sortie == null) {
            Console console = System.console();
            // Sans console (sortie redirigée, lancement depuis un service), on
            // enveloppe la sortie standard une fois pour toutes, au lieu d'y
            // écrire directement en vingt endroits.
            sortie = console != null ? console.writer() : new PrintWriter(System.out, true);
        }
        return sortie;
    }

    private static BufferedReader stdin() {
        if (stdin == null) {
            stdin = new BufferedReader(new InputStreamReader(System.in));
        }
        return stdin;
    }

    public static void main(String[] args) {
        sortie().println("=== 2M Market - réinitialisation d'un mot de passe ===");
        sortie().println("Base : " + Config.describe());
        sortie().println();

        if (!DBConnector.testConnection()) {
            echecFatal("Connexion à la base impossible. Vérifiez " + Config.getConfigFilePath());
            System.exit(1);
        }

        if (!listAccounts()) {
            System.exit(1);
        }

        Console console = System.console();
        try {
            String username = prompt(console, "Nom d'utilisateur à modifier : ");
            if (username == null || username.isBlank()) {
                echecFatal("Nom d'utilisateur vide. Abandon.");
            }
            username = username.trim();

            if (!accountExists(username)) {
                echecFatal("Aucun compte nommé « " + username + " ».");
            }

            char[] first = promptPassword(console, "Nouveau mot de passe (min. "
                    + MIN_LENGTH + " caractères) : ");
            if (first.length < MIN_LENGTH) {
                Arrays.fill(first, '\0');
                echecFatal("Mot de passe trop court. Abandon.");
            }

            char[] second = promptPassword(console, "Confirmation : ");
            if (!Arrays.equals(first, second)) {
                Arrays.fill(first, '\0');
                Arrays.fill(second, '\0');
                echecFatal("Les deux saisies diffèrent. Abandon.");
            }
            Arrays.fill(second, '\0');

            String hash = SecurityUtil.hashPassword(new String(first));
            Arrays.fill(first, '\0');   // effacer le clair de la mémoire

            if (updatePassword(username, hash)) {
                sortie().println();
                sortie().println("✓ Mot de passe de « " + username + " » mis à jour.");
                sortie().println("  Vous pouvez maintenant vous connecter à l'application.");
                LOG.info("Mot de passe du compte « {} » réinitialisé.", username);
            } else {
                echecFatal("✗ Mise à jour impossible.");
            }

        } catch (IOException e) {
            LOG.error("Erreur de saisie", e);
            echecFatal("Erreur de saisie : " + e.getMessage());
        } finally {
            DBConnector.closeConnection();
        }
    }

    // ------------------------------------------------------------------

    /** Signale une erreur à l'utilisateur et dans le journal applicatif. */
    private static void erreur(String message) {
        sortie().println(message);
        sortie().flush();
        LOG.warn(message);
    }

    /** Signale une erreur puis interrompt l'outil avec un code d'échec. */
    private static void echecFatal(String message) {
        erreur(message);
        DBConnector.closeConnection();
        System.exit(1);
    }

    private static boolean listAccounts() {
        String sql = "SELECT id, username, role FROM utilisateurs ORDER BY id";
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            sortie().println("Comptes existants :");
            boolean any = false;
            while (rs.next()) {
                any = true;
                sortie().printf("  %-20s %s%n", rs.getString("username"), rs.getString("role"));
            }
            sortie().println();
            if (!any) {
                erreur("Aucun compte en base.");
            }
            return any;

        } catch (SQLException e) {
            LOG.error("Lecture des comptes impossible", e);
            erreur("Lecture des comptes impossible : " + DBConnector.diagnose(e));
            return false;
        }
    }

    private static boolean accountExists(String username) {
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM utilisateurs WHERE username = ?")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOG.error("Vérification du compte « {} » impossible", username, e);
            erreur("Vérification impossible : " + e.getMessage());
            return false;
        }
    }

    private static boolean updatePassword(String username, String hash) {
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE utilisateurs SET password_hash = ? WHERE username = ?")) {
            stmt.setString(1, hash);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Mise à jour du mot de passe impossible", e);
            erreur("Mise à jour impossible : " + DBConnector.diagnose(e));
            return false;
        }
    }

    private static String prompt(Console console, String message) throws IOException {
        if (console != null) {
            return console.readLine(message);
        }
        sortie().print(message);
        sortie().flush();
        return stdin().readLine();
    }

    /**
     * Lit un mot de passe sans l'afficher lorsque c'est possible.
     * Sans console (sortie redirigée), la saisie reste visible : l'utilisateur
     * en est averti explicitement.
     */
    private static char[] promptPassword(Console console, String message) throws IOException {
        if (console != null) {
            char[] value = console.readPassword(message);
            return value == null ? new char[0] : value;
        }
        sortie().println("[!] Terminal sans masquage : la saisie sera visible à l'écran.");
        sortie().print(message);
        sortie().flush();
        String line = stdin().readLine();
        return line == null ? new char[0] : line.toCharArray();
    }
}

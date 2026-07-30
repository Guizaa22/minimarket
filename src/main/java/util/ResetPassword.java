package util;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

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
 */
public final class ResetPassword {

    private static final int MIN_LENGTH = 8;

    /**
     * Lecteur unique sur l'entrée standard.
     * En créer un par appel ferait perdre les données déjà mises en tampon :
     * la deuxième lecture retournerait une chaîne vide.
     */
    private static BufferedReader stdin;

    private ResetPassword() {
    }

    private static BufferedReader stdin() {
        if (stdin == null) {
            stdin = new BufferedReader(new InputStreamReader(System.in));
        }
        return stdin;
    }

    public static void main(String[] args) {
        System.out.println("=== 2M Market - réinitialisation d'un mot de passe ===");
        System.out.println("Base : " + Config.describe());
        System.out.println();

        if (!DBConnector.testConnection()) {
            System.err.println("Connexion à la base impossible. Vérifiez "
                    + Config.getConfigFilePath());
            System.exit(1);
        }

        if (!listAccounts()) {
            System.exit(1);
        }

        Console console = System.console();
        try {
            String username = prompt(console, "Nom d'utilisateur à modifier : ");
            if (username == null || username.isBlank()) {
                System.err.println("Nom d'utilisateur vide. Abandon.");
                System.exit(1);
            }
            username = username.trim();

            if (!accountExists(username)) {
                System.err.println("Aucun compte nommé « " + username + " ».");
                System.exit(1);
            }

            char[] first = promptPassword(console, "Nouveau mot de passe (min. "
                    + MIN_LENGTH + " caractères) : ");
            if (first.length < MIN_LENGTH) {
                System.err.println("Mot de passe trop court. Abandon.");
                Arrays.fill(first, '\0');
                System.exit(1);
            }

            char[] second = promptPassword(console, "Confirmation : ");
            if (!Arrays.equals(first, second)) {
                System.err.println("Les deux saisies diffèrent. Abandon.");
                Arrays.fill(first, '\0');
                Arrays.fill(second, '\0');
                System.exit(1);
            }
            Arrays.fill(second, '\0');

            String hash = SecurityUtil.hashPassword(new String(first));
            Arrays.fill(first, '\0');   // effacer le clair de la mémoire

            if (updatePassword(username, hash)) {
                System.out.println();
                System.out.println("✓ Mot de passe de « " + username + " » mis à jour.");
                System.out.println("  Vous pouvez maintenant vous connecter à l'application.");
            } else {
                System.err.println("✗ Mise à jour impossible.");
                System.exit(1);
            }

        } catch (IOException e) {
            System.err.println("Erreur de saisie : " + e.getMessage());
            System.exit(1);
        } finally {
            DBConnector.closeConnection();
        }
    }

    // ------------------------------------------------------------------

    private static boolean listAccounts() {
        String sql = "SELECT id, username, role FROM utilisateurs ORDER BY id";
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("Comptes existants :");
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("  %-20s %s%n", rs.getString("username"), rs.getString("role"));
            }
            System.out.println();
            if (!any) {
                System.err.println("Aucun compte en base.");
            }
            return any;

        } catch (SQLException e) {
            System.err.println("Lecture des comptes impossible : " + DBConnector.diagnose(e));
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
            System.err.println("Vérification impossible : " + e.getMessage());
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
            System.err.println("Mise à jour impossible : " + DBConnector.diagnose(e));
            return false;
        }
    }

    private static String prompt(Console console, String message) throws IOException {
        if (console != null) {
            return console.readLine(message);
        }
        System.out.print(message);
        System.out.flush();
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
        System.out.println("[!] Terminal sans masquage : la saisie sera visible à l'écran.");
        System.out.print(message);
        System.out.flush();
        String line = stdin().readLine();
        return line == null ? new char[0] : line.toCharArray();
    }
}

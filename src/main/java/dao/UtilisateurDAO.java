package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import model.Utilisateur;
import util.SecurityUtil;

/**
 * DAO pour l'authentification et la gestion des utilisateurs
 */
public class UtilisateurDAO {

    /**
     * Authentifie un utilisateur
     * @param username Le nom d'utilisateur
     * @param password Le mot de passe en clair
     * @return L'utilisateur si l'authentification réussit, null sinon
     */
    public Utilisateur authenticate(String username, String password) {
        String sql = "SELECT * FROM utilisateurs WHERE username = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // La colonne dans la base de données est password_hash (snake_case)
                String passwordHash = rs.getString("password_hash");
                if (passwordHash != null && SecurityUtil.checkPassword(password, passwordHash)) {
                    return new Utilisateur(
                        rs.getInt("id"),
                        rs.getString("username"),
                        passwordHash,
                        Utilisateur.Role.valueOf(rs.getString("role"))
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de l'authentification: " + e.getMessage());
        }

        return null;
    }

    /**
     * Récupère un utilisateur par son ID
     * @param id L'ID de l'utilisateur
     * @return L'utilisateur trouvé, null sinon
     */
    public Utilisateur findById(int id) {
        String sql = "SELECT * FROM utilisateurs WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Utilisateur(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    Utilisateur.Role.valueOf(rs.getString("role"))
                );
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la recherche d'utilisateur: " + e.getMessage());
        }

        return null;
    }

    /**
     * Récupère tous les utilisateurs
     * @return Liste de tous les utilisateurs
     */
    public List<Utilisateur> findAll() {
        List<Utilisateur> utilisateurs = new ArrayList<>();
        String sql = "SELECT * FROM utilisateurs ORDER BY username";

        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                utilisateurs.add(new Utilisateur(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    Utilisateur.Role.valueOf(rs.getString("role"))
                ));
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des utilisateurs: " + e.getMessage());
        }

        return utilisateurs;
    }

    /**
     * Crée un nouvel utilisateur
     * @param utilisateur L'utilisateur à créer
     * @return true si la création réussit, false sinon
     */
    public boolean create(Utilisateur utilisateur) {
        String sql = "INSERT INTO utilisateurs (username, password_hash, role) VALUES (?, ?, ?)";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, utilisateur.getUsername());
            stmt.setString(2, utilisateur.getPasswordHash());
            stmt.setString(3, utilisateur.getRole().name());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    utilisateur.setId(rs.getInt(1));
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création d'utilisateur: " + e.getMessage());
        }

        return false;
    }

    /**
     * Met à jour un utilisateur
     * @param utilisateur L'utilisateur à mettre à jour
     * @return true si la mise à jour réussit, false sinon
     */
    public boolean update(Utilisateur utilisateur) {
        String sql = "UPDATE utilisateurs SET username = ?, password_hash = ?, role = ? WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, utilisateur.getUsername());
            stmt.setString(2, utilisateur.getPasswordHash());
            stmt.setString(3, utilisateur.getRole().name());
            stmt.setInt(4, utilisateur.getId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur lors de la mise à jour d'utilisateur: " + e.getMessage());
        }

        return false;
    }

    /**
     * Supprime un utilisateur
     * @param id L'ID de l'utilisateur à supprimer
     * @return true si la suppression réussit, false sinon
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM utilisateurs WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur lors de la suppression d'utilisateur: " + e.getMessage());
        }

        return false;
    }

    /**
     * Vérifie si un nom d'utilisateur existe déjà
     * @param username Le nom d'utilisateur à vérifier
     * @return true si le nom d'utilisateur existe, false sinon
     */
    public boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM utilisateurs WHERE username = ?";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la vérification du nom d'utilisateur: " + e.getMessage());
        }

        return false;
    }
}


package model;

/**
 * Classe POJO pour l'entité Utilisateur
 */
public class Utilisateur {
    private int id;
    private String username;
    private String passwordHash;
    private Role role;
    
    public enum Role {
        Admin, Employé
    }
    
    // Constructeurs
    public Utilisateur() {
    }
    
    public Utilisateur(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    
    public Utilisateur(int id, String username, String passwordHash, Role role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    
    // Getters et Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public boolean isAdmin() {
        return role == Role.Admin;
    }
    
    @Override
    public String toString() {
        return "Utilisateur{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role=" + role +
                '}';
    }
}


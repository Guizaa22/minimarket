package util;

import model.Utilisateur;
import model.Utilisateur.Role;
import java.time.LocalDateTime;

/**
 * Gestionnaire de session pour l'utilisateur connecté
 * Pattern Singleton pour garantir une seule instance
 */
public class SessionManager {

    // Instance unique (Singleton)
    private static SessionManager instance;

    // Utilisateur actuellement connecté
    private Utilisateur currentUser;

    // Date et heure de connexion
    private LocalDateTime loginTime;

    // ID de la session
    private String sessionId;

    /**
     * Constructeur privé (Singleton)
     */
    private SessionManager() {
        this.currentUser = null;
        this.loginTime = null;
        this.sessionId = null;
    }

    /**
     * Obtenir l'instance unique du SessionManager
     * @return Instance du SessionManager
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    /**
     * Démarrer une nouvelle session
     * @param user Utilisateur qui se connecte
     */
    public static void startSession(Utilisateur user) {
        SessionManager manager = getInstance();
        manager.currentUser = user;
        manager.loginTime = LocalDateTime.now();
        manager.sessionId = generateSessionId();

        System.out.println("Session démarrée pour: " + user.getUsername() +
                " (" + user.getRole() + ") à " + manager.loginTime);
    }

    /**
     * Terminer la session actuelle
     */
    public static void endSession() {
        SessionManager manager = getInstance();
        if (manager.currentUser != null) {
            System.out.println("Session terminée pour: " + manager.currentUser.getUsername());
        }
        manager.currentUser = null;
        manager.loginTime = null;
        manager.sessionId = null;
    }

    /**
     * Obtenir l'utilisateur actuellement connecté
     * @return Utilisateur connecté ou null si aucun
     */
    public static Utilisateur getCurrentUser() {
        return getInstance().currentUser;
    }

    /**
     * Obtenir l'ID de l'utilisateur connecté
     * @return ID de l'utilisateur ou -1 si non connecté
     */
    public static int getCurrentUserId() {
        Utilisateur user = getCurrentUser();
        return user != null ? user.getId() : -1;
    }

    /**
     * Obtenir le nom d'utilisateur connecté
     * @return Nom d'utilisateur ou null
     */
    public static String getCurrentUsername() {
        Utilisateur user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    /**
     * Obtenir le rôle de l'utilisateur connecté
     * @return Role (Admin ou Employé) ou null
     */
    public static Role getCurrentUserRole() {
        Utilisateur user = getCurrentUser();
        return user != null ? user.getRole() : null;
    }

    /**
     * Vérifier si un utilisateur est connecté
     * @return true si connecté, false sinon
     */
    public static boolean isLoggedIn() {
        return getInstance().currentUser != null;
    }

    /**
     * Vérifier si l'utilisateur connecté est un administrateur
     * @return true si Admin, false sinon
     */
    public static boolean isAdmin() {
        Utilisateur user = getCurrentUser();
        return user != null && user.getRole() == Role.Admin;
    }

    /**
     * Vérifier si l'utilisateur connecté est un employé
     * @return true si Employé, false sinon
     */
    public static boolean isEmploye() {
        Utilisateur user = getCurrentUser();
        return user != null && user.getRole() == Role.Employé;
    }

    /**
     * Obtenir l'heure de connexion
     * @return Date et heure de connexion
     */
    public static LocalDateTime getLoginTime() {
        return getInstance().loginTime;
    }

    /**
     * Obtenir la durée de la session en minutes
     * @return Durée en minutes
     */
    public static long getSessionDuration() {
        SessionManager manager = getInstance();
        if (manager.loginTime != null) {
            return java.time.Duration.between(manager.loginTime, LocalDateTime.now()).toMinutes();
        }
        return 0;
    }

    /**
     * Obtenir l'ID de session
     * @return ID de session
     */
    public static String getSessionId() {
        return getInstance().sessionId;
    }

    /**
     * Mettre à jour les informations de l'utilisateur connecté
     * @param user Utilisateur avec nouvelles informations
     */
    public static void updateCurrentUser(Utilisateur user) {
        SessionManager manager = getInstance();
        if (manager.currentUser != null && manager.currentUser.getId() == user.getId()) {
            manager.currentUser = user;
            System.out.println("Informations utilisateur mises à jour: " + user.getUsername());
        }
    }

    /**
     * Vérifier si la session est valide (pas expirée)
     * @param maxDurationMinutes Durée maximale en minutes
     * @return true si valide, false si expirée
     */
    public static boolean isSessionValid(long maxDurationMinutes) {
        return isLoggedIn() && getSessionDuration() <= maxDurationMinutes;
    }

    /**
     * Générer un ID de session unique
     * @return ID de session
     */
    private static String generateSessionId() {
        return "SESSION_" + System.currentTimeMillis() + "_" +
                (int)(Math.random() * 10000);
    }

    /**
     * Afficher les informations de la session actuelle
     */
    public static void printSessionInfo() {
        SessionManager manager = getInstance();
        if (manager.currentUser != null) {
            System.out.println("=== Session Info ===");
            System.out.println("User: " + manager.currentUser.getUsername());
            System.out.println("Role: " + manager.currentUser.getRole());
            System.out.println("User ID: " + manager.currentUser.getId());
            System.out.println("Login Time: " + manager.loginTime);
            System.out.println("Session Duration: " + getSessionDuration() + " minutes");
            System.out.println("Session ID: " + manager.sessionId);
            System.out.println("===================");
        } else {
            System.out.println("Aucune session active");
        }
    }

    /**
     * Réinitialiser complètement le SessionManager
     * Utilisé principalement pour les tests
     */
    public static void reset() {
        instance = null;
    }
}
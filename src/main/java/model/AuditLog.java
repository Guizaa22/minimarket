package model;

import java.time.LocalDateTime;

/**
 * Entrée du journal d'audit : une action métier réalisée par un utilisateur.
 */
public class AuditLog {

    private int id;
    /** Auteur ; null si l'utilisateur a été supprimé depuis. */
    private Integer userId;
    private String action;
    private String entityName;
    private Integer entityId;
    private String details;
    private LocalDateTime timestamp;

    public AuditLog() {
    }

    public AuditLog(Integer userId, String action, String entityName,
                    Integer entityId, String details, LocalDateTime timestamp) {
        this.userId = userId;
        this.action = action;
        this.entityName = entityName;
        this.entityId = entityId;
        this.details = details;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AuditLog{" + action + " sur " + entityName + "#" + entityId
                + " par utilisateur " + userId + " le " + timestamp + "}";
    }
}

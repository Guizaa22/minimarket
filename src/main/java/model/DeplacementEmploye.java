package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Classe POJO pour le suivi des déplacements des employés
 */
public class DeplacementEmploye {
    private int id;
    private int idEmploye;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String destination;
    private String notes;
    private BigDecimal heuresTravaillees;
    private LocalDateTime dateCreation;
    
    // Constructeurs
    public DeplacementEmploye() {
    }
    
    public DeplacementEmploye(int idEmploye, LocalDateTime dateDebut, String destination, String notes) {
        this.idEmploye = idEmploye;
        this.dateDebut = dateDebut;
        this.destination = destination;
        this.notes = notes;
    }
    
    public DeplacementEmploye(int id, int idEmploye, LocalDateTime dateDebut, LocalDateTime dateFin,
                             String destination, String notes, BigDecimal heuresTravaillees, LocalDateTime dateCreation) {
        this.id = id;
        this.idEmploye = idEmploye;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.destination = destination;
        this.notes = notes;
        this.heuresTravaillees = heuresTravaillees;
        this.dateCreation = dateCreation;
    }
    
    // Getters et Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getIdEmploye() {
        return idEmploye;
    }
    
    public void setIdEmploye(int idEmploye) {
        this.idEmploye = idEmploye;
    }
    
    public LocalDateTime getDateDebut() {
        return dateDebut;
    }
    
    public void setDateDebut(LocalDateTime dateDebut) {
        this.dateDebut = dateDebut;
    }
    
    public LocalDateTime getDateFin() {
        return dateFin;
    }
    
    public void setDateFin(LocalDateTime dateFin) {
        this.dateFin = dateFin;
    }
    
    public String getDestination() {
        return destination;
    }
    
    public void setDestination(String destination) {
        this.destination = destination;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public BigDecimal getHeuresTravaillees() {
        return heuresTravaillees;
    }
    
    public void setHeuresTravaillees(BigDecimal heuresTravaillees) {
        this.heuresTravaillees = heuresTravaillees;
    }
    
    public LocalDateTime getDateCreation() {
        return dateCreation;
    }
    
    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
    
    /**
     * Calcule les heures travaillées à partir des dates de début et fin
     */
    public void calculerHeuresTravaillees() {
        if (dateDebut != null && dateFin != null && dateFin.isAfter(dateDebut)) {
            long minutes = java.time.Duration.between(dateDebut, dateFin).toMinutes();
            this.heuresTravaillees = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        } else {
            this.heuresTravaillees = BigDecimal.ZERO;
        }
    }
}


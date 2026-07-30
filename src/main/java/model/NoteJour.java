package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Classe POJO pour les notes du jour (crédit, sortie de caisse, etc.)
 */
public class NoteJour {
    private int id;
    private int idEmploye;
    private TypeNote typeNote;
    private BigDecimal montant;
    private String description;
    private LocalDateTime dateNote;
    private LocalDateTime dateCreation;
    
    public enum TypeNote {
        Credit("Crédit"),
        Sortie_Caisse("Sortie de Caisse"),
        Autre("Autre");
        
        private final String libelle;
        
        TypeNote(String libelle) {
            this.libelle = libelle;
        }
        
        public String getLibelle() {
            return libelle;
        }
    }
    
    // Constructeurs
    public NoteJour() {
    }
    
    public NoteJour(int idEmploye, TypeNote typeNote, BigDecimal montant, String description, LocalDateTime dateNote) {
        this.idEmploye = idEmploye;
        this.typeNote = typeNote;
        this.montant = montant != null ? montant : BigDecimal.ZERO;
        this.description = description;
        this.dateNote = dateNote;
    }
    
    public NoteJour(int id, int idEmploye, TypeNote typeNote, BigDecimal montant, String description, 
                   LocalDateTime dateNote, LocalDateTime dateCreation) {
        this.id = id;
        this.idEmploye = idEmploye;
        this.typeNote = typeNote;
        this.montant = montant != null ? montant : BigDecimal.ZERO;
        this.description = description;
        this.dateNote = dateNote;
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
    
    public TypeNote getTypeNote() {
        return typeNote;
    }
    
    public void setTypeNote(TypeNote typeNote) {
        this.typeNote = typeNote;
    }
    
    public BigDecimal getMontant() {
        return montant;
    }
    
    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getDateNote() {
        return dateNote;
    }
    
    public void setDateNote(LocalDateTime dateNote) {
        this.dateNote = dateNote;
    }
    
    public LocalDateTime getDateCreation() {
        return dateCreation;
    }
    
    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
}


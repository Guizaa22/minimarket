package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Classe POJO pour les paiements aux fournisseurs (notes pour argent sorti de caisse)
 */
public class PaiementFournisseur {
    private int id;
    private int idFournisseur;
    private int idEmploye;
    private BigDecimal montant;
    private String notes;
    private LocalDateTime datePaiement;
    private LocalDateTime dateCreation;
    
    // Constructeurs
    public PaiementFournisseur() {
    }
    
    public PaiementFournisseur(int idFournisseur, int idEmploye, BigDecimal montant, String notes, LocalDateTime datePaiement) {
        this.idFournisseur = idFournisseur;
        this.idEmploye = idEmploye;
        this.montant = montant;
        this.notes = notes;
        this.datePaiement = datePaiement;
    }
    
    public PaiementFournisseur(int id, int idFournisseur, int idEmploye, BigDecimal montant, String notes, 
                               LocalDateTime datePaiement, LocalDateTime dateCreation) {
        this.id = id;
        this.idFournisseur = idFournisseur;
        this.idEmploye = idEmploye;
        this.montant = montant;
        this.notes = notes;
        this.datePaiement = datePaiement;
        this.dateCreation = dateCreation;
    }
    
    // Getters et Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getIdFournisseur() {
        return idFournisseur;
    }
    
    public void setIdFournisseur(int idFournisseur) {
        this.idFournisseur = idFournisseur;
    }
    
    public int getIdEmploye() {
        return idEmploye;
    }
    
    public void setIdEmploye(int idEmploye) {
        this.idEmploye = idEmploye;
    }
    
    public BigDecimal getMontant() {
        return montant;
    }
    
    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public LocalDateTime getDatePaiement() {
        return datePaiement;
    }
    
    public void setDatePaiement(LocalDateTime datePaiement) {
        this.datePaiement = datePaiement;
    }
    
    public LocalDateTime getDateCreation() {
        return dateCreation;
    }
    
    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
}


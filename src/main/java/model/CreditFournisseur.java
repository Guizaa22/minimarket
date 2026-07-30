package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Classe POJO pour les crédits avec les fournisseurs
 */
public class CreditFournisseur {
    private int id;
    private int idFournisseur;
    private BigDecimal montant;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
    
    // Constructeurs
    public CreditFournisseur() {
    }
    
    public CreditFournisseur(int idFournisseur, BigDecimal montant) {
        this.idFournisseur = idFournisseur;
        this.montant = montant;
    }
    
    public CreditFournisseur(int id, int idFournisseur, BigDecimal montant, 
                            LocalDateTime dateCreation, LocalDateTime dateModification) {
        this.id = id;
        this.idFournisseur = idFournisseur;
        this.montant = montant;
        this.dateCreation = dateCreation;
        this.dateModification = dateModification;
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
    
    public BigDecimal getMontant() {
        return montant;
    }
    
    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }
    
    public LocalDateTime getDateCreation() {
        return dateCreation;
    }
    
    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
    
    public LocalDateTime getDateModification() {
        return dateModification;
    }
    
    public void setDateModification(LocalDateTime dateModification) {
        this.dateModification = dateModification;
    }
}


package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Classe POJO pour les ajouts de stock avec informations fournisseur
 */
public class AjoutStock {
    private int id;
    private int idProduit;
    private int idEmploye;
    private Integer idFournisseur; // Nullable
    private int quantite;
    private BigDecimal montantPaiement;
    private BigDecimal creditUtilise;
    private String notes;
    private LocalDateTime dateAjout;
    private LocalDateTime dateCreation;
    private String typeAjoutTabac; // "paquet" ou "cigarette" pour les produits tabac
    private Integer quantiteCigarettes; // Nombre de cigarettes si typeAjoutTabac = "cigarette"
    
    // Constructeurs
    public AjoutStock() {
    }
    
    public AjoutStock(int idProduit, int idEmploye, Integer idFournisseur, int quantite,
                     BigDecimal montantPaiement, BigDecimal creditUtilise, String notes, LocalDateTime dateAjout) {
        this(idProduit, idEmploye, idFournisseur, quantite, montantPaiement, creditUtilise, notes, dateAjout, null, null);
    }
    
    public AjoutStock(int idProduit, int idEmploye, Integer idFournisseur, int quantite,
                     BigDecimal montantPaiement, BigDecimal creditUtilise, String notes, LocalDateTime dateAjout,
                     String typeAjoutTabac, Integer quantiteCigarettes) {
        this.idProduit = idProduit;
        this.idEmploye = idEmploye;
        this.idFournisseur = idFournisseur;
        this.quantite = quantite;
        this.montantPaiement = montantPaiement != null ? montantPaiement : BigDecimal.ZERO;
        this.creditUtilise = creditUtilise != null ? creditUtilise : BigDecimal.ZERO;
        this.notes = notes;
        this.dateAjout = dateAjout;
        this.typeAjoutTabac = typeAjoutTabac;
        this.quantiteCigarettes = quantiteCigarettes;
    }
    
    public AjoutStock(int id, int idProduit, int idEmploye, Integer idFournisseur, int quantite,
                     BigDecimal montantPaiement, BigDecimal creditUtilise, String notes, 
                     LocalDateTime dateAjout, LocalDateTime dateCreation) {
        this.id = id;
        this.idProduit = idProduit;
        this.idEmploye = idEmploye;
        this.idFournisseur = idFournisseur;
        this.quantite = quantite;
        this.montantPaiement = montantPaiement != null ? montantPaiement : BigDecimal.ZERO;
        this.creditUtilise = creditUtilise != null ? creditUtilise : BigDecimal.ZERO;
        this.notes = notes;
        this.dateAjout = dateAjout;
        this.dateCreation = dateCreation;
    }
    
    // Getters et Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getIdProduit() {
        return idProduit;
    }
    
    public void setIdProduit(int idProduit) {
        this.idProduit = idProduit;
    }
    
    public int getIdEmploye() {
        return idEmploye;
    }
    
    public void setIdEmploye(int idEmploye) {
        this.idEmploye = idEmploye;
    }
    
    public Integer getIdFournisseur() {
        return idFournisseur;
    }
    
    public void setIdFournisseur(Integer idFournisseur) {
        this.idFournisseur = idFournisseur;
    }
    
    public int getQuantite() {
        return quantite;
    }
    
    public void setQuantite(int quantite) {
        this.quantite = quantite;
    }
    
    public BigDecimal getMontantPaiement() {
        return montantPaiement;
    }
    
    public void setMontantPaiement(BigDecimal montantPaiement) {
        this.montantPaiement = montantPaiement;
    }
    
    public BigDecimal getCreditUtilise() {
        return creditUtilise;
    }
    
    public void setCreditUtilise(BigDecimal creditUtilise) {
        this.creditUtilise = creditUtilise;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public LocalDateTime getDateAjout() {
        return dateAjout;
    }
    
    public void setDateAjout(LocalDateTime dateAjout) {
        this.dateAjout = dateAjout;
    }
    
    public LocalDateTime getDateCreation() {
        return dateCreation;
    }
    
    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
    
    public String getTypeAjoutTabac() {
        return typeAjoutTabac;
    }
    
    public void setTypeAjoutTabac(String typeAjoutTabac) {
        this.typeAjoutTabac = typeAjoutTabac;
    }
    
    public Integer getQuantiteCigarettes() {
        return quantiteCigarettes;
    }
    
    public void setQuantiteCigarettes(Integer quantiteCigarettes) {
        this.quantiteCigarettes = quantiteCigarettes;
    }
    
    /**
     * Calcule le nombre total de cigarettes pour cet ajout de stock
     * @return Nombre total de cigarettes (1 paquet = 20 cigarettes)
     */
    public int getQuantiteEnCigarettes() {
        if ("cigarette".equalsIgnoreCase(typeAjoutTabac)) {
            return quantiteCigarettes != null ? quantiteCigarettes : quantite;
        } else if ("paquet".equalsIgnoreCase(typeAjoutTabac)) {
            return quantite * 20; // 1 paquet = 20 cigarettes
        }
        return quantite; // Par défaut, retourner la quantité normale
    }
}


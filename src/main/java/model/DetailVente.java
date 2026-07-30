package model;

import java.math.BigDecimal;

/**
 * Classe POJO pour l'entité DetailVente
 */
public class DetailVente {
    private int idDetail;
    private int venteId;
    private int produitId;
    private int quantite;
    private BigDecimal prixVenteUnitaire;
    private BigDecimal prixAchatUnitaire;
    
    // Type de vente pour tabac: "paquet" ou "cigarette" (null pour autres produits)
    private String typeVenteTabac; // "paquet" ou "cigarette"
    
    // Pour les produits "frak cigarette": ID du produit tabac associé dont le stock sera décrémenté
    private Integer produitTabacAssocieId;
    
    // Références optionnelles pour faciliter l'affichage
    private Produit produit;
    
    // Constructeurs
    public DetailVente() {
    }
    
    public DetailVente(int venteId, int produitId, int quantite, 
                       BigDecimal prixVenteUnitaire, BigDecimal prixAchatUnitaire) {
        this.venteId = venteId;
        this.produitId = produitId;
        this.quantite = quantite;
        this.prixVenteUnitaire = prixVenteUnitaire;
        this.prixAchatUnitaire = prixAchatUnitaire;
    }
    
    public DetailVente(int idDetail, int venteId, int produitId, int quantite, 
                       BigDecimal prixVenteUnitaire, BigDecimal prixAchatUnitaire) {
        this.idDetail = idDetail;
        this.venteId = venteId;
        this.produitId = produitId;
        this.quantite = quantite;
        this.prixVenteUnitaire = prixVenteUnitaire;
        this.prixAchatUnitaire = prixAchatUnitaire;
    }
    
    // Getters et Setters
    public int getIdDetail() {
        return idDetail;
    }
    
    public void setIdDetail(int idDetail) {
        this.idDetail = idDetail;
    }
    
    public int getVenteId() {
        return venteId;
    }
    
    public void setVenteId(int venteId) {
        this.venteId = venteId;
    }
    
    public int getProduitId() {
        return produitId;
    }
    
    public void setProduitId(int produitId) {
        this.produitId = produitId;
    }
    
    public int getQuantite() {
        return quantite;
    }
    
    public void setQuantite(int quantite) {
        this.quantite = quantite;
    }
    
    public BigDecimal getPrixVenteUnitaire() {
        return prixVenteUnitaire;
    }
    
    public void setPrixVenteUnitaire(BigDecimal prixVenteUnitaire) {
        this.prixVenteUnitaire = prixVenteUnitaire;
    }
    
    public BigDecimal getPrixAchatUnitaire() {
        return prixAchatUnitaire;
    }
    
    public void setPrixAchatUnitaire(BigDecimal prixAchatUnitaire) {
        this.prixAchatUnitaire = prixAchatUnitaire;
    }
    
    public Produit getProduit() {
        return produit;
    }
    
    public void setProduit(Produit produit) {
        this.produit = produit;
    }
    
    public String getTypeVenteTabac() {
        return typeVenteTabac;
    }
    
    public void setTypeVenteTabac(String typeVenteTabac) {
        this.typeVenteTabac = typeVenteTabac;
    }
    
    public Integer getProduitTabacAssocieId() {
        return produitTabacAssocieId;
    }
    
    public void setProduitTabacAssocieId(Integer produitTabacAssocieId) {
        this.produitTabacAssocieId = produitTabacAssocieId;
    }
    
    /**
     * Retourne la quantité en cigarettes (1 paquet = 20 cigarettes)
     */
    public int getQuantiteEnCigarettes() {
        if ("cigarette".equals(typeVenteTabac)) {
            return quantite; // Déjà en cigarettes
        } else if ("paquet".equals(typeVenteTabac)) {
            return quantite * 20; // Convertir paquets en cigarettes
        }
        return quantite; // Par défaut, retourner la quantité normale
    }
    
    public BigDecimal getSousTotal() {
        return prixVenteUnitaire.multiply(BigDecimal.valueOf(quantite));
    }
    
    public BigDecimal getProfit() {
        BigDecimal profitUnitaire = prixVenteUnitaire.subtract(prixAchatUnitaire);
        return profitUnitaire.multiply(BigDecimal.valueOf(quantite));
    }
    
    @Override
    public String toString() {
        return "DetailVente{" +
                "idDetail=" + idDetail +
                ", venteId=" + venteId +
                ", produitId=" + produitId +
                ", quantite=" + quantite +
                ", prixVenteUnitaire=" + prixVenteUnitaire +
                ", prixAchatUnitaire=" + prixAchatUnitaire +
                '}';
    }
}


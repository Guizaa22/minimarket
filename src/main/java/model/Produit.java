package model;

import java.math.BigDecimal;

/**
 * Classe POJO pour l'entité Produit
 */
public class Produit {
    private int id;
    private String codeBarre;
    private String nom;
    private String categorie;
    private BigDecimal prixAchatActuel;
    private BigDecimal prixVenteDefaut;
    private int quantiteStock;
    private String unite; // kg, grammes, unité, litre, etc.
    private int seuilAlerte;
    
    // Constructeurs
    public Produit() {
    }
    
    public Produit(String codeBarre, String nom, String categorie, BigDecimal prixAchatActuel, 
                   BigDecimal prixVenteDefaut, int quantiteStock, String unite, int seuilAlerte) {
        this.codeBarre = codeBarre;
        this.nom = nom;
        this.categorie = categorie;
        this.prixAchatActuel = prixAchatActuel;
        this.prixVenteDefaut = prixVenteDefaut;
        this.quantiteStock = quantiteStock;
        this.unite = unite != null ? unite : "unité";
        this.seuilAlerte = seuilAlerte;
    }
    
    public Produit(int id, String codeBarre, String nom, String categorie, BigDecimal prixAchatActuel, 
                   BigDecimal prixVenteDefaut, int quantiteStock, String unite, int seuilAlerte) {
        this.id = id;
        this.codeBarre = codeBarre;
        this.nom = nom;
        this.categorie = categorie;
        this.prixAchatActuel = prixAchatActuel;
        this.prixVenteDefaut = prixVenteDefaut;
        this.quantiteStock = quantiteStock;
        this.unite = unite != null ? unite : "unité";
        this.seuilAlerte = seuilAlerte;
    }
    
    // Getters et Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getCodeBarre() {
        return codeBarre;
    }
    
    public void setCodeBarre(String codeBarre) {
        this.codeBarre = codeBarre;
    }
    
    public String getNom() {
        return nom;
    }
    
    public void setNom(String nom) {
        this.nom = nom;
    }
    
    public String getCategorie() {
        return categorie;
    }
    
    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }
    
    public BigDecimal getPrixAchatActuel() {
        return prixAchatActuel;
    }
    
    public void setPrixAchatActuel(BigDecimal prixAchatActuel) {
        this.prixAchatActuel = prixAchatActuel;
    }
    
    public BigDecimal getPrixVenteDefaut() {
        return prixVenteDefaut;
    }
    
    public void setPrixVenteDefaut(BigDecimal prixVenteDefaut) {
        this.prixVenteDefaut = prixVenteDefaut;
    }
    
    public int getQuantiteStock() {
        return quantiteStock;
    }
    
    public void setQuantiteStock(int quantiteStock) {
        this.quantiteStock = quantiteStock;
    }
    
    public int getSeuilAlerte() {
        return seuilAlerte;
    }
    
    public void setSeuilAlerte(int seuilAlerte) {
        this.seuilAlerte = seuilAlerte;
    }
    
    public String getUnite() {
        return unite != null ? unite : "unité";
    }
    
    public void setUnite(String unite) {
        this.unite = unite != null ? unite : "unité";
    }
    
    public boolean isStockFaible() {
        return quantiteStock <= seuilAlerte;
    }
    
    /**
     * Vérifie si le produit est de type tabac (tabac, puff, terrea, etc.)
     * Insensible à la casse et aux espaces
     */
    public boolean isTabac() {
        if (categorie == null) return false;
        String catLower = categorie.trim().toLowerCase();
        return catLower.contains("tabac") || catLower.contains("puff") || 
               catLower.contains("terrea") || catLower.contains("cigarette");
    }
    
    /**
     * Vérifie si le produit est un "frak cigarette" (cigarettes vendues à l'unité)
     * Les produits "frak cigarette" sont liés à un produit tabac principal
     */
    public boolean isFrakCigarette() {
        if (categorie == null) return false;
        String catLower = categorie.trim().toLowerCase();
        return catLower.contains("frak") && catLower.contains("cigarette");
    }
    
    @Override
    public String toString() {
        return "Produit{" +
                "id=" + id +
                ", codeBarre='" + codeBarre + '\'' +
                ", nom='" + nom + '\'' +
                ", prixAchatActuel=" + prixAchatActuel +
                ", prixVenteDefaut=" + prixVenteDefaut +
                ", quantiteStock=" + quantiteStock +
                ", seuilAlerte=" + seuilAlerte +
                '}';
    }
}


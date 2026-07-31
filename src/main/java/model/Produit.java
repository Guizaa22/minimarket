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
    /** Renseigné depuis categories.type ; null pour les produits sans catégorie. */
    private TypeCategorie typeCategorie;
    
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
     * Type de la catégorie du produit.
     *
     * Renseigné depuis {@code categories.type} lorsque le produit est rattaché au
     * référentiel. Pour les produits importés sans catégorie, il est déduit du
     * libellé — c'était auparavant le seul mécanisme, ce qui rendait le
     * comportement dépendant de l'orthographe du nom de catégorie.
     */
    public TypeCategorie getTypeCategorie() {
        if (typeCategorie != null) {
            return typeCategorie;
        }
        return TypeCategorie.devinerDepuisLibelle(categorie);
    }

    public void setTypeCategorie(TypeCategorie typeCategorie) {
        this.typeCategorie = typeCategorie;
    }

    /** true si le produit relève du tabac (paquet ou vente à l'unité). */
    public boolean isTabac() {
        return getTypeCategorie().estTabac();
    }

    /**
     * true si le produit correspond à des cigarettes vendues à l'unité.
     * Ces produits n'ont pas de stock propre : la vente décrémente le paquet
     * de tabac associé.
     */
    public boolean isFrakCigarette() {
        return getTypeCategorie() == TypeCategorie.FrakCigarette;
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


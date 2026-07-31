package model;

import java.time.LocalDateTime;

/**
 * Mouvement de stock : toute variation de la quantité d'un produit.
 *
 * {@code quantityChange} est signé : négatif pour une sortie (vente), positif
 * pour une entrée (réapprovisionnement).
 */
public class StockMovement {

    /** Origine du mouvement. */
    public enum Type {
        /** Vente encaissée sur le poste de caisse. */
        DESKTOP_SALE,
        /** Réapprovisionnement saisi depuis l'application mobile. */
        MOBILE_ADD,
        /** Réapprovisionnement saisi au comptoir. */
        DESKTOP_ADD,
        /** Correction manuelle d'inventaire. */
        INVENTORY_ADJUSTMENT,
        /** Remise en stock après annulation d'une vente. */
        SALE_CANCELLED;

        public static Type depuisBase(String valeur) {
            if (valeur == null) {
                return INVENTORY_ADJUSTMENT;
            }
            for (Type type : values()) {
                if (type.name().equalsIgnoreCase(valeur.trim())) {
                    return type;
                }
            }
            return INVENTORY_ADJUSTMENT;
        }
    }

    private int id;
    private int productId;
    /** Auteur ; null si l'utilisateur a été supprimé depuis. */
    private Integer userId;
    private int quantityChange;
    /** Stock résultant, pour reconstituer l'historique sans rejouer les calculs. */
    private Integer stockApres;
    private Type type;
    /** Référence libre : numéro de vente, de ticket... */
    private String reference;
    private LocalDateTime createdAt;

    public StockMovement() {
    }

    public StockMovement(int productId, Integer userId, int quantityChange,
                         Integer stockApres, Type type, String reference,
                         LocalDateTime createdAt) {
        this.productId = productId;
        this.userId = userId;
        this.quantityChange = quantityChange;
        this.stockApres = stockApres;
        this.type = type;
        this.reference = reference;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public Integer getStockApres() {
        return stockApres;
    }

    public void setStockApres(Integer stockApres) {
        this.stockApres = stockApres;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** true s'il s'agit d'une entrée en stock. */
    public boolean estEntree() {
        return quantityChange > 0;
    }

    @Override
    public String toString() {
        return "StockMovement{produit=" + productId
                + ", " + (quantityChange > 0 ? "+" : "") + quantityChange
                + ", " + type + ", " + createdAt + "}";
    }
}

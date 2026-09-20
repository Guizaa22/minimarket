package model;

import java.math.BigDecimal;

/**
 * Statistiques de vente d'un produit : quantité écoulée et chiffre d'affaires.
 *
 * Résidait auparavant dans {@code GestionVentesController}, si bien que
 * {@code DetailVenteDAO} importait une classe du paquet contrôleur : la
 * dépendance remontait la stratification à contresens. Le type est un objet
 * du domaine, sa place est ici.
 */
public class ProduitStats {

    private int rang;
    private final String nomProduit;
    private final int quantiteVendue;
    private final BigDecimal chiffreAffaires;

    public ProduitStats(String nomProduit, int quantiteVendue, BigDecimal ca) {
        this.nomProduit = nomProduit;
        this.quantiteVendue = quantiteVendue;
        this.chiffreAffaires = ca != null ? ca : BigDecimal.ZERO;
    }

    public int getRang() {
        return rang;
    }

    public void setRang(int rang) {
        this.rang = rang;
    }

    public String getNomProduit() {
        return nomProduit;
    }

    public int getQuantiteVendue() {
        return quantiteVendue;
    }

    /** Montant exact, pour tout calcul ou export. */
    public BigDecimal getChiffreAffaires() {
        return chiffreAffaires;
    }

    /** Montant mis en forme, lié directement aux colonnes des tableaux JavaFX. */
    public String getCaGenere() {
        return String.format("%.2f DT", chiffreAffaires);
    }
}

package service;

import java.math.BigDecimal;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.DetailVente;
import model.Produit;

/**
 * Panier de la caisse en cours.
 *
 * Remplace le champ statique {@code CategorieProduitsController.panierGlobal} :
 * le panier vivait à l'intérieur d'un contrôleur d'écran, que trois autres
 * contrôleurs interrogeaient statiquement. Impossible à tester, impossible
 * d'avoir deux caisses, et la durée de vie du panier était celle de la classe
 * plutôt que celle de la session.
 *
 * La liste reste observable : les vues s'y abonnent pour ne rafraîchir que ce
 * qui change.
 */
public class Panier {

    private final ObservableList<DetailVente> lignes = FXCollections.observableArrayList();

    /** Liste observable des lignes, à lier aux vues. */
    public ObservableList<DetailVente> getLignes() {
        return lignes;
    }

    public boolean estVide() {
        return lignes.isEmpty();
    }

    public void vider() {
        lignes.clear();
    }

    /** Nombre total d'articles, toutes lignes confondues. */
    public int getNombreArticles() {
        return lignes.stream().mapToInt(DetailVente::getQuantite).sum();
    }

    /** Montant total du panier. */
    public BigDecimal getTotal() {
        return lignes.stream()
                .map(d -> d.getPrixVenteUnitaire().multiply(BigDecimal.valueOf(d.getQuantite())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Recherche une ligne par produit. */
    public Optional<DetailVente> trouverLigne(int produitId) {
        return lignes.stream()
                .filter(d -> d.getProduitId() == produitId)
                .findFirst();
    }

    /**
     * Ajoute un produit, en regroupant avec la ligne existante s'il y en a une.
     *
     * @param produit  produit vendu
     * @param quantite quantité à ajouter (doit être positive)
     * @return la ligne créée ou mise à jour
     */
    public DetailVente ajouter(Produit produit, int quantite) {
        if (produit == null) {
            throw new IllegalArgumentException("Produit absent");
        }
        if (quantite <= 0) {
            throw new IllegalArgumentException("Quantité invalide : " + quantite);
        }

        Optional<DetailVente> existante = trouverLigne(produit.getId());
        if (existante.isPresent()) {
            DetailVente ligne = existante.get();
            ligne.setQuantite(ligne.getQuantite() + quantite);
            // Remplacement de l'élément : déclenche les listeners de la liste,
            // une simple mutation de l'objet passerait inaperçue.
            lignes.set(lignes.indexOf(ligne), ligne);
            return ligne;
        }

        DetailVente ligne = new DetailVente();
        ligne.setProduitId(produit.getId());
        ligne.setProduit(produit);
        ligne.setQuantite(quantite);
        ligne.setPrixVenteUnitaire(produit.getPrixVenteDefaut());
        ligne.setPrixAchatUnitaire(produit.getPrixAchatActuel());
        lignes.add(ligne);
        return ligne;
    }

    /**
     * Modifie la quantité d'une ligne. Une quantité nulle ou négative retire
     * la ligne du panier.
     */
    public void modifierQuantite(DetailVente ligne, int nouvelleQuantite) {
        if (ligne == null) {
            return;
        }
        if (nouvelleQuantite <= 0) {
            lignes.remove(ligne);
            return;
        }
        ligne.setQuantite(nouvelleQuantite);
        int index = lignes.indexOf(ligne);
        if (index >= 0) {
            lignes.set(index, ligne);
        }
    }

    public void retirer(DetailVente ligne) {
        lignes.remove(ligne);
    }
}

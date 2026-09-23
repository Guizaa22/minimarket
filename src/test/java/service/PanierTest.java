package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import model.DetailVente;
import model.Produit;

/**
 * Panier de la caisse.
 *
 * Le total du panier devient le montant annoncé au client puis encaissé : il
 * est calculé en {@link BigDecimal}, jamais en flottant.
 */
@DisplayName("Panier")
class PanierTest {

    private Panier panier;

    @BeforeEach
    void setUp() {
        panier = new Panier();
    }

    private Produit produit(int id, String nom, String prixAchat, String prixVente) {
        return new Produit(id, "CB" + id, nom, "Divers",
                new BigDecimal(prixAchat), new BigDecimal(prixVente), 100, "unité", 5);
    }

    @Test
    @DisplayName("un panier neuf est vide")
    void panierNeufVide() {
        assertTrue(panier.estVide());
        assertEquals(0, panier.getNombreArticles());
        assertEquals(0, BigDecimal.ZERO.compareTo(panier.getTotal()));
    }

    @Test
    @DisplayName("ajouter un produit crée une ligne au prix de vente du produit")
    void ajoutCreeUneLigne() {
        DetailVente ligne = panier.ajouter(produit(1, "Café", "2.500", "5.000"), 2);

        assertFalse(panier.estVide());
        assertEquals(1, panier.getLignes().size());
        assertEquals(2, ligne.getQuantite());
        assertEquals(0, new BigDecimal("5.000").compareTo(ligne.getPrixVenteUnitaire()));
        assertEquals(0, new BigDecimal("2.500").compareTo(ligne.getPrixAchatUnitaire()),
                "le prix d'achat du jour est figé sur la ligne, il sert au bénéfice");
    }

    @Test
    @DisplayName("ajouter deux fois le même produit regroupe les quantités")
    void ajoutRegroupe() {
        Produit café = produit(1, "Café", "2.500", "5.000");
        panier.ajouter(café, 2);
        panier.ajouter(café, 3);

        assertEquals(1, panier.getLignes().size(), "une seule ligne pour un même produit");
        assertEquals(5, panier.getNombreArticles());
    }

    @Test
    @DisplayName("le total additionne les lignes en BigDecimal")
    void totalExact() {
        panier.ajouter(produit(1, "Café", "2.500", "5.000"), 2);   // 10.000
        panier.ajouter(produit(2, "Thé", "1.200", "3.000"), 3);    //  9.000

        assertEquals(0, new BigDecimal("19.000").compareTo(panier.getTotal()));
    }

    @Test
    @DisplayName("le total reste exact sur des prix non représentables en binaire")
    void totalSansDerivFlottante() {
        // 0,10 et 0,20 ne sont pas représentables exactement en double :
        // trois fois 0,10 y donnerait 0,30000000000000004.
        panier.ajouter(produit(1, "Bonbon", "0.050", "0.100"), 3);

        assertEquals(0, new BigDecimal("0.300").compareTo(panier.getTotal()),
                "aucun calcul monétaire ne doit passer par un flottant");
    }

    @Test
    @DisplayName("une quantité nulle ou négative est refusée")
    void quantiteInvalideRefusee() {
        Produit café = produit(1, "Café", "2.500", "5.000");

        assertThrows(IllegalArgumentException.class, () -> panier.ajouter(café, 0));
        assertThrows(IllegalArgumentException.class, () -> panier.ajouter(café, -1));
        assertTrue(panier.estVide());
    }

    @Test
    @DisplayName("un produit absent est refusé")
    void produitAbsentRefuse() {
        assertThrows(IllegalArgumentException.class, () -> panier.ajouter(null, 1));
    }

    @Test
    @DisplayName("modifier une quantité met à jour le total")
    void modificationQuantite() {
        DetailVente ligne = panier.ajouter(produit(1, "Café", "2.500", "5.000"), 2);

        panier.modifierQuantite(ligne, 4);

        assertEquals(4, panier.getNombreArticles());
        assertEquals(0, new BigDecimal("20.000").compareTo(panier.getTotal()));
    }

    @Test
    @DisplayName("passer une quantité à zéro retire la ligne")
    void quantiteNulleRetireLaLigne() {
        DetailVente ligne = panier.ajouter(produit(1, "Café", "2.500", "5.000"), 2);

        panier.modifierQuantite(ligne, 0);

        assertTrue(panier.estVide());
    }

    @Test
    @DisplayName("retirer une ligne la supprime du panier")
    void retraitLigne() {
        DetailVente café = panier.ajouter(produit(1, "Café", "2.500", "5.000"), 2);
        panier.ajouter(produit(2, "Thé", "1.200", "3.000"), 1);

        panier.retirer(café);

        assertEquals(1, panier.getLignes().size());
        assertEquals(0, new BigDecimal("3.000").compareTo(panier.getTotal()));
    }

    @Test
    @DisplayName("vider le panier le remet à zéro")
    void videRemetAZero() {
        panier.ajouter(produit(1, "Café", "2.500", "5.000"), 2);

        panier.vider();

        assertTrue(panier.estVide());
        assertEquals(0, BigDecimal.ZERO.compareTo(panier.getTotal()));
    }

    @Test
    @DisplayName("trouverLigne retrouve un produit déjà au panier")
    void trouverLigne() {
        panier.ajouter(produit(7, "Café", "2.500", "5.000"), 1);

        assertTrue(panier.trouverLigne(7).isPresent());
        assertTrue(panier.trouverLigne(8).isEmpty());
    }
}

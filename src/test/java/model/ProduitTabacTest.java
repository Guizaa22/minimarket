package model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vente du tabac au paquet ou à la cigarette.
 *
 * La vente à l'unité est facultative et se décide produit par produit : tous
 * les paquets ne se vendent pas au détail. Renseigner un prix cigarette
 * l'active, le laisser vide la désactive.
 */
@DisplayName("Produit — tarification du tabac")
class ProduitTabacTest {

    private Produit tabac(String prixPaquet, String prixCigarette) {
        Produit p = new Produit(1, "CB1", "Marlboro", "Tabac",
                new BigDecimal("5.000"), new BigDecimal(prixPaquet), 10, "unité", 2);
        p.setTypeCategorie(TypeCategorie.Tabac);
        if (prixCigarette != null) {
            p.setPrixVenteCigarette(new BigDecimal(prixCigarette));
        }
        return p;
    }

    @Test
    @DisplayName("Sans prix cigarette, le produit ne se vend qu'au paquet")
    void sansPrixCigaretteVenteAuPaquetSeulement() {
        Produit p = tabac("8.500", null);

        assertTrue(p.isTabac());
        assertFalse(p.vendableALaCigarette(),
                "l'option cigarette ne doit pas être proposée sans prix unitaire");
        assertEquals(0, new BigDecimal("8.500").compareTo(p.prixPour("paquet")));
    }

    @Test
    @DisplayName("Un prix cigarette à zéro n'active pas la vente à l'unité")
    void prixZeroNActivePas() {
        assertFalse(tabac("8.500", "0").vendableALaCigarette(),
                "un prix nul revient à ne pas vendre à l'unité");
    }

    @Test
    @DisplayName("Avec un prix cigarette, la vente à l'unité est possible")
    void avecPrixCigaretteVenteALUnite() {
        Produit p = tabac("8.500", "0.600");

        assertTrue(p.vendableALaCigarette());
        assertEquals(0, new BigDecimal("0.600").compareTo(p.prixPour("cigarette")));
        assertEquals(0, new BigDecimal("8.500").compareTo(p.prixPour("paquet")));
    }

    @Test
    @DisplayName("Demander un prix cigarette inexistant est refusé, sans repli sur paquet/20")
    void refusePrixCigaretteInexistant() {
        Produit p = tabac("8.500", null);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> p.prixPour("cigarette"));

        assertTrue(e.getMessage().contains("paquet"),
                "le message doit expliquer que le produit ne se vend qu'au paquet : "
                + e.getMessage());

        // Un repli sur paquet / 20 factureraait un tarif que le commerçant
        // n'a jamais fixé — d'où le refus explicite.
    }

    @Test
    @DisplayName("Un produit non tabac n'est jamais vendable à la cigarette")
    void produitStandardJamaisALaCigarette() {
        Produit p = new Produit(2, "CB2", "Café", "Alimentaire",
                new BigDecimal("2.000"), new BigDecimal("5.000"), 10, "unité", 2);
        p.setTypeCategorie(TypeCategorie.Standard);
        // Même avec un prix renseigné par erreur.
        p.setPrixVenteCigarette(new BigDecimal("0.500"));

        assertFalse(p.vendableALaCigarette());
    }

    @Test
    @DisplayName("Le total cigarettes se calcule sur le prix unitaire, pas sur le paquet")
    void totalCigarettes() {
        Produit p = tabac("8.500", "0.600");

        BigDecimal total = p.prixPour("cigarette").multiply(BigDecimal.valueOf(7));
        assertEquals(0, new BigDecimal("4.200").compareTo(total),
                "7 cigarettes à 0.600 = 4.200 DT");
    }

    @Test
    @DisplayName("Les paquets entamés sont arrondis au supérieur")
    void paquetsEntames() {
        int parPaquet = TypeCategorie.CIGARETTES_PAR_PAQUET;
        assertEquals(20, parPaquet);

        // 25 cigarettes entament 2 paquets, 20 en entament 1.
        assertEquals(1, (20 + parPaquet - 1) / parPaquet);
        assertEquals(2, (21 + parPaquet - 1) / parPaquet);
        assertEquals(2, (25 + parPaquet - 1) / parPaquet);
        assertEquals(1, (7 + parPaquet - 1) / parPaquet);
    }
}

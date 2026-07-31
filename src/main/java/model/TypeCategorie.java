package model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comportement métier d'une catégorie de produits.
 *
 * Remplace la déduction par le libellé : une catégorie était auparavant traitée
 * comme du tabac si son nom contenait « tabac », « puff », « terrea » ou
 * « cigarette ». La renommer — ou en créer une nommée « Cigarettes détail » —
 * modifiait donc silencieusement la façon dont le stock était décrémenté.
 */
public enum TypeCategorie {

    /** Produit ordinaire : le stock est décrémenté de la quantité vendue. */
    Standard("Standard"),

    /** Tabac vendu au paquet ; peut être décliné en vente à l'unité. */
    Tabac("Tabac"),

    /**
     * Cigarettes vendues à l'unité. Ces produits n'ont pas de stock propre :
     * la vente décrémente le paquet de tabac associé, à raison de
     * {@link #CIGARETTES_PAR_PAQUET} cigarettes par paquet.
     */
    FrakCigarette("Frak cigarette");

    private static final Logger LOG = LoggerFactory.getLogger(TypeCategorie.class);

    /** Nombre de cigarettes contenues dans un paquet. */
    public static final int CIGARETTES_PAR_PAQUET = 20;

    private final String libelle;

    TypeCategorie(String libelle) {
        this.libelle = libelle;
    }

    /** Libellé lisible, pour l'interface. */
    public String getLibelle() {
        return libelle;
    }

    /** true pour les catégories relevant du tabac (paquet ou unité). */
    public boolean estTabac() {
        return this == Tabac || this == FrakCigarette;
    }

    /**
     * Convertit la valeur stockée en base, en retombant sur {@link #Standard}
     * si elle est absente ou inconnue.
     */
    public static TypeCategorie depuisBase(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return Standard;
        }
        for (TypeCategorie type : values()) {
            if (type.name().equalsIgnoreCase(valeur.trim())) {
                return type;
            }
        }
        LOG.warn("Type de catégorie inconnu en base : {} — traité comme Standard", valeur);
        return Standard;
    }

    /**
     * Devine le type à partir d'un libellé.
     *
     * Uniquement pour les produits rattachés à aucune catégorie du référentiel
     * (données importées). Le type explicite doit toujours primer.
     */
    public static TypeCategorie devinerDepuisLibelle(String libelle) {
        if (libelle == null) {
            return Standard;
        }
        String texte = libelle.trim().toLowerCase();
        if (texte.contains("frak") && texte.contains("cigarette")) {
            return FrakCigarette;
        }
        if (texte.contains("tabac") || texte.contains("puff")
                || texte.contains("terrea") || texte.contains("cigarette")) {
            return Tabac;
        }
        return Standard;
    }

    @Override
    public String toString() {
        return libelle;
    }
}

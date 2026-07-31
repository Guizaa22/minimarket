package service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.ProduitDAO;
import dao.StockMovementDAO;
import exception.ApplicationException;
import model.Produit;
import model.StockMovement;

/**
 * Règles métier autour des produits et du stock.
 *
 * Les contrôleurs passaient directement par les DAO : les validations étaient
 * dupliquées d'un écran à l'autre, ou absentes. Le contrôle du couple prix
 * d'achat / prix de vente, par exemple, n'existait que dans l'écran de gestion
 * du stock, et seules les valeurs négatives étaient rejetées.
 */
public class ProduitService {

    private static final Logger LOG = LoggerFactory.getLogger(ProduitService.class);

    /** Au-delà de ce rapport, le prix de vente est jugé suspect. */
    public static final BigDecimal RATIO_MARGE_SUSPECT = new BigDecimal("20");

    private final ProduitDAO produitDAO;
    private final StockMovementDAO mouvementDAO;
    private final AuditService audit;

    public ProduitService(ProduitDAO produitDAO, StockMovementDAO mouvementDAO, AuditService audit) {
        this.produitDAO = produitDAO;
        this.mouvementDAO = mouvementDAO;
        this.audit = audit;
    }

    public ProduitService() {
        this(new ProduitDAO(), new StockMovementDAO(), new AuditService());
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    public List<Produit> listerTous() {
        return produitDAO.findAll();
    }

    public List<Produit> listerParCategorie(String categorie) {
        return produitDAO.findByCategorie(categorie);
    }

    public List<String> listerCategories() {
        return produitDAO.findAllCategories();
    }

    /** Produits à réapprovisionner (stock au niveau ou sous le seuil d'alerte). */
    public List<Produit> listerStockFaible() {
        return produitDAO.findStockFaible();
    }

    public Produit rechercher(String terme) {
        if (terme == null || terme.isBlank()) {
            return null;
        }
        return produitDAO.rechercherProduit(terme.trim());
    }

    public Produit parId(int id) {
        return produitDAO.findById(id);
    }

    // ------------------------------------------------------------------
    // Écriture
    // ------------------------------------------------------------------

    public void creer(Produit produit, int utilisateurId) {
        valider(produit);

        if (!produitDAO.create(produit)) {
            throw new ApplicationException("La création du produit a échoué.");
        }

        if (produit.getQuantiteStock() > 0) {
            enregistrerMouvement(produit.getId(), utilisateurId, produit.getQuantiteStock(),
                    produit.getQuantiteStock(), StockMovement.Type.INVENTORY_ADJUSTMENT,
                    "Stock initial");
        }

        audit.enregistrer(utilisateurId, "CREATION_PRODUIT", "produits", produit.getId(),
                produit.getNom() + " (" + produit.getCodeBarre() + ")");
    }

    public void modifier(Produit produit, int utilisateurId) {
        valider(produit);

        Produit avant = produitDAO.findById(produit.getId());

        if (!produitDAO.update(produit)) {
            throw new ApplicationException("La mise à jour du produit a échoué.");
        }

        // Une correction de la quantité depuis la fiche produit est un ajustement
        // d'inventaire : elle doit laisser une trace au même titre qu'une vente.
        if (avant != null && avant.getQuantiteStock() != produit.getQuantiteStock()) {
            int delta = produit.getQuantiteStock() - avant.getQuantiteStock();
            enregistrerMouvement(produit.getId(), utilisateurId, delta,
                    produit.getQuantiteStock(), StockMovement.Type.INVENTORY_ADJUSTMENT,
                    "Correction depuis la fiche produit");
        }

        audit.enregistrer(utilisateurId, "MODIFICATION_PRODUIT", "produits", produit.getId(),
                decrireChangement(avant, produit));
    }

    /**
     * Réapprovisionne un produit.
     *
     * @param origine {@code DESKTOP_ADD} au comptoir, {@code MOBILE_ADD} depuis
     *                l'application mobile
     */
    public void ajouterStock(int produitId, int quantite, int utilisateurId,
                             StockMovement.Type origine, String note) {
        if (quantite <= 0) {
            throw new ApplicationException("La quantité ajoutée doit être supérieure à 0.");
        }

        // Incrément atomique côté base : calculer la nouvelle valeur en mémoire
        // écraserait une vente encaissée entre-temps.
        if (!produitDAO.augmenterStock(produitId, quantite)) {
            throw new ApplicationException("Le stock n'a pas pu être mis à jour.");
        }

        Produit apres = produitDAO.findById(produitId);
        enregistrerMouvement(produitId, utilisateurId, quantite,
                apres != null ? apres.getQuantiteStock() : null, origine, note);

        audit.enregistrer(utilisateurId, "AJOUT_STOCK", "produits", produitId,
                "+" + quantite + (note != null && !note.isBlank() ? " — " + note : ""));
    }

    public void supprimer(Produit produit, int utilisateurId, boolean forcer) {
        try {
            if (!produitDAO.delete(produit.getId(), forcer)) {
                throw new ApplicationException("La suppression du produit a échoué.");
            }
        } catch (java.sql.SQLException e) {
            // Message déjà formulé pour l'utilisateur par le DAO.
            throw new ApplicationException(e.getMessage(), e);
        }

        audit.enregistrer(utilisateurId, forcer ? "SUPPRESSION_FORCEE_PRODUIT" : "SUPPRESSION_PRODUIT",
                "produits", produit.getId(), produit.getNom() + " (" + produit.getCodeBarre() + ")");
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * Contrôles communs à la création et à la modification.
     * Étaient auparavant dispersés dans les contrôleurs, donc appliqués de
     * façon inégale selon l'écran utilisé.
     */
    public void valider(Produit produit) {
        if (produit == null) {
            throw new ApplicationException("Produit absent.");
        }
        if (produit.getCodeBarre() == null || produit.getCodeBarre().isBlank()) {
            throw new ApplicationException("Le code-barres est obligatoire.");
        }
        if (produit.getNom() == null || produit.getNom().isBlank()) {
            throw new ApplicationException("Le nom du produit est obligatoire.");
        }
        if (produit.getPrixAchatActuel() == null || produit.getPrixVenteDefaut() == null) {
            throw new ApplicationException("Les deux prix sont obligatoires.");
        }
        if (produit.getPrixAchatActuel().signum() < 0 || produit.getPrixVenteDefaut().signum() < 0) {
            throw new ApplicationException("Les prix ne peuvent pas être négatifs.");
        }
        if (produit.getQuantiteStock() < 0) {
            throw new ApplicationException("La quantité en stock ne peut pas être négative.");
        }
        if (produit.getSeuilAlerte() < 0) {
            throw new ApplicationException("Le seuil d'alerte ne peut pas être négatif.");
        }
    }

    /** true si le produit est vendu à perte. */
    public boolean estVenduAPerte(Produit produit) {
        return produit.getPrixAchatActuel() != null
                && produit.getPrixVenteDefaut() != null
                && produit.getPrixAchatActuel().signum() > 0
                && produit.getPrixVenteDefaut().compareTo(produit.getPrixAchatActuel()) < 0;
    }

    /**
     * true si la marge est anormalement élevée — signe d'une faute de frappe.
     * La base contient un produit acheté 15 222 DT et vendu 5 112 255 DT, saisi
     * sans aucun avertissement.
     */
    public boolean margeSuspecte(Produit produit) {
        return produit.getPrixAchatActuel() != null
                && produit.getPrixVenteDefaut() != null
                && produit.getPrixAchatActuel().signum() > 0
                && produit.getPrixVenteDefaut()
                          .compareTo(produit.getPrixAchatActuel().multiply(RATIO_MARGE_SUSPECT)) > 0;
    }

    // ------------------------------------------------------------------

    private void enregistrerMouvement(int produitId, int utilisateurId, int delta,
                                      Integer stockApres, StockMovement.Type type, String reference) {
        try {
            mouvementDAO.create(new StockMovement(produitId,
                    utilisateurId > 0 ? utilisateurId : null,
                    delta, stockApres, type, reference, LocalDateTime.now()));
        } catch (Exception e) {
            // Ne pas faire échouer l'opération métier pour un défaut de traçabilité.
            LOG.error("Mouvement de stock non enregistré (produit={}, delta={})", produitId, delta, e);
        }
    }

    private String decrireChangement(Produit avant, Produit apres) {
        if (avant == null) {
            return apres.getNom();
        }
        StringBuilder sb = new StringBuilder(apres.getNom());
        if (!avant.getNom().equals(apres.getNom())) {
            sb.append(" | nom : ").append(avant.getNom()).append(" → ").append(apres.getNom());
        }
        if (avant.getPrixVenteDefaut().compareTo(apres.getPrixVenteDefaut()) != 0) {
            sb.append(" | prix de vente : ").append(avant.getPrixVenteDefaut())
              .append(" → ").append(apres.getPrixVenteDefaut());
        }
        if (avant.getQuantiteStock() != apres.getQuantiteStock()) {
            sb.append(" | stock : ").append(avant.getQuantiteStock())
              .append(" → ").append(apres.getQuantiteStock());
        }
        return sb.toString();
    }
}

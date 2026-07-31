package service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.VenteDAO;
import exception.ApplicationException;
import exception.StockInsuffisantException;
import model.DetailVente;
import model.Produit;
import model.Vente;

/**
 * Processus d'encaissement.
 *
 * Regroupe ce que {@code CaisseController} faisait directement : constituer la
 * vente, vérifier le panier, appeler le DAO. Le contrôleur se limite désormais
 * à l'affichage, et la logique devient testable sans interface graphique.
 */
public class VenteService {

    private static final Logger LOG = LoggerFactory.getLogger(VenteService.class);

    private final VenteDAO venteDAO;
    private final ProduitService produitService;
    private final AuditService audit;

    public VenteService(VenteDAO venteDAO, ProduitService produitService, AuditService audit) {
        this.venteDAO = venteDAO;
        this.produitService = produitService;
        this.audit = audit;
    }

    public VenteService() {
        this(new VenteDAO(), new ProduitService(), new AuditService());
    }

    // ------------------------------------------------------------------
    // Encaissement
    // ------------------------------------------------------------------

    /**
     * Encaisse le contenu du panier.
     *
     * @param panier         panier à encaisser
     * @param utilisateurId  employé réalisant la vente
     * @param typePaiement   {@code Espèces}, {@code Carte} ou {@code Autre}
     * @return la vente enregistrée, identifiant renseigné
     * @throws ApplicationException        panier vide ou session absente
     * @throws StockInsuffisantException   stock insuffisant pour une ligne
     */
    public Vente encaisser(Panier panier, int utilisateurId, String typePaiement) {
        if (panier == null || panier.estVide()) {
            throw new ApplicationException("Le panier est vide.");
        }
        if (utilisateurId <= 0) {
            // Contrôle explicite : l'ancien code retombait ici sur un utilisateur
            // « par défaut », ce qui attribuait toutes les ventes au compte admin.
            throw new ApplicationException(
                    "Aucun utilisateur connecté. Reconnectez-vous avant d'encaisser.");
        }

        validerLignes(panier.getLignes());

        Vente vente = new Vente();
        vente.setDateVente(LocalDateTime.now());
        vente.setTotalVente(panier.getTotal());
        vente.setUtilisateurId(utilisateurId);
        vente.setTypePaiement(typePaiement != null ? typePaiement : Vente.PAIEMENT_ESPECES);
        panier.getLignes().forEach(vente::addDetail);

        // Le DAO gère la transaction : insertion de la vente, des lignes et
        // décrémentation atomique du stock, le tout validé ou annulé ensemble.
        venteDAO.create(vente);

        LOG.info("Vente {} encaissée par l'utilisateur {} : {} article(s), {} DT",
                vente.getId(), utilisateurId, panier.getNombreArticles(), vente.getTotalVente());

        audit.enregistrer(utilisateurId, "VENTE", "ventes", vente.getId(),
                panier.getNombreArticles() + " article(s) — " + vente.getTotalVente() + " DT");

        return vente;
    }

    /**
     * Vérifie le panier avant encaissement, sans rien écrire.
     * Permet de prévenir le caissier avant qu'il n'annonce un montant.
     */
    public void verifierDisponibilite(Panier panier) {
        for (DetailVente ligne : panier.getLignes()) {
            Produit produit = produitService.parId(ligne.getProduitId());
            if (produit == null) {
                throw new ApplicationException(
                        "Un produit du panier n'existe plus en base (id " + ligne.getProduitId() + ").");
            }
            // Les cigarettes à l'unité n'ont pas de stock propre : c'est le
            // paquet associé qui est décrémenté, contrôlé au moment de la vente.
            if (!produit.isFrakCigarette() && produit.getQuantiteStock() < ligne.getQuantite()) {
                throw new StockInsuffisantException(
                        produit.getNom(), produit.getQuantiteStock(), ligne.getQuantite());
            }
        }
    }

    private void validerLignes(List<DetailVente> lignes) {
        for (DetailVente ligne : lignes) {
            if (ligne == null) {
                throw new ApplicationException("Ligne de vente invalide.");
            }
            if (ligne.getProduitId() <= 0) {
                throw new ApplicationException("Un produit du panier est invalide.");
            }
            if (ligne.getQuantite() <= 0) {
                throw new ApplicationException("Quantité invalide pour un produit du panier.");
            }
            if (ligne.getPrixVenteUnitaire() == null || ligne.getPrixAchatUnitaire() == null) {
                throw new ApplicationException("Prix manquant pour un produit du panier.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    public List<Vente> listerRecentes(int limite) {
        return venteDAO.findRecent(limite);
    }

    /** Ventes d'un employé pour une journée. */
    public List<Vente> listerDuJour(int utilisateurId, LocalDateTime jour) {
        return venteDAO.findByUtilisateurAndDate(utilisateurId, jour, jour);
    }

    public List<DetailVente> listerDetails(int venteId) {
        return venteDAO.findDetailsByVente(venteId);
    }

    public BigDecimal chiffreAffaires(LocalDateTime debut, LocalDateTime fin) {
        return venteDAO.getTotalRecettes(debut, fin);
    }

    public BigDecimal benefice(LocalDateTime debut, LocalDateTime fin) {
        return venteDAO.getTotalProfit(debut, fin);
    }

    public int nombreVentes(LocalDateTime debut, LocalDateTime fin) {
        return venteDAO.getNombreVentesParPeriode(debut, fin);
    }
}

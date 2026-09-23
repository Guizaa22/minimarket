package service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.AjoutStockDAO;
import dao.CreditFournisseurDAO;
import dao.DBConnector;
import dao.PaiementFournisseurDAO;
import dao.ProduitDAO;
import dao.StockMovementDAO;
import exception.ApplicationException;
import model.AjoutStock;
import model.PaiementFournisseur;
import model.Produit;
import model.StockMovement;

/**
 * Réapprovisionnement d'un produit auprès d'un fournisseur.
 *
 * Un ajout de stock au comptoir touche jusqu'à quatre tables : le stock du
 * produit, la trace dans {@code ajouts_stock}, le paiement éventuel au
 * fournisseur et le crédit consommé chez lui. L'écran de saisie enchaînait ces
 * écritures une à une, chacune sur sa propre connexion : une panne au milieu
 * laissait par exemple le stock augmenté et le crédit intact, ou un paiement
 * enregistré sans l'ajout correspondant. Les comptes fournisseur devenaient
 * alors faux sans que rien ne le signale.
 *
 * Tout passe désormais par une transaction unique, sur une seule connexion
 * empruntée au pool : les quatre écritures sont validées ensemble ou annulées
 * ensemble, sur le modèle de {@code VenteDAO.create}.
 *
 * Les traces (journal d'audit et mouvement de stock) sont écrites après la
 * validation : un défaut de traçabilité ne doit pas annuler une opération
 * métier réussie, conformément au contrat de {@link AuditService}.
 */
public class ApprovisionnementService {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovisionnementService.class);

    private final ProduitDAO produitDAO;
    private final AjoutStockDAO ajoutStockDAO;
    private final PaiementFournisseurDAO paiementDAO;
    private final CreditFournisseurDAO creditDAO;
    private final StockMovementDAO mouvementDAO;
    private final AuditService audit;

    public ApprovisionnementService(ProduitDAO produitDAO, AjoutStockDAO ajoutStockDAO,
                                    PaiementFournisseurDAO paiementDAO, CreditFournisseurDAO creditDAO,
                                    StockMovementDAO mouvementDAO, AuditService audit) {
        this.produitDAO = produitDAO;
        this.ajoutStockDAO = ajoutStockDAO;
        this.paiementDAO = paiementDAO;
        this.creditDAO = creditDAO;
        this.mouvementDAO = mouvementDAO;
        this.audit = audit;
    }

    public ApprovisionnementService() {
        this(new ProduitDAO(), new AjoutStockDAO(), new PaiementFournisseurDAO(),
             new CreditFournisseurDAO(), new StockMovementDAO(), new AuditService());
    }

    /**
     * Enregistre un réapprovisionnement complet.
     *
     * @param produitId          produit réapprovisionné
     * @param employeId          employé qui saisit l'ajout
     * @param fournisseurId      fournisseur, ou {@code null} pour un ajout rapide
     * @param quantite           quantité ajoutée, strictement positive
     * @param montantPaiement    montant réglé au fournisseur ({@code ZERO} si aucun)
     * @param creditUtilise      crédit consommé chez le fournisseur ({@code ZERO} si aucun)
     * @param notes              commentaire libre
     * @param typeAjoutTabac     {@code paquet} ou {@code cigarette}, {@code null} hors tabac
     * @param quantiteCigarettes nombre de cigarettes pour un ajout à l'unité
     * @return le stock du produit après l'opération
     * @throws ApplicationException si la saisie est invalide, si le crédit
     *         disponible est insuffisant ou si l'écriture échoue ; la
     *         transaction est alors entièrement annulée
     */
    public int enregistrerAjout(int produitId, int employeId, Integer fournisseurId, int quantite,
                                BigDecimal montantPaiement, BigDecimal creditUtilise, String notes,
                                String typeAjoutTabac, Integer quantiteCigarettes) {

        if (quantite <= 0) {
            throw new ApplicationException("La quantité ajoutée doit être supérieure à 0.");
        }
        if (employeId <= 0) {
            throw new ApplicationException(
                    "Aucun utilisateur connecté. Reconnectez-vous pour poursuivre.");
        }

        BigDecimal paiement = montantPaiement != null ? montantPaiement : BigDecimal.ZERO;
        BigDecimal credit = creditUtilise != null ? creditUtilise : BigDecimal.ZERO;

        if (paiement.signum() < 0) {
            throw new ApplicationException("Le montant de paiement ne peut pas être négatif.");
        }
        if (credit.signum() < 0) {
            throw new ApplicationException("Le crédit utilisé ne peut pas être négatif.");
        }
        if (fournisseurId == null && (paiement.signum() > 0 || credit.signum() > 0)) {
            throw new ApplicationException(
                    "Un paiement ou un crédit suppose un fournisseur : sélectionnez-en un.");
        }

        LocalDateTime maintenant = LocalDateTime.now();
        AjoutStock ajout = new AjoutStock(produitId, employeId, fournisseurId, quantite,
                paiement, credit, notes, maintenant, typeAjoutTabac, quantiteCigarettes);

        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);

            // 1. Stock : incrément calculé par la base, jamais en mémoire.
            if (!produitDAO.augmenterStock(conn, produitId, quantite)) {
                conn.rollback();
                throw new ApplicationException("Le stock n'a pas pu être mis à jour.");
            }

            // 2. Trace de l'ajout.
            if (!ajoutStockDAO.create(conn, ajout)) {
                conn.rollback();
                throw new ApplicationException("L'ajout de stock n'a pas pu être enregistré.");
            }

            // 3. Paiement au fournisseur, le cas échéant.
            if (paiement.signum() > 0) {
                PaiementFournisseur pf = new PaiementFournisseur(
                        fournisseurId, employeId, paiement, notes, maintenant);
                if (!paiementDAO.create(conn, pf)) {
                    conn.rollback();
                    throw new ApplicationException(
                            "Le paiement fournisseur n'a pas pu être enregistré.");
                }
            }

            // 4. Crédit consommé : la base refuse elle-même un débit supérieur
            //    au solde, dans la même instruction que la vérification.
            if (credit.signum() > 0 && !creditDAO.utiliserCredit(conn, fournisseurId, credit)) {
                conn.rollback();
                throw new ApplicationException(
                        "Crédit fournisseur insuffisant : l'ajout de stock a été annulé.");
            }

            conn.commit();

        } catch (SQLException e) {
            annulerSilencieusement(conn);
            LOG.error("Échec du réapprovisionnement (produit={}, quantité={}, fournisseur={})",
                    produitId, quantite, fournisseurId, e);
            throw new ApplicationException(
                    "L'enregistrement du réapprovisionnement a échoué. Aucune écriture n'a été conservée.", e);

        } catch (ApplicationException e) {
            // Le rollback a déjà eu lieu au point de détection ; celui-ci ne
            // couvre que les cas où l'exception vient d'ailleurs.
            annulerSilencieusement(conn);
            throw e;

        } finally {
            fermer(conn);
        }

        // Traces écrites hors transaction : leur échec ne doit pas défaire
        // un réapprovisionnement déjà validé.
        Produit apres = produitDAO.findById(produitId);
        int stockApres = apres != null ? apres.getQuantiteStock() : quantite;

        enregistrerMouvement(produitId, employeId, quantite, stockApres, notes);
        audit.enregistrer(employeId, "AJOUT_STOCK", "ajouts_stock", ajout.getId(),
                "Produit #" + produitId + " : +" + quantite
                + (fournisseurId != null ? " | fournisseur #" + fournisseurId : "")
                + (paiement.signum() > 0 ? " | payé " + paiement : "")
                + (credit.signum() > 0 ? " | crédit " + credit : ""));

        return stockApres;
    }

    private void enregistrerMouvement(int produitId, int employeId, int delta,
                                      Integer stockApres, String reference) {
        try {
            mouvementDAO.create(new StockMovement(produitId, employeId > 0 ? employeId : null,
                    delta, stockApres, StockMovement.Type.DESKTOP_ADD, reference, LocalDateTime.now()));
        } catch (Exception e) {
            LOG.error("Mouvement de stock non enregistré (produit={}, delta={})", produitId, delta, e);
        }
    }

    private void annulerSilencieusement(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException e) {
            LOG.error("Annulation de la transaction impossible", e);
        }
    }

    private void fermer(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.setAutoCommit(true);
            // Rend la connexion au pool (ne coupe pas la connexion réseau).
            conn.close();
        } catch (SQLException e) {
            LOG.error("Fermeture de la connexion impossible", e);
        }
    }
}

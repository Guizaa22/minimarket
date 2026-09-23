package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dao.ProduitDAO;
import dao.StockMovementDAO;
import exception.ApplicationException;
import model.Produit;
import model.Utilisateur;
import model.StockMovement;

/**
 * Tests unitaires de {@link ProduitService}, sans base de données.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProduitService")
class ProduitServiceTest {

    @Mock
    private ProduitDAO produitDAO;

    @Mock
    private StockMovementDAO mouvementDAO;

    @Mock
    private AuditService audit;

    private ProduitService service;

    @BeforeEach
    void setUp() {
        service = new ProduitService(produitDAO, mouvementDAO, audit);
    }

    private Produit produit(String prixAchat, String prixVente) {
        return new Produit(1, "CB1", "Café", "Divers",
                new BigDecimal(prixAchat), new BigDecimal(prixVente), 10, "unité", 5);
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Rejette un produit sans code-barres")
    void rejetteCodeBarreManquant() {
        Produit p = produit("2.000", "5.000");
        p.setCodeBarre("  ");

        ApplicationException e = assertThrows(ApplicationException.class, () -> service.valider(p));
        assertTrue(e.getMessage().toLowerCase().contains("code-barres"));
    }

    @Test
    @DisplayName("Rejette un prix négatif")
    void rejettePrixNegatif() {
        Produit p = produit("2.000", "-1.000");

        ApplicationException e = assertThrows(ApplicationException.class, () -> service.valider(p));
        assertTrue(e.getMessage().toLowerCase().contains("négatif"));
    }

    @Test
    @DisplayName("Détecte une vente à perte")
    void detecteVenteAPerte() {
        assertTrue(service.estVenduAPerte(produit("5.000", "3.000")),
                "vendu 3 alors qu'il coûte 5 : perte à chaque vente");
        assertFalse(service.estVenduAPerte(produit("2.000", "5.000")));
    }

    @Test
    @DisplayName("Détecte une marge invraisemblable (faute de frappe sur le prix)")
    void detecteMargeSuspecte() {
        // Cas réel de la base : acheté 15 222 DT, vendu 5 112 255 DT, saisi
        // sans le moindre avertissement, portant le chiffre d'affaires à 35,8 M.
        assertTrue(service.margeSuspecte(produit("15222.000", "5112255.000")));

        // Une marge commerciale ordinaire ne doit pas déclencher l'alerte.
        assertFalse(service.margeSuspecte(produit("2.000", "5.000")));
        assertFalse(service.margeSuspecte(produit("1.000", "20.000")),
                "20x est la limite, pas encore suspect");
    }

    // ------------------------------------------------------------------
    // Stock
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un réapprovisionnement passe par un incrément atomique et laisse une trace")
    void ajouteStockEtTrace() {
        when(produitDAO.augmenterStock(1, 12)).thenReturn(true);
        when(produitDAO.findById(1)).thenReturn(produit("2.000", "5.000"));

        service.ajouterStock(1, 12, 42, StockMovement.Type.MOBILE_ADD, "Livraison");

        // L'incrément est calculé par la base : recalculer la valeur en mémoire
        // écraserait une vente encaissée entre-temps.
        verify(produitDAO).augmenterStock(1, 12);

        ArgumentCaptor<StockMovement> capture = ArgumentCaptor.forClass(StockMovement.class);
        verify(mouvementDAO).create(capture.capture());
        StockMovement mouvement = capture.getValue();

        assertEquals(1, mouvement.getProductId());
        assertEquals(12, mouvement.getQuantityChange());
        assertEquals(StockMovement.Type.MOBILE_ADD, mouvement.getType());
        assertEquals(42, mouvement.getUserId());
        assertTrue(mouvement.estEntree());

        verify(audit).enregistrer(anyInt(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Refuse une quantité nulle ou négative")
    void refuseQuantiteInvalide() {
        assertThrows(ApplicationException.class,
                () -> service.ajouterStock(1, 0, 42, StockMovement.Type.DESKTOP_ADD, null));
        assertThrows(ApplicationException.class,
                () -> service.ajouterStock(1, -5, 42, StockMovement.Type.DESKTOP_ADD, null));

        verify(produitDAO, never()).augmenterStock(anyInt(), anyInt());
    }

    @Test
    @DisplayName("Signale l'échec de mise à jour du stock au lieu de l'ignorer")
    void signaleEchecMiseAJourStock() {
        when(produitDAO.augmenterStock(1, 5)).thenReturn(false);

        assertThrows(ApplicationException.class,
                () -> service.ajouterStock(1, 5, 42, StockMovement.Type.DESKTOP_ADD, null));

        // Aucun mouvement ne doit être écrit si le stock n'a pas bougé.
        verify(mouvementDAO, never()).create(any(StockMovement.class));
    }

    @Test
    @DisplayName("Une trace de mouvement en échec ne fait pas échouer le réapprovisionnement")
    void echecDeTraceNInterromptPasLOperation() {
        when(produitDAO.augmenterStock(1, 5)).thenReturn(true);
        when(produitDAO.findById(1)).thenReturn(produit("2.000", "5.000"));
        when(mouvementDAO.create(any(StockMovement.class)))
                .thenThrow(new RuntimeException("base indisponible"));

        // Le stock a bien été mis à jour : l'opération métier ne doit pas être
        // annulée parce que sa trace n'a pas pu être écrite.
        service.ajouterStock(1, 5, 42, StockMovement.Type.DESKTOP_ADD, null);

        verify(produitDAO).augmenterStock(1, 5);
    }

    // ------------------------------------------------------------------
    // Suppression et archivage : réservés à l'administrateur
    // ------------------------------------------------------------------

    private Utilisateur admin() {
        return new Utilisateur(1, "patron", "empreinte", Utilisateur.Role.Admin);
    }

    private Utilisateur employe() {
        return new Utilisateur(9, "caissier", "empreinte", Utilisateur.Role.Employé);
    }

    @Test
    @DisplayName("un administrateur peut supprimer un produit jamais vendu")
    void adminPeutSupprimer() throws java.sql.SQLException {
        when(produitDAO.delete(anyInt(), eq(false))).thenReturn(true);

        service.supprimer(produit("2.000", "5.000"), admin());

        verify(produitDAO).delete(anyInt(), eq(false));
        verify(audit).enregistrer(eq(1), eq("SUPPRESSION_PRODUIT"), eq("produits"),
                any(), anyString());
    }

    @Test
    @DisplayName("un employé ne peut pas supprimer un produit")
    void employeNePeutPasSupprimer() throws java.sql.SQLException {
        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.supprimer(produit("2.000", "5.000"), employe()));

        assertTrue(e.getMessage().toLowerCase().contains("administrateur"),
                "le refus doit être explicite : " + e.getMessage());
        // Le contrôle précède l'écriture : rien ne doit partir en base.
        verify(produitDAO, never()).delete(anyInt(), anyBoolean());
        verify(audit, never()).enregistrer(anyInt(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("sans session ouverte, la suppression est refusée")
    void suppressionSansSessionRefusee() throws java.sql.SQLException {
        assertThrows(ApplicationException.class,
                () -> service.supprimer(produit("2.000", "5.000"), null));

        verify(produitDAO, never()).delete(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("un administrateur peut archiver un produit")
    void adminPeutArchiver() {
        when(produitDAO.definirActif(anyInt(), eq(false))).thenReturn(true);
        Produit p = produit("2.000", "5.000");

        service.archiver(p, admin());

        assertFalse(p.isActif(), "l'objet affiché doit refléter l'archivage");
        verify(audit).enregistrer(eq(1), eq("ARCHIVAGE_PRODUIT"), eq("produits"),
                any(), anyString());
    }

    @Test
    @DisplayName("un employé ne peut pas archiver un produit")
    void employeNePeutPasArchiver() {
        Produit p = produit("2.000", "5.000");

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.archiver(p, employe()));

        assertTrue(e.getMessage().toLowerCase().contains("administrateur"));
        assertTrue(p.isActif(), "le produit doit rester en vente");
        verify(produitDAO, never()).definirActif(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("un employé ne peut pas remettre un produit en vente")
    void employeNePeutPasReactiver() {
        assertThrows(ApplicationException.class,
                () -> service.reactiver(produit("2.000", "5.000"), employe()));

        verify(produitDAO, never()).definirActif(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("un administrateur peut remettre un produit en vente")
    void adminPeutReactiver() {
        when(produitDAO.definirActif(anyInt(), eq(true))).thenReturn(true);
        Produit p = produit("2.000", "5.000");
        p.setActif(false);

        service.reactiver(p, admin());

        assertTrue(p.isActif());
        verify(audit).enregistrer(eq(1), eq("REACTIVATION_PRODUIT"), eq("produits"),
                any(), anyString());
    }

    @Test
    @DisplayName("un produit référencé remonte le message du DAO, qui oriente vers l'archivage")
    void produitReferenceOrienteVersLArchivage() throws java.sql.SQLException {
        when(produitDAO.delete(anyInt(), eq(false))).thenThrow(new java.sql.SQLException(
                "Ce produit figure dans des ventes ou des ajouts de stock : "
                + "il ne peut pas être supprimé sans fausser l'historique. "
                + "Archivez-le pour le retirer de la vente."));

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.supprimer(produit("2.000", "5.000"), admin()));

        assertTrue(e.getMessage().toLowerCase().contains("archivez"));
    }
}

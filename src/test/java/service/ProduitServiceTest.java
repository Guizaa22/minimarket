package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
}

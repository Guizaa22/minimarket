package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import dao.VenteDAO;
import exception.ApplicationException;
import exception.StockInsuffisantException;
import model.Produit;
import model.TypeCategorie;
import model.Vente;

/**
 * Tests unitaires de {@link VenteService}.
 *
 * Les DAO sont simulés : ces tests s'exécutent sans base de données, ce qui
 * était impossible tant que la logique d'encaissement vivait dans le contrôleur
 * JavaFX.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VenteService")
class VenteServiceTest {

    @Mock
    private VenteDAO venteDAO;

    @Mock
    private ProduitService produitService;

    @Mock
    private AuditService audit;

    private VenteService service;
    private Panier panier;

    @BeforeEach
    void setUp() {
        service = new VenteService(venteDAO, produitService, audit);
        panier = new Panier();
    }

    private Produit produit(int id, String nom, int stock, String prixAchat, String prixVente) {
        Produit p = new Produit(id, "CB" + id, nom, "Divers",
                new BigDecimal(prixAchat), new BigDecimal(prixVente), stock, "unité", 5);
        p.setTypeCategorie(TypeCategorie.Standard);
        return p;
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("Encaisse le panier et attribue la vente à l'employé connecté")
    void encaisseEtAttribueAlEmploye() {
        panier.ajouter(produit(1, "Café", 10, "2.500", "5.000"), 2);
        when(venteDAO.create(any())).thenReturn(true);

        Vente vente = service.encaisser(panier, 42, Vente.PAIEMENT_ESPECES);

        ArgumentCaptor<Vente> capture = ArgumentCaptor.forClass(Vente.class);
        verify(venteDAO).create(capture.capture());

        Vente enregistree = capture.getValue();
        assertEquals(42, enregistree.getUtilisateurId(),
                "la vente doit être attribuée à l'employé connecté, pas à un compte par défaut");
        assertEquals(0, new BigDecimal("10.000").compareTo(enregistree.getTotalVente()),
                "2 x 5.000 = 10.000");
        assertEquals(1, enregistree.getDetails().size());
        assertEquals(Vente.PAIEMENT_ESPECES, enregistree.getTypePaiement());
        assertEquals(vente, enregistree);
    }

    @Test
    @DisplayName("Refuse d'encaisser un panier vide")
    void refusePanierVide() {
        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.encaisser(panier, 42, Vente.PAIEMENT_ESPECES));

        assertTrue(e.getMessage().toLowerCase().contains("vide"));
        verify(venteDAO, never()).create(any());
    }

    @Test
    @DisplayName("Refuse d'encaisser sans utilisateur connecté")
    void refuseSansSession() {
        panier.ajouter(produit(1, "Café", 10, "2.500", "5.000"), 1);

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.encaisser(panier, -1, Vente.PAIEMENT_ESPECES));

        assertTrue(e.getMessage().toLowerCase().contains("connecté"),
                "message attendu sur la session : " + e.getMessage());
        // Point central : aucune vente ne doit partir sans employé identifié.
        // L'ancien code choisissait un utilisateur « par défaut », ce qui
        // attribuait toutes les ventes au compte admin.
        verify(venteDAO, never()).create(any());
    }

    @Test
    @DisplayName("Le total additionne toutes les lignes du panier")
    void totalCumuleLesLignes() {
        panier.ajouter(produit(1, "Café", 10, "2.500", "5.000"), 2);   // 10.000
        panier.ajouter(produit(2, "Thé", 10, "1.200", "3.000"), 3);    //  9.000
        when(venteDAO.create(any())).thenReturn(true);

        service.encaisser(panier, 7, Vente.PAIEMENT_CARTE);

        ArgumentCaptor<Vente> capture = ArgumentCaptor.forClass(Vente.class);
        verify(venteDAO).create(capture.capture());

        assertEquals(0, new BigDecimal("19.000").compareTo(capture.getValue().getTotalVente()));
        assertEquals(2, capture.getValue().getDetails().size());
    }

    @Test
    @DisplayName("verifierDisponibilite signale un stock insuffisant avant encaissement")
    void signaleStockInsuffisant() {
        Produit café = produit(1, "Café", 1, "2.500", "5.000");
        panier.ajouter(café, 3);
        when(produitService.parId(1)).thenReturn(café);

        StockInsuffisantException e = assertThrows(StockInsuffisantException.class,
                () -> service.verifierDisponibilite(panier));

        assertEquals("Café", e.getNomProduit());
        assertEquals(1, e.getStockDisponible());
        assertEquals(3, e.getQuantiteDemandee());
        assertTrue(e.getMessageUtilisateur().contains("1"),
                "le message doit indiquer le stock restant : " + e.getMessageUtilisateur());
    }

    @Test
    @DisplayName("Les cigarettes à l'unité échappent au contrôle de stock propre")
    void frakCigaretteIgnoreSonPropreStock() {
        Produit frak = produit(9, "Marlboro unité", 0, "0.500", "1.000");
        frak.setTypeCategorie(TypeCategorie.FrakCigarette);
        panier.ajouter(frak, 5);
        when(produitService.parId(9)).thenReturn(frak);

        // Ne doit pas lever : c'est le paquet associé qui porte le stock,
        // vérifié au moment de l'encaissement.
        service.verifierDisponibilite(panier);
    }

    @Test
    @DisplayName("Une écriture refusée par la base interrompt l'encaissement")
    void echecEcritureInterrompLEncaissement() {
        panier.ajouter(produit(1, "Café", 10, "2.500", "5.000"), 2);
        // create() renvoie false sans lever d'exception quand la base ne rend
        // aucun identifiant. Ce retour était ignoré : le contrôleur imprimait
        // le ticket, affichait « Vente enregistrée » et vidait le panier alors
        // que rien n'avait été écrit.
        when(venteDAO.create(any())).thenReturn(false);

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.encaisser(panier, 42, Vente.PAIEMENT_ESPECES));

        assertTrue(e.getMessage().toLowerCase().contains("panier est conservé")
                        || e.getMessage().toLowerCase().contains("réessayez"),
                "le caissier doit être invité à réessayer : " + e.getMessage());
    }

    @Test
    @DisplayName("Une écriture refusée n'est pas tracée comme une vente réussie")
    void echecEcritureNeTracePasDAudit() {
        panier.ajouter(produit(1, "Café", 10, "2.500", "5.000"), 2);
        when(venteDAO.create(any())).thenReturn(false);

        assertThrows(ApplicationException.class,
                () -> service.encaisser(panier, 42, Vente.PAIEMENT_ESPECES));

        // Une vente inexistante ne doit pas apparaître dans audit_logs.
        verify(audit, never()).enregistrer(anyInt(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Une vente encaissée est tracée dans le journal d'audit")
    void traceLaVenteDansLAudit() {
        panier.ajouter(produit(1, "Café", 10, "2.500", "5.000"), 2);
        when(venteDAO.create(any())).thenReturn(true);

        service.encaisser(panier, 42, Vente.PAIEMENT_ESPECES);

        verify(audit).enregistrer(anyInt(), anyString(), anyString(), any(), anyString());
    }
}

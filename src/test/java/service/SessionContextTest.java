package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dao.UtilisateurDAO;
import model.Produit;
import model.Utilisateur;

/**
 * État partagé de la session.
 *
 * Source de vérité unique de l'utilisateur connecté et du panier : c'est
 * l'absence de cette centralisation qui attribuait autrefois toutes les ventes
 * au compte admin, les contrôleurs retombant sur un utilisateur par défaut.
 */
@DisplayName("SessionContext")
class SessionContextTest {

    @AfterEach
    void tearDown() {
        // L'instance est partagée : la laisser ouverte polluerait les autres
        // tests de la même JVM.
        SessionContext.reset();
    }

    private Utilisateur employe(int id, String nom, Utilisateur.Role role) {
        return new Utilisateur(id, nom, "empreinte", role);
    }

    private AuthService authService(SessionContext session, Utilisateur attendu) {
        UtilisateurDAO dao = org.mockito.Mockito.mock(UtilisateurDAO.class);
        org.mockito.Mockito.when(dao.authenticate(attendu.getUsername(), "motdepasse"))
                .thenReturn(attendu);
        AuthService service = new AuthService(dao, session,
                org.mockito.Mockito.mock(AuditService.class));
        AuthService.reinitialiserTentatives();
        return service;
    }

    @Test
    @DisplayName("une session neuve n'a pas d'utilisateur")
    void sessionNeuveVide() {
        SessionContext session = new SessionContext();

        assertFalse(session.estConnecte());
        assertFalse(session.estAdmin());
        assertNull(session.getUtilisateurConnecte());
        assertEquals(-1, session.getUtilisateurId(),
                "l'absence de session doit être explicite, pas un identifiant plausible");
    }

    @Test
    @DisplayName("l'ouverture renseigne l'utilisateur et son rôle")
    void ouvertureRenseigneLUtilisateur() {
        SessionContext session = new SessionContext();
        Utilisateur caissier = employe(12, "caissier", Utilisateur.Role.Employé);

        authService(session, caissier).connecter("caissier", "motdepasse");

        assertTrue(session.estConnecte());
        assertEquals(12, session.getUtilisateurId());
        assertFalse(session.estAdmin());
        assertSame(caissier, session.getUtilisateurConnecte());
    }

    @Test
    @DisplayName("le rôle administrateur est reconnu")
    void roleAdminReconnu() {
        SessionContext session = new SessionContext();
        Utilisateur admin = employe(1, "patron", Utilisateur.Role.Admin);

        authService(session, admin).connecter("patron", "motdepasse");

        assertTrue(session.estAdmin());
    }

    @Test
    @DisplayName("la fermeture vide la session et le panier")
    void fermetureVideToutu() {
        SessionContext session = new SessionContext();
        Utilisateur caissier = employe(12, "caissier", Utilisateur.Role.Employé);
        AuthService auth = authService(session, caissier);
        auth.connecter("caissier", "motdepasse");

        session.getPanier().ajouter(new Produit(1, "CB1", "Café", "Divers",
                new BigDecimal("2.500"), new BigDecimal("5.000"), 10, "unité", 5), 2);
        assertFalse(session.getPanier().estVide());

        auth.deconnecter();

        assertFalse(session.estConnecte());
        assertEquals(-1, session.getUtilisateurId());
        // Un panier conservé ferait basculer les articles d'un employé sur la
        // session du suivant.
        assertTrue(session.getPanier().estVide());
    }

    @Test
    @DisplayName("l'ouverture d'une session vide le panier précédent")
    void ouvertureVideLePanier() {
        SessionContext session = new SessionContext();
        session.getPanier().ajouter(new Produit(1, "CB1", "Café", "Divers",
                new BigDecimal("2.500"), new BigDecimal("5.000"), 10, "unité", 5), 1);

        Utilisateur caissier = employe(12, "caissier", Utilisateur.Role.Employé);
        authService(session, caissier).connecter("caissier", "motdepasse");

        assertTrue(session.getPanier().estVide());
    }

    @Test
    @DisplayName("l'instance partagée est unique")
    void instancePartageeUnique() {
        assertSame(SessionContext.get(), SessionContext.get());
    }

    @Test
    @DisplayName("reset rend une instance neuve")
    void resetRendUneInstanceNeuve() {
        SessionContext avant = SessionContext.get();
        SessionContext.reset();

        org.junit.jupiter.api.Assertions.assertNotSame(avant, SessionContext.get());
    }
}

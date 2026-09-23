package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dao.UtilisateurDAO;
import model.Utilisateur;

/**
 * Tests unitaires de {@link AuthService}, sans base de données.
 *
 * Couvre l'ouverture de session — dont l'oubli attribuait autrefois toutes
 * les ventes au compte admin — et le blocage après échecs répétés.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String MOT_DE_PASSE = "motdepasse-correct";

    @Mock
    private UtilisateurDAO utilisateurDAO;

    @Mock
    private AuditService audit;

    private SessionContext session;
    private AuthService service;
    private Utilisateur caissier;

    @BeforeEach
    void setUp() {
        // Contexte isolé : le contexte partagé traverserait les tests.
        session = new SessionContext();
        service = new AuthService(utilisateurDAO, session, audit);
        caissier = new Utilisateur(7, "caissier", "hash-bcrypt", Utilisateur.Role.Employé);

        // Le compteur d'échecs est statique, donc commun à toute la JVM.
        AuthService.reinitialiserTentatives();
    }

    @AfterEach
    void tearDown() {
        AuthService.reinitialiserTentatives();
    }

    // ------------------------------------------------------------------
    // Connexion réussie
    // ------------------------------------------------------------------

    @Test
    @DisplayName("une connexion réussie ouvre la session et laisse une trace d'audit")
    void connexionReussieOuvreLaSession() {
        when(utilisateurDAO.authenticate("caissier", MOT_DE_PASSE)).thenReturn(caissier);

        Optional<Utilisateur> connecte = service.connecter("caissier", MOT_DE_PASSE);

        assertTrue(connecte.isPresent());
        assertSame(caissier, connecte.get());
        assertTrue(session.estConnecte());
        assertEquals(7, session.getUtilisateurId());
        verify(audit).enregistrer(eq(7), eq("CONNEXION"), eq("utilisateurs"), eq(7), anyString());
    }

    @Test
    @DisplayName("les espaces autour du nom d'utilisateur sont ignorés")
    void nomDUtilisateurNettoye() {
        when(utilisateurDAO.authenticate("caissier", MOT_DE_PASSE)).thenReturn(caissier);

        assertTrue(service.connecter("  caissier  ", MOT_DE_PASSE).isPresent());
    }

    // ------------------------------------------------------------------
    // Connexion refusée
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un mot de passe erroné laisse la session fermée")
    void motDePasseErroneRefuse() {
        when(utilisateurDAO.authenticate("caissier", "mauvais")).thenReturn(null);

        ResultatConnexion resultat = service.tenterConnexion("caissier", "mauvais");

        assertEquals(ResultatConnexion.Statut.IDENTIFIANTS_INVALIDES, resultat.statut());
        assertTrue(resultat.utilisateur().isEmpty());
        assertFalse(session.estConnecte());
        verify(audit, never()).enregistrer(anyInt(), eq("CONNEXION"), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("un compte inconnu est refusé comme un mot de passe erroné")
    void compteInconnuRefuse() {
        when(utilisateurDAO.authenticate("fantome", MOT_DE_PASSE)).thenReturn(null);

        assertTrue(service.connecter("fantome", MOT_DE_PASSE).isEmpty());
        assertFalse(session.estConnecte());
    }

    @Test
    @DisplayName("une saisie vide est rejetée sans interroger la base")
    void saisieVideRejetee() {
        assertEquals(ResultatConnexion.Statut.SAISIE_INVALIDE,
                service.tenterConnexion("", MOT_DE_PASSE).statut());
        assertEquals(ResultatConnexion.Statut.SAISIE_INVALIDE,
                service.tenterConnexion("   ", MOT_DE_PASSE).statut());
        assertEquals(ResultatConnexion.Statut.SAISIE_INVALIDE,
                service.tenterConnexion("caissier", "").statut());
        assertEquals(ResultatConnexion.Statut.SAISIE_INVALIDE,
                service.tenterConnexion(null, null).statut());

        assertFalse(session.estConnecte());
        // Une saisie incomplète ne doit pas consommer d'essai ni toucher la base.
        verifyNoInteractions(utilisateurDAO);
    }

    // ------------------------------------------------------------------
    // Blocage après échecs répétés
    // ------------------------------------------------------------------

    @Test
    @DisplayName("le blocage se déclenche au cinquième échec")
    void blocageApresCinqEchecs() {
        when(utilisateurDAO.authenticate("caissier", "mauvais")).thenReturn(null);

        for (int essai = 1; essai < AuthService.TENTATIVES_AVANT_BLOCAGE; essai++) {
            assertEquals(ResultatConnexion.Statut.IDENTIFIANTS_INVALIDES,
                    service.tenterConnexion("caissier", "mauvais").statut(),
                    "essai n°" + essai + " : le compte ne doit pas encore être bloqué");
        }

        ResultatConnexion dernier = service.tenterConnexion("caissier", "mauvais");
        assertEquals(ResultatConnexion.Statut.COMPTE_BLOQUE, dernier.statut());
        assertEquals(AuthService.DUREE_BLOCAGE.toMinutes(), dernier.minutesRestantes());
        verify(audit).enregistrer(anyInt(), eq("BLOCAGE_COMPTE"), eq("utilisateurs"),
                any(), anyString());
    }

    @Test
    @DisplayName("pendant le blocage, même le bon mot de passe est refusé")
    void bonMotDePasseRefusePendantLeBlocage() {
        when(utilisateurDAO.authenticate("caissier", "mauvais")).thenReturn(null);
        for (int essai = 0; essai < AuthService.TENTATIVES_AVANT_BLOCAGE; essai++) {
            service.tenterConnexion("caissier", "mauvais");
        }

        ResultatConnexion resultat = service.tenterConnexion("caissier", MOT_DE_PASSE);

        assertEquals(ResultatConnexion.Statut.COMPTE_BLOQUE, resultat.statut());
        assertTrue(resultat.minutesRestantes() > 0);
        assertFalse(session.estConnecte());
        // La base n'est même pas interrogée : le refus est prononcé en amont.
        verify(utilisateurDAO, never()).authenticate("caissier", MOT_DE_PASSE);
        assertTrue(resultat.message().contains("Compte temporairement bloqué"));
    }

    @Test
    @DisplayName("la casse du nom ne permet pas de contourner le blocage")
    void blocageInsensibleALaCasse() {
        when(utilisateurDAO.authenticate(anyString(), eq("mauvais"))).thenReturn(null);
        for (int essai = 0; essai < AuthService.TENTATIVES_AVANT_BLOCAGE; essai++) {
            service.tenterConnexion("caissier", "mauvais");
        }

        assertEquals(ResultatConnexion.Statut.COMPTE_BLOQUE,
                service.tenterConnexion("CAISSIER", MOT_DE_PASSE).statut());
    }

    @Test
    @DisplayName("le blocage ne vise que l'identifiant fautif")
    void blocageLimiteAUnIdentifiant() {
        when(utilisateurDAO.authenticate("caissier", "mauvais")).thenReturn(null);
        when(utilisateurDAO.authenticate("gerant", MOT_DE_PASSE)).thenReturn(caissier);

        for (int essai = 0; essai < AuthService.TENTATIVES_AVANT_BLOCAGE; essai++) {
            service.tenterConnexion("caissier", "mauvais");
        }

        assertTrue(service.tenterConnexion("gerant", MOT_DE_PASSE).estReussi());
    }

    @Test
    @DisplayName("une connexion réussie remet le compteur d'échecs à zéro")
    void connexionReussieRemetLeCompteurAZero() {
        when(utilisateurDAO.authenticate("caissier", "mauvais")).thenReturn(null);
        when(utilisateurDAO.authenticate("caissier", MOT_DE_PASSE)).thenReturn(caissier);

        for (int essai = 0; essai < AuthService.TENTATIVES_AVANT_BLOCAGE - 1; essai++) {
            service.tenterConnexion("caissier", "mauvais");
        }
        assertTrue(service.tenterConnexion("caissier", MOT_DE_PASSE).estReussi());

        // Le quota repart de zéro : quatre nouveaux échecs ne bloquent pas.
        for (int essai = 0; essai < AuthService.TENTATIVES_AVANT_BLOCAGE - 1; essai++) {
            assertEquals(ResultatConnexion.Statut.IDENTIFIANTS_INVALIDES,
                    service.tenterConnexion("caissier", "mauvais").statut());
        }
    }

    // ------------------------------------------------------------------
    // Déconnexion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la déconnexion ferme la session et laisse une trace d'audit")
    void deconnexionFermeLaSession() {
        when(utilisateurDAO.authenticate("caissier", MOT_DE_PASSE)).thenReturn(caissier);
        service.connecter("caissier", MOT_DE_PASSE);

        service.deconnecter();

        assertFalse(session.estConnecte());
        verify(audit).enregistrer(eq(7), eq("DECONNEXION"), eq("utilisateurs"), eq(7), anyString());
    }
}

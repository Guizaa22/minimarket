package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dao.CategorieDAO;
import exception.ApplicationException;
import model.Categorie;
import model.TypeCategorie;

/**
 * Tests unitaires de {@link CategorieService}, sans base de données.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategorieService")
class CategorieServiceTest {

    @Mock
    private CategorieDAO categorieDAO;

    @Mock
    private AuditService audit;

    private CategorieService service;

    @BeforeEach
    void setUp() {
        service = new CategorieService(categorieDAO, audit);
    }

    @Test
    @DisplayName("la création enregistre le type choisi et laisse une trace d'audit")
    void creationTraceLeType() {
        when(categorieDAO.findByNom("Épicerie")).thenReturn(null);
        when(categorieDAO.create(any())).thenReturn(true);

        service.creer("Épicerie", TypeCategorie.Tabac, null, null, 4);

        ArgumentCaptor<Categorie> capture = ArgumentCaptor.forClass(Categorie.class);
        verify(categorieDAO).create(capture.capture());

        // Le type ne se déduit jamais du libellé : « Épicerie » en Tabac doit
        // rester en Tabac, et inversement.
        assertEquals(TypeCategorie.Tabac, capture.getValue().getType());
        assertEquals("Épicerie", capture.getValue().getNom());
        verify(audit).enregistrer(eq(4), eq("CREATION_CATEGORIE"), eq("categories"),
                any(), anyString());
    }

    @Test
    @DisplayName("le nom est nettoyé de ses espaces")
    void nomNettoye() {
        when(categorieDAO.findByNom("Boissons")).thenReturn(null);
        when(categorieDAO.create(any())).thenReturn(true);

        service.creer("  Boissons  ", TypeCategorie.Standard, null, null, 4);

        ArgumentCaptor<Categorie> capture = ArgumentCaptor.forClass(Categorie.class);
        verify(categorieDAO).create(capture.capture());
        assertEquals("Boissons", capture.getValue().getNom());
    }

    @Test
    @DisplayName("sans type explicite, la catégorie est Standard")
    void typeParDefautStandard() {
        when(categorieDAO.findByNom("Divers")).thenReturn(null);
        when(categorieDAO.create(any())).thenReturn(true);

        service.creer("Divers", null, null, null, 4);

        ArgumentCaptor<Categorie> capture = ArgumentCaptor.forClass(Categorie.class);
        verify(categorieDAO).create(capture.capture());
        assertEquals(TypeCategorie.Standard, capture.getValue().getType());
    }

    @Test
    @DisplayName("un nom déjà pris est refusé")
    void nomEnDoubleRefuse() {
        when(categorieDAO.findByNom("Tabac")).thenReturn(new Categorie("Tabac", TypeCategorie.Tabac));

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.creer("Tabac", TypeCategorie.Tabac, null, null, 4));

        assertTrue(e.getMessage().contains("existe déjà"));
        verify(categorieDAO, never()).create(any());
    }

    @Test
    @DisplayName("un nom vide est refusé")
    void nomVideRefuse() {
        assertThrows(ApplicationException.class,
                () -> service.creer("   ", TypeCategorie.Standard, null, null, 4));
        verify(categorieDAO, never()).create(any());
    }

    @Test
    @DisplayName("une écriture refusée n'est pas tracée comme un succès")
    void ecritureRefuseeNonTracee() {
        when(categorieDAO.findByNom("Épicerie")).thenReturn(null);
        when(categorieDAO.create(any())).thenReturn(false);

        assertThrows(ApplicationException.class,
                () -> service.creer("Épicerie", TypeCategorie.Standard, null, null, 4));

        verify(audit, never()).enregistrer(anyInt(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("la photo fournie est transmise au DAO")
    void photoTransmise() {
        when(categorieDAO.findByNom("Fruits")).thenReturn(null);
        when(categorieDAO.create(any())).thenReturn(true);
        byte[] image = {1, 2, 3};

        service.creer("Fruits", TypeCategorie.Standard, image, "image/png", 4);

        ArgumentCaptor<Categorie> capture = ArgumentCaptor.forClass(Categorie.class);
        verify(categorieDAO).create(capture.capture());
        assertEquals("image/png", capture.getValue().getImageMime());
    }

    @Test
    @DisplayName("la modification est tracée dans l'audit")
    void modificationTracee() {
        when(categorieDAO.update(any())).thenReturn(true);
        Categorie c = new Categorie("Boissons", TypeCategorie.Standard);

        service.modifier(c, 4);

        verify(audit).enregistrer(eq(4), eq("MODIFICATION_CATEGORIE"), eq("categories"),
                any(), anyString());
    }

    @Test
    @DisplayName("la suppression est tracée dans l'audit")
    void suppressionTracee() throws java.sql.SQLException {
        when(categorieDAO.delete(anyInt())).thenReturn(true);
        Categorie c = new Categorie("Obsolète", TypeCategorie.Standard);

        service.supprimer(c, 4);

        verify(audit).enregistrer(eq(4), eq("SUPPRESSION_CATEGORIE"), eq("categories"),
                any(), anyString());
    }

    @Test
    @DisplayName("une catégorie encore utilisée remonte le message du DAO")
    void suppressionRefuseeRemonteLeMessage() throws java.sql.SQLException {
        when(categorieDAO.delete(anyInt()))
                .thenThrow(new java.sql.SQLException("Des produits utilisent cette catégorie."));
        Categorie c = new Categorie("Utilisée", TypeCategorie.Standard);

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.supprimer(c, 4));

        assertTrue(e.getMessage().contains("produits"));
    }
}

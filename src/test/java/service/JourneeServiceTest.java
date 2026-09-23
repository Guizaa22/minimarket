package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dao.DeplacementEmployeDAO;
import dao.NoteJourDAO;
import exception.ApplicationException;
import model.DeplacementEmploye;
import model.NoteJour;

/**
 * Tests unitaires de {@link JourneeService}, sans base de données.
 *
 * Ces écritures partaient directement des contrôleurs : ni validation
 * commune, ni trace dans audit_logs. Une sortie de caisse constate pourtant
 * de l'argent qui quitte le tiroir, et les heures d'un déplacement
 * alimentent la paie.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JourneeService")
class JourneeServiceTest {

    @Mock
    private NoteJourDAO noteDAO;

    @Mock
    private DeplacementEmployeDAO deplacementDAO;

    @Mock
    private AuditService audit;

    private JourneeService service;

    @BeforeEach
    void setUp() {
        service = new JourneeService(noteDAO, deplacementDAO, audit);
    }

    // ------------------------------------------------------------------
    // Notes du jour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("une sortie de caisse est enregistrée et tracée dans l'audit")
    void sortieDeCaisseTracee() {
        when(noteDAO.create(any())).thenReturn(true);

        service.enregistrerNote(7, NoteJour.TypeNote.Sortie_Caisse,
                new BigDecimal("42.500"), "Achat de sacs");

        ArgumentCaptor<NoteJour> capture = ArgumentCaptor.forClass(NoteJour.class);
        verify(noteDAO).create(capture.capture());
        NoteJour note = capture.getValue();

        assertEquals(7, note.getIdEmploye());
        assertEquals(NoteJour.TypeNote.Sortie_Caisse, note.getTypeNote());
        assertEquals(0, new BigDecimal("42.500").compareTo(note.getMontant()));
        verify(audit).enregistrer(eq(7), eq("CREATION_NOTE"), eq("notes_jour"), any(), anyString());
    }

    @Test
    @DisplayName("le montant reste un BigDecimal exact")
    void montantExact() {
        when(noteDAO.create(any())).thenReturn(true);

        service.enregistrerNote(7, NoteJour.TypeNote.Credit,
                new BigDecimal("0.001"), "Arrondi");

        ArgumentCaptor<NoteJour> capture = ArgumentCaptor.forClass(NoteJour.class);
        verify(noteDAO).create(capture.capture());
        assertEquals(0, new BigDecimal("0.001").compareTo(capture.getValue().getMontant()),
                "aucune conversion en flottant ne doit intervenir");
    }

    @Test
    @DisplayName("une note sans description est refusée")
    void descriptionObligatoire() {
        assertThrows(ApplicationException.class,
                () -> service.enregistrerNote(7, NoteJour.TypeNote.Autre, BigDecimal.ONE, "  "));
        verifyNoInteractions(noteDAO);
    }

    @Test
    @DisplayName("un montant négatif est refusé")
    void montantNegatifRefuse() {
        ApplicationException e = assertThrows(ApplicationException.class,
                () -> service.enregistrerNote(7, NoteJour.TypeNote.Credit,
                        new BigDecimal("-1.000"), "Erreur de saisie"));

        assertTrue(e.getMessage().toLowerCase().contains("négatif"));
        verifyNoInteractions(noteDAO);
    }

    @Test
    @DisplayName("un crédit sans montant est refusé, une note « Autre » l'accepte")
    void montantObligatoireSaufAutre() {
        assertThrows(ApplicationException.class,
                () -> service.enregistrerNote(7, NoteJour.TypeNote.Credit,
                        BigDecimal.ZERO, "Sans montant"));

        when(noteDAO.create(any())).thenReturn(true);
        assertNotNull(service.enregistrerNote(7, NoteJour.TypeNote.Autre,
                BigDecimal.ZERO, "Remarque du jour"));
    }

    @Test
    @DisplayName("une note sans employé connecté est refusée")
    void employeObligatoire() {
        assertThrows(ApplicationException.class,
                () -> service.enregistrerNote(-1, NoteJour.TypeNote.Autre,
                        BigDecimal.ZERO, "Remarque"));
        verifyNoInteractions(noteDAO);
    }

    @Test
    @DisplayName("une écriture refusée par la base n'est pas tracée comme un succès")
    void ecritureRefuseeNonTracee() {
        when(noteDAO.create(any())).thenReturn(false);

        assertThrows(ApplicationException.class,
                () -> service.enregistrerNote(7, NoteJour.TypeNote.Sortie_Caisse,
                        new BigDecimal("10.000"), "Achat"));

        verify(audit, never()).enregistrer(anyInt(), anyString(), anyString(), any(), anyString());
    }

    // ------------------------------------------------------------------
    // Déplacements
    // ------------------------------------------------------------------

    @Test
    @DisplayName("le départ en déplacement est enregistré et tracé")
    void departTrace() {
        when(deplacementDAO.create(any())).thenReturn(true);

        DeplacementEmploye d = service.demarrerDeplacement(3, "Grossiste", "Réappro");

        assertEquals(3, d.getIdEmploye());
        assertEquals("Grossiste", d.getDestination());
        assertNull(d.getDateFin(), "un déplacement qui commence n'a pas de date de fin");
        verify(audit).enregistrer(eq(3), eq("DEBUT_DEPLACEMENT"), eq("deplacements_employe"),
                any(), anyString());
    }

    @Test
    @DisplayName("une destination vide est refusée")
    void destinationObligatoire() {
        assertThrows(ApplicationException.class,
                () -> service.demarrerDeplacement(3, "   ", "Notes"));
        verifyNoInteractions(deplacementDAO);
    }

    @Test
    @DisplayName("la clôture fige les heures travaillées et les trace")
    void clotureFigeLesHeures() {
        when(deplacementDAO.update(any())).thenReturn(true);
        DeplacementEmploye d = new DeplacementEmploye(3,
                LocalDateTime.now().minusHours(2), "Grossiste", "Réappro");

        service.terminerDeplacement(d, 3);

        assertNotNull(d.getDateFin());
        assertNotNull(d.getHeuresTravaillees());
        assertTrue(d.getHeuresTravaillees().compareTo(BigDecimal.ZERO) > 0,
                "deux heures de déplacement doivent être comptées");
        verify(audit).enregistrer(eq(3), eq("FIN_DEPLACEMENT"), eq("deplacements_employe"),
                any(), anyString());
    }

    @Test
    @DisplayName("un déplacement déjà clos ne peut pas être clos deux fois")
    void clotureUnique() {
        DeplacementEmploye d = new DeplacementEmploye(3,
                LocalDateTime.now().minusHours(2), "Grossiste", null);
        d.setDateFin(LocalDateTime.now());

        assertThrows(ApplicationException.class, () -> service.terminerDeplacement(d, 3));
        verifyNoInteractions(deplacementDAO);
    }

    @Test
    @DisplayName("si la clôture échoue, le déplacement n'est pas marqué terminé en mémoire")
    void echecClotureNeLaissePasDEtatIncoherent() {
        when(deplacementDAO.update(any())).thenReturn(false);
        DeplacementEmploye d = new DeplacementEmploye(3,
                LocalDateTime.now().minusHours(1), "Grossiste", null);

        assertThrows(ApplicationException.class, () -> service.terminerDeplacement(d, 3));

        // L'objet est celui affiché à l'écran : il ne doit pas paraître clos
        // alors que la base ne l'est pas.
        assertNull(d.getDateFin());
    }
}

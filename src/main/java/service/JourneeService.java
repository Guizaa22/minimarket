package service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.DeplacementEmployeDAO;
import dao.NoteJourDAO;
import exception.ApplicationException;
import model.DeplacementEmploye;
import model.NoteJour;

/**
 * Écritures de la journée d'un employé : notes de caisse et déplacements.
 *
 * Ces deux écrans appelaient leur DAO directement depuis le contrôleur. Deux
 * conséquences : les validations n'existaient qu'à l'écran, et surtout rien
 * n'était tracé dans {@code audit_logs}. Or une note de type
 * {@code Sortie_Caisse} constate de l'argent qui quitte le tiroir, et les
 * heures d'un déplacement alimentent la paie — c'est exactement ce que le
 * journal d'audit doit pouvoir restituer.
 *
 * Les montants restent en {@link BigDecimal} de bout en bout.
 */
public class JourneeService {

    private static final Logger LOG = LoggerFactory.getLogger(JourneeService.class);

    private final NoteJourDAO noteDAO;
    private final DeplacementEmployeDAO deplacementDAO;
    private final AuditService audit;

    public JourneeService(NoteJourDAO noteDAO, DeplacementEmployeDAO deplacementDAO,
                          AuditService audit) {
        this.noteDAO = noteDAO;
        this.deplacementDAO = deplacementDAO;
        this.audit = audit;
    }

    public JourneeService() {
        this(new NoteJourDAO(), new DeplacementEmployeDAO(), new AuditService());
    }

    // ------------------------------------------------------------------
    // Notes du jour
    // ------------------------------------------------------------------

    /**
     * Enregistre une note de la journée.
     *
     * @throws ApplicationException si la saisie est invalide ou l'écriture refusée
     */
    public NoteJour enregistrerNote(int employeId, NoteJour.TypeNote type, BigDecimal montant,
                                    String description) {
        if (employeId <= 0) {
            throw new ApplicationException(
                    "Aucun utilisateur connecté. Reconnectez-vous pour enregistrer une note.");
        }
        if (type == null) {
            throw new ApplicationException("Le type de note est obligatoire.");
        }
        if (description == null || description.isBlank()) {
            throw new ApplicationException("La description est obligatoire.");
        }

        BigDecimal valeur = montant != null ? montant : BigDecimal.ZERO;
        if (valeur.signum() < 0) {
            throw new ApplicationException("Le montant ne peut pas être négatif.");
        }
        // Un crédit ou une sortie de caisse sans montant n'a aucun sens : c'est
        // le montant qui fait la note. Seule la note « Autre » peut être nulle.
        if (valeur.signum() == 0 && type != NoteJour.TypeNote.Autre) {
            throw new ApplicationException(
                    "Un montant est obligatoire pour une note de type « " + type.getLibelle() + " ».");
        }

        NoteJour note = new NoteJour(employeId, type, valeur, description.trim(), LocalDateTime.now());
        if (!noteDAO.create(note)) {
            throw new ApplicationException("L'enregistrement de la note a échoué.");
        }

        audit.enregistrer(employeId, "CREATION_NOTE", "notes_jour", note.getId(),
                type.getLibelle() + " — " + valeur + " DT : " + note.getDescription());
        LOG.info("Note « {} » de {} DT enregistrée par l'employé {}", type, valeur, employeId);
        return note;
    }

    public List<NoteJour> listerNotesDuJour(int employeId, LocalDateTime jour) {
        return noteDAO.findByEmployeAndDate(employeId, jour);
    }

    // ------------------------------------------------------------------
    // Déplacements
    // ------------------------------------------------------------------

    /**
     * Ouvre un déplacement (départ de l'employé).
     *
     * @throws ApplicationException si la saisie est invalide ou l'écriture refusée
     */
    public DeplacementEmploye demarrerDeplacement(int employeId, String destination, String notes) {
        if (employeId <= 0) {
            throw new ApplicationException(
                    "Aucun utilisateur connecté. Reconnectez-vous pour enregistrer un déplacement.");
        }
        if (destination == null || destination.isBlank()) {
            throw new ApplicationException("La destination est obligatoire.");
        }

        DeplacementEmploye deplacement = new DeplacementEmploye(
                employeId, LocalDateTime.now(), destination.trim(), notes);

        if (!deplacementDAO.create(deplacement)) {
            throw new ApplicationException("L'enregistrement du déplacement a échoué.");
        }

        audit.enregistrer(employeId, "DEBUT_DEPLACEMENT", "deplacements_employe",
                deplacement.getId(), "Départ vers « " + deplacement.getDestination() + " »");
        return deplacement;
    }

    /**
     * Clôt un déplacement et fige les heures travaillées.
     *
     * Les heures alimentent la paie : elles sont tracées dans le journal
     * d'audit au même titre qu'un mouvement d'argent.
     *
     * @throws ApplicationException si le déplacement est absent ou déjà clos
     */
    public void terminerDeplacement(DeplacementEmploye deplacement, int employeId) {
        if (deplacement == null) {
            throw new ApplicationException("Aucun déplacement en cours.");
        }
        if (deplacement.getDateFin() != null) {
            throw new ApplicationException("Ce déplacement est déjà terminé.");
        }

        deplacement.setDateFin(LocalDateTime.now());
        deplacement.calculerHeuresTravaillees();

        if (!deplacementDAO.update(deplacement)) {
            // L'objet appartient à l'écran appelant : il ne doit pas rester
            // marqué comme terminé alors que la base ne l'est pas.
            deplacement.setDateFin(null);
            throw new ApplicationException("La clôture du déplacement a échoué.");
        }

        audit.enregistrer(employeId, "FIN_DEPLACEMENT", "deplacements_employe",
                deplacement.getId(),
                "Retour de « " + deplacement.getDestination() + " » — "
                + deplacement.getHeuresTravaillees() + " h");
    }
}

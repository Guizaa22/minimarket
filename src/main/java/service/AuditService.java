package service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.AuditLogDAO;
import model.AuditLog;

/**
 * Journalisation des actions métier dans {@code audit_logs}.
 *
 * Distinct du journal technique (Logback) : celui-ci sert au diagnostic, la
 * table d'audit répond à « qui a modifié ce prix », « qui a supprimé ce
 * produit ». Elle sera aussi la source des actions remontées par la future
 * application mobile.
 *
 * Une écriture d'audit qui échoue ne doit jamais faire échouer l'opération
 * métier : l'erreur est journalisée, l'action se poursuit.
 */
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogDAO auditLogDAO;

    public AuditService(AuditLogDAO auditLogDAO) {
        this.auditLogDAO = auditLogDAO;
    }

    public AuditService() {
        this(new AuditLogDAO());
    }

    /**
     * Enregistre une action.
     *
     * @param utilisateurId auteur ({@code <= 0} si inconnu)
     * @param action        verbe court, ex. {@code CREATION_PRODUIT}
     * @param entite        table concernée, ex. {@code produits}
     * @param entiteId      identifiant de la ligne concernée
     * @param details       description lisible
     */
    public void enregistrer(int utilisateurId, String action, String entite,
                            Integer entiteId, String details) {
        try {
            AuditLog log = new AuditLog(
                    utilisateurId > 0 ? utilisateurId : null,
                    action, entite, entiteId, details, LocalDateTime.now());
            auditLogDAO.create(log);
        } catch (Exception e) {
            // Volontairement absorbé : une vente ne doit pas échouer parce que
            // sa trace d'audit n'a pas pu être écrite.
            LOG.error("Écriture du journal d'audit impossible (action={}, entité={})",
                    action, entite, e);
        }
    }
}

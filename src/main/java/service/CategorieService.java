package service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dao.CategorieDAO;
import exception.ApplicationException;
import model.Categorie;
import model.TypeCategorie;

/**
 * Règles métier des catégories de produits.
 *
 * La création se faisait depuis l'écran de gestion du stock, DAO en direct :
 * le contrôle d'unicité vivait dans le contrôleur et rien n'était tracé dans
 * {@code audit_logs}. Or le type d'une catégorie décide du comportement du
 * stock ({@link TypeCategorie}) : passer une catégorie en {@code Tabac} change
 * la façon dont ses produits sont décrémentés, et cela doit pouvoir être
 * retrouvé après coup.
 */
public class CategorieService {

    private static final Logger LOG = LoggerFactory.getLogger(CategorieService.class);

    private final CategorieDAO categorieDAO;
    private final AuditService audit;

    public CategorieService(CategorieDAO categorieDAO, AuditService audit) {
        this.categorieDAO = categorieDAO;
        this.audit = audit;
    }

    public CategorieService() {
        this(new CategorieDAO(), new AuditService());
    }

    public List<Categorie> listerToutes() {
        return categorieDAO.findAll();
    }

    public Categorie parNom(String nom) {
        if (nom == null || nom.isBlank()) {
            return null;
        }
        return categorieDAO.findByNom(nom.trim());
    }

    /**
     * Crée une catégorie.
     *
     * @param image     contenu de la photo, ou {@code null}
     * @param imageMime type MIME de la photo, ou {@code null}
     * @throws ApplicationException si le nom est vide, déjà pris, ou si
     *         l'écriture échoue
     */
    public Categorie creer(String nom, TypeCategorie type, byte[] image, String imageMime,
                           int utilisateurId) {
        if (nom == null || nom.isBlank()) {
            throw new ApplicationException("Le nom de la catégorie est obligatoire.");
        }
        String libelle = nom.trim();

        if (categorieDAO.findByNom(libelle) != null) {
            throw new ApplicationException("Une catégorie nommée « " + libelle + " » existe déjà.");
        }

        // Le type n'est jamais déduit du libellé : c'est un choix explicite,
        // qu'un renommage ne doit pas pouvoir modifier en silence.
        Categorie categorie = new Categorie(libelle,
                type != null ? type : TypeCategorie.Standard);
        if (image != null) {
            categorie.setImage(image);
            categorie.setImageMime(imageMime);
        }

        if (!categorieDAO.create(categorie)) {
            throw new ApplicationException("La création de la catégorie a échoué.");
        }

        audit.enregistrer(utilisateurId, "CREATION_CATEGORIE", "categories", categorie.getId(),
                "« " + libelle + " » (" + categorie.getType() + ")");
        LOG.info("Catégorie « {} » créée ({})", libelle, categorie.getType());
        return categorie;
    }

    /**
     * Met à jour une catégorie. Le renommage reclasse les produits associés,
     * dans la même transaction que la catégorie elle-même (voir le DAO).
     */
    public void modifier(Categorie categorie, int utilisateurId) {
        if (categorie == null) {
            throw new ApplicationException("Catégorie absente.");
        }
        if (categorie.getNom() == null || categorie.getNom().isBlank()) {
            throw new ApplicationException("Le nom de la catégorie est obligatoire.");
        }

        if (!categorieDAO.update(categorie)) {
            throw new ApplicationException("La mise à jour de la catégorie a échoué.");
        }

        audit.enregistrer(utilisateurId, "MODIFICATION_CATEGORIE", "categories",
                categorie.getId(), "« " + categorie.getNom() + " » (" + categorie.getType() + ")");
    }

    /**
     * Supprime une catégorie inutilisée.
     *
     * @throws ApplicationException si des produits y sont encore rattachés
     */
    public void supprimer(Categorie categorie, int utilisateurId) {
        if (categorie == null) {
            throw new ApplicationException("Catégorie absente.");
        }
        try {
            if (!categorieDAO.delete(categorie.getId())) {
                throw new ApplicationException("La suppression de la catégorie a échoué.");
            }
        } catch (java.sql.SQLException e) {
            // Message déjà formulé pour l'utilisateur par le DAO.
            throw new ApplicationException(e.getMessage(), e);
        }

        audit.enregistrer(utilisateurId, "SUPPRESSION_CATEGORIE", "categories",
                categorie.getId(), "« " + categorie.getNom() + " »");
    }
}

package util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.image.Image;

/**
 * Préparation et lecture des photos de produits et de catégories.
 *
 * Les photos sont enregistrées en base. Une photo prise au téléphone pèse
 * plusieurs mégaoctets : stockée telle quelle, elle gonflerait la base et
 * ralentirait chaque affichage de la grille de produits, d'autant plus quand
 * la base est sur le réseau. Les images sont donc redimensionnées et
 * recompressées avant insertion.
 */
public final class ImageUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ImageUtil.class);

    /** Côté maximal de l'image stockée, en pixels. Suffisant pour une vignette de carte. */
    public static final int TAILLE_MAX = 512;

    /** Refus au-delà : au-dessus, il ne s'agit probablement pas d'une photo de produit. */
    public static final long TAILLE_FICHIER_MAX = 15L * 1024 * 1024;

    /** Qualité JPEG visée, en dessous de laquelle on ne descend pas. */
    private static final float QUALITE_MIN = 0.55f;

    private static final String MIME = "image/jpeg";

    private ImageUtil() {
    }

    /** Résultat de la préparation d'une image. */
    public static final class ImagePreparee {
        public final byte[] donnees;
        public final String mime;

        ImagePreparee(byte[] donnees, String mime) {
            this.donnees = donnees;
            this.mime = mime;
        }

        public int tailleKo() {
            return donnees.length / 1024;
        }
    }

    /**
     * Lit un fichier image, le redimensionne et le recompresse.
     *
     * @return l'image prête à être enregistrée, ou null si le fichier est
     *         illisible ou n'est pas une image
     */
    public static ImagePreparee preparer(File fichier) {
        if (fichier == null || !fichier.isFile()) {
            return null;
        }
        if (fichier.length() > TAILLE_FICHIER_MAX) {
            LOG.warn("Image refusée, {} Mo dépasse la limite de {} Mo",
                    fichier.length() / (1024 * 1024), TAILLE_FICHIER_MAX / (1024 * 1024));
            return null;
        }

        try {
            BufferedImage source = ImageIO.read(fichier);
            if (source == null) {
                LOG.warn("Fichier non reconnu comme image : {}", fichier.getName());
                return null;
            }

            BufferedImage redimensionnee = redimensionner(source, TAILLE_MAX);
            byte[] donnees = compresser(redimensionnee);

            LOG.debug("Image préparée : {} -> {} Ko ({}x{})", fichier.getName(),
                    donnees.length / 1024, redimensionnee.getWidth(), redimensionnee.getHeight());

            return new ImagePreparee(donnees, MIME);

        } catch (IOException e) {
            LOG.error("Lecture de l'image {} impossible", fichier.getName(), e);
            return null;
        }
    }

    /**
     * Convertit les octets stockés en image JavaFX affichable.
     *
     * @return null si les données sont absentes ou illisibles ; l'appelant
     *         affiche alors un visuel de remplacement
     */
    public static Image versImageFx(byte[] donnees) {
        if (donnees == null || donnees.length == 0) {
            return null;
        }
        try {
            Image image = new Image(new ByteArrayInputStream(donnees));
            return image.isError() ? null : image;
        } catch (Exception e) {
            LOG.warn("Image illisible en base ({} octets)", donnees.length, e);
            return null;
        }
    }

    // ------------------------------------------------------------------

    /**
     * Réduit l'image pour que son plus grand côté n'excède pas {@code cote},
     * en conservant les proportions. Une image déjà plus petite est renvoyée
     * telle quelle, sans agrandissement qui n'ajouterait aucun détail.
     */
    private static BufferedImage redimensionner(BufferedImage source, int cote) {
        int largeur = source.getWidth();
        int hauteur = source.getHeight();

        if (largeur <= cote && hauteur <= cote) {
            return versRgb(source);
        }

        double facteur = (double) cote / Math.max(largeur, hauteur);
        int nouvelleLargeur = Math.max(1, (int) Math.round(largeur * facteur));
        int nouvelleHauteur = Math.max(1, (int) Math.round(hauteur * facteur));

        // TYPE_INT_RGB : le JPEG ne gère pas la transparence, et une image
        // ARGB donnerait un fond noir sur les PNG détourés.
        BufferedImage cible = new BufferedImage(nouvelleLargeur, nouvelleHauteur, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cible.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, nouvelleLargeur, nouvelleHauteur);
            g.drawImage(source, 0, 0, nouvelleLargeur, nouvelleHauteur, null);
        } finally {
            g.dispose();
        }
        return cible;
    }

    /** Aplatit sur fond blanc, pour que les PNG transparents restent lisibles. */
    private static BufferedImage versRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage cible = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cible.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return cible;
    }

    private static byte[] compresser(BufferedImage image) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", sortie);
        return sortie.toByteArray();
    }

    /** Qualité minimale conservée à la compression. */
    public static float qualiteMinimale() {
        return QUALITE_MIN;
    }
}

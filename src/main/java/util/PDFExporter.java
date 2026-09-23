package util;

import dao.*;
import model.*;
import model.ProduitStats;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Classe utilitaire pour exporter la recette du jour en PDF
 */
public class PDFExporter {
    
    private static final float MARGIN = 50;
    private static final float LINE_HEIGHT = 20;
    private static final float TITLE_FONT_SIZE = 18;
    private static final float HEADER_FONT_SIZE = 12;
    private static final float NORMAL_FONT_SIZE = 10;
    
    public static void exportRecetteJour(File file, LocalDate date, VenteDAO venteDAO, 
                                        PaiementFournisseurDAO paiementDAO, AjoutStockDAO ajoutStockDAO,
                                        NoteJourDAO noteDAO, FournisseurDAO fournisseurDAO, 
                                        ProduitDAO produitDAO, DeplacementEmployeDAO deplacementDAO,
                                        UtilisateurDAO utilisateurDAO, int idEmploye, boolean isAdmin) throws IOException {
        
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDPageContentStream[] contentStreamRef = {new PDPageContentStream(document, page)};
        PDPage[] currentPageRef = {page};
        
        try {
            float[] yPosition = {PDRectangle.A4.getHeight() - MARGIN};
            
            // Titre
            contentStreamRef[0].beginText();
            contentStreamRef[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_FONT_SIZE);
            contentStreamRef[0].newLineAtOffset(MARGIN, yPosition[0]);
            contentStreamRef[0].showText("RECETTE DU JOUR - 2M MARKET");
            contentStreamRef[0].endText();
            yPosition[0] -= LINE_HEIGHT * 1.5f;
            
            // Date et utilisateur
            contentStreamRef[0].beginText();
            contentStreamRef[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
            contentStreamRef[0].newLineAtOffset(MARGIN, yPosition[0]);
            contentStreamRef[0].showText("Date: " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            contentStreamRef[0].endText();
            yPosition[0] -= LINE_HEIGHT;
            
            // Afficher l'employé ou "Tous les employés" pour admin
            contentStreamRef[0].beginText();
            contentStreamRef[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
            contentStreamRef[0].newLineAtOffset(MARGIN, yPosition[0]);
            if (isAdmin) {
                contentStreamRef[0].showText("Rapport: Tous les employés (Admin)");
            } else {
                Utilisateur employe = utilisateurDAO.findById(idEmploye);
                String nomEmploye = employe != null ? employe.getUsername() : "Employé #" + idEmploye;
                contentStreamRef[0].showText("Employé: " + nomEmploye);
            }
            contentStreamRef[0].endText();
            yPosition[0] -= LINE_HEIGHT * 2;
            
            LocalDateTime dateDebut = date.atStartOfDay();
            // Borne de fin exclusive : le début du lendemain, et non
            // 23:59:59 qui laissait échapper la dernière seconde.
            LocalDateTime dateFin = date.plusDays(1).atStartOfDay();
            
            // Résumé
            BigDecimal totalVentes = venteDAO.getTotalRecettes(dateDebut, dateFin);
            BigDecimal totalPaiements = paiementDAO.getTotalByDate(dateDebut);
            BigDecimal totalCredits = BigDecimal.ZERO;
            BigDecimal totalHeures = BigDecimal.ZERO;
            
            // Calculer les totaux selon le rôle
            List<AjoutStock> ajouts;
            List<PaiementFournisseur> paiements;
            List<NoteJour> notes;
            List<DeplacementEmploye> deplacements;
            
            if (isAdmin) {
                // Admin: tous les employés
                ajouts = ajoutStockDAO.findByDate(dateDebut);
                paiements = paiementDAO.findByDate(dateDebut);
                notes = noteDAO.findByDate(dateDebut);
                deplacements = deplacementDAO.findByDate(dateDebut);
            } else {
                // Employé: seulement ses données
                ajouts = ajoutStockDAO.findByEmployeAndDate(idEmploye, dateDebut);
                paiements = paiementDAO.findByEmployeAndDate(idEmploye, dateDebut);
                notes = noteDAO.findByEmployeAndDate(idEmploye, dateDebut);
                deplacements = deplacementDAO.findByEmployeAndDate(idEmploye, dateDebut);
            }
            
            for (AjoutStock ajout : ajouts) {
                totalCredits = totalCredits.add(ajout.getCreditUtilise());
            }
            
            for (DeplacementEmploye deplacement : deplacements) {
                if (deplacement.getHeuresTravaillees() != null) {
                    totalHeures = totalHeures.add(deplacement.getHeuresTravaillees());
                }
            }
            
            BigDecimal recetteNette = totalVentes.subtract(totalPaiements);
            
            yPosition[0] = ajouterSection(contentStreamRef[0], "RÉSUMÉ", yPosition[0]);
            yPosition[0] = ajouterLigne(contentStreamRef[0], "Total Ventes:", String.format("%.2f DT", totalVentes), yPosition[0]);
            yPosition[0] = ajouterLigne(contentStreamRef[0], "Paiements Fournisseurs:", String.format("%.2f DT", totalPaiements), yPosition[0]);
            yPosition[0] = ajouterLigne(contentStreamRef[0], "Crédits Utilisés:", String.format("%.2f DT", totalCredits), yPosition[0]);
            yPosition[0] = ajouterLigne(contentStreamRef[0], "Heures Travaillées:", String.format("%.2f h", totalHeures), yPosition[0]);
            yPosition[0] = ajouterLigne(contentStreamRef[0], "RECETTE NETTE:", String.format("%.2f DT", recetteNette), yPosition[0], true);
            yPosition[0] -= LINE_HEIGHT;
            
            // Ajouts de Stock
            if (!ajouts.isEmpty()) {
                yPosition[0] = ajouterSection(contentStreamRef[0], "AJOUTS DE STOCK", yPosition[0]);
                for (AjoutStock ajout : ajouts) {
                    if (yPosition[0] < MARGIN + 100) {
                        contentStreamRef[0].close();
                        currentPageRef[0] = new PDPage(PDRectangle.A4);
                        document.addPage(currentPageRef[0]);
                        contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                        yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                    }
                    Produit p = produitDAO.findById(ajout.getIdProduit());
                    String produitNom = p != null ? p.getNom() : "Produit #" + ajout.getIdProduit();
                    String fournisseurNom = "N/A";
                    if (ajout.getIdFournisseur() != null) {
                        Fournisseur f = fournisseurDAO.findById(ajout.getIdFournisseur());
                        fournisseurNom = f != null ? f.getNom() : "N/A";
                    }
                    String ligne = String.format("%s - Qty: %d - Paiement: %.2f DT - Crédit: %.2f DT", 
                        produitNom, ajout.getQuantite(), ajout.getMontantPaiement(), ajout.getCreditUtilise());
                    if (isAdmin) {
                        Utilisateur emp = utilisateurDAO.findById(ajout.getIdEmploye());
                        String empNom = emp != null ? emp.getUsername() : "Employé #" + ajout.getIdEmploye();
                        ligne = "[" + empNom + "] " + ligne;
                    }
                    yPosition[0] = ajouterLigne(contentStreamRef[0], "", ligne, yPosition[0]);
                }
                yPosition[0] -= LINE_HEIGHT;
            }
            
            // Paiements Fournisseurs
            if (!paiements.isEmpty()) {
                yPosition[0] = ajouterSection(contentStreamRef[0], "PAIEMENTS FOURNISSEURS", yPosition[0]);
                for (PaiementFournisseur paiement : paiements) {
                    if (yPosition[0] < MARGIN + 100) {
                        contentStreamRef[0].close();
                        currentPageRef[0] = new PDPage(PDRectangle.A4);
                        document.addPage(currentPageRef[0]);
                        contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                        yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                    }
                    Fournisseur f = fournisseurDAO.findById(paiement.getIdFournisseur());
                    String fournisseur = f != null ? f.getNom() : "N/A";
                    String ligne = String.format("%s - %s DT", 
                        fournisseur, 
                        String.format("%.2f", paiement.getMontant()));
                    if (paiement.getNotes() != null && !paiement.getNotes().isEmpty()) {
                        ligne += " - " + paiement.getNotes();
                    }
                    if (isAdmin) {
                        Utilisateur emp = utilisateurDAO.findById(paiement.getIdEmploye());
                        String empNom = emp != null ? emp.getUsername() : "Employé #" + paiement.getIdEmploye();
                        ligne = "[" + empNom + "] " + ligne;
                    }
                    yPosition[0] = ajouterLigne(contentStreamRef[0], "", ligne, yPosition[0]);
                }
                yPosition[0] -= LINE_HEIGHT;
            }
            
            // Déplacements
            if (!deplacements.isEmpty()) {
                yPosition[0] = ajouterSection(contentStreamRef[0], "DÉPLACEMENTS", yPosition[0]);
                for (DeplacementEmploye deplacement : deplacements) {
                    if (yPosition[0] < MARGIN + 100) {
                        contentStreamRef[0].close();
                        currentPageRef[0] = new PDPage(PDRectangle.A4);
                        document.addPage(currentPageRef[0]);
                        contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                        yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                    }
                    String ligne = String.format("%s - %.2f h", 
                        deplacement.getDestination() != null ? deplacement.getDestination() : "N/A",
                        deplacement.getHeuresTravaillees() != null ? deplacement.getHeuresTravaillees() : BigDecimal.ZERO);
                    if (deplacement.getNotes() != null && !deplacement.getNotes().isEmpty()) {
                        ligne += " - " + deplacement.getNotes();
                    }
                    if (isAdmin) {
                        Utilisateur emp = utilisateurDAO.findById(deplacement.getIdEmploye());
                        String empNom = emp != null ? emp.getUsername() : "Employé #" + deplacement.getIdEmploye();
                        ligne = "[" + empNom + "] " + ligne;
                    }
                    yPosition[0] = ajouterLigne(contentStreamRef[0], "", ligne, yPosition[0]);
                }
                yPosition[0] -= LINE_HEIGHT;
            }
            
            // Notes
            if (!notes.isEmpty()) {
                yPosition[0] = ajouterSection(contentStreamRef[0], "NOTES", yPosition[0]);
                for (NoteJour note : notes) {
                    if (yPosition[0] < MARGIN + 100) {
                        contentStreamRef[0].close();
                        currentPageRef[0] = new PDPage(PDRectangle.A4);
                        document.addPage(currentPageRef[0]);
                        contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                        yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                    }
                    String ligne = String.format("[%s] %s", 
                        note.getTypeNote().getLibelle(),
                        note.getDescription());
                    if (note.getMontant().compareTo(BigDecimal.ZERO) > 0) {
                        ligne += " - " + String.format("%.2f DT", note.getMontant());
                    }
                    if (isAdmin) {
                        Utilisateur emp = utilisateurDAO.findById(note.getIdEmploye());
                        String empNom = emp != null ? emp.getUsername() : "Employé #" + note.getIdEmploye();
                        ligne = "[" + empNom + "] " + ligne;
                    }
                    yPosition[0] = ajouterLigne(contentStreamRef[0], "", ligne, yPosition[0]);
                }
                yPosition[0] -= LINE_HEIGHT;
            }
            
            // Statistiques Tabac (cigarettes et paquets)
            BigDecimal totalVentesTabac = venteDAO.getTotalVentesTabac(dateDebut, dateFin);
            if (totalVentesTabac.compareTo(BigDecimal.ZERO) > 0) {
                if (yPosition[0] < MARGIN + 150) {
                    contentStreamRef[0].close();
                    currentPageRef[0] = new PDPage(PDRectangle.A4);
                    document.addPage(currentPageRef[0]);
                    contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                    yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                }
                yPosition[0] = ajouterSection(contentStreamRef[0], "STATISTIQUES TABAC", yPosition[0]);
                yPosition[0] = ajouterLigne(contentStreamRef[0], "Total Ventes Tabac:", String.format("%.2f DT", totalVentesTabac), yPosition[0]);
                
                // Calculer paquets et cigarettes
                int totalPaquets = 0;
                int totalCigarettes = 0;
                // Requête bornée à la journée : findAll() chargeait toutes les
                // ventes jamais enregistrées pour n'en garder qu'un jour, et
                // l'export ralentissait de mois en mois.
                List<Vente> ventes = venteDAO.findByDate(dateDebut);
                
                for (Vente vente : ventes) {
                    List<DetailVente> details = venteDAO.findDetailsByVente(vente.getId());
                    for (DetailVente detail : details) {
                        Produit produit = produitDAO.findById(detail.getProduitId());
                        if (produit != null && produit.isTabac()) {
                            if ("cigarette".equals(detail.getTypeVenteTabac())) {
                                totalCigarettes += detail.getQuantite();
                            } else if ("paquet".equals(detail.getTypeVenteTabac())) {
                                totalPaquets += detail.getQuantite();
                                totalCigarettes += detail.getQuantite() * 20; // 1 paquet = 20 cigarettes
                            } else {
                                // Ancien format, considérer comme paquets
                                totalPaquets += detail.getQuantite();
                                totalCigarettes += detail.getQuantite() * 20;
                            }
                        }
                    }
                }
                
                yPosition[0] = ajouterLigne(contentStreamRef[0], "Paquets vendus:", String.valueOf(totalPaquets), yPosition[0]);
                yPosition[0] = ajouterLigne(contentStreamRef[0], "Cigarettes vendues:", String.valueOf(totalCigarettes), yPosition[0]);
                yPosition[0] -= LINE_HEIGHT;
            }
            
            // Historique des ventes
            List<Vente> ventes;
            if (isAdmin) {
                // Admin: toutes les ventes de la date, groupées par employé
                ventes = venteDAO.findByDate(dateDebut);
                
                // Grouper par employé pour l'admin
                java.util.Map<Integer, List<Vente>> ventesParEmploye = ventes.stream()
                    .collect(java.util.stream.Collectors.groupingBy(Vente::getUtilisateurId));
                
                if (!ventesParEmploye.isEmpty()) {
                    if (yPosition[0] < MARGIN + 150) {
                        contentStreamRef[0].close();
                        currentPageRef[0] = new PDPage(PDRectangle.A4);
                        document.addPage(currentPageRef[0]);
                        contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                        yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                    }
                    yPosition[0] = ajouterSection(contentStreamRef[0], "HISTORIQUE DES VENTES PAR EMPLOYÉ", yPosition[0]);
                    
                    // Afficher les ventes groupées par employé
                    for (java.util.Map.Entry<Integer, List<Vente>> entry : ventesParEmploye.entrySet()) {
                        int empId = entry.getKey();
                        List<Vente> ventesEmp = entry.getValue();
                        Utilisateur emp = utilisateurDAO.findById(empId);
                        String empNom = emp != null ? emp.getUsername() : "Employé #" + empId;
                        
                        if (yPosition[0] < MARGIN + 100) {
                            contentStreamRef[0].close();
                            currentPageRef[0] = new PDPage(PDRectangle.A4);
                            document.addPage(currentPageRef[0]);
                            contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                            yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                        }
                        
                        yPosition[0] = ajouterLigne(contentStreamRef[0], "Employé: " + empNom, 
                            "(" + ventesEmp.size() + " ventes)", yPosition[0], true);
                        yPosition[0] -= LINE_HEIGHT * 0.5f;
                        
                        for (Vente vente : ventesEmp) {
                            if (yPosition[0] < MARGIN + 100) {
                                contentStreamRef[0].close();
                                currentPageRef[0] = new PDPage(PDRectangle.A4);
                                document.addPage(currentPageRef[0]);
                                contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                                yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                            }
                            
                            String heureVente = vente.getDateVente().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                            String ligneVente = String.format("  Vente #%d - %s - %.2f DT", 
                                vente.getId(), heureVente, vente.getTotalVente());
                            
                            // Ajouter les détails des produits
                            List<DetailVente> details = venteDAO.findDetailsByVente(vente.getId());
                            if (!details.isEmpty()) {
                                StringBuilder detailsStr = new StringBuilder();
                                for (int i = 0; i < details.size() && i < 5; i++) {
                                    DetailVente detail = details.get(i);
                                    Produit produit = produitDAO.findById(detail.getProduitId());
                                    if (produit != null) {
                                        if (i > 0) detailsStr.append(", ");
                                        detailsStr.append(produit.getNom()).append(" (").append(detail.getQuantite()).append(")");
                                    }
                                }
                                if (details.size() > 5) {
                                    detailsStr.append("... (+").append(details.size() - 5).append(" autres)");
                                }
                                if (detailsStr.length() > 0) {
                                    ligneVente += " - " + detailsStr.toString();
                                }
                            }
                            
                            // Tronquer la ligne si elle est trop longue pour le PDF
                            if (ligneVente.length() > 100) {
                                ligneVente = ligneVente.substring(0, 97) + "...";
                            }
                            yPosition[0] = ajouterLigne(contentStreamRef[0], "", ligneVente, yPosition[0]);
                        }
                        yPosition[0] -= LINE_HEIGHT;
                    }
                    yPosition[0] -= LINE_HEIGHT;
                }
            } else {
                // Employé: seulement ses ventes
                ventes = venteDAO.findByUtilisateurAndDate(idEmploye, dateDebut, dateFin);
                
                if (!ventes.isEmpty()) {
                    if (yPosition[0] < MARGIN + 150) {
                        contentStreamRef[0].close();
                        currentPageRef[0] = new PDPage(PDRectangle.A4);
                        document.addPage(currentPageRef[0]);
                        contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                        yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                    }
                    yPosition[0] = ajouterSection(contentStreamRef[0], "HISTORIQUE DES VENTES", yPosition[0]);
                    
                    for (Vente vente : ventes) {
                    if (yPosition[0] < MARGIN + 100) {
                        contentStreamRef[0].close();
                        currentPageRef[0] = new PDPage(PDRectangle.A4);
                        document.addPage(currentPageRef[0]);
                        contentStreamRef[0] = new PDPageContentStream(document, currentPageRef[0]);
                        yPosition[0] = PDRectangle.A4.getHeight() - MARGIN;
                    }
                    
                    String heureVente = vente.getDateVente().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String ligneVente = String.format("Vente #%d - %s - %.2f DT", 
                        vente.getId(), heureVente, vente.getTotalVente());
                    
                    // Ajouter les détails des produits
                    List<DetailVente> details = venteDAO.findDetailsByVente(vente.getId());
                    if (!details.isEmpty()) {
                        StringBuilder detailsStr = new StringBuilder();
                        for (int i = 0; i < details.size() && i < 5; i++) {
                            DetailVente detail = details.get(i);
                            Produit produit = produitDAO.findById(detail.getProduitId());
                            if (produit != null) {
                                if (i > 0) detailsStr.append(", ");
                                detailsStr.append(produit.getNom()).append(" (").append(detail.getQuantite()).append(")");
                            }
                        }
                        if (details.size() > 5) {
                            detailsStr.append("... (+").append(details.size() - 5).append(" autres)");
                        }
                        if (detailsStr.length() > 0) {
                            ligneVente += " - " + detailsStr.toString();
                        }
                    }
                    
                    // Afficher l'employé si admin
                    if (isAdmin) {
                        Utilisateur emp = utilisateurDAO.findById(vente.getUtilisateurId());
                        String empNom = emp != null ? emp.getUsername() : "Employé #" + vente.getUtilisateurId();
                        ligneVente = "[" + empNom + "] " + ligneVente;
                    }
                    
                    // Tronquer la ligne si elle est trop longue pour le PDF
                    if (ligneVente.length() > 100) {
                        ligneVente = ligneVente.substring(0, 97) + "...";
                    }
                        yPosition[0] = ajouterLigne(contentStreamRef[0], "", ligneVente, yPosition[0]);
                    }
                    yPosition[0] -= LINE_HEIGHT;
                }
            }
            
            contentStreamRef[0].close();
        } finally {
            if (contentStreamRef[0] != null) {
                try {
                    contentStreamRef[0].close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        
        document.save(file);
        document.close();
    }
    
    /**
     * Exporte un rapport de ventes portant sur une période.
     *
     * Sert les quatre rapports de l'écran « Gestion des ventes » (journalier,
     * hebdomadaire, mensuel, annuel) : seules les bornes changent.
     *
     * Les montants sont reçus en {@link BigDecimal} et mis en forme ici ; ils
     * ne transitent jamais par un type flottant.
     *
     * @param file        fichier PDF à écrire
     * @param titre       intitulé du rapport, ex. « RAPPORT HEBDOMADAIRE »
     * @param debut       début de la période (inclus)
     * @param fin         fin de la période (incluse)
     * @param chiffreAffaires chiffre d'affaires de la période
     * @param benefice    bénéfice de la période
     * @param nombreVentes nombre de ventes encaissées
     * @param topProduits produits les plus vendus, déjà classés
     */
    public static void exportRapportPeriode(File file, String titre,
                                            LocalDateTime debut, LocalDateTime fin,
                                            BigDecimal chiffreAffaires, BigDecimal benefice,
                                            int nombreVentes, List<ProduitStats> topProduits)
            throws IOException {

        DateTimeFormatter jour = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        BigDecimal ca = chiffreAffaires != null ? chiffreAffaires : BigDecimal.ZERO;
        BigDecimal marge = benefice != null ? benefice : BigDecimal.ZERO;

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float y = PDRectangle.A4.getHeight() - MARGIN;

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_FONT_SIZE);
                contentStream.newLineAtOffset(MARGIN, y);
                contentStream.showText(titre + " - 2M MARKET");
                contentStream.endText();
                y -= LINE_HEIGHT * 1.5f;

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
                contentStream.newLineAtOffset(MARGIN, y);
                contentStream.showText("Période : du " + debut.format(jour) + " au " + fin.format(jour));
                contentStream.endText();
                y -= LINE_HEIGHT;

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
                contentStream.newLineAtOffset(MARGIN, y);
                contentStream.showText("Édité le " + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")));
                contentStream.endText();
                y -= LINE_HEIGHT;

                // Synthèse
                y = ajouterSection(contentStream, "SYNTHÈSE", y);
                y = ajouterLigne(contentStream, "Chiffre d'affaires :", String.format("%.2f DT", ca), y);
                y = ajouterLigne(contentStream, "Bénéfice :", String.format("%.2f DT", marge), y);
                y = ajouterLigne(contentStream, "Nombre de ventes :", String.valueOf(nombreVentes), y);

                // Panier moyen : division protégée, une période sans vente est
                // parfaitement normale (jour de fermeture).
                String panierMoyen = nombreVentes > 0
                        ? String.format("%.2f DT", ca.divide(BigDecimal.valueOf(nombreVentes),
                                3, java.math.RoundingMode.HALF_UP))
                        : "-";
                y = ajouterLigne(contentStream, "Panier moyen :", panierMoyen, y);

                String tauxMarge = ca.signum() > 0
                        ? String.format("%.1f %%", marge.divide(ca, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)))
                        : "-";
                y = ajouterLigne(contentStream, "Taux de marge :", tauxMarge, y, true);
                y -= LINE_HEIGHT;

                // Top produits
                y = ajouterSection(contentStream, "PRODUITS LES PLUS VENDUS", y);
                if (topProduits == null || topProduits.isEmpty()) {
                    ajouterLigne(contentStream, "", "Aucune vente sur la période.", y);
                } else {
                    y = ajouterLigne(contentStream, "", String.format("%-4s %-38s %10s %14s",
                            "Rang", "Produit", "Quantité", "CA"), y, true);
                    for (ProduitStats stats : topProduits) {
                        if (y < MARGIN + LINE_HEIGHT * 2) {
                            break;  // une page suffit : le classement est court
                        }
                        String nom = stats.getNomProduit() != null ? stats.getNomProduit() : "";
                        if (nom.length() > 38) {
                            nom = nom.substring(0, 35) + "...";
                        }
                        y = ajouterLigne(contentStream, "", String.format("%-4d %-38s %10d %14s",
                                stats.getRang(), nom, stats.getQuantiteVendue(),
                                String.format("%.2f DT", stats.getChiffreAffaires())), y);
                    }
                }
            }

            document.save(file);
        }
    }

    private static float ajouterSection(PDPageContentStream contentStream, String titre, float y) throws IOException {
        y -= LINE_HEIGHT;
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), HEADER_FONT_SIZE);
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.showText(titre);
        contentStream.endText();
        y -= LINE_HEIGHT * 0.5f;
        return y;
    }
    
    private static float ajouterLigne(PDPageContentStream contentStream, String label, String valeur, float y) throws IOException {
        return ajouterLigne(contentStream, label, valeur, y, false);
    }
    
    private static float ajouterLigne(PDPageContentStream contentStream, String label, String valeur, float y, boolean bold) throws IOException {
        y -= LINE_HEIGHT;
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
        contentStream.newLineAtOffset(MARGIN, y);
        if (!label.isEmpty()) {
            contentStream.showText(label + " ");
        }
        contentStream.setFont(new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
        contentStream.showText(valeur);
        contentStream.endText();
        return y;
    }
    
}

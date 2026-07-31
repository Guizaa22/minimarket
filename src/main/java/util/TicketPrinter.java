package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.DetailVente;
import model.Vente;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Classe utilitaire pour imprimer les tickets de vente
 */
public class TicketPrinter {
    private static final Logger LOG = LoggerFactory.getLogger(TicketPrinter.class);

    
    /**
     * Imprime un ticket de vente
     * @param vente La vente à imprimer
     * @param details La liste des détails de vente
     */
    public static void imprimerTicket(Vente vente, List<DetailVente> details) {
        try {
            // Créer le contenu du ticket
            StringBuilder ticket = new StringBuilder();
            ticket.append("================================\n");
            ticket.append("     2M MARKET - TICKET\n");
            ticket.append("================================\n");
            ticket.append("Date: ").append(vente.getDateVente().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
            ticket.append("Ticket N°: ").append(vente.getId()).append("\n");
            ticket.append("--------------------------------\n");
            ticket.append("ARTICLES:\n");
            ticket.append("--------------------------------\n");
            
            BigDecimal total = BigDecimal.ZERO;
            for (DetailVente detail : details) {
                if (detail == null) continue;
                if (detail.getPrixVenteUnitaire() == null) {
                    LOG.error("Avertissement: Prix de vente manquant pour un détail");
                    continue;
                }
                if (detail.getProduit() == null) {
                    LOG.error("Avertissement: Produit manquant pour un détail");
                    continue;
                }
                BigDecimal sousTotal = detail.getPrixVenteUnitaire().multiply(new BigDecimal(detail.getQuantite()));
                total = total.add(sousTotal);
                ticket.append(String.format("%-20s %2dx %6.2f DT = %6.2f DT\n",
                    truncate(detail.getProduit().getNom(), 20),
                    detail.getQuantite(),
                    detail.getPrixVenteUnitaire().doubleValue(),
                    sousTotal.doubleValue()));
            }
            
            ticket.append("--------------------------------\n");
            ticket.append(String.format("TOTAL: %26.2f DT\n", total.doubleValue()));
            ticket.append("================================\n");
            ticket.append("     MERCI DE VOTRE VISITE\n");
            ticket.append("================================\n");
            
            // Sauvegarder dans un fichier (simulation impression).
            // Écrit dans le dossier de données de l'application et non dans le
            // répertoire courant : celui-ci dépend de la façon dont l'application
            // est lancée et n'est pas toujours accessible en écriture.
            String filename = "ticket_" + vente.getId() + "_" +
                vente.getDateVente().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";
            java.nio.file.Path target = Config.getTicketsDir().resolve(filename);

            try (FileWriter writer = new FileWriter(target.toFile())) {
                writer.write(ticket.toString());
            }

            LOG.info("Ticket imprimé: " + target);
            LOG.info(ticket.toString());
            
        } catch (IOException e) {
            LOG.error("Erreur lors de l'impression du ticket: " + e.getMessage(), e);
        }
    }
    
    /**
     * Tronque une chaîne à une longueur maximale
     */
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}


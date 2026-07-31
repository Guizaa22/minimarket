package exception;

/**
 * Stock insuffisant pour satisfaire une vente.
 *
 * Porte le détail nécessaire au caissier : quel produit, combien il en reste,
 * combien étaient demandés. L'ancien code se contentait d'une SQLException avec
 * un message concaténé, impossible à exploiter autrement qu'en l'affichant brut.
 */
public class StockInsuffisantException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    private final String nomProduit;
    private final int stockDisponible;
    private final int quantiteDemandee;

    public StockInsuffisantException(String nomProduit, int stockDisponible, int quantiteDemandee) {
        super(String.format("Stock insuffisant pour « %s » : %d disponible(s), %d demandé(s)",
                nomProduit, stockDisponible, quantiteDemandee));
        this.nomProduit = nomProduit;
        this.stockDisponible = stockDisponible;
        this.quantiteDemandee = quantiteDemandee;
    }

    @Override
    public String getMessageUtilisateur() {
        if (stockDisponible <= 0) {
            return String.format("« %s » est en rupture de stock.", nomProduit);
        }
        return String.format("Il ne reste que %d « %s » en stock (%d demandé%s).",
                stockDisponible, nomProduit, quantiteDemandee,
                quantiteDemandee > 1 ? "s" : "");
    }

    public String getNomProduit() {
        return nomProduit;
    }

    public int getStockDisponible() {
        return stockDisponible;
    }

    public int getQuantiteDemandee() {
        return quantiteDemandee;
    }
}

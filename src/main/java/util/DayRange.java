package util;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Bornes d'une journée, pour les requêtes « du jour ».
 *
 * Les requêtes utilisaient auparavant {@code DATE(colonne) = DATE(?)}. Comme les
 * dates étaient enregistrées en millisecondes epoch, {@code DATE()} renvoyait NULL
 * pour chaque ligne et ces requêtes ne retournaient jamais rien.
 *
 * On compare désormais sur un intervalle semi-ouvert [début, lendemain[, ce qui
 * fonctionne sur une vraie colonne TIMESTAMP et permet en plus d'utiliser l'index.
 */
public final class DayRange {

    /** Fragment SQL à insérer dans un WHERE. Consomme deux paramètres. */
    public static final String SQL = "%s >= ? AND %s < ?";

    private DayRange() {
    }

    /** Construit la condition SQL pour la colonne indiquée. */
    public static String where(String column) {
        return column + " >= ? AND " + column + " < ?";
    }

    /**
     * Renseigne les deux paramètres de l'intervalle.
     *
     * @param stmt       requête préparée
     * @param firstIndex index du premier des deux paramètres
     * @param day        n'importe quel instant de la journée visée
     * @return index du paramètre suivant
     */
    public static int bind(PreparedStatement stmt, int firstIndex, LocalDateTime day)
            throws SQLException {
        LocalDateTime start = day.toLocalDate().atStartOfDay();
        stmt.setTimestamp(firstIndex, Timestamp.valueOf(start));
        stmt.setTimestamp(firstIndex + 1, Timestamp.valueOf(start.plusDays(1)));
        return firstIndex + 2;
    }

    /**
     * Renseigne les deux paramètres pour une plage de jours, du jour de {@code debut}
     * au jour de {@code fin} inclus. L'intervalle reste semi-ouvert [début, lendemain
     * de fin[, donc {@code debut == fin} équivaut à {@link #bind}.
     *
     * @param stmt       requête préparée
     * @param firstIndex index du premier des deux paramètres
     * @param debut      n'importe quel instant du premier jour de la plage
     * @param fin        n'importe quel instant du dernier jour de la plage
     * @return index du paramètre suivant
     */
    public static int bindRange(PreparedStatement stmt, int firstIndex,
                                LocalDateTime debut, LocalDateTime fin) throws SQLException {
        LocalDateTime start = debut.toLocalDate().atStartOfDay();
        LocalDateTime end = fin.toLocalDate().plusDays(1).atStartOfDay();
        stmt.setTimestamp(firstIndex, Timestamp.valueOf(start));
        stmt.setTimestamp(firstIndex + 1, Timestamp.valueOf(end));
        return firstIndex + 2;
    }
}

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
}

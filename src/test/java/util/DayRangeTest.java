package util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

/**
 * Bornes des requêtes « du jour ».
 *
 * L'intervalle est semi-ouvert {@code [début, lendemain[} : une comparaison
 * inclusive à 23:59:59 laisse échapper la dernière fraction de seconde, et
 * {@code DATE(colonne) = DATE(?)} empêchait en plus l'usage de l'index.
 */
@DisplayName("DayRange")
class DayRangeTest {

    /** Capture les paramètres liés, sans base de données. */
    private static Map<Integer, Timestamp> capturer(Liaison liaison) throws SQLException {
        Map<Integer, Timestamp> parametres = new HashMap<>();
        PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            parametres.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(stmt).setTimestamp(Mockito.anyInt(), Mockito.any(Timestamp.class));

        liaison.appliquer(stmt);
        return parametres;
    }

    @FunctionalInterface
    private interface Liaison {
        void appliquer(PreparedStatement stmt) throws SQLException;
    }

    @Test
    @DisplayName("la condition SQL est semi-ouverte et porte sur la colonne indiquée")
    void conditionSemiOuverte() {
        String where = DayRange.where("date_vente");

        assertEquals("date_vente >= ? AND date_vente < ?", where);
        assertTrue(!where.contains("BETWEEN"),
                "BETWEEN inclut la borne de fin et laisserait échapper la dernière seconde");
        assertTrue(!where.contains("DATE("),
                "une fonction sur la colonne empêcherait l'utilisation de l'index");
    }

    @Test
    @DisplayName("bind borne la journée du minuit au minuit suivant")
    void bindBorneLaJournee() throws SQLException {
        LocalDateTime midi = LocalDate.of(2024, 3, 15).atTime(12, 34, 56);

        Map<Integer, Timestamp> p = capturer(stmt -> DayRange.bind(stmt, 1, midi));

        assertEquals(Timestamp.valueOf(LocalDate.of(2024, 3, 15).atStartOfDay()), p.get(1));
        assertEquals(Timestamp.valueOf(LocalDate.of(2024, 3, 16).atStartOfDay()), p.get(2));
    }

    @Test
    @DisplayName("bind renvoie l'index du paramètre suivant")
    void bindRenvoieLIndexSuivant() throws SQLException {
        PreparedStatement stmt = Mockito.mock(PreparedStatement.class);

        assertEquals(4, DayRange.bind(stmt, 2, LocalDateTime.now()),
                "deux paramètres consommés à partir de l'index 2");
    }

    @Test
    @DisplayName("l'heure de la journée fournie n'influe pas sur les bornes")
    void heureSansInfluence() throws SQLException {
        LocalDateTime tot = LocalDate.of(2024, 3, 15).atTime(0, 0, 1);
        LocalDateTime tard = LocalDate.of(2024, 3, 15).atTime(23, 59, 59);

        assertEquals(capturer(stmt -> DayRange.bind(stmt, 1, tot)),
                     capturer(stmt -> DayRange.bind(stmt, 1, tard)));
    }

    @Test
    @DisplayName("bindRange couvre du premier jour au lendemain du dernier")
    void bindRangeCouvreLaPlage() throws SQLException {
        LocalDateTime debut = LocalDate.of(2024, 3, 11).atTime(9, 0);
        LocalDateTime fin = LocalDate.of(2024, 3, 17).atTime(18, 30);

        Map<Integer, Timestamp> p = capturer(stmt -> DayRange.bindRange(stmt, 1, debut, fin));

        assertEquals(Timestamp.valueOf(LocalDate.of(2024, 3, 11).atStartOfDay()), p.get(1));
        assertEquals(Timestamp.valueOf(LocalDate.of(2024, 3, 18).atStartOfDay()), p.get(2),
                "le dernier jour doit être inclus en entier");
    }

    @Test
    @DisplayName("une plage d'un seul jour équivaut à bind")
    void plageDUnJourEquivautABind() throws SQLException {
        LocalDateTime jour = LocalDate.of(2024, 3, 15).atTime(10, 0);

        assertEquals(capturer(stmt -> DayRange.bind(stmt, 1, jour)),
                     capturer(stmt -> DayRange.bindRange(stmt, 1, jour, jour)));
    }
}

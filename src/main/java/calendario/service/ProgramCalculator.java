package calendario.service;

import calendario.model.ProgramState;
import calendario.model.ProgramSummary;
import calendario.model.TimeRange;

import java.time.*;
import java.util.*;

/**
 * Servicio de cálculo de resumen para un ProgramState.
 * No crea UI, solo recorre fechas y produce totales.
 */
public final class ProgramCalculator {

    /**
     * Calcula el resumen del programa.
     *
     * @param state Estado del programa
     * @return Resumen calculado o null si no hay rango
     */
    public ProgramSummary calculate(ProgramState state) {
        if (state == null || !state.hasRange()) return null;

        LocalDate start = state.minDate();
        LocalDate end = state.maxDate();

        Map<YearMonth, Integer> minByMonth = new TreeMap<>();
        Map<Integer, Integer> minByWeek = new TreeMap<>();
        Set<Integer> weeksWithAny = new HashSet<>();

        int selectedDays = 0;
        int totalMinutes = 0;

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!state.isSelected(d)) continue;

            selectedDays++;

            int mins = 0;
            TimeRange tr = state.getTimeByDay().get(d.getDayOfWeek());
            if (tr != null) {
                mins = tr.minutes();
            }

            totalMinutes += mins;

            YearMonth ym = YearMonth.from(d);
            minByMonth.merge(ym, mins, Integer::sum);

            int wk = weekOfProgram(start, d);
            weeksWithAny.add(wk);
            minByWeek.merge(wk, mins, Integer::sum);
        }

        long daysInRange = Duration.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays();
        long weeksInRange = (daysInRange + 6) / 7;

        return new ProgramSummary(
                start,
                end,
                selectedDays,
                totalMinutes,
                weeksInRange,
                weeksWithAny.size(),
                minByMonth,
                minByWeek
        );
    }

    /**
     * Semana del programa contando desde start.
     *
     * @param start Inicio del programa
     * @param d Fecha objetivo
     * @return Semana 1, 2, 3
     */
    private int weekOfProgram(LocalDate start, LocalDate d) {
        long days = Duration.between(start.atStartOfDay(), d.atStartOfDay()).toDays();
        return (int) (days / 7) + 1;
    }
}

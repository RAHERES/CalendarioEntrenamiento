package calendario.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * Resumen calculado de un programa.
 *
 * @param start Fecha inicial normalizada
 * @param end Fecha final normalizada
 * @param selectedDays Número de días seleccionados efectivamente
 * @param totalMinutes Minutos totales acumulados
 * @param weeksInRange Semanas del rango por calendario simple
 * @param weeksWithTraining Semanas que tienen al menos un día seleccionado
 * @param minutesByMonth Mapa minutos por mes
 * @param minutesByWeek Mapa minutos por semana del programa, semana 1 empieza en start
 */
public record ProgramSummary(
        LocalDate start,
        LocalDate end,
        int selectedDays,
        int totalMinutes,
        long weeksInRange,
        int weeksWithTraining,
        Map<YearMonth, Integer> minutesByMonth,
        Map<Integer, Integer> minutesByWeek
) {}

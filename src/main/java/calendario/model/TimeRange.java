package calendario.model;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Representa un rango horario con hora de inicio y hora de fin.
 * Si la hora de fin es anterior a la hora de inicio, se interpreta como cruce de medianoche.
 *
 * @param start Hora de inicio
 * @param end Hora de fin
 */
public record TimeRange(LocalTime start, LocalTime end) {

    /**
     * Calcula la duración total del rango en minutos.
     * Si end es anterior a start, se asume que el fin ocurre al día siguiente.
     *
     * @return Duración en minutos, nunca negativa.
     */
    public int minutes() {
        LocalTime effectiveEnd = end;
        if (effectiveEnd.isBefore(start)) {
            effectiveEnd = effectiveEnd.plusHours(24);
        }
        long mins = Duration.between(start, effectiveEnd).toMinutes();
        return (int) Math.max(0, mins);
    }
}

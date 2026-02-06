package calendario.model;

/**
 * Evento personalizado asociado a una fecha específica.
 *
 * @param title Título del evento
 * @param description Descripción opcional
 * @param location Ubicación opcional
 * @param time Horario del evento
 * @param reminder Indicador para crear alarma en exportación ICS
 */
public record CalendarEvent(
        String title,
        String description,
        String location,
        TimeRange time,
        boolean reminder
) {}

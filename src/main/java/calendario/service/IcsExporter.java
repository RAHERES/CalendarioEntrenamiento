package calendario.service;

import calendario.model.CalendarEvent;
import calendario.model.ProgramState;
import calendario.model.TimeRange;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Exportador iCalendar.
 * Genera un archivo .ics con un VEVENT por cada sesión de entrenamiento seleccionada con horario,
 * y un VEVENT por cada evento personalizado.
 */
public final class IcsExporter {

    /**
     * Exporta un ProgramState a formato iCalendar.
     *
     * @param state Estado del programa
     * @param path Ruta del archivo .ics
     * @throws Exception Error de escritura
     */
    public void export(ProgramState state, Path path) throws Exception {
        if (state == null || !state.hasRange()) {
            throw new IllegalStateException("No hay rango definido para exportar.");
        }

        String tzid = ZoneId.systemDefault().getId();
        String prodId = "-//CalendarioEntrenamiento//1.0//ES";

        StringBuilder sb = new StringBuilder(16_384);

        vline(sb, "BEGIN:VCALENDAR");
        vline(sb, "PRODID:" + prodId);
        vline(sb, "VERSION:2.0");
        vline(sb, "CALSCALE:GREGORIAN");
        vline(sb, "METHOD:PUBLISH");

        LocalDate start = state.minDate();
        LocalDate end = state.maxDate();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!state.isSelected(d)) continue;

            TimeRange tr = state.getTimeByDay().get(d.getDayOfWeek());
            if (tr == null) continue;

            LocalDateTime dtStart = LocalDateTime.of(d, tr.start());
            LocalDateTime dtEnd = tr.end().isBefore(tr.start())
                    ? LocalDateTime.of(d.plusDays(1), tr.end())
                    : LocalDateTime.of(d, tr.end());

            String summary = "Entrenamiento (" + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("es","ES")) + ")";
            String uid = d.toString().replace("-", "") + "-" + UUID.randomUUID();

            vline(sb, "BEGIN:VEVENT");
            vline(sb, "UID:" + uid);
            vline(sb, "SUMMARY:" + esc(summary));
            vline(sb, "DTSTAMP:" + fmtUtc(LocalDateTime.now(ZoneId.of("UTC"))));
            vline(sb, "DTSTART;TZID=" + tzid + ":" + fmtLocal(dtStart));
            vline(sb, "DTEND;TZID=" + tzid + ":" + fmtLocal(dtEnd));
            vline(sb, "END:VEVENT");
        }

        for (Map.Entry<LocalDate, List<CalendarEvent>> ent : state.getEvents().entrySet()) {
            LocalDate fecha = ent.getKey();
            for (CalendarEvent ev : ent.getValue()) {
                TimeRange tr = ev.time();
                LocalDateTime dtStart = LocalDateTime.of(fecha, tr.start());
                LocalDateTime dtEnd = tr.end().isBefore(tr.start())
                        ? LocalDateTime.of(fecha.plusDays(1), tr.end())
                        : LocalDateTime.of(fecha, tr.end());

                String uid = fecha.toString().replace("-", "") + "-evt-" + UUID.randomUUID();

                vline(sb, "BEGIN:VEVENT");
                vline(sb, "UID:" + uid);
                vline(sb, "SUMMARY:" + esc(ev.title()));
                if (ev.description() != null && !ev.description().isBlank()) {
                    vline(sb, "DESCRIPTION:" + esc(ev.description()));
                }
                if (ev.location() != null && !ev.location().isBlank()) {
                    vline(sb, "LOCATION:" + esc(ev.location()));
                }
                vline(sb, "DTSTAMP:" + fmtUtc(LocalDateTime.now(ZoneId.of("UTC"))));
                vline(sb, "DTSTART;TZID=" + tzid + ":" + fmtLocal(dtStart));
                vline(sb, "DTEND;TZID=" + tzid + ":" + fmtLocal(dtEnd));

                if (ev.reminder()) {
                    vline(sb, "BEGIN:VALARM");
                    vline(sb, "TRIGGER:-PT10M");
                    vline(sb, "ACTION:DISPLAY");
                    vline(sb, "DESCRIPTION:" + esc(ev.title()));
                    vline(sb, "END:VALARM");
                }

                vline(sb, "END:VEVENT");
            }
        }

        vline(sb, "END:VCALENDAR");

        String ics = sb.toString().replace("\n", "\r\n");
        Files.writeString(path, ics, StandardCharsets.UTF_8);
    }

    private static void vline(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace(";","\\;").replace(",","\\,");
    }

    private static String fmtLocal(LocalDateTime ldt) {
        return String.format("%04d%02d%02dT%02d%02d%02d",
                ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                ldt.getHour(), ldt.getMinute(), ldt.getSecond());
    }

    private static String fmtUtc(LocalDateTime utc) {
        return String.format("%04d%02d%02dT%02d%02d%02dZ",
                utc.getYear(), utc.getMonthValue(), utc.getDayOfMonth(),
                utc.getHour(), utc.getMinute(), utc.getSecond());
    }
}

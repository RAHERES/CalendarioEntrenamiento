package calendario.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Estado del programa.
 * Contiene rango, filtro por días, horarios por día, excepciones por fecha y eventos.
 * Esta clase es la fuente única de verdad para decidir si una fecha está seleccionada.
 */
public final class ProgramState {

    /**
     * Fecha de inicio del rango.
     */
    private LocalDate start;

    /**
     * Fecha de fin del rango.
     */
    private LocalDate end;

    /**
     * Días de entrenamiento activos como filtro.
     * Si está vacío, el rango completo se considera seleccionado.
     */
    private final EnumSet<DayOfWeek> trainingDays = EnumSet.noneOf(DayOfWeek.class);

    /**
     * Horarios asignados por día de la semana.
     */
    private final EnumMap<DayOfWeek, TimeRange> timeByDay = new EnumMap<>(DayOfWeek.class);

    /**
     * Excepciones para forzar selección por fecha.
     */
    private final Set<LocalDate> forceOn = new HashSet<>();

    /**
     * Excepciones para forzar deselección por fecha.
     */
    private final Set<LocalDate> forceOff = new HashSet<>();

    /**
     * Eventos personalizados por fecha.
     */
    private final Map<LocalDate, List<CalendarEvent>> events = new HashMap<>();

    /**
     * Último clic extra fuera del rango para mantener una selección única fuera de rango.
     */
    private LocalDate lastOutsideClick = null;

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public void setStart(LocalDate start) {
        this.start = start;
    }

    public void setEnd(LocalDate end) {
        this.end = end;
    }

    public EnumSet<DayOfWeek> getTrainingDays() {
        return trainingDays;
    }

    public EnumMap<DayOfWeek, TimeRange> getTimeByDay() {
        return timeByDay;
    }

    public Set<LocalDate> getForceOn() {
        return forceOn;
    }

    public Set<LocalDate> getForceOff() {
        return forceOff;
    }

    public Map<LocalDate, List<CalendarEvent>> getEvents() {
        return events;
    }

    /**
     * Indica si el rango está definido.
     *
     * @return true si start y end no son null.
     */
    public boolean hasRange() {
        return start != null && end != null;
    }

    /**
     * Devuelve el mínimo del rango normalizado.
     *
     * @return Fecha mínima.
     */
    public LocalDate minDate() {
        if (!hasRange()) return null;
        return start.isBefore(end) ? start : end;
    }

    /**
     * Devuelve el máximo del rango normalizado.
     *
     * @return Fecha máxima.
     */
    public LocalDate maxDate() {
        if (!hasRange()) return null;
        return end.isAfter(start) ? end : start;
    }

    /**
     * Indica si una fecha está dentro del rango actual.
     *
     * @param d Fecha a evaluar
     * @return true si está dentro del rango
     */
    public boolean isInsideRange(LocalDate d) {
        if (!hasRange()) return false;
        LocalDate a = minDate();
        LocalDate z = maxDate();
        return !d.isBefore(a) && !d.isAfter(z);
    }

    /**
     * Selección efectiva final para una fecha.
     * Prioridad:
     * forceOn, luego forceOff, luego rango y filtro por dayOfWeek.
     *
     * @param d Fecha
     * @return true si se considera seleccionada
     */
    public boolean isSelected(LocalDate d) {
        if (forceOn.contains(d)) return true;
        if (forceOff.contains(d)) return false;

        if (!isInsideRange(d)) return false;

        if (trainingDays.isEmpty()) return true;
        return trainingDays.contains(d.getDayOfWeek());
    }

    /**
     * Define un rango completo.
     *
     * @param a Fecha A
     * @param z Fecha Z
     */
    public void setRange(LocalDate a, LocalDate z) {
        this.start = a;
        this.end = z;
    }

    /**
     * Cierra el rango usando start como ancla, asignando end a la fecha indicada.
     * Si start está vacío, se asigna start y se deja end null.
     *
     * @param d Fecha seleccionada para cierre
     */
    public void closeRangeAt(LocalDate d) {
        if (start == null) {
            start = d;
            end = null;
            return;
        }
        if (d.isBefore(start)) {
            end = start;
            start = d;
        } else {
            end = d;
        }
    }

    /**
     * Ajusta un rango con comportamiento de Shift click.
     * Si no hay start, se asigna start.
     * Si hay start sin end, se cierra.
     * Si hay rango completo, se ajusta tomando start como referencia y d como nuevo end.
     *
     * @param d Fecha objetivo
     */
    public void adjustRangeWith(LocalDate d) {
        if (start == null) {
            start = d;
            end = null;
            return;
        }

        if (end == null) {
            closeRangeAt(d);
            return;
        }

        if (d.isBefore(start)) {
            end = start;
            start = d;
        } else {
            end = d;
        }
    }

    /**
     * Alterna excepción de fecha, útil para Ctrl click.
     * Si está seleccionado, pasa a forceOff.
     * Si no está seleccionado, pasa a forceOn.
     *
     * @param d Fecha
     */
    public void toggleException(LocalDate d) {
        if (isSelected(d)) {
            forceOn.remove(d);
            forceOff.add(d);
        } else {
            forceOff.remove(d);
            forceOn.add(d);
        }
    }

    /**
     * Selección puntual fuera del rango.
     * Mantiene máximo una selección extra fuera del rango, salvo que se fuerce con forceOn.
     *
     * @param d Fecha fuera del rango
     */
    public void toggleOutsideSelection(LocalDate d) {
        if (isInsideRange(d)) return;

        if (lastOutsideClick != null
                && !lastOutsideClick.equals(d)
                && !forceOn.contains(lastOutsideClick)) {
            forceOn.remove(lastOutsideClick);
        }

        if (forceOn.contains(d)) {
            forceOn.remove(d);
            if (d.equals(lastOutsideClick)) lastOutsideClick = null;
        } else {
            forceOn.add(d);
            lastOutsideClick = d;
        }
    }

    /**
     * Fuerza selección de una fecha sin tocar otras reglas.
     *
     * @param d Fecha
     */
    public void forceOn(LocalDate d) {
        forceOff.remove(d);
        forceOn.add(d);
    }

    /**
     * Fuerza deselección de una fecha sin tocar otras reglas.
     *
     * @param d Fecha
     */
    public void forceOff(LocalDate d) {
        forceOn.remove(d);
        forceOff.add(d);
    }

    /**
     * Copia el estado completo desde otro ProgramState.
     *
     * @param other Estado fuente
     */
    public void copyFrom(ProgramState other) {
        this.start = other.start;
        this.end = other.end;

        this.trainingDays.clear();
        this.trainingDays.addAll(other.trainingDays);

        this.timeByDay.clear();
        this.timeByDay.putAll(other.timeByDay);

        this.forceOn.clear();
        this.forceOn.addAll(other.forceOn);

        this.forceOff.clear();
        this.forceOff.addAll(other.forceOff);

        this.events.clear();
        for (Map.Entry<LocalDate, List<CalendarEvent>> e : other.events.entrySet()) {
            this.events.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        this.lastOutsideClick = other.lastOutsideClick;
    }
}

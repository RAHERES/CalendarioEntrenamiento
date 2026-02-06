package calendario.service;

import calendario.model.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.*;

/**
 * Servicio de persistencia para ProgramState.
 * Guarda y carga JSON con Jackson, guarda CSV simple.
 */
public final class ProgramStorage {

    private final ObjectMapper mapper;

    public ProgramStorage() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Guarda el estado como JSON incluyendo totales calculados.
     *
     * @param state Estado
     * @param path Archivo destino
     * @param calculator Calculadora para incluir resumen
     * @throws Exception Error de IO o serialización
     */
    public void saveJson(ProgramState state, Path path, ProgramCalculator calculator) throws Exception {
        Objects.requireNonNull(state);
        Objects.requireNonNull(path);

        ProgramSummary summary = (calculator != null) ? calculator.calculate(state) : null;

        SaveDto dto = SaveDto.from(state, summary);
        String json = mapper.writeValueAsString(dto);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /**
     * Carga el estado desde JSON.
     *
     * @param path Archivo JSON
     * @return Estado cargado
     * @throws Exception Error de IO o parseo
     */
    public ProgramState loadJson(Path path) throws Exception {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        SaveDto dto = mapper.readValue(json, SaveDto.class);
        return dto.toState();
    }

    /**
     * Guarda CSV con fechas seleccionadas.
     *
     * @param state Estado
     * @param path Archivo destino
     * @param calculator Calculadora para generar resumen
     * @throws Exception Error de IO
     */
    public void saveCsv(ProgramState state, Path path, ProgramCalculator calculator) throws Exception {
        ProgramSummary s = (calculator != null) ? calculator.calculate(state) : null;
        if (s == null) throw new IllegalStateException("No hay rango para exportar CSV.");

        StringBuilder sb = new StringBuilder(4096);
        sb.append("fecha,dow,minutos\n");

        for (var d = s.start(); !d.isAfter(s.end()); d = d.plusDays(1)) {
            if (!state.isSelected(d)) continue;
            TimeRange tr = state.getTimeByDay().get(d.getDayOfWeek());
            int mins = tr == null ? 0 : tr.minutes();
            sb.append(d).append(",").append(d.getDayOfWeek()).append(",").append(mins).append("\n");
        }

        sb.append("\nresumen,valor\n");
        sb.append("semanas_del_rango,").append(s.weeksInRange()).append("\n");
        sb.append("semanas_con_entrenamiento,").append(s.weeksWithTraining()).append("\n");
        sb.append("dias_seleccionados,").append(s.selectedDays()).append("\n");
        sb.append("minutos_totales,").append(s.totalMinutes()).append("\n");

        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * DTO de persistencia para JSON.
     * Se usa para desacoplar el JSON del modelo interno y evitar serializar estructuras complejas directamente.
     */
    public static final class SaveDto {

        public String start;
        public String end;

        public List<String> trainingDays;
        public Map<String, TimeRangeDto> timeByDay;

        public List<String> forceOn;
        public List<String> forceOff;

        public Map<String, List<EventDto>> events;

        public SummaryDto totals;

        public SaveDto() {}

        static SaveDto from(ProgramState state, ProgramSummary summary) {
            SaveDto dto = new SaveDto();
            dto.start = state.getStart() == null ? null : state.getStart().toString();
            dto.end = state.getEnd() == null ? null : state.getEnd().toString();

            dto.trainingDays = new ArrayList<>();
            for (DayOfWeek d : state.getTrainingDays()) dto.trainingDays.add(d.name());

            dto.timeByDay = new LinkedHashMap<>();
            for (var e : state.getTimeByDay().entrySet()) {
                dto.timeByDay.put(e.getKey().name(), TimeRangeDto.from(e.getValue()));
            }

            dto.forceOn = new ArrayList<>();
            for (var d : state.getForceOn()) dto.forceOn.add(d.toString());

            dto.forceOff = new ArrayList<>();
            for (var d : state.getForceOff()) dto.forceOff.add(d.toString());

            dto.events = new LinkedHashMap<>();
            for (var e : state.getEvents().entrySet()) {
                List<EventDto> list = new ArrayList<>();
                for (CalendarEvent ev : e.getValue()) list.add(EventDto.from(ev));
                dto.events.put(e.getKey().toString(), list);
            }

            if (summary != null) {
                dto.totals = SummaryDto.from(summary);
            }
            return dto;
        }

        ProgramState toState() {
            ProgramState st = new ProgramState();

            if (start != null) st.setStart(java.time.LocalDate.parse(start));
            if (end != null) st.setEnd(java.time.LocalDate.parse(end));

            st.getTrainingDays().clear();
            if (trainingDays != null) {
                for (String s : trainingDays) {
                    try {
                        st.getTrainingDays().add(DayOfWeek.valueOf(s));
                    } catch (Exception ignore) {}
                }
            }

            st.getTimeByDay().clear();
            if (timeByDay != null) {
                for (var e : timeByDay.entrySet()) {
                    try {
                        DayOfWeek dow = DayOfWeek.valueOf(e.getKey());
                        st.getTimeByDay().put(dow, e.getValue().toModel());
                    } catch (Exception ignore) {}
                }
            }

            st.getForceOn().clear();
            if (forceOn != null) {
                for (String s : forceOn) st.getForceOn().add(java.time.LocalDate.parse(s));
            }

            st.getForceOff().clear();
            if (forceOff != null) {
                for (String s : forceOff) st.getForceOff().add(java.time.LocalDate.parse(s));
            }

            st.getEvents().clear();
            if (events != null) {
                for (var e : events.entrySet()) {
                    java.time.LocalDate date = java.time.LocalDate.parse(e.getKey());
                    List<CalendarEvent> list = new ArrayList<>();
                    for (EventDto ev : e.getValue()) list.add(ev.toModel());
                    st.getEvents().put(date, list);
                }
            }

            return st;
        }
    }

    public static final class TimeRangeDto {
        public String start;
        public String end;

        public TimeRangeDto() {}

        static TimeRangeDto from(TimeRange tr) {
            TimeRangeDto dto = new TimeRangeDto();
            dto.start = tr.start().toString();
            dto.end = tr.end().toString();
            return dto;
        }

        TimeRange toModel() {
            return new TimeRange(java.time.LocalTime.parse(start), java.time.LocalTime.parse(end));
        }
    }

    public static final class EventDto {
        public String title;
        public String description;
        public String location;
        public TimeRangeDto time;
        public boolean reminder;

        public EventDto() {}

        static EventDto from(CalendarEvent ev) {
            EventDto dto = new EventDto();
            dto.title = ev.title();
            dto.description = ev.description();
            dto.location = ev.location();
            dto.time = TimeRangeDto.from(ev.time());
            dto.reminder = ev.reminder();
            return dto;
        }

        CalendarEvent toModel() {
            return new CalendarEvent(
                    title == null ? "" : title,
                    description == null ? "" : description,
                    location == null ? "" : location,
                    time == null ? new TimeRange(java.time.LocalTime.of(0,0), java.time.LocalTime.of(0,0)) : time.toModel(),
                    reminder
            );
        }
    }

    public static final class SummaryDto {
        public String start;
        public String end;
        public long weeksInRange;
        public int weeksWithTraining;
        public int selectedDays;
        public int totalMinutes;

        public SummaryDto() {}

        static SummaryDto from(ProgramSummary s) {
            SummaryDto dto = new SummaryDto();
            dto.start = s.start().toString();
            dto.end = s.end().toString();
            dto.weeksInRange = s.weeksInRange();
            dto.weeksWithTraining = s.weeksWithTraining();
            dto.selectedDays = s.selectedDays();
            dto.totalMinutes = s.totalMinutes();
            return dto;
        }
    }
}

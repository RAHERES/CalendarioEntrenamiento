package com.example.calendarioentrenamiento;



import calendario.model.CalendarEvent;
import calendario.model.ProgramState;
import calendario.model.ProgramSummary;
import calendario.model.TimeRange;
import calendario.service.IcsExporter;
import calendario.service.ProgramCalculator;
import calendario.service.ProgramStorage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Diálogo anual para seleccionar un rango de fechas, filtrar por días de entrenamiento,
 * asignar horarios por día de la semana, gestionar excepciones por fecha (forceOn/forceOff),
 * y administrar eventos personalizados por día.
 *
 * Reglas de responsabilidad:
 * - Esta clase solo arma interfaz y coordina servicios.
 * - La lógica de selección vive en {@link ProgramState}.
 * - Los cálculos del resumen viven en {@link ProgramCalculator}.
 * - Guardar y cargar viven en {@link ProgramStorage}.
 * - Exportar ICS vive en {@link IcsExporter}.
 */
public class CalendarioAnualDialog {

    /* =========================
       Dependencias de negocio
       ========================= */

    /**
     * Estado del programa de entrenamiento, contiene rango, filtros, horarios, excepciones y eventos.
     */
    private final ProgramState state;

    /**
     * Servicio de cálculos del programa y resumen.
     */
    private final ProgramCalculator calculator;

    /**
     * Servicio de persistencia JSON y CSV.
     */
    private final ProgramStorage storage;

    /**
     * Servicio de exportación iCalendar.
     */
    private final IcsExporter icsExporter;

    /* =========================
       Estado visual
       ========================= */

    /**
     * Año actualmente mostrado en el calendario anual.
     */
    private LocalDate selectedDate = LocalDate.now();

    /**
     * Etiqueta del año en el encabezado.
     */
    private Label yearLabel;

    /**
     * Contenedor de mini calendarios por mes.
     */
    private TilePane monthsGrid;

    /**
     * Contenedor del resumen (se renderiza con {@link #renderResumen(ProgramSummary)}).
     */
    private VBox resumenBox;

    /**
     * Mapa de checkboxes para poder sincronizar selección al cargar.
     */
    private final EnumMap<DayOfWeek, CheckBox> checksPorDia = new EnumMap<>(DayOfWeek.class);

    /**
     * Bandera para evitar diálogos de horario al cargar desde archivo.
     */
    private boolean cargandoDesdeArchivo = false;

    /* =========================
       Estilos
       ========================= */

    private static final String ROUNDED = "-fx-background-radius:6; -fx-border-radius:6;";
    private static final String STYLE_NORMAL   = "-fx-background-color:white; -fx-text-fill:black; -fx-border-color:transparent;";
    private static final String STYLE_START    = "-fx-background-color:#1976D2; -fx-text-fill:white;" + ROUNDED;
    private static final String STYLE_END      = "-fx-background-color:#E53935; -fx-text-fill:white;" + ROUNDED;
    private static final String STYLE_SELECTED = "-fx-background-color:#90CAF9; -fx-text-fill:black;" + ROUNDED;
    private static final String STYLE_TODAY =
            "-fx-border-color:#FF9800; -fx-border-width:2; -fx-background-color:white; -fx-text-fill:black; -fx-background-radius:6; -fx-border-radius:6;";


    /**
     * Nombres de meses en español para encabezados.
     */
    private final List<String> monthsES = Arrays.asList(
            "Enero","Febrero","Marzo","Abril","Mayo","Junio",
            "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"
    );

    /* =========================
       Drag y preview de rango
       ========================= */

    /**
     * Indica si está activo el arrastre para previsualizar rango.
     */
    private boolean dragging = false;

    /**
     * Inicio del preview durante el drag.
     */
    private LocalDate previewStart = null;

    /**
     * Fin del preview durante el drag.
     */
    private LocalDate previewEnd = null;

    /* =========================
       Constructores
       ========================= */

    /**
     * Crea el diálogo con servicios por defecto.
     *
     * Requisitos:
     * - Deben existir las clases en model y service.
     */
    public CalendarioAnualDialog() {
        this(new ProgramState(), new ProgramCalculator(), new ProgramStorage(), new IcsExporter());
    }

    /**
     * Crea el diálogo inyectando dependencias.
     *
     * @param state Estado del programa.
     * @param calculator Servicio de cálculo.
     * @param storage Servicio de guardado y carga.
     * @param icsExporter Servicio de exportación ICS.
     */
    public CalendarioAnualDialog(ProgramState state,
                                 ProgramCalculator calculator,
                                 ProgramStorage storage,
                                 IcsExporter icsExporter) {
        this.state = Objects.requireNonNull(state);
        this.calculator = Objects.requireNonNull(calculator);
        this.storage = Objects.requireNonNull(storage);
        this.icsExporter = Objects.requireNonNull(icsExporter);
    }

    /* =========================
       API pública
       ========================= */

    /**
     * Muestra el diálogo modal.
     *
     * @param owner Ventana dueña.
     * @param lblSalida Etiqueta que recibirá la fecha o rango seleccionado para mostrar externamente.
     */
    public void mostrar(Stage owner, Label lblSalida) {
        Stage ventana = new Stage();
        ventana.setTitle("Calendario anual");
        ventana.initModality(Modality.WINDOW_MODAL);
        ventana.initOwner(owner);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #F7F7F7;");

        HBox header = buildHeader();
        HBox selectorDias = buildDaySelector();

        monthsGrid = new TilePane();
        monthsGrid.setHgap(12);
        monthsGrid.setVgap(12);
        monthsGrid.setPrefColumns(3);
        monthsGrid.setTileAlignment(Pos.TOP_CENTER);
        monthsGrid.setPadding(new Insets(10));

        resumenBox = new VBox(8);
        resumenBox.setPadding(new Insets(10, 12, 10, 12));
        resumenBox.setStyle("-fx-background-color:#FFFFFF; -fx-border-color:#DDD; -fx-border-radius:8; -fx-background-radius:8;");
        resumenBox.setMaxWidth(Double.MAX_VALUE);

        ScrollPane resumenScroll = new ScrollPane(resumenBox);
        resumenScroll.setFitToWidth(true);
        resumenScroll.setPrefViewportHeight(220);

        HBox footer = buildFooter(ventana, lblSalida);

        // Pintar inicial
        if (state.getStart() != null) {
            selectedDate = state.getStart();
        }
        reconstruirMeses();
        renderResumen(calculator.calculate(state));

        root.getChildren().addAll(header, selectorDias, monthsGrid, resumenScroll, footer);

        Scene scene = new Scene(root, 1200, 720);
        ventana.setScene(scene);
        ventana.showAndWait();
    }

    /**
     * Devuelve el año que se está visualizando.
     *
     * @return Fecha base del año mostrado.
     */
    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    /* =========================
       Construcción UI
       ========================= */

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER);

        Button prevYear = new Button("<");
        prevYear.setOnAction(e -> cambiarAnio(-1));
        estilizarBotonHeader(prevYear);

        yearLabel = new Label(String.valueOf(selectedDate.getYear()));
        yearLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        Button nextYear = new Button(">");
        nextYear.setOnAction(e -> cambiarAnio(1));
        estilizarBotonHeader(nextYear);

        header.getChildren().addAll(prevYear, yearLabel, nextYear);
        return header;
    }

    private HBox buildDaySelector() {
        HBox selectorDias = new HBox(10);
        selectorDias.setAlignment(Pos.CENTER);

        DayOfWeek[] orden = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };

        for (DayOfWeek dow : orden) {
            String etq = dow.getDisplayName(TextStyle.SHORT, new Locale("es", "ES"));
            CheckBox cb = new CheckBox(etq);
            checksPorDia.put(dow, cb);

            cb.setSelected(state.getTrainingDays().contains(dow));

            cb.setOnAction(e -> {
                if (cb.isSelected()) {
                    state.getTrainingDays().add(dow);

                    if (!cargandoDesdeArchivo) {
                        mostrarSelectorHorario(dow);
                    }
                } else {
                    state.getTrainingDays().remove(dow);
                    state.getTimeByDay().remove(dow);
                }
                reconstruirMeses();
                renderResumen(calculator.calculate(state));
            });

            selectorDias.getChildren().add(cb);
        }

        return selectorDias;
    }

    private HBox buildFooter(Stage ventana, Label lblSalida) {
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.BOTTOM_RIGHT);

        Button btnCargar = new Button("Cargar…");
        btnCargar.setOnAction(e -> cargarPrograma());

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setOnAction(e -> ventana.close());
        btnCancelar.setStyle("-fx-background-color:#E0E0E0; -fx-text-fill:black;");

        Button btnOk = new Button("OK");
        btnOk.setStyle("-fx-background-color:#E0E0E0; -fx-text-fill:black;");
        btnOk.setOnAction(e -> {
            if (state.hasRange()) {
                lblSalida.setText(state.minDate() + " hasta " + state.maxDate());
            } else if (state.getStart() != null) {
                lblSalida.setText(state.getStart().toString());
            } else {
                lblSalida.setText("");
            }
            ventana.close();
        });

        Button btnGuardar = new Button("Guardar…");
        btnGuardar.setOnAction(e -> guardarPrograma());

        Button btnExportIcs = new Button("Exportar .ics");
        btnExportIcs.setOnAction(e -> exportarICS());

        footer.getChildren().addAll(btnCargar, btnCancelar, btnOk, btnGuardar, btnExportIcs);
        return footer;
    }

    /* =========================
       Navegación
       ========================= */

    private void cambiarAnio(int delta) {
        selectedDate = selectedDate.plusYears(delta);
        yearLabel.setText(String.valueOf(selectedDate.getYear()));
        reconstruirMeses();
        renderResumen(calculator.calculate(state));
    }

    /* =========================
       Renderizado de meses
       ========================= */

    private void reconstruirMeses() {
        monthsGrid.getChildren().clear();
        int year = selectedDate.getYear();

        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            VBox monthPane = crearMiniCalendario(ym);
            monthPane.setPrefWidth(250);
            monthsGrid.getChildren().add(monthPane);
        }
    }

    private VBox crearMiniCalendario(YearMonth ym) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color:white; -fx-border-color:#DDD; -fx-border-radius:8; -fx-background-radius:8;");
        box.setPrefWidth(260);

        Label lblMes = new Label(monthsES.get(ym.getMonthValue() - 1) + " " + ym.getYear());
        lblMes.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        String[] weekDays = {"D","L","M","M","J","V","S"};
        for (int i = 0; i < weekDays.length; i++) {
            Label d = new Label(weekDays[i]);
            d.setMinWidth(32);
            d.setAlignment(Pos.CENTER);
            d.setStyle("-fx-font-weight:bold;");
            grid.add(d, i, 0);
        }

        int firstDayColumn = mapSundayZero(ym.atDay(1).getDayOfWeek());
        int daysInMonth = ym.lengthOfMonth();

        int col = firstDayColumn;
        int row = 1;

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = ym.atDay(day);

            Button b = new Button(String.valueOf(day));
            b.setMinSize(32, 32);
            b.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            aplicarEstilo(b, date);

            StackPane cell = new StackPane(b);

            // Badge de eventos
            List<CalendarEvent> evs = state.getEvents().get(date);
            if (evs != null && !evs.isEmpty()) {
                Label badge = new Label(String.valueOf(evs.size()));
                badge.setStyle("-fx-background-color:#FF7043; -fx-text-fill:white; -fx-font-size:10; -fx-padding:1 4 1 4; -fx-background-radius:10;");
                StackPane.setAlignment(badge, Pos.TOP_RIGHT);
                cell.getChildren().add(badge);

                StringBuilder tt = new StringBuilder("Eventos:\n");
                for (CalendarEvent ev : evs) {
                    TimeRange tr = ev.time();
                    tt.append("• ").append(ev.title()).append(" (")
                            .append(String.format("%02d:%02d", tr.start().getHour(), tr.start().getMinute()))
                            .append(")\n");
                }
                b.setTooltip(new Tooltip(tt.toString()));
            }

            // Click normal
            b.setOnAction(e -> {
                if (state.isInsideRange(date)) {
                    return;
                }
                state.toggleOutsideSelection(date);
                aplicarEstilo(b, date);
                renderResumen(calculator.calculate(state));
                reconstruirMeses();
            });

            // Click con modificadores y menú contextual
            b.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.isControlDown()) {
                    state.toggleException(date);
                    aplicarEstilo(b, date);
                    renderResumen(calculator.calculate(state));
                    e.consume();
                    return;
                }

                if (e.getButton() == MouseButton.PRIMARY && e.isShiftDown()) {
                    state.adjustRangeWith(date);
                    dragging = false;
                    previewStart = null;
                    previewEnd = null;
                    reconstruirMeses();
                    renderResumen(calculator.calculate(state));
                    e.consume();
                    return;
                }

                if (e.getButton() == MouseButton.SECONDARY) {
                    ContextMenu cm = crearContextMenuDia(date, b);
                    cm.show(b, e.getScreenX(), e.getScreenY());
                    e.consume();
                }
            });

            // Drag para rango
            b.setOnDragDetected(e -> {
                dragging = true;
                previewStart = date;
                previewEnd = date;
                b.startFullDrag();
                aplicarEstilo(b, date);
            });

            b.setOnMouseDragEntered(e -> {
                if (!dragging) return;
                previewEnd = date;
                aplicarEstilo(b, date);
            });

            b.setOnMouseReleased(e -> {
                if (!dragging) return;
                LocalDate a = pMin();
                LocalDate z = pMax();
                state.setRange(a, z);
                dragging = false;
                previewStart = null;
                previewEnd = null;
                reconstruirMeses();
                renderResumen(calculator.calculate(state));
            });

            // Tooltip con horario por día de semana si existe
            TimeRange tr = state.getTimeByDay().get(date.getDayOfWeek());
            if (tr != null) {
                b.setTooltip(new Tooltip("Horario: " + fmtRange(tr)));
            }

            grid.add(cell, col, row);

            col++;
            if (col == 7) {
                col = 0;
                row++;
            }
        }

        box.getChildren().addAll(lblMes, grid);
        return box;
    }

    /* =========================
       Estilo y utilidades
       ========================= */

    private void aplicarEstilo(Button b, LocalDate d) {
        // inicio y fin reales tienen prioridad visual
        if (state.getStart() != null && d.equals(state.getStart())) {
            b.setStyle(STYLE_START);
            return;
        }
        if (state.getEnd() != null && d.equals(state.getEnd())) {
            b.setStyle(STYLE_END);
            return;
        }

        // preview del drag
        if (hayPreview() && !d.isBefore(pMin()) && !d.isAfter(pMax())) {
            b.setStyle(STYLE_SELECTED);
            return;
        }


        // === HOY (solo si no está seleccionado por reglas) ===
        if (d.equals(LocalDate.now())) {
            b.setStyle(STYLE_TODAY);
            return;
        }

       // selección efectiva
        if (state.isSelected(d)) {
            b.setStyle(STYLE_SELECTED);
        } else {
            b.setStyle(STYLE_NORMAL);
        }



    }

    private int mapSundayZero(DayOfWeek dow) {
        int v = dow.getValue();
        return v % 7;
    }

    private void estilizarBotonHeader(Button b) {
        b.setStyle("-fx-background-color:#E0E0E0; -fx-text-fill:black;");
        b.setMinWidth(36);
    }

    private boolean hayPreview() {
        return dragging && previewStart != null && previewEnd != null;
    }

    private LocalDate pMin() {
        return previewStart.isBefore(previewEnd) ? previewStart : previewEnd;
    }

    private LocalDate pMax() {
        return previewEnd.isAfter(previewStart) ? previewEnd : previewStart;
    }

    private String fmtRange(TimeRange tr) {
        return String.format("%02d:%02d - %02d:%02d",
                tr.start().getHour(), tr.start().getMinute(),
                tr.end().getHour(), tr.end().getMinute());
    }

    /* =========================
       Menú contextual por día
       ========================= */

    private ContextMenu crearContextMenuDia(LocalDate d, Button bRef) {
        ContextMenu cm = new ContextMenu();

        MenuItem beginRange = new MenuItem("Comenzar rango aquí");
        MenuItem endRange   = new MenuItem("Terminar rango aquí");

        MenuItem selectDay  = new MenuItem("Seleccionar este día");
        MenuItem unselectDay= new MenuItem("Deseleccionar este día");

        MenuItem assignTime = new MenuItem("Asignar horario…");

        MenuItem addEvent   = new MenuItem("Agregar evento…");
        MenuItem viewEdit   = new MenuItem("Ver / editar eventos…");
        MenuItem delAll     = new MenuItem("Eliminar todos los eventos del día");

        beginRange.setOnAction(e -> {
            state.setStart(d);
            state.setEnd(null);
            reconstruirMeses();
            renderResumen(calculator.calculate(state));
            cm.hide();
        });

        endRange.setOnAction(e -> {
            state.closeRangeAt(d);
            reconstruirMeses();
            renderResumen(calculator.calculate(state));
            cm.hide();
        });

        selectDay.setOnAction(e -> {
            state.forceOn(d);
            aplicarEstilo(bRef, d);
            renderResumen(calculator.calculate(state));
            reconstruirMeses();
            cm.hide();
        });

        unselectDay.setOnAction(e -> {
            state.forceOff(d);
            aplicarEstilo(bRef, d);
            renderResumen(calculator.calculate(state));
            reconstruirMeses();
            cm.hide();
        });

        assignTime.setOnAction(e -> {
            mostrarSelectorHorario(d.getDayOfWeek());
            reconstruirMeses();
            renderResumen(calculator.calculate(state));
            cm.hide();
        });

        addEvent.setOnAction(e -> {
            mostrarDialogoEvento(d, null, () -> {
                reconstruirMeses();
                renderResumen(calculator.calculate(state));
            });
            cm.hide();
        });

        viewEdit.setOnAction(e -> {
            mostrarEventosDelDia(d);
            cm.hide();
        });

        delAll.setOnAction(e -> {
            state.getEvents().remove(d);
            reconstruirMeses();
            renderResumen(calculator.calculate(state));
            cm.hide();
        });

        // Ajuste dinámico al abrir
        cm.setOnShowing(ev -> {
            boolean sel = state.isSelected(d);
            selectDay.setDisable(sel);
            unselectDay.setDisable(!sel);
        });

        cm.getItems().addAll(
                beginRange, endRange,
                new SeparatorMenuItem(),
                selectDay, unselectDay,
                new SeparatorMenuItem(),
                assignTime,
                new SeparatorMenuItem(),
                addEvent, viewEdit, delAll
        );

        return cm;
    }

    /* =========================
       Horarios
       ========================= */

    private void mostrarSelectorHorario(DayOfWeek dow) {
        Stage dialog = new Stage();
        dialog.setTitle("Asignar horario para " + dow.getDisplayName(TextStyle.FULL, new Locale("es", "ES")));
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));

        TimeRange actual = state.getTimeByDay().get(dow);

        int hIni = actual != null ? actual.start().getHour() : 16;
        int mIni = actual != null ? actual.start().getMinute() : 30;
        int hFin = actual != null ? actual.end().getHour() : 18;
        int mFin = actual != null ? actual.end().getMinute() : 0;

        Spinner<Integer> horaInicio = new Spinner<>(0, 23, hIni);
        Spinner<Integer> minutoInicio = new Spinner<>(0, 59, mIni);
        Spinner<Integer> horaFin = new Spinner<>(0, 23, hFin);
        Spinner<Integer> minutoFin = new Spinner<>(0, 59, mFin);

        horaInicio.setEditable(true);
        minutoInicio.setEditable(true);
        horaFin.setEditable(true);
        minutoFin.setEditable(true);

        HBox inicioBox = new HBox(5, new Label("Inicio"), horaInicio, new Label(":"), minutoInicio);
        HBox finBox = new HBox(5, new Label("Fin"), horaFin, new Label(":"), minutoFin);

        Button okBtn = new Button("OK");
        okBtn.setOnAction(e -> {
            LocalTime ini = LocalTime.of(horaInicio.getValue(), minutoInicio.getValue());
            LocalTime fin = LocalTime.of(horaFin.getValue(), minutoFin.getValue());
            state.getTimeByDay().put(dow, new TimeRange(ini, fin));
            dialog.close();
        });

        box.getChildren().addAll(inicioBox, finBox, okBtn);

        dialog.setScene(new Scene(box));
        dialog.showAndWait();
    }

    /* =========================
       Resumen
       ========================= */

    private void renderResumen(ProgramSummary s) {
        if (resumenBox == null) return;

        resumenBox.getChildren().clear();

        if (s == null || s.start() == null || s.end() == null) {
            resumenBox.getChildren().add(new Label("Selecciona un rango, días y horarios para ver el resumen."));
            return;
        }

        Label tituloTot = new Label("Resumen del programa");
        tituloTot.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        GridPane gTot = new GridPane();
        gTot.setHgap(18);
        gTot.setVgap(6);

        int fila = 0;
        gTot.add(new Label("Semanas del programa"), 0, fila);
        gTot.add(new Label(String.valueOf(s.weeksInRange())), 1, fila++);
        gTot.add(new Label("Semanas con entrenamiento"), 0, fila);
        gTot.add(new Label(String.valueOf(s.weeksWithTraining())), 1, fila++);
        gTot.add(new Label("Días seleccionados"), 0, fila);
        gTot.add(new Label(String.valueOf(s.selectedDays())), 1, fila++);
        gTot.add(new Label("Total minutos"), 0, fila);
        gTot.add(new Label(String.valueOf(s.totalMinutes())), 1, fila++);
        gTot.add(new Label("Total horas"), 0, fila);
        gTot.add(new Label(fmtHHMM(s.totalMinutes()) + "  (" + String.format(Locale.US, "%.2f h", s.totalMinutes() / 60.0) + ")"), 1, fila++);

        Label tituloMes = new Label("Tiempo por mes");
        tituloMes.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        GridPane gMes = new GridPane();
        gMes.setHgap(18);
        gMes.setVgap(4);

        int r = 0;
        for (Map.Entry<YearMonth, Integer> e : s.minutesByMonth().entrySet()) {
            String mes = monthsES.get(e.getKey().getMonthValue() - 1);
            gMes.add(new Label(mes), 0, r);
            gMes.add(new Label(fmtHHMM(e.getValue()) + "  (" + fmtHM(e.getValue()) + ")"), 1, r);
            r++;
        }
        if (r == 0) gMes.add(new Label("Sin minutos asignados."), 0, 0);

        Label tituloSem = new Label("Tiempo por semana del programa");
        tituloSem.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        GridPane gSem = new GridPane();
        gSem.setHgap(18);
        gSem.setVgap(4);

        int r2 = 0;
        for (Map.Entry<Integer, Integer> e : s.minutesByWeek().entrySet()) {
            gSem.add(new Label("Semana " + e.getKey()), 0, r2);
            gSem.add(new Label(fmtHHMM(e.getValue()) + "  (" + fmtHM(e.getValue()) + ")"), 1, r2);
            r2++;
        }
        if (r2 == 0) gSem.add(new Label("Sin minutos asignados."), 0, 0);

        resumenBox.getChildren().addAll(
                tituloTot, gTot,
                new Separator(),
                tituloMes, gMes,
                new Separator(),
                tituloSem, gSem
        );
    }

    private String fmtHM(int minutos) {
        int h = minutos / 60;
        int m = minutos % 60;
        if (h == 0) return m + " m";
        if (m == 0) return h + " h";
        return h + " h " + m + " m";
    }

    private String fmtHHMM(int minutos) {
        int h = minutos / 60;
        int m = minutos % 60;
        return String.format("%02d:%02d", h, m);
    }

    /* =========================
       Guardar, cargar, exportar
       ========================= */

    private void guardarPrograma() {
        if (!state.hasRange()) {
            alerta("Primero elige un rango de fechas.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar programa");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivo JSON (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv")
        );

        java.io.File f = fc.showSaveDialog(yearLabel.getScene().getWindow());
        if (f == null) return;

        try {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                storage.saveCsv(state, f.toPath(), calculator);
            } else {
                java.io.File target = f.getName().toLowerCase(Locale.ROOT).endsWith(".json")
                        ? f
                        : new java.io.File(f.getAbsolutePath() + ".json");
                storage.saveJson(state, target.toPath(), calculator);
            }
            alerta("Guardado con éxito.");
        } catch (Exception ex) {
            alerta("Error al guardar.\n" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void cargarPrograma() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Cargar programa (JSON)");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivo JSON (*.json)", "*.json")
        );

        java.io.File f = fc.showOpenDialog(yearLabel.getScene().getWindow());
        if (f == null) return;

        try {
            cargandoDesdeArchivo = true;
            ProgramState loaded = storage.loadJson(f.toPath());
            state.copyFrom(loaded);

            // sincroniza checks
            for (Map.Entry<DayOfWeek, CheckBox> e : checksPorDia.entrySet()) {
                e.getValue().setSelected(state.getTrainingDays().contains(e.getKey()));
            }

            if (state.getStart() != null) {
                selectedDate = state.getStart();
            }
            yearLabel.setText(String.valueOf(selectedDate.getYear()));

            reconstruirMeses();
            renderResumen(calculator.calculate(state));
            alerta("Programa cargado.");
        } catch (Exception ex) {
            alerta("Error al cargar.\n" + ex.getMessage());
            ex.printStackTrace();
        } finally {
            cargandoDesdeArchivo = false;
        }
    }

    private void exportarICS() {
        if (!state.hasRange()) {
            alerta("Primero elige un rango de fechas.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar calendario iCalendar (.ics)");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("iCalendar (*.ics)", "*.ics")
        );

        java.io.File f = fc.showSaveDialog(yearLabel.getScene().getWindow());
        if (f == null) return;

        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".ics")) {
            f = new java.io.File(f.getAbsolutePath() + ".ics");
        }

        try {
            icsExporter.export(state, f.toPath());
            alerta("Exportado.");
        } catch (Exception ex) {
            alerta("Error al exportar.\n" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void alerta(String mensaje) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, mensaje, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("Información");
        a.showAndWait();
    }

    /* =========================
       Eventos por día
       ========================= */

    private void mostrarDialogoEvento(LocalDate fecha, CalendarEvent existente, Runnable postSave) {
        Stage dlg = new Stage();
        dlg.setTitle((existente == null ? "Agregar" : "Editar") + " evento - " + fecha);
        dlg.initModality(Modality.APPLICATION_MODAL);

        TextField txtTitulo = new TextField();
        txtTitulo.setPromptText("Título del evento");

        TextField txtUbicacion = new TextField();
        txtUbicacion.setPromptText("Ubicación (opcional)");

        TextArea txtDescripcion = new TextArea();
        txtDescripcion.setPromptText("Descripción (opcional)");
        txtDescripcion.setPrefRowCount(3);

        Spinner<Integer> hIni = new Spinner<>(0, 23, 9);
        Spinner<Integer> mIni = new Spinner<>(0, 59, 0);
        Spinner<Integer> hFin = new Spinner<>(0, 23, 10);
        Spinner<Integer> mFin = new Spinner<>(0, 59, 0);

        hIni.setEditable(true);
        mIni.setEditable(true);
        hFin.setEditable(true);
        mFin.setEditable(true);

        CheckBox chkRecordar = new CheckBox("Recordatorio 10 min antes");

        if (existente != null) {
            txtTitulo.setText(existente.title());
            txtUbicacion.setText(existente.location());
            txtDescripcion.setText(existente.description());
            hIni.getValueFactory().setValue(existente.time().start().getHour());
            mIni.getValueFactory().setValue(existente.time().start().getMinute());
            hFin.getValueFactory().setValue(existente.time().end().getHour());
            mFin.getValueFactory().setValue(existente.time().end().getMinute());
            chkRecordar.setSelected(existente.reminder());
        }

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);

        grid.add(new Label("Título"), 0, 0);
        grid.add(txtTitulo, 1, 0, 3, 1);

        grid.add(new Label("Ubicación"), 0, 1);
        grid.add(txtUbicacion, 1, 1, 3, 1);

        grid.add(new Label("Descripción"), 0, 2);
        grid.add(txtDescripcion, 1, 2, 3, 1);

        grid.add(new Label("Inicio"), 0, 3);
        grid.add(hIni, 1, 3);
        grid.add(new Label(":"), 2, 3);
        grid.add(mIni, 3, 3);

        grid.add(new Label("Fin"), 0, 4);
        grid.add(hFin, 1, 4);
        grid.add(new Label(":"), 2, 4);
        grid.add(mFin, 3, 4);

        grid.add(chkRecordar, 1, 5, 3, 1);

        Button btnCancelar = new Button("Cancelar");
        Button btnGuardar = new Button("Guardar");

        HBox acciones = new HBox(10, btnCancelar, btnGuardar);
        acciones.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, grid, acciones);
        root.setPadding(new Insets(16));

        btnCancelar.setOnAction(e -> dlg.close());
        btnGuardar.setOnAction(e -> {
            String t = txtTitulo.getText().trim();
            if (t.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "El título es obligatorio.", ButtonType.OK).showAndWait();
                return;
            }

            LocalTime ini = LocalTime.of(hIni.getValue(), mIni.getValue());
            LocalTime fin = LocalTime.of(hFin.getValue(), mFin.getValue());
            TimeRange tr = new TimeRange(ini, fin);

            CalendarEvent ev = new CalendarEvent(
                    t,
                    txtDescripcion.getText().trim(),
                    txtUbicacion.getText().trim(),
                    tr,
                    chkRecordar.isSelected()
            );

            List<CalendarEvent> lista = state.getEvents().computeIfAbsent(fecha, k -> new ArrayList<>());
            if (existente != null) {
                lista.remove(existente);
            }
            lista.add(ev);
            lista.sort(Comparator.comparing(a -> a.time().start()));

            if (postSave != null) postSave.run();
            dlg.close();
        });

        dlg.setScene(new Scene(root, 520, 320));
        dlg.showAndWait();
    }

    private void mostrarEventosDelDia(LocalDate fecha) {
        List<CalendarEvent> lista = state.getEvents().getOrDefault(fecha, Collections.emptyList());

        Stage dlg = new Stage();
        dlg.setTitle("Eventos - " + fecha);
        dlg.initModality(Modality.APPLICATION_MODAL);

        ListView<CalendarEvent> lv = new ListView<>();
        lv.getItems().setAll(lista);

        lv.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(CalendarEvent it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) {
                    setText(null);
                    return;
                }
                TimeRange tr = it.time();
                String ubic = (it.location() == null || it.location().isBlank()) ? "" : " @ " + it.location();
                setText(String.format("%s  (%02d:%02d - %02d:%02d)%s",
                        it.title(),
                        tr.start().getHour(), tr.start().getMinute(),
                        tr.end().getHour(), tr.end().getMinute(),
                        ubic
                ));
            }
        });

        Button btnAgregar = new Button("Agregar");
        Button btnEditar = new Button("Editar");
        Button btnEliminar = new Button("Eliminar");
        Button btnCerrar = new Button("Cerrar");

        HBox acc = new HBox(10, btnAgregar, btnEditar, btnEliminar, btnCerrar);
        acc.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, lv, acc);
        root.setPadding(new Insets(12));

        btnAgregar.setOnAction(e -> mostrarDialogoEvento(fecha, null, () -> {
            lv.getItems().setAll(state.getEvents().getOrDefault(fecha, Collections.emptyList()));
            reconstruirMeses();
        }));

        btnEditar.setOnAction(e -> {
            CalendarEvent sel = lv.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            mostrarDialogoEvento(fecha, sel, () -> {
                lv.getItems().setAll(state.getEvents().getOrDefault(fecha, Collections.emptyList()));
                reconstruirMeses();
            });
        });

        btnEliminar.setOnAction(e -> {
            CalendarEvent sel = lv.getSelectionModel().getSelectedItem();
            if (sel == null) return;

            List<CalendarEvent> evs = state.getEvents().getOrDefault(fecha, new ArrayList<>());
            evs.remove(sel);

            if (evs.isEmpty()) state.getEvents().remove(fecha);
            lv.getItems().setAll(state.getEvents().getOrDefault(fecha, Collections.emptyList()));
            reconstruirMeses();
        });

        btnCerrar.setOnAction(e -> dlg.close());

        dlg.setScene(new Scene(root, 520, 360));
        dlg.showAndWait();
    }

    /* =========================
       Nota importante sobre ProgramState
       =========================
       Este dialog usa estos métodos en ProgramState:
       - LocalDate getStart(), getEnd()
       - void setStart(LocalDate), void setEnd(LocalDate)
       - void setRange(LocalDate a, LocalDate z)
       - void closeRangeAt(LocalDate d)
       - void adjustRangeWith(LocalDate d)
       - boolean hasRange()
       - LocalDate minDate(), maxDate()
       - boolean isInsideRange(LocalDate d)
       - boolean isSelected(LocalDate d)
       - void toggleException(LocalDate d)
       - void toggleOutsideSelection(LocalDate d)
       - void forceOn(LocalDate d)
       - void forceOff(LocalDate d)
       - EnumSet<DayOfWeek> getTrainingDays()
       - EnumMap<DayOfWeek, TimeRange> getTimeByDay()
       - Map<LocalDate, List<CalendarEvent>> getEvents()
       - void copyFrom(ProgramState other)
     ========================= */
}

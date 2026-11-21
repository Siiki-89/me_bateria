package br.com.memotos.bateria;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.function.Function;

final class BatteryInsights {
    private static final Locale PT = new Locale("pt", "BR");
    private final Context context;
    private final Ui ui;
    private final LinearLayout details, cellList;
    private final TileGrid sensorGrid;
    private final TextView summary, delta, scale, cellNote, sensorNote, cycles, charge, discharge;
    private final ArrayList<CellRow> cells = new ArrayList<>();
    private final ArrayList<TextView> sensorValues = new ArrayList<>();

    BatteryInsights(Context context, Ui ui) {
        this.context = context;
        this.ui = ui;
        details = ui.column();
        LinearLayout cellPanel = ui.panel();
        details.addView(cellPanel);
        LinearLayout header = ui.row();
        cellPanel.addView(header);
        header.addView(heading("Grupos de células"), new LinearLayout.LayoutParams(0, -2, 1));
        delta = ui.text("", 14, Ui.INK, true);
        delta.setBackground(ui.shape(Ui.ELEVATED, 12));
        ui.pad(delta, 10, 5);
        header.addView(delta);
        ui.gap(cellPanel, 6);
        summary = ui.text("", 14, Ui.MUTED, false);
        cellPanel.addView(summary);
        ui.gap(cellPanel, 12);
        cellList = ui.column();
        cellPanel.addView(cellList);
        scale = ui.text("", 12, Ui.MUTED, false);
        scale.setPadding(0, ui.dp(6), 0, 0);
        cellPanel.addView(scale);
        cellNote = ui.text("", 13, Ui.MUTED, false);
        cellPanel.addView(cellNote);

        ui.gap(details, 12);
        LinearLayout sensors = ui.panel();
        details.addView(sensors);
        sensors.addView(heading("Temperaturas"));
        sensorNote = ui.text("", 14, Ui.MUTED, false);
        sensorNote.setPadding(0, ui.dp(12), 0, 0);
        sensors.addView(sensorNote);
        ui.gap(sensors, 8);
        sensorGrid = new TileGrid(100, 2, 8);
        sensors.addView(sensorGrid);
        ui.gap(details, 12);
        LinearLayout bms = ui.panel();
        details.addView(bms);
        bms.addView(heading("Informações do BMS"));
        ui.gap(bms, 8);
        cycles = metricRow(bms, "Ciclos");
        charge = metricRow(bms, "Carga");
        discharge = metricRow(bms, "Descarga");
        updateDetails(null, null);
    }

    LinearLayout detailsView() { return details; }

    void updateDetails(Jbd.Basic basic, int[] groups) {
        int count = groups == null ? 0 : groups.length;
        delta.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        scale.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        cellList.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        while (cells.size() > count) cellList.removeView(cells.remove(cells.size() - 1).body);
        while (cells.size() < count) {
            CellRow row = new CellRow(cells.size() + 1);
            cells.add(row);
            cellList.addView(row.body);
        }
        if (count > 0) {
            int low = groups[0], high = groups[0];
            long sum = 0;
            for (int value : groups) {
                low = Math.min(low, value);
                high = Math.max(high, value);
                sum += value;
            }
            setText(delta, "Δ " + (high - low) + " mV");
            int minimumIndex = 0, maximumIndex = 0;
            for (int i = 1; i < count; i++) {
                if (groups[i] < groups[minimumIndex]) minimumIndex = i;
                if (groups[i] > groups[maximumIndex]) maximumIndex = i;
            }
            String extrema = low == high ? "Mín. e máx. " + volts(low)
                    : "Mín. " + volts(low) + " · #" + (minimumIndex + 1)
                    + "     Máx. " + volts(high) + " · #" + (maximumIndex + 1);
            setText(summary, extrema);
            // A shared zoomed scale keeps millivolt differences visible without stretching a tiny spread to the full width.
            int topMv = Math.max(10, ((high + 9) / 10) * 10);
            int bottomMv = Math.max(0, Math.min((low / 10) * 10, topMv - 150));
            setText(scale, count + (count == 1 ? " grupo" : " grupos") + " · Média " + volts(sum / (double) count)
                    + "\nEscala das barras: " + volts(bottomMv) + " a " + volts(topMv));
            for (int i = 0; i < count; i++) {
                int extreme = high == low ? 0 : groups[i] == low ? -1 : groups[i] == high ? 1 : 0;
                cells.get(i).update(groups[i], extreme, bottomMv, topMv);
            }
            String note = high == low && count > 1 ? "Todos os grupos têm a mesma tensão." : "";
            if (basic != null && basic.groups() != count) {
                note += (note.isEmpty() ? "" : "\n") + "A quantidade de grupos difere entre as respostas do BMS.";
            }
            setText(cellNote, note);
            cellNote.setPadding(0, ui.dp(12), 0, 0);
            cellNote.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            setText(summary, "Sem tensões disponíveis");
            setText(cellNote, "As tensões aparecem após uma leitura dos grupos de células.");
            cellNote.setPadding(0, 0, 0, 0);
            cellNote.setVisibility(View.VISIBLE);
        }
        double[] temperatures = basic == null ? null : basic.temperatures();
        int sensorCount = temperatures == null ? 0 : temperatures.length;
        while (sensorValues.size() > sensorCount) {
            TextView removed = sensorValues.remove(sensorValues.size() - 1);
            sensorGrid.removeView((View) removed.getParent());
        }
        while (sensorValues.size() < sensorCount) {
            sensorValues.add(sensorTile(sensorValues.size() + 1));
        }
        sensorNote.setVisibility(sensorCount == 0 ? View.VISIBLE : View.GONE);
        if (sensorCount == 0) setText(sensorNote, basic == null ? "Sem leituras de temperatura." : "O BMS não informou sensores de temperatura.");
        for (int i = 0; i < sensorCount; i++) {
            setText(sensorValues.get(i), String.format(PT, "%.1f °C", temperatures[i]));
        }
        setText(cycles, basic == null ? "—" : Integer.toString(basic.cycles()));
        switchStatus(charge, basic, 1);
        switchStatus(discharge, basic, 2);
    }

    private void switchStatus(TextView view, Jbd.Basic basic, int mask) {
        boolean enabled = basic != null && (basic.switches() & mask) != 0;
        setText(view, basic == null ? "—" : enabled ? "Habilitada" : "Desabilitada");
        int color = enabled ? Ui.MINT : Ui.MUTED;
        if (view.getCurrentTextColor() != color) view.setTextColor(color);
    }

    private TextView heading(String title) {
        TextView text = ui.text(title, 18, Ui.INK, true);
        text.setAccessibilityHeading(true);
        return text;
    }

    private TextView sensorTile(int index) {
        LinearLayout tile = ui.column();
        tile.setBackground(ui.shape(Ui.ELEVATED, 12));
        ui.pad(tile, 12, 10);
        tile.addView(ui.text("Sensor " + index, 13, Ui.MUTED, false));
        ui.gap(tile, 4);
        TextView value = ui.text("", 18, Ui.INK, true);
        tile.addView(value);
        sensorGrid.addView(tile);
        return value;
    }

    private TextView metricRow(LinearLayout parent, String name) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(44));
        row.setPadding(0, ui.dp(8), 0, ui.dp(4));
        TextView label = ui.text(name, 14, Ui.MUTED, false);
        TextView value = ui.text("", 15, Ui.INK, true);
        value.setGravity(Gravity.END);
        value.setFontFeatureSettings("tnum");
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row);
        return value;
    }

    private static void setText(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    private static String volts(double millivolts) { return String.format(PT, "%.3f V", millivolts / 1000.0); }

    private final class CellRow {
        final LinearLayout body = ui.row();
        final TextView index, voltage;
        final ScaleDrawable bar;
        final int number;
        final String groupLabel;
        int lastMv = -1, lastExtreme = -2;

        CellRow(int number) {
            this.number = number;
            groupLabel = String.format(PT, "G%02d", number);
            body.setMinimumHeight(ui.dp(24));
            index = ui.text(groupLabel, 13, Ui.MUTED, true);
            index.setMinWidth((int) Math.ceil(index.getPaint().measureText("G128 ↓")) + ui.dp(4));
            body.addView(index);
            View track = new View(context);
            bar = new ScaleDrawable(ui.shape(Ui.ACCENT, 4), Gravity.LEFT, 1f, -1f);
            track.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{
                    ui.shape(Ui.BORDER, 4), bar}));
            body.addView(track, new LinearLayout.LayoutParams(0, ui.dp(5), 1));
            ui.gap(body, 10);
            voltage = ui.text("", 15, Ui.INK, true);
            voltage.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            voltage.setMinWidth((int) Math.ceil(voltage.getPaint().measureText("0,000 V")));
            body.addView(voltage);
            body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            index.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            voltage.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void update(int mv, int extreme, int bottomMv, int topMv) {
            bar.setLevel(Math.round(10000f * (mv - bottomMv) / (topMv - bottomMv)));
            if (mv == lastMv && extreme == lastExtreme) return;
            String value = volts(mv);
            setText(voltage, value);
            if (extreme != lastExtreme) {
                String marker = extreme < 0 ? " ↓" : extreme > 0 ? " ↑" : "";
                setText(index, groupLabel + marker);
                index.setTextColor(extreme == 0 ? Ui.MUTED : Ui.INK);
            }
            String description = extreme < 0 ? ", tensão mínima" : extreme > 0 ? ", tensão máxima" : "";
            body.setContentDescription("Grupo " + number + ", " + value + description);
            lastMv = mv;
            lastExtreme = extreme;
        }
    }

    /** Equal-width tiles wrap according to the panel's measured width and the user's font scale. */
    private final class TileGrid extends ViewGroup {
        final int minimumWidth, maxColumns, gap;
        int columns = 1;
        TileGrid(int minimumWidth, int maxColumns, int gap) {
            super(context);
            this.minimumWidth = ui.dp(minimumWidth);
            this.maxColumns = maxColumns;
            this.gap = ui.dp(gap);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
            columns = Math.max(1, Math.min(maxColumns, (width + gap) / (Math.round(minimumWidth * fontScale) + gap)));
            int height = 0, rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int column = i % columns;
                int childWidth = edge(width, column + 1) - edge(width, column) - gap;
                child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, childWidth), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
                if (column == columns - 1 || i == getChildCount() - 1) {
                    height += rowHeight + (i == getChildCount() - 1 ? 0 : gap);
                    rowHeight = 0;
                }
            }
            setMeasuredDimension(width, resolveSize(height, heightSpec));
        }
        private int edge(int width, int column) { return column * (width + gap) / columns; }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int y = 0, rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int column = i % columns;
                int x = edge(getWidth(), column);
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
                if (column == columns - 1) {
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
            }
        }
    }

    void renderHistory(LinearLayout container, JSONArray history, String filter, Function<String, String> batteryName) {
        container.removeAllViews();
        boolean filtered = filter != null && !filter.isEmpty();
        ArrayList<JSONObject> entries = new ArrayList<>();
        boolean unreadable = false;
        for (int i = 0; i < history.length(); i++) {
            JSONObject entry = history.optJSONObject(i);
            if (entry == null || !entry.has("address")) unreadable = true;
            else if (!filtered || filter.equals(entry.optString("address"))) entries.add(entry);
        }
        entries.sort(Comparator.comparingLong((JSONObject e) -> e.optLong("end")).reversed());
        SimpleDateFormat dateFormat = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", PT);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", PT);
        String previousDate = "";
        for (JSONObject entry : entries) {
            try {
                Date end = new Date(entry.getLong("end"));
                String date = dateFormat.format(end);
                View card = sessionCard(entry, date, timeFormat.format(end), batteryName);
                if (!date.equals(previousDate)) {
                    if (container.getChildCount() > 0) ui.gap(container, 12);
                    TextView title = ui.text(date, 16, Ui.INK, true);
                    title.setAccessibilityHeading(true);
                    container.addView(title);
                    previousDate = date;
                }
                ui.gap(container, 8);
                container.addView(card);
            } catch (JSONException e) {
                unreadable = true;
            }
        }
        if (unreadable) {
            ui.gap(container, 8);
            container.addView(ui.text("Não foi possível ler todo o histórico.", 14, Ui.AMBER, false));
        } else if (entries.isEmpty()) {
            LinearLayout empty = ui.panel();
            container.addView(empty);
            empty.addView(new HistoryIcon(), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            ui.gap(empty, 20);
            empty.addView(heading("Nenhuma sessão registrada"));
            ui.gap(empty, 8);
            String explanation = filtered ? "As recargas observadas para esta bateria aparecerão aqui." :
                    "As sessões aparecem aqui quando o app acompanha uma recarga pela conexão Bluetooth.";
            empty.addView(ui.text(explanation, 15, Ui.MUTED, false));
        }
    }

    void showHistoryError(LinearLayout container) {
        container.removeAllViews();
        container.addView(ui.text("Não foi possível ler o histórico de recargas.", 15, Ui.AMBER, false));
    }

    private View sessionCard(JSONObject entry, String date, String time, Function<String, String> batteryName) throws JSONException {
        String id = entry.getString("address");
        boolean interrupted = entry.getBoolean("interrupted");
        long seconds = entry.getLong("duration") / 1000;
        String duration = String.format(PT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        String energy = String.format(PT, "%.2f Wh", entry.getDouble("energy"));
        String peak = String.format(PT, "%.0f W", entry.getDouble("peak"));
        LinearLayout card = ui.panel();
        TextView status = ui.text(interrupted ? "Leitura interrompida" : "Recarga encerrada", 13,
                interrupted ? Ui.AMBER : Ui.MINT, true);
        status.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
        status.setBackground(ui.shape(Ui.ELEVATED, 16));
        card.addView(status, new LinearLayout.LayoutParams(-2, -2));
        ui.gap(card, 12);
        card.addView(heading(batteryName.apply(id)));
        ui.gap(card, 12);
        TextView value = ui.text(energy, 27, Ui.INK, true);
        value.setFontFeatureSettings("tnum");
        card.addView(value);
        card.addView(ui.text("Energia estimada", 13, Ui.MUTED, false));
        ui.gap(card, 12);
        card.addView(ui.text((interrupted ? "Última leitura às " : "Encerramento às ") + time, 14, Ui.MUTED, false));
        ui.gap(card, 8);
        View line = new View(context);
        line.setBackgroundColor(Ui.BORDER);
        card.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, ui.dp(1))));
        LinearLayout expanded = ui.column();
        expanded.setVisibility(View.GONE);
        TextView more = ui.text("Ver detalhes", 14, Ui.ACCENT, true);
        more.setGravity(Gravity.CENTER_VERTICAL);
        more.setMinimumHeight(ui.dp(48));
        more.setFocusable(true);
        more.setBackground(ui.ripple(Ui.SURFACE, 8));
        more.setContentDescription("Ver detalhes de " + batteryName.apply(id) + ", " + date + " às " + time);
        card.addView(more);
        card.addView(expanded);
        more.setOnClickListener(v -> {
            boolean opening = expanded.getVisibility() != View.VISIBLE;
            if (opening && expanded.getChildCount() == 0) {
                setText(metricRow(expanded, "Duração observada"), duration);
                setText(metricRow(expanded, "Pico de potência"), peak);
                ui.gap(expanded, 8);
                expanded.addView(ui.text("Identificador Bluetooth", 13, Ui.MUTED, false));
                TextView address = ui.text(id, 15, Ui.INK, false);
                address.setTextIsSelectable(true);
                expanded.addView(address);
            }
            expanded.setVisibility(opening ? View.VISIBLE : View.GONE);
            if (opening) ui.enter(expanded);
            String action = opening ? "Ocultar detalhes" : "Ver detalhes";
            more.setText(action);
            more.setContentDescription(action + " de " + batteryName.apply(id) + ", " + date + " às " + time);
            more.setStateDescription(opening ? "Expandido" : "Recolhido");
        });
        return card;
    }

    private final class HistoryIcon extends View {
        final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path history = new Path();
        HistoryIcon() {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setBackground(ui.shape(Ui.ELEVATED, 14));
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(2);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeJoin(Paint.Join.ROUND);
            ink.setColor(Ui.ACCENT);
            history.moveTo(10, 14); history.lineTo(10, 21); history.lineTo(17, 21);
            history.moveTo(25, 17); history.lineTo(25, 24); history.lineTo(30, 27);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            canvas.scale(getWidth() / 48f, getHeight() / 48f);
            canvas.drawArc(12, 11, 38, 37, 196, 310, false, ink);
            canvas.drawPath(history, ink);
            canvas.restoreToCount(save);
        }
    }
}

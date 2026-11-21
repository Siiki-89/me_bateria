package br.com.memotos.bateria;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static br.com.memotos.bateria.Ui.*;

/** Saved-device presentation and enrollment dialogs. The activity owns discovery and persistence. */
final class BatteryFlows {
    private final Activity activity;
    private final Ui ui;

    BatteryFlows(Activity activity, Ui ui) {
        this.activity = activity;
        this.ui = ui;
    }

    void empty(LinearLayout parent, Runnable add) {
        LinearLayout card = ui.column();
        card.setBackground(outline(SURFACE, 24, BORDER));
        card.setClipToOutline(true);
        parent.addView(card, MATCH_PARENT, WRAP_CONTENT);
        card.addView(new PackDrawing(activity), MATCH_PARENT, dp(132));
        LinearLayout content = ui.column();
        content.setPadding(dp(20), dp(4), dp(20), dp(20));
        card.addView(content);
        content.addView(ui.text("Sua primeira bateria", 23, INK, true));
        ui.gap(content, 8);
        content.addView(ui.text("Use o segundo QR Code da etiqueta original ou digite o identificador Bluetooth.", 15, MUTED, false));
        ui.gap(content, 20);
        content.addView(button("Adicionar bateria", "plus", true, add));
    }

    void addButton(LinearLayout parent, Runnable add) {
        parent.addView(button("Adicionar bateria", "plus", true, add));
    }

    void card(LinearLayout parent, String id, String alias, String bluetoothName, boolean selected,
              boolean live, boolean busy, String connectionLabel, int percent, long sampleTime,
              Runnable open, Runnable options) {
        LinearLayout card = ui.column();
        card.setBackground(outline(SURFACE, 22, live || selected ? blend(BORDER, ACCENT, .44f) : BORDER));
        card.setPadding(dp(16), dp(12), dp(12), dp(16));
        parent.addView(card, MATCH_PARENT, WRAP_CONTENT);

        LinearLayout top = ui.row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);
        ImageView icon = icon("battery", live ? ACCENT : INK, 44);
        icon.setBackground(ui.shape(live ? blend(SURFACE, ACCENT, .16f) : ELEVATED, 14));
        top.addView(icon, dp(44), dp(44));
        LinearLayout name = ui.column();
        name.setPadding(dp(12), 0, 0, 0);
        top.addView(name, weighted());
        if (selected) {
            TextView eyebrow = ui.text("SELECIONADA", 11, ACCENT, true);
            eyebrow.setLetterSpacing(.09f);
            name.addView(eyebrow);
        }
        name.addView(ui.text(alias, 19, INK, true));
        ImageButton menu = new ImageButton(activity);
        menu.setImageDrawable(ui.icon("more", MUTED));
        menu.setScaleType(ImageView.ScaleType.CENTER);
        menu.setBackground(ui.ripple(SURFACE, 24));
        menu.setContentDescription("Opções de " + alias);
        menu.setOnClickListener(view -> options.run());
        top.addView(menu, dp(48), dp(48));

        ui.gap(card, 12);
        LinearLayout state = ui.row();
        state.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(state);
        int stateColor = live ? MINT : busy ? AMBER : MUTED;
        String label = !TextUtils.isEmpty(connectionLabel) ? connectionLabel
                : live ? "Conectada" : busy ? "Conectando" : "Desconectada";
        TextView badge = ui.text(label, 12, stateColor, true);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(ui.shape(blend(SURFACE, stateColor, .12f), 10));
        state.addView(badge, WRAP_CONTENT, WRAP_CONTENT);

        TextView hardware = ui.text(TextUtils.isEmpty(bluetoothName) ? id : bluetoothName + "  ·  " + id, 13, MUTED, false);
        ui.gap(card, 8);
        card.addView(hardware);
        ui.gap(card, 16);
        divider(card);
        ui.gap(card, 12);
        LinearLayout sample = ui.row();
        sample.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(sample);
        LinearLayout sampleInfo = ui.column();
        sample.addView(sampleInfo, weighted());
        sampleInfo.addView(ui.text(live ? "Carga atual" : "Última leitura", 14, INK, true));
        if (sampleTime > 0) {
            Date date = new Date(sampleTime);
            DateFormat day = android.text.format.DateFormat.getDateFormat(activity);
            DateFormat hour = android.text.format.DateFormat.getTimeFormat(activity);
            sampleInfo.addView(ui.text(day.format(date) + " · " + hour.format(date), 12, MUTED, false));
        } else {
            sampleInfo.addView(ui.text(percent < 0 ? "Aguardando dados do BMS" : "Horário indisponível", 12, MUTED, false));
        }
        TextView value = ui.text(percent < 0 ? "—" : percent + "%", 28, live ? ACCENT : INK, true);
        value.setFontFeatureSettings("tnum");
        value.setPadding(dp(8), 0, dp(4), 0);
        sample.addView(value, WRAP_CONTENT, WRAP_CONTENT);
        ui.gap(card, 12);
        String action = live || busy ? "Ver painel" : percent >= 0 ? "Reconectar" : "Conectar";
        card.addView(button(action, live || busy ? "chevron" : "bluetooth", false, open));
        card.setOnClickListener(view -> open.run());
        // The explicit button gives keyboard and TalkBack users an unambiguous action.
        card.setFocusable(false);
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.setForeground(new RippleDrawable(ColorStateList.valueOf(0x124ce4cd), null, ui.shape(Color.WHITE, 22)));
        ui.gap(parent, 12);
    }

    void choose(Runnable camera, Runnable manual) {
        Sheet sheet = new Sheet("Adicionar bateria");
        sheet.content.addView(ui.text("Como você quer identificar a bateria?", 15, MUTED, false));
        ui.gap(sheet.content, 20);
        sheet.option("scan", "Escanear QR Code", "Use o segundo QR Code da etiqueta original.", camera);
        sheet.option("edit", "Digitar identificador", "Informe os seis pares do identificador Bluetooth.", manual);
        sheet.show(false);
    }

    void filter(String[] ids, String selected, java.util.function.Function<String,String> alias,
                Consumer<String> select) {
        Sheet sheet = new Sheet("Filtrar por bateria");
        for (String id : ids) {
            String title = id.isEmpty() ? "Todas as baterias" : alias.apply(id);
            String detail = id.isEmpty() ? null : id;
            if (id.equals(selected)) detail = detail == null ? "Filtro atual" : detail + " · Filtro atual";
            sheet.option("battery", title, detail, () -> select.accept(id));
        }
        sheet.show(false);
    }

    void manual(Predicate<String> valid, Consumer<String> submit) {
        Sheet sheet = new Sheet("Identificador Bluetooth");
        sheet.content.addView(ui.text("Digite os seis pares separados por dois-pontos, como aparecem na etiqueta original.", 15, MUTED, false));
        ui.gap(sheet.content, 20);
        EditText input = field(sheet.content, "Identificador", "AA:BB:CC:DD:EE:FF");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTypeface(Typeface.MONOSPACE);
        TextView error = ui.text("", 14, AMBER, true);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setPadding(0, dp(8), 0, 0);
        error.setVisibility(View.GONE);
        sheet.content.addView(error);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable value) {
                if (error.getVisibility() != View.VISIBLE) return;
                error.setVisibility(View.GONE);
                input.setBackground(outline(ELEVATED, 12, ACCENT));
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        Runnable connect = () -> {
            String value = input.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!valid.test(value)) {
                error.setText("Confira o identificador. Use seis pares de 0–9 ou A–F, separados por dois-pontos.");
                error.setVisibility(View.VISIBLE);
                input.setBackground(outline(ELEVATED, 12, AMBER));
                input.requestFocus();
                return;
            }
            sheet.dialog.dismiss();
            submit.accept(value);
        };
        ui.gap(sheet.content, 16);
        sheet.content.addView(button("Buscar bateria", "bluetooth", true, connect));
        onDone(input, connect);
        input.requestFocus();
        sheet.show(true);
    }

    void rename(String name, Consumer<String> save) {
        Sheet sheet = new Sheet("Editar apelido");
        EditText input = field(sheet.content, "Nome da bateria", "Minha bateria");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        input.setText(name);
        input.setSelectAllOnFocus(true);
        Runnable confirm = () -> {
            String value = input.getText().toString().trim();
            sheet.dialog.dismiss();
            save.accept(value.isEmpty() ? "Minha bateria" : value);
        };
        ui.gap(sheet.content, 16);
        sheet.content.addView(button("Salvar apelido", null, true, confirm));
        onDone(input, confirm);
        input.requestFocus();
        sheet.show(true);
    }

    void options(String alias, String id, String bluetoothName, Runnable rename, Runnable disconnect, Runnable remove) {
        Sheet sheet = new Sheet(alias);
        LinearLayout identity = ui.column();
        identity.setPadding(dp(16), dp(12), dp(16), dp(12));
        identity.setBackground(ui.shape(ELEVATED, 14));
        sheet.content.addView(identity);
        identity.addView(ui.text("Identificador Bluetooth", 13, MUTED, false));
        TextView full = ui.text(id, 17, INK, true);
        full.setTypeface(Typeface.MONOSPACE);
        full.setTextIsSelectable(true);
        identity.addView(full);
        ui.gap(identity, 12);
        identity.addView(ui.text("Nome Bluetooth", 13, MUTED, false));
        identity.addView(ui.text(TextUtils.isEmpty(bluetoothName) ? "Indisponível" : bluetoothName, 16, INK, false));
        ui.gap(sheet.content, 16);
        sheet.option("edit", "Editar apelido", null, rename);
        if (disconnect != null) sheet.option("bluetooth", "Desconectar", null, disconnect);
        sheet.option("remove", "Remover bateria", null, () -> confirmRemoval(alias, id, remove));
        sheet.show(false);
    }

    private void confirmRemoval(String alias, String id, Runnable remove) {
        Sheet sheet = new Sheet("Remover bateria?");
        sheet.content.addView(ui.text(alias, 19, INK, true));
        sheet.content.addView(ui.text(id, 14, MUTED, false));
        ui.gap(sheet.content, 16);
        sheet.content.addView(ui.text("Esta bateria sairá da sua lista. O histórico de recargas continuará disponível. Você poderá cadastrá-la novamente pela etiqueta.", 15, INK, false));
        ui.gap(sheet.content, 20);
        Button confirm = button("Confirmar remoção", null, false, sheet.afterDismiss(remove));
        confirm.setTextColor(AMBER);
        sheet.content.addView(confirm);
        ui.gap(sheet.content, 8);
        sheet.content.addView(button("Cancelar", null, false, sheet.dialog::dismiss));
        sheet.show(false);
    }

    void scanError(String message, Runnable retry, Runnable manual) {
        Sheet sheet = new Sheet("Leitura da etiqueta");
        sheet.content.addView(ui.text(message, 16, INK, false));
        ui.gap(sheet.content, 20);
        sheet.content.addView(button("Tentar novamente", "scan", true, sheet.afterDismiss(retry)));
        ui.gap(sheet.content, 8);
        sheet.content.addView(button("Digitar identificador", "edit", false, sheet.afterDismiss(manual)));
        sheet.show(false);
    }

    Dialog camera(SurfaceView preview, Runnable manual) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(BG));
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.setDecorFitsSystemWindows(false);
        LinearLayout page = ui.column();
        page.setBackgroundColor(BG);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        dialog.setContentView(page);
        LinearLayout nav = ui.row();
        nav.setPadding(dp(8), dp(4), dp(16), dp(4));
        nav.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(activity);
        back.setImageDrawable(ui.icon("back", INK));
        back.setBackground(ui.ripple(BG, 24));
        back.setContentDescription("Voltar");
        back.setOnClickListener(view -> dialog.dismiss());
        nav.addView(back, dp(48), dp(48));
        TextView title = ui.text("Escanear QR Code", 20, INK, true);
        title.setPadding(dp(4), 0, 0, 0);
        nav.addView(title, weighted());
        page.addView(nav);
        PreviewArea area = new PreviewArea(activity, preview);
        area.addView(new ScanGuide(activity), MATCH_PARENT, MATCH_PARENT);
        page.addView(area, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout hint = ui.column();
        hint.setPadding(dp(20), dp(16), dp(20), dp(16));
        hint.addView(ui.text("Enquadre o segundo QR Code", 20, INK, true));
        ui.gap(hint, 4);
        hint.addView(ui.text("Na etiqueta original da bateria, procure a indicação “Second step”.", 15, MUTED, false));
        ui.gap(hint, 12);
        hint.addView(button("Digitar identificador", "edit", false, () -> {
            dialog.dismiss();
            manual.run();
        }));
        page.addView(hint);
        return dialog;
    }

    void previewAspect(SurfaceView preview, int frameWidth, int frameHeight) {
        if (preview.getParent() instanceof PreviewArea && frameWidth > 0 && frameHeight > 0) {
            PreviewArea area = (PreviewArea) preview.getParent();
            area.aspect = (float) frameWidth / frameHeight;
            area.requestLayout();
        }
    }

    /** A centered crop maintains the proportions of the rotated camera buffer. */
    private static final class PreviewArea extends FrameLayout {
        private final SurfaceView preview;
        float aspect;

        PreviewArea(Context context, SurfaceView preview) {
            super(context);
            this.preview = preview;
            addView(preview, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER));
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            if (aspect <= 0) return;
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            int scaledWidth = Math.max(width, Math.round(height * aspect));
            int scaledHeight = Math.max(height, Math.round(width / aspect));
            preview.measure(MeasureSpec.makeMeasureSpec(scaledWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(scaledHeight, MeasureSpec.EXACTLY));
        }
    }

    private final class ScanGuide extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ScanGuide(Context context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float side = Math.min(dp(300), Math.min(w, h) * .75f);
            float x = (w - side) / 2f, y = (h - side) / 2f;
            paint.setColor(0x6b07141a);
            canvas.drawRect(0, 0, w, y, paint);
            canvas.drawRect(0, y + side, w, h, paint);
            canvas.drawRect(0, y, x, y + side, paint);
            canvas.drawRect(x + side, y, w, y + side, paint);
            paint.setColor(ACCENT);
            paint.setStrokeWidth(dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            float leg = Math.min(dp(26), side / 5f);
            for (int i = 0; i < 4; i++) {
                canvas.save();
                canvas.rotate(90 * i, w / 2f, h / 2f);
                canvas.drawLine(x + leg, y, x, y, paint);
                canvas.drawLine(x, y, x, y + leg, paint);
                canvas.restore();
            }
        }
    }

    private final class Sheet {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog_NoActionBar);
        final LinearLayout content = ui.column();
        Sheet(String title) {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            Window window = dialog.getWindow();
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(.64f);
            window.setNavigationBarColor(SURFACE);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.setCanceledOnTouchOutside(true);
            ScrollView scroll = new ScrollView(activity);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setBackground(ui.shape(SURFACE, 24));
            scroll.setClipToOutline(true);
            scroll.addView(content);
            dialog.setContentView(scroll);
            content.setPadding(dp(20), dp(8), dp(20), dp(20));
            View grip = new View(activity);
            grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            grip.setBackground(ui.shape(BORDER, 3));
            LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(36), dp(4));
            gripParams.gravity = Gravity.CENTER_HORIZONTAL;
            content.addView(grip, gripParams);
            LinearLayout header = ui.row();
            header.setGravity(Gravity.CENTER_VERTICAL);
            ui.gap(content, 8);
            content.addView(header);
            TextView heading = ui.text(title, 24, INK, true);
            heading.setAccessibilityHeading(true);
            header.addView(heading, weighted());
            Button close = button("Fechar", null, false, dialog::dismiss);
            close.setBackground(ui.ripple(SURFACE, 20));
            close.setTextColor(MUTED);
            header.addView(close, WRAP_CONTENT, WRAP_CONTENT);
            ui.gap(content, 12);
        }

        Runnable afterDismiss(Runnable action) {
            return () -> {
                dialog.dismiss();
                action.run();
            };
        }

        void option(String key, String title, String detail, Runnable action) {
            LinearLayout row = ui.row();
            row.setBackground(ui.ripple(ELEVATED, 16));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setMinimumHeight(dp(56));
            row.setFocusable(true);
            row.setContentDescription(detail == null ? title : title + ". " + detail);
            row.setOnClickListener(view -> afterDismiss(action).run());
            row.addView(icon(key, ACCENT, 36), dp(36), dp(36));
            LinearLayout labels = ui.column();
            labels.setPadding(dp(8), 0, dp(8), 0);
            row.addView(labels, weighted());
            labels.addView(ui.text(title, 16, INK, true));
            if (detail != null) {
                ui.gap(labels, 4);
                labels.addView(ui.text(detail, 14, MUTED, false));
            }
            row.addView(icon("chevron", MUTED, 20), dp(20), dp(20));
            content.addView(row);
            ui.gap(content, 8);
        }

        void show(boolean keyboard) {
            Window window = dialog.getWindow();
            if (keyboard) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            window.setLayout(MATCH_PARENT, WRAP_CONTENT);
            dialog.show();
            window.setLayout(MATCH_PARENT, WRAP_CONTENT);
        }
    }

    private EditText field(LinearLayout content, String label, String placeholder) {
        TextView caption = ui.text(label, 14, INK, true);
        content.addView(caption);
        ui.gap(content, 8);
        EditText input = new EditText(activity);
        input.setId(View.generateViewId());
        caption.setLabelFor(input.getId());
        input.setSingleLine(true);
        input.setHint(placeholder);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setTextSize(17);
        input.setBackground(outline(ELEVATED, 12, BORDER));
        input.setMinHeight(dp(56));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        content.addView(input, MATCH_PARENT, WRAP_CONTENT);
        return input;
    }

    private void onDone(EditText input, Runnable action) {
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            action.run();
            return true;
        });
    }

    private Button button(String title, String key, boolean primary, Runnable action) {
        Button button = new Button(activity);
        int foreground = primary ? BG : INK;
        button.setText(title);
        button.setTextColor(foreground);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setLetterSpacing(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setGravity(Gravity.CENTER);
        button.setBackground(ui.ripple(primary ? ACCENT : ELEVATED, 14));
        if (key != null) {
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(ui.icon(key, foreground), null, null, null);
            button.setCompoundDrawablePadding(dp(6));
        }
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private ImageView icon(String key, int color, int box) {
        ImageView image = new ImageView(activity);
        image.setImageDrawable(ui.icon(key, color));
        image.setScaleType(box < 24 ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    private void divider(LinearLayout parent) {
        View rule = new View(activity);
        rule.setBackgroundColor(BORDER);
        parent.addView(rule, MATCH_PARENT, dp(1));
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
    }

    private GradientDrawable outline(int fill, int radius, int border) {
        GradientDrawable shape = ui.shape(fill, radius);
        shape.setStroke(dp(1), border);
        return shape;
    }

    private int dp(int value) { return ui.dp(value); }

    private static int blend(int base, int overlay, float alpha) {
        return Color.rgb(Math.round(Color.red(base) * (1 - alpha) + Color.red(overlay) * alpha),
                Math.round(Color.green(base) * (1 - alpha) + Color.green(overlay) * alpha),
                Math.round(Color.blue(base) * (1 - alpha) + Color.blue(overlay) * alpha));
    }

    /** A small technical illustration for the unpaired state, with no telemetry marks. */
    private static final class PackDrawing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        PackDrawing(Context context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float scale = Math.min(getWidth() / 280f, getHeight() / 116f);
            canvas.save();
            canvas.translate(getWidth() * .5f, getHeight() * .5f);
            canvas.scale(scale, scale);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(SURFACE, MUTED, .22f));
            for (int x = -132; x <= 132; x += 12) {
                for (int y = -45; y <= 45; y += 12) canvas.drawCircle(x, y, .7f, paint);
            }
            face(canvas, blend(ELEVATED, ACCENT, .10f), -63,-17, 27,-40, 65,-18, -25,7);
            face(canvas, ELEVATED, -63,-17, -25,7, -25,39, -63,16);
            face(canvas, blend(ELEVATED, BG, .36f), -25,7, 65,-18, 65,14, -25,39);
            for (int i = 0; i < 5; i++) {
                float x = -12 + i * 13, y = 10 - i * 3.6f;
                line(canvas, blend(ELEVATED, MUTED, .62f), x, y, x, y + 15);
            }
            line(canvas, MINT, -54, -1, -39, 8);
            face(canvas, SURFACE, -12,-24, 14,-31, 27,-23, 1,-16);
            canvas.restore();
        }

        private void face(Canvas canvas, int fill, float x1, float y1, float x2, float y2,
                          float x3, float y3, float x4, float y4) {
            path.rewind();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            path.lineTo(x3, y3);
            path.lineTo(x4, y4);
            path.close();
            paint.setColor(fill);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            paint.setColor(blend(ELEVATED, ACCENT, .57f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.15f);
            paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(path, paint);
        }

        private void line(Canvas canvas, int color, float x1, float y1, float x2, float y2) {
            paint.setColor(color);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }
}

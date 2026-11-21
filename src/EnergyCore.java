package br.com.memotos.bateria;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Charge reported by the BMS. The scale and numeral always show the same reading. */
final class EnergyCore extends View {
    private static final int TRACK = 0xff2e4149;
    private static final int ACCENT = 0xff33b9bf;
    private static final int MINT = 0xffa2eee0;
    private static final int WHITE = 0xffeef7f5;
    private static final int MUTED = 0xffb0c1c6;
    private static final int SEGMENTS = 48;
    private static final float START = 144f;
    private static final float SWEEP = 252f;
    private static final float STEP = SWEEP / SEGMENTS;
    private static final float RADIUS = 94;
    private static final float CY = 111;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface numbers = Typeface.create(Typeface.DEFAULT, 600, false);
    private final Typeface labels = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface scaleLabels = Typeface.create("sans-serif", Typeface.NORMAL);
    private final RectF arc = new RectF(-RADIUS, CY - RADIUS, RADIUS, CY + RADIUS);
    private final RectF inner = new RectF(-81, CY - 81, 81, CY + 81);
    private final RectF pill = new RectF();
    private final Path bolt = new Path();
    private final ValueAnimator motion = ValueAnimator.ofFloat(0f, 1f);
    private final float density;

    private int percent = -1;
    private boolean live;
    private int direction;
    private String numeral = "—";
    private String status = "Sem leitura";
    private float glint = 1f;

    EnergyCore(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        bolt.moveTo(2.8f, 49);
        bolt.lineTo(-6, 61);
        bolt.lineTo(-.7f, 61);
        bolt.lineTo(-2, 68);
        bolt.lineTo(6.6f, 55.5f);
        bolt.lineTo(1.4f, 55.5f);
        bolt.close();
        motion.setDuration(650);
        motion.setInterpolator(new DecelerateInterpolator());
        motion.addUpdateListener(value -> {
            glint = (float) value.getAnimatedValue();
            invalidate();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Carga da bateria. Sem leitura.");
    }

    void setReading(int newPercent, boolean isLive, double current) {
        int value = newPercent >= 0 && newPercent <= 100 ? newPercent : -1;
        boolean connectedReading = isLive && value >= 0;
        int sign = !connectedReading ? 0 : current > 0 ? 1 : current < 0 ? -1 : 0;
        if (value == percent && live == connectedReading && direction == sign) return;
        percent = value;
        live = connectedReading;
        direction = sign;
        numeral = value < 0 ? "—" : Integer.toString(value);
        status = value < 0 ? "Sem leitura" : !live ? "Última leitura"
                : sign > 0 ? "Carregando" : sign < 0 ? "Descarregando" : "Sem corrente";
        setContentDescription("Carga da bateria. " + (value < 0 ? "" : numeral + " por cento. ") + status + ".");
        stopMotion();
        if (sign != 0 && isAttachedToWindow() && isShown() && ValueAnimator.areAnimatorsEnabled()) {
            motion.start();
        }
        invalidate();
    }

    private void stopMotion() {
        motion.cancel();
        glint = 1f;
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (!visible) stopMotion();
    }

    @Override protected void onDetachedFromWindow() {
        stopMotion();
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(Math.round(300 * density), widthSpec),
                resolveSize(Math.round(228 * density), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float scale = Math.min(Math.min(width / 242f, height / 217f), 1.25f * density);
        if (scale <= 0) return;
        int save = canvas.save();
        canvas.translate(getPaddingLeft() + width / 2, getPaddingTop() + (height - 217 * scale) / 2);
        canvas.scale(scale, scale);
        drawScale(canvas);
        drawValue(canvas);
        drawState(canvas);
        canvas.restoreToCount(save);
    }

    private void drawScale(Canvas canvas) {
        float extent = percent < 0 ? 0 : SWEEP * percent / 100f;
        float moving = direction < 0 ? extent * (1 - glint) : extent * glint;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(11f);
        for (int i = 0; i < SEGMENTS; i++) {
            float at = i * STEP;
            float fullSegment = STEP - 1.05f;
            paint.setColor(TRACK);
            canvas.drawArc(arc, START + at, fullSegment, false, paint);
            float filled = Math.min(fullSegment, extent - at);
            if (filled <= 0) continue;
            int color = live ? ACCENT : 0xff70979c;
            if (live && extent - at < 3 * STEP) color = MINT;
            if (glint < 1 && Math.abs(at - moving) < STEP) color = 0xffddfff0;
            paint.setColor(color);
            canvas.drawArc(arc, START + at, filled, false, paint);
        }
        paint.setStrokeWidth(.8f);
        paint.setColor(0xff32464c);
        canvas.drawArc(inner, START, SWEEP, false, paint);
        for (int i = 0; i <= 10; i++) {
            canvas.save();
            canvas.rotate(START + (SWEEP * i / 10), 0, CY);
            canvas.drawLine(81, CY, i % 5 == 0 ? 75 : 78, CY, paint);
            canvas.restore();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(scaleLabels);
        paint.setTextSize(11);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(MUTED);
        canvas.drawText("0", -80, 184, paint);
        canvas.drawText("100", 80, 184, paint);
    }

    private void drawValue(Canvas canvas) {
        paint.setColor(live ? MINT : MUTED);
        canvas.drawPath(bolt, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(numbers);
        paint.setTextSize(62);
        float width = paint.measureText(numeral);
        paint.setTextSize(22);
        float unitWidth = percent >= 0 ? paint.measureText("%") + 2 : 0;
        float x = -(width + unitWidth) / 2;
        paint.setColor(WHITE);
        paint.setTextSize(62);
        canvas.drawText(numeral, x, 130, paint);
        if (percent >= 0) {
            paint.setColor(MUTED);
            paint.setTextSize(22);
            canvas.drawText("%", x + width + 2, 129, paint);
        }
    }

    private void drawState(Canvas canvas) {
        float fontScale = Math.min(1.55f, getResources().getConfiguration().fontScale);
        paint.setTypeface(labels);
        paint.setTextSize(12 * fontScale);
        float width = paint.measureText(status);
        float halfHeight = Math.max(12, paint.getTextSize() * .75f);
        float center = 152;
        pill.set(-width / 2 - 11, center - halfHeight, width / 2 + 11, center + halfHeight);
        paint.setColor(live && direction != 0 ? 0xff19383a : 0xff243139);
        canvas.drawRoundRect(pill, halfHeight, halfHeight, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(live && direction != 0 ? MINT : MUTED);
        canvas.drawText(status, 0, center - (paint.ascent() + paint.descent()) / 2, paint);
    }
}

package it.passi.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Anello animato dei passi: si riempie con l'obiettivo e pulsa mentre un'attività è in corso. */
final class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tBig = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private double progress;
    private boolean live;
    private String top = "";
    private String big = "0";
    private String sub = "";
    private float shown;
    private float pulse;
    private ValueAnimator pulser;

    RingView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(14 * d);
        track.setColor(Ui.SURFACE2);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(14 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.FILL);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(Ui.GOLD_LIGHT);
        tTop.setColor(Ui.GOLD);
        tTop.setTextAlign(Paint.Align.CENTER);
        tTop.setTextSize(14 * d);
        tTop.setTypeface(Typeface.SERIF);
        tBig.setColor(Ui.TEXT);
        tBig.setTextAlign(Paint.Align.CENTER);
        tBig.setTextSize(50 * d);
        tBig.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tSub.setColor(Ui.MUTED);
        tSub.setTextAlign(Paint.Align.CENTER);
        tSub.setTextSize(14 * d);
    }

    void setState(double progress01, String topText, String bigText, String subText, boolean isLive) {
        progress = progress01;
        top = topText;
        big = bigText;
        sub = subText;
        if (isLive != live) {
            live = isLive;
            updatePulser();
        }
        invalidate();
    }

    private void updatePulser() {
        if (live && isAttachedToWindow()) {
            if (pulser == null) {
                pulser = ValueAnimator.ofFloat(0f, 1f);
                pulser.setDuration(1800);
                pulser.setRepeatCount(ValueAnimator.INFINITE);
                pulser.setRepeatMode(ValueAnimator.REVERSE);
                pulser.setInterpolator(new LinearInterpolator());
                pulser.addUpdateListener(a -> {
                    pulse = (Float) a.getAnimatedValue();
                    invalidate();
                });
            }
            if (!pulser.isStarted()) pulser.start();
        } else if (pulser != null) {
            pulser.cancel();
            pulse = 0;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePulser();
    }

    @Override protected void onDetachedFromWindow() {
        if (pulser != null) pulser.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int size = Math.min(MeasureSpec.getSize(wSpec), Ui.dp(320));
        setMeasuredDimension(size, size);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        arc.setShader(new SweepGradient(w / 2f, h / 2f,
            new int[] {Ui.TEAL, Ui.GOLD, Ui.GOLD_LIGHT}, new float[] {0f, 0.65f, 1f}));
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float stroke = track.getStrokeWidth();
        float r = Math.min(w, h) / 2f - stroke * 1.2f;
        box.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawCircle(cx, cy, r, track);

        float target = (float) Calc.clamp01(progress);
        shown += (target - shown) * 0.12f;
        boolean settling = Math.abs(target - shown) > 0.001f;
        if (!settling) shown = target;

        if (shown > 0.002f) {
            canvas.save();
            canvas.rotate(-90, cx, cy);
            canvas.drawArc(box, 0, 360f * shown, false, arc);
            canvas.restore();
            double ang = Math.toRadians(360.0 * shown - 90.0);
            float ex = (float) (cx + r * Math.cos(ang));
            float ey = (float) (cy + r * Math.sin(ang));
            if (live) {
                glow.setColor(Ui.GOLD);
                glow.setAlpha((int) (40 + 70 * pulse));
                canvas.drawCircle(ex, ey, stroke * (1.2f + 0.8f * pulse), glow);
            }
            canvas.drawCircle(ex, ey, stroke * 0.32f, dot);
        }

        canvas.drawText(top, cx, cy - tBig.getTextSize() * 0.62f, tTop);
        canvas.drawText(big, cx, cy + tBig.getTextSize() * 0.32f, tBig);
        canvas.drawText(sub, cx, cy + tBig.getTextSize() * 0.82f, tSub);

        if (settling) postInvalidateOnAnimation();
    }
}

/** Barra di avanzamento semplice con angoli arrotondati. */
final class BarView extends View {
    private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint front = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float fraction;

    BarView(Context c, int color) {
        super(c);
        back.setColor(Ui.SURFACE2);
        front.setColor(color);
    }

    void setFraction(double f) {
        fraction = (float) Calc.clamp01(f);
        invalidate();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), Ui.dp(10));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2, h / 2, back);
        if (fraction > 0.001f) {
            r.set(0, 0, Math.max(h, w * fraction), h);
            c.drawRoundRect(r, h / 2, h / 2, front);
        }
    }
}

/** Grafico a barre dei passi degli ultimi giorni, con il trattino dell'obiettivo. */
final class DaysChartView extends View {
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private long[] steps = new long[0];
    private String[] labels = new String[0];
    private int goal = 8000;

    DaysChartView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        label.setColor(Ui.MUTED);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(11 * d);
        value.setColor(Ui.TEXT);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTextSize(10.5f * d);
        goalMark.setColor(Ui.GOLD);
        goalMark.setStrokeWidth(2f * d);
        goalMark.setStrokeCap(Paint.Cap.ROUND);
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(1 * d);
    }

    void setData(long[] stepsPerDay, String[] dayLabels, int goalSteps) {
        steps = stepsPerDay;
        labels = dayLabels;
        goal = goalSteps;
        invalidate();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), Ui.dp(200));
    }

    @Override protected void onDraw(Canvas c) {
        int n = steps.length;
        if (n == 0) return;
        float w = getWidth(), h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        float top = 22 * d, bottom = h - 22 * d;
        double max = Math.max(goal, 1000);
        for (long s : steps) max = Math.max(max, s);
        max *= 1.1;
        c.drawLine(0, bottom, w, bottom, grid);
        float slot = w / n;
        float bw = Math.min(slot * 0.56f, 34 * d);
        float gy = (float) (bottom - (bottom - top) * (goal / max));
        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2f;
            float bh = (float) ((bottom - top) * (steps[i] / max));
            bar.setColor(steps[i] >= goal ? Ui.TEAL : 0xFF6F7F9E);
            r.set(cx - bw / 2, bottom - Math.max(bh, 3 * d), cx + bw / 2, bottom);
            c.drawRoundRect(r, 6 * d, 6 * d, bar);
            c.drawLine(cx - bw / 2 - 3 * d, gy, cx + bw / 2 + 3 * d, gy, goalMark);
            String v = steps[i] >= 1000 ? Fmt.num(steps[i] / 1000.0, 1) + "k" : String.valueOf(steps[i]);
            c.drawText(v, cx, bottom - Math.max(bh, 3 * d) - 5 * d, value);
            if (i < labels.length) c.drawText(labels[i], cx, h - 5 * d, label);
        }
    }
}

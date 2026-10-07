package it.passi.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colori e piccoli costruttori di elementi grafici: stile scuro ed elegante. */
final class Ui {
    static final int BG = 0xFF0B1220;
    static final int SURFACE = 0xFF131C30;
    static final int SURFACE2 = 0xFF1B2742;
    static final int GOLD = 0xFFD4AF6A;
    static final int GOLD_LIGHT = 0xFFF1D9A0;
    static final int TEAL = 0xFF5FD0C0;
    static final int TEXT = 0xFFEAF0FA;
    static final int MUTED = 0xFF8FA0BC;
    static final int DANGER = 0xFFE57373;
    static final int HAIRLINE = 0x22FFFFFF;

    private static float density = 1f;

    private Ui() {}

    static void init(Context c) { density = c.getResources().getDisplayMetrics().density; }

    static int dp(float v) { return Math.round(v * density); }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static TextView title(Context c, CharSequence s, float sp) {
        TextView t = text(c, s, sp, GOLD_LIGHT);
        t.setTypeface(Typeface.SERIF);
        return t;
    }

    static GradientDrawable rect(int fill, float radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        l.setBackground(rect(SURFACE, 20, HAIRLINE));
        return l;
    }

    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setGravity(Gravity.CENTER);
        if (primary) {
            b.setTextColor(0xFF1A1405);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {GOLD, GOLD_LIGHT});
            g.setCornerRadius(dp(16));
            b.setBackground(g);
        } else {
            b.setTextColor(GOLD_LIGHT);
            b.setBackground(rect(SURFACE2, 16, 0x55D4AF6A));
        }
        return b;
    }

    static EditText input(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setBackground(rect(SURFACE2, 14, 0x33FFFFFF));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    static LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    static LinearLayout.LayoutParams full(int topDp) {
        return lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, topDp);
    }
}

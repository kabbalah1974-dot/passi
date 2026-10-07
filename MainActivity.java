package it.passi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_ACT = 21;
    private static final int REQ_LOC = 22;
    private static final String[] TAB_NAMES = {"Oggi", "Storico", "Profilo"};
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private Store store;
    private FrameLayout content;
    private LinearLayout tabBar;
    private final TextView[] tabViews = new TextView[TAB_NAMES.length];
    private int tab = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sm;
    private SensorEventListener liveListener;
    private int pendingType;

    // schermata Oggi
    private RingView ring;
    private TextView valKcal, valDist, valTotal, statusText;
    private Button statusBtn;
    private LinearLayout sessBox;
    private String sessKey = "";
    private TextView tvTime, tvDist, tvPace, tvKcal, tvSteps, tvGps;

    // schermata Profilo
    private EditText inHeight, inWeight, inAge, inGoal;
    private boolean sexMale = true;
    private TextView sexMaleBtn, sexFemaleBtn;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (tab == 0) refreshToday();
            handler.postDelayed(this, 1000);
        }
    };

    // ------------------------------------------------------------------ ciclo di vita

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        store = new Store(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.SURFACE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        tabBar = buildTabBar();
        root.addView(tabBar, new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(root);

        if (!store.hasProfile()) showOnboarding(); else showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if (store.hasProfile()) {
            store.setHasStepSensor(Sampler.hasSensor(this));
            Sampler.scheduleNext(this, Sampler.EVERY_MS);
            startLiveSteps();
            resumeSessionIfNeeded();
        }
    }

    /** Se un'attività risulta in corso ma il servizio è stato fermato dal sistema, lo rimette in piedi. */
    private void resumeSessionIfNeeded() {
        if (store.read().session == null) return;
        try {
            startForegroundService(new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_START));
        } catch (RuntimeException ignored) {
            // si riproverà alla prossima apertura
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        stopLiveSteps();
        super.onPause();
    }

    /** Con lo schermo acceso i passi si aggiornano subito. */
    private void startLiveSteps() {
        stopLiveSteps();
        if (!Sampler.canCount(this)) return;
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (s == null) return;
        liveListener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent ev) {
                if (ev.values != null && ev.values.length > 0) Sampler.applyStepCounter(MainActivity.this, (long) ev.values[0]);
            }

            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };
        sm.registerListener(liveListener, s, SensorManager.SENSOR_DELAY_UI);
    }

    private void stopLiveSteps() {
        if (sm != null && liveListener != null) sm.unregisterListener(liveListener);
        liveListener = null;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_ACT) {
            startLiveSteps();
            Sampler.sampleOnce(this, handler, 3000, () -> {
                if (tab == 0) refreshToday();
            });
            if (tab == 0) refreshToday();
        } else if (code == REQ_LOC) {
            boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!fine) toast("Parto senza GPS: userò solo i passi.");
            doStartSession(pendingType);
        }
    }

    // ------------------------------------------------------------------ struttura

    private LinearLayout buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Ui.SURFACE);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, TAB_NAMES[i], 13, Ui.MUTED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(14), 0, Ui.dp(14));
            t.setOnClickListener(v -> showTab(idx));
            tabViews[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        return bar;
    }

    private void styleTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            boolean sel = i == tab;
            tabViews[i].setTextColor(sel ? Ui.GOLD_LIGHT : Ui.MUTED);
            tabViews[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void showOnboarding() {
        tab = -1;
        tabBar.setVisibility(View.GONE);
        content.removeAllViews();
        content.addView(buildProfileScreen(true));
    }

    private void showTab(int t) {
        tab = t;
        tabBar.setVisibility(View.VISIBLE);
        styleTabs();
        content.removeAllViews();
        sessKey = "";
        if (t == 0) {
            content.addView(buildTodayScreen());
            refreshToday();
        } else if (t == 1) {
            content.addView(buildHistoryScreen());
        } else {
            content.addView(buildProfileScreen(false));
        }
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Ui.dp(20), Ui.dp(26), Ui.dp(20), Ui.dp(28));
        return l;
    }

    private ScrollView scroll(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        return sv;
    }

    private View header(String title, String sub) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.addView(Ui.title(this, title, 30));
        if (sub != null && !sub.isEmpty()) h.addView(Ui.text(this, sub, 14, Ui.MUTED), Ui.full(2));
        return h;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    private String todayText() {
        String s = new SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(new Date());
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ schermata Oggi

    private LinearLayout statCard(String label, int idx) {
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        c.addView(Ui.text(this, label, 12, Ui.MUTED));
        TextView v = Ui.text(this, "—", 17, Ui.TEXT);
        v.setTypeface(Typeface.SERIF);
        if (idx == 0) valKcal = v; else if (idx == 1) valDist = v; else valTotal = v;
        c.addView(v, Ui.full(4));
        return c;
    }

    private View buildTodayScreen() {
        LinearLayout col = column();
        col.addView(header("Passi", todayText()));

        ring = new RingView(this);
        FrameLayout rw = new FrameLayout(this);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(MATCH, WRAP);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rw.addView(ring, rp);
        col.addView(rw, Ui.full(12));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Calorie attive", "Distanza", "Totali stimate"};
        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
            p.leftMargin = Ui.dp(i == 0 ? 0 : 8);
            stats.addView(statCard(labels[i], i), p);
        }
        col.addView(stats, Ui.full(14));
        col.addView(Ui.text(this, "Le calorie attive sono quelle spese camminando e correndo, senza il metabolismo di base.", 11, Ui.MUTED), Ui.full(6));

        LinearLayout st = Ui.card(this);
        statusText = Ui.text(this, "", 14, Ui.TEXT);
        st.addView(statusText);
        statusBtn = Ui.button(this, "Attiva il contapassi", true);
        statusBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 29) requestPermissions(new String[] {Manifest.permission.ACTIVITY_RECOGNITION}, REQ_ACT);
        });
        st.addView(statusBtn, Ui.full(12));
        col.addView(st, Ui.full(14));

        sessBox = Ui.card(this);
        col.addView(sessBox, Ui.full(12));

        LinearLayout link = Ui.card(this);
        link.addView(Ui.title(this, "Collegamento con Digiuno", 18));
        boolean has = digiunoInstalled();
        link.addView(Ui.text(this,
            "Passi condivide calorie e passi con Digiuno solo quando, dentro Digiuno, accendi il collegamento per un digiuno. "
            + "Se non lo accendi, Digiuno non riceve niente.\n\n"
            + (has ? "Digiuno è installata su questo telefono." : "Digiuno non è installata su questo telefono."),
            13, Ui.TEXT), Ui.full(8));
        col.addView(link, Ui.full(12));

        col.addView(Ui.text(this,
            "Per contare bene anche ad app chiusa, nelle impostazioni della batteria lascia Passi senza limiti. "
            + "Passi è un'app di benessere: calorie e distanze sono stime.",
            11, Ui.MUTED), Ui.full(14));
        return scroll(col);
    }

    private boolean digiunoInstalled() {
        try {
            getPackageManager().getPackageInfo("it.digiuno.app", 0);
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    private double hoursSinceMidnight() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0 + c.get(Calendar.SECOND) / 3600.0;
    }

    private void refreshToday() {
        if (ring == null || valKcal == null || statusText == null) return;
        long now = System.currentTimeMillis();
        Engine e = store.read();
        Totals t = e.days.get(Store.dayKey(now));
        if (t == null) t = new Totals();
        int goal = store.stepGoal();
        double prog = goal > 0 ? (double) t.steps / goal : 0;
        ring.setState(prog, "OGGI", Fmt.thousands(t.steps),
            "su " + Fmt.thousands(goal) + " · " + Math.round(prog * 100) + "%", e.session != null);
        valKcal.setText(Fmt.num(t.kcal, 0) + " kcal");
        valDist.setText(Fmt.km(t.distM));
        double bmrH = Body.bmrPerDay(store.male(), store.weightKg(), store.heightCm(), store.age()) / 24.0;
        valTotal.setText(Fmt.num(bmrH * hoursSinceMidnight() + t.kcal, 0) + " kcal");

        boolean sensor = Sampler.hasSensor(this);
        boolean perm = Sampler.canCount(this);
        if (!sensor) {
            statusText.setText("Questo telefono non ha il contapassi: userò il GPS durante le attività.");
            statusBtn.setVisibility(View.GONE);
        } else if (!perm) {
            statusText.setText("Per contare i passi serve il permesso «Attività fisica».");
            statusBtn.setVisibility(View.VISIBLE);
        } else {
            statusText.setText("Contapassi attivo, anche con l'app chiusa (si aggiorna ogni 20 minuti circa).");
            statusBtn.setVisibility(View.GONE);
        }

        String key = e.session != null ? "a" : "i";
        if (!key.equals(sessKey)) {
            sessKey = key;
            buildSessionBox(e.session != null);
        }
        if (e.session != null) updateSession(e.session, now);
    }

    private void buildSessionBox(boolean active) {
        sessBox.removeAllViews();
        sessBox.addView(Ui.title(this, "Attività con GPS", 20));
        if (!active) {
            sessBox.addView(Ui.text(this,
                "Per camminate e corse: il GPS misura la distanza e la velocità, e le calorie diventano più precise. "
                + "Se il GPS non c'è o è spento, l'attività continua a funzionare con i passi.",
                14, Ui.TEXT), Ui.full(8));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button walk = Ui.button(this, "Cammino", true);
            walk.setOnClickListener(v -> startSession(0));
            Button run = Ui.button(this, "Corro", true);
            run.setOnClickListener(v -> startSession(1));
            row.addView(walk, new LinearLayout.LayoutParams(0, WRAP, 1f));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            rp.leftMargin = Ui.dp(8);
            row.addView(run, rp);
            sessBox.addView(row, Ui.full(14));
            return;
        }
        tvTime = Ui.text(this, "00:00:00", 36, Ui.TEXT);
        tvTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        sessBox.addView(tvTime, Ui.full(6));
        LinearLayout grid1 = new LinearLayout(this);
        grid1.setOrientation(LinearLayout.HORIZONTAL);
        tvDist = liveCell(grid1, "Distanza", 0);
        tvPace = liveCell(grid1, "Passo medio", 1);
        sessBox.addView(grid1, Ui.full(12));
        LinearLayout grid2 = new LinearLayout(this);
        grid2.setOrientation(LinearLayout.HORIZONTAL);
        tvKcal = liveCell(grid2, "Calorie attive", 0);
        tvSteps = liveCell(grid2, "Passi", 1);
        sessBox.addView(grid2, Ui.full(10));
        tvGps = Ui.text(this, "", 13, Ui.TEAL);
        tvGps.setOnClickListener(v -> {
            if (!gpsEnabled()) {
                try {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                } catch (RuntimeException ex) {
                    toast("Apri le impostazioni del telefono e attiva la posizione.");
                }
            }
        });
        sessBox.addView(tvGps, Ui.full(12));
        Button stop = Ui.button(this, "Ferma l'attività", true);
        stop.setOnClickListener(v -> stopSession());
        sessBox.addView(stop, Ui.full(14));
    }

    private TextView liveCell(LinearLayout row, String label, int idx) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(Ui.text(this, label, 12, Ui.MUTED));
        TextView v = Ui.text(this, "—", 20, Ui.TEXT);
        v.setTypeface(Typeface.SERIF);
        c.addView(v, Ui.full(2));
        row.addView(c, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return v;
    }

    private boolean gpsEnabled() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void updateSession(Session s, long now) {
        if (tvTime == null || tvGps == null) return;
        tvTime.setText(Fmt.hms(now - s.startMs));
        tvDist.setText(Fmt.km(s.distM));
        tvPace.setText(Fmt.pace(s.avgSpeedKmh(now)));
        tvKcal.setText(Fmt.num(s.kcal, 0) + " kcal");
        tvSteps.setText(Fmt.thousands(s.steps));
        boolean perm = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!perm) {
            tvGps.setText("GPS non consentito: calcolo dai passi.");
            tvGps.setTextColor(Ui.MUTED);
        } else if (!gpsEnabled()) {
            tvGps.setText("GPS spento: calcolo dai passi. Tocca qui per attivarlo.");
            tvGps.setTextColor(Ui.GOLD);
        } else if (s.gpsFresh(now)) {
            tvGps.setText("GPS: segnale ok");
            tvGps.setTextColor(Ui.TEAL);
        } else {
            tvGps.setText("Cerco il segnale GPS… intanto calcolo dai passi.");
            tvGps.setTextColor(Ui.GOLD);
        }
    }

    private void startSession(int type) {
        pendingType = type;
        List<String> need = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (need.isEmpty()) {
            doStartSession(type);
        } else {
            requestPermissions(need.toArray(new String[0]), REQ_LOC);
        }
    }

    private void doStartSession(int type) {
        if (Build.VERSION.SDK_INT >= 34
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            toast("Da Android 14 le attività richiedono il permesso posizione: consentilo e riprova. Il contapassi di ogni giorno funziona comunque.");
            return;
        }
        Intent i = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_START).putExtra(TrackingService.EXTRA_TYPE, type);
        try {
            startForegroundService(i);
        } catch (RuntimeException e) {
            toast("Non riesco ad avviare l'attività. Riprova con l'app aperta.");
            return;
        }
        if (!Sampler.canCount(this) && Sampler.hasSensor(this)) {
            toast("Senza il permesso «Attività fisica» i passi non si contano: la distanza arriva dal GPS.");
        }
        // lo stato viene scritto dal servizio: la schermata si aggiorna da sola entro un secondo
        sessKey = "";
        handler.postDelayed(this::refreshToday, 600);
    }

    private void stopSession() {
        long now = System.currentTimeMillis();
        SessionRec r = store.finishSession(now);
        try {
            startService(new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_STOP));
        } catch (RuntimeException ignored) {
            // il servizio si ferma comunque da solo senza attività
        }
        sessKey = "";
        refreshToday();
        if (r != null) {
            dialog().setTitle(r.type == 1 ? "Corsa conclusa" : "Camminata conclusa")
                .setMessage(Fmt.km(r.distM) + " in " + Fmt.hm(r.hours()) + "\n"
                    + Fmt.num(r.kcal, 0) + " kcal attive · " + Fmt.thousands(r.steps) + " passi")
                .setPositiveButton("Ok", null).show();
        }
    }

    // ------------------------------------------------------------------ schermata Storico

    private View buildHistoryScreen() {
        LinearLayout col = column();
        col.addView(header("Storico", "Gli ultimi giorni e le tue attività"));

        Engine e = store.read();
        long[] steps = new long[7];
        String[] labels = new String[7];
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -6);
        SimpleDateFormat dn = new SimpleDateFormat("EEE", Locale.ITALY);
        long total = 0;
        int daysWithData = 0;
        for (int i = 0; i < 7; i++) {
            Totals t = e.days.get(Store.dayKey(c.getTimeInMillis()));
            steps[i] = t == null ? 0 : t.steps;
            labels[i] = dn.format(c.getTime());
            total += steps[i];
            if (steps[i] > 0) daysWithData++;
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        LinearLayout chartCard = Ui.card(this);
        chartCard.addView(Ui.text(this, "Passi degli ultimi 7 giorni (il trattino dorato è l'obiettivo)", 12, Ui.MUTED));
        DaysChartView chart = new DaysChartView(this);
        chart.setData(steps, labels, store.stepGoal());
        chartCard.addView(chart, Ui.full(10));
        chartCard.addView(Ui.text(this,
            "Totale " + Fmt.thousands(total) + " passi · media " + Fmt.thousands(daysWithData == 0 ? 0 : total / daysWithData) + " al giorno",
            13, Ui.TEXT), Ui.full(8));
        col.addView(chartCard, Ui.full(16));

        List<SessionRec> all = store.sessions();
        if (all.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.addView(Ui.text(this, "Nessuna attività registrata. Quando ne concludi una, compare qui.", 15, Ui.TEXT));
            col.addView(empty, Ui.full(12));
            return scroll(col);
        }
        SimpleDateFormat f = new SimpleDateFormat("d MMM · HH:mm", Locale.ITALY);
        int shown = 0;
        for (int i = all.size() - 1; i >= 0 && shown < 30; i--, shown++) {
            SessionRec r = all.get(i);
            LinearLayout row = Ui.card(this);
            row.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
            row.addView(Ui.text(this, (r.type == 1 ? "Corsa" : "Camminata") + " · " + Fmt.km(r.distM), 17, Ui.TEXT));
            row.addView(Ui.text(this,
                f.format(new Date(r.start)) + " · " + Fmt.hm(r.hours()) + " · " + Fmt.num(r.kcal, 0) + " kcal · " + Fmt.thousands(r.steps) + " passi",
                12, Ui.MUTED), Ui.full(2));
            col.addView(row, Ui.full(8));
        }
        Button clear = Ui.button(this, "Cancella le attività", false);
        clear.setOnClickListener(v -> dialog().setTitle("Cancellare le attività?")
            .setMessage("Le attività registrate saranno eliminate. I passi di ogni giorno restano. Non si può annullare.")
            .setPositiveButton("Cancella", (d, w) -> {
                store.clearSessions();
                showTab(1);
            })
            .setNegativeButton("Annulla", null).show());
        col.addView(clear, Ui.full(16));
        return scroll(col);
    }

    // ------------------------------------------------------------------ schermata Profilo

    private static Double parse(String s) {
        if (s == null) return null;
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) return null;
        try {
            double v = Double.parseDouble(t);
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void styleSex() {
        if (sexMaleBtn == null) return;
        TextView[] b = {sexMaleBtn, sexFemaleBtn};
        for (int i = 0; i < 2; i++) {
            boolean sel = (i == 0) == sexMale;
            b[i].setTextColor(sel ? 0xFF1A1405 : Ui.GOLD_LIGHT);
            b[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b[i].setBackground(sel ? Ui.rect(Ui.GOLD, 14, 0) : Ui.rect(Ui.SURFACE2, 14, 0x33D4AF6A));
        }
    }

    private TextView sexChip(String label, boolean male) {
        TextView t = Ui.text(this, label, 16, Ui.GOLD_LIGHT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(12), 0, Ui.dp(12));
        t.setOnClickListener(v -> {
            sexMale = male;
            styleSex();
        });
        return t;
    }

    private View buildProfileScreen(boolean onboarding) {
        LinearLayout col = column();
        col.addView(header(onboarding ? "Benvenuto" : "Profilo",
            onboarding ? "Servono per calcolare passo e calorie" : "I tuoi dati di base"));

        LinearLayout form = Ui.card(this);
        inHeight = Ui.input(this, "Altezza in cm", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inWeight = Ui.input(this, "Peso in kg", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inAge = Ui.input(this, "Età in anni", InputType.TYPE_CLASS_NUMBER);
        inGoal = Ui.input(this, "Obiettivo passi al giorno", InputType.TYPE_CLASS_NUMBER);
        if (store.hasProfile()) {
            inHeight.setText(Fmt.num(store.heightCm(), 0));
            inWeight.setText(Fmt.num(store.weightKg(), 1));
            inAge.setText(String.valueOf(store.age()));
            sexMale = store.male();
        }
        inGoal.setText(String.valueOf(store.stepGoal()));
        form.addView(Ui.text(this, "Altezza (cm)", 13, Ui.MUTED));
        form.addView(inHeight, Ui.full(6));
        form.addView(Ui.text(this, "Peso (kg)", 13, Ui.MUTED), Ui.full(14));
        form.addView(inWeight, Ui.full(6));
        form.addView(Ui.text(this, "Età (anni)", 13, Ui.MUTED), Ui.full(14));
        form.addView(inAge, Ui.full(6));
        form.addView(Ui.text(this, "Sesso (serve solo per stimare passo e metabolismo)", 13, Ui.MUTED), Ui.full(14));
        LinearLayout sex = new LinearLayout(this);
        sex.setOrientation(LinearLayout.HORIZONTAL);
        sexMaleBtn = sexChip("Uomo", true);
        sexFemaleBtn = sexChip("Donna", false);
        sex.addView(sexMaleBtn, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        fp.leftMargin = Ui.dp(8);
        sex.addView(sexFemaleBtn, fp);
        form.addView(sex, Ui.full(6));
        styleSex();
        form.addView(Ui.text(this, "Obiettivo passi al giorno", 13, Ui.MUTED), Ui.full(14));
        form.addView(inGoal, Ui.full(6));
        col.addView(form, Ui.full(16));

        Button save = Ui.button(this, onboarding ? "Inizia" : "Salva", true);
        save.setOnClickListener(v -> saveProfile(onboarding));
        col.addView(save, Ui.full(14));
        col.addView(Ui.text(this,
            "I dati restano sul tuo telefono. L'app non li invia a nessuno. Passi è un'app di benessere: le calorie sono stime.",
            12, Ui.MUTED), Ui.full(12));
        return scroll(col);
    }

    private void saveProfile(boolean onboarding) {
        Double h = parse(inHeight.getText().toString());
        Double w = parse(inWeight.getText().toString());
        Double a = parse(inAge.getText().toString());
        Double g = parse(inGoal.getText().toString());
        if (h == null || !Body.validHeight(h)) { toast("Altezza non valida: inserisci i centimetri, tra 100 e 250."); return; }
        if (w == null || !Body.validWeight(w)) { toast("Peso non valido: inserisci i chili, tra 30 e 300."); return; }
        if (a == null || !Body.validAge((int) Math.round(a))) { toast("Età non valida."); return; }
        if (g == null || !Body.validGoal(Math.round(g))) { toast("Obiettivo non valido: tra 1.000 e 50.000 passi."); return; }
        store.saveProfile(h, w, (int) Math.round(a), sexMale, (int) Math.round(g));
        store.setHasStepSensor(Sampler.hasSensor(this));
        Sampler.scheduleNext(this, Sampler.EVERY_MS);
        if (onboarding) {
            showTab(0);
            if (Build.VERSION.SDK_INT >= 29 && !Sampler.canCount(this) && Sampler.hasSensor(this)) {
                requestPermissions(new String[] {Manifest.permission.ACTIVITY_RECOGNITION}, REQ_ACT);
            } else {
                startLiveSteps();
            }
        } else {
            toast("Salvato");
            showTab(2);
        }
    }
}

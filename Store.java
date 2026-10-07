package it.passi.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Tutti i dati restano sul telefono, in preferenze private dell'app. Ogni modifica passa da update(), con un solo blocco comune. */
final class Store {
    static final Object LOCK = new Object();
    private static final int MAX_SESSIONS = 100;

    interface Op {
        void run(Engine e);
    }

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("passi", Context.MODE_PRIVATE);
    }

    static String dayKey(long ms) {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ms));
    }

    // ---- profilo ----
    boolean hasProfile() { return p.contains("heightCm") && p.contains("weightKg") && p.contains("age"); }
    double heightCm() { return Double.longBitsToDouble(p.getLong("heightCm", Double.doubleToLongBits(170))); }
    double weightKg() { return Double.longBitsToDouble(p.getLong("weightKg", Double.doubleToLongBits(70))); }
    int age() { return p.getInt("age", 40); }
    boolean male() { return p.getBoolean("male", true); }
    int stepGoal() { return p.getInt("stepGoal", 8000); }

    void saveProfile(double heightCm, double weightKg, int age, boolean male, int goal) {
        p.edit()
            .putLong("heightCm", Double.doubleToLongBits(heightCm))
            .putLong("weightKg", Double.doubleToLongBits(weightKg))
            .putInt("age", age)
            .putBoolean("male", male)
            .putInt("stepGoal", goal)
            .apply();
    }

    void setHasStepSensor(boolean has) { p.edit().putBoolean("hasSensor", has).apply(); }

    // ---- motore ----
    private Engine load() {
        Engine e = new Engine();
        e.kg = weightKg();
        e.cm = heightCm();
        e.male = male();
        e.hasStepSensor = p.getBoolean("hasSensor", true);
        e.lastCounter = p.getLong("lastCounter", -1);
        e.cum = Totals.decode(p.getString("cum", ""));
        Engine.decodeDays(p.getString("days", ""), e.days);
        e.log = TotalsLog.decode(p.getString("log", ""));
        e.session = Session.decode(p.getString("session", ""));
        return e;
    }

    private void save(Engine e) {
        SharedPreferences.Editor ed = p.edit();
        ed.putLong("lastCounter", e.lastCounter);
        ed.putString("cum", e.cum.encode());
        ed.putString("days", Engine.encodeDays(e.days));
        ed.putString("log", e.log.encode());
        if (e.session != null) ed.putString("session", e.session.encode()); else ed.remove("session");
        ed.apply();
    }

    /** Legge lo stato, applica la modifica e salva, tutto sotto lo stesso blocco. */
    void update(Op op) {
        synchronized (LOCK) {
            Engine e = load();
            op.run(e);
            save(e);
        }
    }

    /** Sola lettura. */
    Engine read() {
        synchronized (LOCK) {
            return load();
        }
    }

    // ---- attività concluse ----
    List<SessionRec> sessions() { return SessionRec.decode(p.getString("sessions", "")); }

    void addSession(SessionRec r) {
        synchronized (LOCK) {
            List<SessionRec> l = SessionRec.add(sessions(), r, MAX_SESSIONS);
            p.edit().putString("sessions", SessionRec.encode(l)).apply();
        }
    }

    void clearSessions() {
        synchronized (LOCK) {
            p.edit().remove("sessions").apply();
        }
    }

    /** Chiude l'attività in corso (se c'è) e la salva nello storico, se è abbastanza lunga da contare. */
    SessionRec finishSession(final long nowMs) {
        final SessionRec[] out = new SessionRec[1];
        update(new Op() {
            @Override public void run(Engine e) {
                Session s = e.endSession();
                if (s == null) return;
                SessionRec r = SessionRec.of(s, nowMs);
                if (r.distM >= 20 || r.steps >= 30 || nowMs - s.startMs >= 60000) {
                    out[0] = r;
                }
            }
        });
        if (out[0] != null) addSession(out[0]);
        return out[0];
    }
}

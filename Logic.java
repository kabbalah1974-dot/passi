package it.passi.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Logica pura di Passi (nessuna dipendenza da Android): calorie, filtro GPS, totali, registro per Digiuno. */

/** Costo energetico per velocità, dalla tabella del Compendium of Physical Activities (camminata e corsa). */
final class Met {
    /** {km/h, MET}. Camminata fino a 7,2 km/h, poi corsa. */
    private static final double[][] TABLE = {
        {0.0, 1.0}, {3.2, 2.8}, {4.0, 3.0}, {4.8, 3.8}, {5.6, 4.8}, {6.4, 5.5}, {7.2, 7.0},
        {8.0, 8.5}, {8.9, 9.0}, {9.7, 9.3}, {10.8, 10.5}, {11.3, 11.0}, {12.1, 11.8},
        {12.9, 12.0}, {13.8, 12.5}, {14.5, 13.0}, {16.1, 14.8}
    };

    static double met(double kmh) {
        if (!(kmh > 0)) return 1.0;
        for (int i = 1; i < TABLE.length; i++) {
            if (kmh <= TABLE[i][0]) {
                double x0 = TABLE[i - 1][0], x1 = TABLE[i][0];
                double y0 = TABLE[i - 1][1], y1 = TABLE[i][1];
                return y0 + (y1 - y0) * (kmh - x0) / (x1 - x0);
            }
        }
        return TABLE[TABLE.length - 1][1];
    }

    /** MET in più rispetto al riposo: è ciò che si "spende" per muoversi. */
    static double netMet(double kmh) { return Math.max(0, met(kmh) - 1.0); }
}

final class Calc {
    static double clamp01(double v) {
        if (Double.isNaN(v)) return 0;
        return Math.max(0, Math.min(1, v));
    }
}

final class Body {
    /** Calorie in più per kg e per km camminando a ritmo medio (circa 5 km/h). */
    static final double KCAL_PER_KG_KM_STEPS = 0.60;

    static double strideM(double heightCm, boolean male) {
        return heightCm / 100.0 * (male ? 0.415 : 0.413);
    }

    static double stepsDistM(long steps, double strideM) { return steps * strideM; }

    static double stepsKcal(long steps, double kg, double strideM) {
        return steps * strideM / 1000.0 * kg * KCAL_PER_KG_KM_STEPS;
    }

    /** Metabolismo basale (Mifflin-St Jeor) in kcal al giorno. */
    static double bmrPerDay(boolean male, double kg, double cm, int age) {
        double b = 10 * kg + 6.25 * cm - 5 * age;
        return male ? b + 5 : b - 161;
    }

    static boolean validHeight(double cm) { return cm >= 100 && cm <= 250; }
    static boolean validWeight(double kg) { return kg >= 30 && kg <= 300; }
    static boolean validAge(int age) { return age >= 5 && age <= 110; }
    static boolean validGoal(long steps) { return steps >= 1000 && steps <= 50000; }
}

final class Totals {
    long steps;
    double kcal;
    double distM;

    Totals() {}

    Totals(long steps, double kcal, double distM) {
        this.steps = steps;
        this.kcal = kcal;
        this.distM = distM;
    }

    Totals copy() { return new Totals(steps, kcal, distM); }

    Totals minus(Totals o) {
        return new Totals(Math.max(0, steps - o.steps), Math.max(0, kcal - o.kcal), Math.max(0, distM - o.distM));
    }

    String encode() {
        return steps + "," + String.format(Locale.US, "%.3f", kcal) + "," + String.format(Locale.US, "%.1f", distM);
    }

    static Totals decode(String s) {
        Totals t = new Totals();
        if (s == null) return t;
        String[] f = s.split(",");
        if (f.length != 3) return t;
        try {
            long st = Long.parseLong(f[0].trim());
            double k = Double.parseDouble(f[1].trim());
            double d = Double.parseDouble(f[2].trim());
            if (st >= 0 && k >= 0 && d >= 0 && !Double.isNaN(k) && !Double.isNaN(d)) return new Totals(st, k, d);
        } catch (NumberFormatException ignored) {
            // dato rovinato: si riparte da zero
        }
        return new Totals();
    }
}

/** Filtro dei punti GPS: scarta i punti imprecisi, il "tremolio" da fermi e i salti impossibili. */
final class GpsFilter {
    static final double MAX_ACC_M = 30;
    static final double MIN_MOVE_M = 4;
    static final double MAX_SPEED_MS = 8;
    static final int REANCHOR_AFTER = 3;

    static final class Segment {
        final double distM;
        final double dtSec;

        Segment(double distM, double dtSec) {
            this.distM = distM;
            this.dtSec = dtSec;
        }
    }

    private boolean has;
    private double lat, lon;
    private long ms;
    private int bad;

    /** acc <= 0 significa "precisione sconosciuta". */
    static boolean usable(double lat, double lon, double acc) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || Double.isNaN(acc)) return false;
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return false;
        if (lat == 0 && lon == 0) return false;
        return !(acc > MAX_ACC_M);
    }

    static double distance(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Restituisce il tratto percorso se il punto è valido e in movimento, altrimenti null. */
    Segment add(double la, double lo, double acc, long t) {
        if (!usable(la, lo, acc)) return null;
        if (!has) {
            has = true;
            lat = la;
            lon = lo;
            ms = t;
            return null;
        }
        double dt = (t - ms) / 1000.0;
        if (dt <= 0) return null;
        double d = distance(lat, lon, la, lo);
        double a = acc > 0 ? acc : 15;
        double threshold = Math.max(MIN_MOVE_M, 0.6 * a);
        if (d < threshold) {
            bad = 0;
            return null;
        }
        if (d / dt > MAX_SPEED_MS) {
            bad++;
            if (bad >= REANCHOR_AFTER) {
                lat = la;
                lon = lo;
                ms = t;
                bad = 0;
            }
            return null;
        }
        bad = 0;
        lat = la;
        lon = lo;
        ms = t;
        return new Segment(d, dt);
    }
}

/** Attività con GPS in corso. type: 0 camminata, 1 corsa. */
final class Session {
    static final long FRESH_MS = 20000;

    final long startMs;
    final int type;
    double distM;
    double kcal;
    long steps;
    long lastGoodFixMs;

    Session(long startMs, int type) {
        this.startMs = startMs;
        this.type = type;
    }

    boolean gpsFresh(long nowMs) {
        return lastGoodFixMs > 0 && nowMs - lastGoodFixMs <= FRESH_MS;
    }

    double avgSpeedKmh(long nowMs) {
        double h = (nowMs - startMs) / 3600000.0;
        return h > 0 ? distM / 1000.0 / h : 0;
    }

    String encode() {
        return startMs + "," + type + "," + String.format(Locale.US, "%.1f", distM) + ","
            + String.format(Locale.US, "%.3f", kcal) + "," + steps + "," + lastGoodFixMs;
    }

    static Session decode(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] f = s.split(",");
        if (f.length != 6) return null;
        try {
            Session x = new Session(Long.parseLong(f[0].trim()), Integer.parseInt(f[1].trim()));
            x.distM = Double.parseDouble(f[2].trim());
            x.kcal = Double.parseDouble(f[3].trim());
            x.steps = Long.parseLong(f[4].trim());
            x.lastGoodFixMs = Long.parseLong(f[5].trim());
            if (x.startMs <= 0 || x.distM < 0 || x.kcal < 0 || x.steps < 0) return null;
            return x;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

/** Attività conclusa, per lo storico. */
final class SessionRec {
    final long start, end;
    final int type;
    final double distM, kcal;
    final long steps;

    SessionRec(long start, long end, int type, double distM, double kcal, long steps) {
        this.start = start;
        this.end = end;
        this.type = type;
        this.distM = distM;
        this.kcal = kcal;
        this.steps = steps;
    }

    double hours() { return Math.max(0, (end - start) / 3600000.0); }

    static SessionRec of(Session s, long endMs) {
        return new SessionRec(s.startMs, endMs, s.type, s.distM, s.kcal, s.steps);
    }

    static String encode(List<SessionRec> list) {
        StringBuilder sb = new StringBuilder();
        for (SessionRec r : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(r.start).append(',').append(r.end).append(',').append(r.type).append(',')
              .append(String.format(Locale.US, "%.1f", r.distM)).append(',')
              .append(String.format(Locale.US, "%.3f", r.kcal)).append(',').append(r.steps);
        }
        return sb.toString();
    }

    static List<SessionRec> decode(String s) {
        List<SessionRec> out = new ArrayList<SessionRec>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) {
            String[] f = part.split(",");
            if (f.length != 6) continue;
            try {
                long a = Long.parseLong(f[0].trim()), b = Long.parseLong(f[1].trim());
                int ty = Integer.parseInt(f[2].trim());
                double d = Double.parseDouble(f[3].trim()), k = Double.parseDouble(f[4].trim());
                long st = Long.parseLong(f[5].trim());
                if (b >= a && d >= 0 && k >= 0 && st >= 0) out.add(new SessionRec(a, b, ty, d, k, st));
            } catch (NumberFormatException ignored) {
                // voce rovinata: si salta
            }
        }
        return out;
    }

    static List<SessionRec> add(List<SessionRec> list, SessionRec r, int max) {
        List<SessionRec> out = new ArrayList<SessionRec>(list);
        out.add(r);
        while (out.size() > max) out.remove(0);
        return out;
    }
}

/** Registro dei totali nel tempo: permette a Digiuno di sapere quante calorie sono state spese "dall'inizio del digiuno". */
final class TotalsLog {
    static final class Point {
        long ms;
        double kcal;
        long steps;
        double dist;

        Totals toTotals() { return new Totals(steps, kcal, dist); }
    }

    static final int MAX_POINTS = 500;
    static final long MAX_AGE_MS = 3L * 24 * 3600000L;
    static final long MERGE_MS = 30000;

    final List<Point> pts = new ArrayList<Point>();

    void add(long ms, Totals c) {
        Point last = pts.isEmpty() ? null : pts.get(pts.size() - 1);
        if (last != null && ms < last.ms) return;
        if (last != null && pts.size() > 1 && ms - last.ms < MERGE_MS) {
            last.ms = ms;
            last.kcal = c.kcal;
            last.steps = c.steps;
            last.dist = c.distM;
        } else {
            Point p = new Point();
            p.ms = ms;
            p.kcal = c.kcal;
            p.steps = c.steps;
            p.dist = c.distM;
            pts.add(p);
        }
        while (pts.size() > MAX_POINTS) pts.remove(0);
        while (pts.size() > 2 && ms - pts.get(0).ms > MAX_AGE_MS) pts.remove(0);
    }

    /** Totali stimati in un certo istante (interpolati tra due punti del registro). */
    Totals at(long ms) {
        if (pts.isEmpty()) return new Totals();
        Point f = pts.get(0);
        if (ms <= f.ms) return f.toTotals();
        Point l = pts.get(pts.size() - 1);
        if (ms >= l.ms) return l.toTotals();
        for (int i = 1; i < pts.size(); i++) {
            Point b = pts.get(i);
            if (ms <= b.ms) {
                Point a = pts.get(i - 1);
                double frac = (ms - a.ms) / (double) (b.ms - a.ms);
                return new Totals(
                    Math.round(a.steps + (b.steps - a.steps) * frac),
                    a.kcal + (b.kcal - a.kcal) * frac,
                    a.dist + (b.dist - a.dist) * frac);
            }
        }
        return l.toTotals();
    }

    String encode() {
        StringBuilder sb = new StringBuilder();
        for (Point p : pts) {
            if (sb.length() > 0) sb.append(';');
            sb.append(p.ms).append(':').append(String.format(Locale.US, "%.3f", p.kcal)).append(':')
              .append(p.steps).append(':').append(String.format(Locale.US, "%.1f", p.dist));
        }
        return sb.toString();
    }

    static TotalsLog decode(String s) {
        TotalsLog log = new TotalsLog();
        if (s == null || s.isEmpty()) return log;
        long prev = Long.MIN_VALUE;
        for (String part : s.split(";")) {
            String[] f = part.split(":");
            if (f.length != 4) continue;
            try {
                Point p = new Point();
                p.ms = Long.parseLong(f[0].trim());
                p.kcal = Double.parseDouble(f[1].trim());
                p.steps = Long.parseLong(f[2].trim());
                p.dist = Double.parseDouble(f[3].trim());
                if (p.ms < prev || p.kcal < 0 || p.steps < 0 || p.dist < 0) continue;
                prev = p.ms;
                log.pts.add(p);
            } catch (NumberFormatException ignored) {
                // punto rovinato: si salta
            }
        }
        return log;
    }
}

/** Motore: riceve letture del contapassi e punti GPS, aggiorna i totali. Nessuna dipendenza da Android. */
final class Engine {
    static final long MAX_DELTA_STEPS = 60000;
    static final int MAX_DAYS = 60;

    double kg = 70;
    double cm = 170;
    boolean male = true;
    boolean hasStepSensor = true;
    long lastCounter = -1;
    Totals cum = new Totals();
    final TreeMap<String, Totals> days = new TreeMap<String, Totals>();
    TotalsLog log = new TotalsLog();
    Session session;
    private GpsFilter gps = new GpsFilter();

    double stride() { return Body.strideM(cm, male); }

    Totals today(String dayKey) {
        Totals t = days.get(dayKey);
        if (t == null) {
            t = new Totals();
            days.put(dayKey, t);
            while (days.size() > MAX_DAYS) days.remove(days.firstKey());
        }
        return t;
    }

    private void credit(String dayKey, long steps, double kcal, double distM) {
        Totals d = today(dayKey);
        d.steps += steps;
        d.kcal += kcal;
        d.distM += distM;
        cum.steps += steps;
        cum.kcal += kcal;
        cum.distM += distM;
        if (session != null) {
            session.steps += steps;
            session.kcal += kcal;
            session.distM += distM;
        }
    }

    private void ensureLog(long ms) {
        if (log.pts.isEmpty()) log.add(ms, cum);
    }

    /** counter = valore cumulativo del sensore (si azzera al riavvio del telefono). */
    void onStepCounter(long ms, long counter, String dayKey) {
        ensureLog(ms);
        if (counter < 0) return;
        if (lastCounter < 0) {
            lastCounter = counter;
            log.add(ms, cum);
            return;
        }
        long delta = counter >= lastCounter ? counter - lastCounter : counter;
        lastCounter = counter;
        if (delta <= 0 || delta > MAX_DELTA_STEPS) {
            log.add(ms, cum);
            return;
        }
        if (session == null || !session.gpsFresh(ms)) {
            double st = stride();
            credit(dayKey, delta, Body.stepsKcal(delta, kg, st), Body.stepsDistM(delta, st));
        } else {
            credit(dayKey, delta, 0, 0);
        }
        log.add(ms, cum);
    }

    void onFix(long ms, double lat, double lon, double acc, String dayKey) {
        if (session == null) return;
        ensureLog(ms);
        if (GpsFilter.usable(lat, lon, acc)) session.lastGoodFixMs = ms;
        GpsFilter.Segment s = gps.add(lat, lon, acc, ms);
        if (s == null) return;
        double kmh = s.distM / s.dtSec * 3.6;
        double kcal = Met.netMet(kmh) * kg * s.dtSec / 3600.0;
        long est = hasStepSensor ? 0 : Math.round(s.distM / stride());
        credit(dayKey, est, kcal, s.distM);
        log.add(ms, cum);
    }

    void startSession(long ms, int type) {
        ensureLog(ms);
        session = new Session(ms, type);
        gps = new GpsFilter();
    }

    Session endSession() {
        Session s = session;
        session = null;
        return s;
    }

    /** Totali dall'istante startMs a ora. */
    Totals since(long startMs) { return cum.minus(log.at(startMs)); }

    static String encodeDays(Map<String, Totals> days) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Totals> e : days.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue().encode());
        }
        return sb.toString();
    }

    static void decodeDays(String s, TreeMap<String, Totals> out) {
        out.clear();
        if (s == null || s.isEmpty()) return;
        for (String part : s.split(";")) {
            int i = part.indexOf('=');
            if (i != 8) continue;
            String key = part.substring(0, i);
            boolean digits = true;
            for (int k = 0; k < key.length(); k++) if (!Character.isDigit(key.charAt(k))) digits = false;
            if (!digits) continue;
            out.put(key, Totals.decode(part.substring(i + 1)));
        }
        while (out.size() > MAX_DAYS) out.remove(out.firstKey());
    }
}

final class Fmt {
    static String hms(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    static String km(double meters) {
        return String.format(Locale.ITALY, "%.2f km", Math.max(0, meters) / 1000.0);
    }

    static String num(double v, int decimals) {
        return String.format(Locale.ITALY, "%." + decimals + "f", v);
    }

    static String thousands(long v) {
        return String.format(Locale.ITALY, "%,d", v);
    }

    /** Passo in minuti per km, es. "6:15 /km"; "—" se troppo lento o fermo. */
    static String pace(double kmh) {
        if (!(kmh >= 1.0)) return "—";
        double minPerKm = 60.0 / kmh;
        int m = (int) minPerKm;
        int s = (int) Math.round((minPerKm - m) * 60);
        if (s == 60) {
            m++;
            s = 0;
        }
        return String.format(Locale.US, "%d:%02d /km", m, s);
    }

    static String hm(double hours) {
        long totalMin = Math.round(Math.max(0, hours) * 60);
        long h = totalMin / 60, m = totalMin % 60;
        if (h == 0) return m + " min";
        if (m == 0) return h + " h";
        return h + " h " + m + " min";
    }
}

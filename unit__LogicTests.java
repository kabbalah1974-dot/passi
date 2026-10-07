package it.passi.app;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Prove automatiche della logica pura. Se una fallisce, la costruzione dell'app si ferma. */
final class LogicTests {
    private static int passed = 0;
    private static int failed = 0;
    private static final String D1 = "20261007";
    private static final String D2 = "20261008";

    private static void check(boolean ok, String name) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FALLITA: " + name);
        }
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    public static void main(String[] args) {
        met();
        body();
        codecs();
        gpsFilter();
        engineSteps();
        engineSession();
        engineGpsWalk();
        logAndSince();
        formats();
        System.out.println("Prove riuscite: " + passed + ", fallite: " + failed);
        if (failed > 0) System.exit(1);
    }

    private static void met() {
        check(Met.met(0) == 1.0, "MET a 0 km/h");
        check(Met.met(-3) == 1.0, "MET negativo");
        check(Met.met(Double.NaN) == 1.0, "MET NaN");
        check(near(Met.met(3.2), 2.8, 1e-9), "MET 3,2 km/h");
        check(near(Met.met(4.8), 3.8, 1e-9), "MET 4,8 km/h");
        check(near(Met.met(8.0), 8.5, 1e-9), "MET 8 km/h");
        check(near(Met.met(16.1), 14.8, 1e-9), "MET 16,1 km/h");
        check(Met.met(40) == 14.8, "MET oltre tabella");
        check(near(Met.met(1.6), 1.9, 1e-9), "MET interpolato a 1,6 km/h");
        double prev = 0;
        boolean monotone = true;
        for (double v = 0; v <= 30; v += 0.1) {
            double m = Met.met(v);
            if (m < prev - 1e-12) monotone = false;
            prev = m;
        }
        check(monotone, "MET crescente con la velocità");
        check(Met.netMet(0) == 0, "MET netto fermo");
        check(near(Met.netMet(4.8), 2.8, 1e-9), "MET netto 4,8");
    }

    private static void body() {
        check(Calc.clamp01(0.5) == 0.5 && Calc.clamp01(-1) == 0 && Calc.clamp01(7) == 1 && Calc.clamp01(Double.NaN) == 0, "clamp01");
        check(near(Body.strideM(170, true), 0.7055, 1e-9), "passo uomo");
        check(near(Body.strideM(170, false), 0.7021, 1e-9), "passo donna");
        check(near(Body.stepsKcal(1000, 70, Body.strideM(170, true)), 29.631, 0.01), "kcal 1000 passi");
        check(near(Body.stepsDistM(1000, 0.7), 700, 1e-9), "distanza da passi");
        check(near(Body.bmrPerDay(true, 80, 180, 40), 1730, 1e-9), "BMR uomo");
        check(near(Body.bmrPerDay(false, 60, 165, 30), 1320.25, 1e-9), "BMR donna");
        check(Body.validHeight(165) && !Body.validHeight(80) && !Body.validHeight(260), "altezza");
        check(Body.validWeight(100) && !Body.validWeight(20) && !Body.validWeight(400), "peso");
        check(Body.validAge(51) && !Body.validAge(2) && !Body.validAge(130), "età");
        check(Body.validGoal(8000) && !Body.validGoal(500) && !Body.validGoal(90000), "obiettivo passi");
    }

    private static void codecs() {
        Totals t = new Totals(1234, 56.789, 901.2);
        Totals b = Totals.decode(t.encode());
        check(b.steps == 1234 && near(b.kcal, 56.789, 1e-3) && near(b.distM, 901.2, 0.1), "Totals andata e ritorno");
        check(Totals.decode(null).steps == 0, "Totals null");
        check(Totals.decode("x,y").steps == 0, "Totals rovinato");
        check(Totals.decode("5,-1,3").steps == 0, "Totals negativo scartato");
        check(Totals.decode("5,NaN,3").steps == 0, "Totals NaN scartato");
        check(near(t.minus(new Totals(234, 6.789, 1.2)).kcal, 50, 1e-9), "Totals minus");
        check(t.minus(new Totals(9999, 999, 99999)).steps == 0, "Totals minus non negativo");

        Session s = new Session(1000, 1);
        s.distM = 123.4;
        s.kcal = 7.5;
        s.steps = 150;
        s.lastGoodFixMs = 5000;
        Session s2 = Session.decode(s.encode());
        check(s2 != null && s2.type == 1 && s2.steps == 150 && s2.lastGoodFixMs == 5000, "Session andata e ritorno");
        check(Session.decode(null) == null && Session.decode("") == null && Session.decode("1,2,3") == null, "Session rovinata");
        check(Session.decode("0,0,1,1,1,1") == null, "Session senza inizio");

        List<SessionRec> l = new ArrayList<SessionRec>();
        l.add(new SessionRec(1000, 3601000, 0, 3200, 150.5, 4100));
        l.add(new SessionRec(9000000, 9600000, 1, 1500, 110, 1800));
        List<SessionRec> back = SessionRec.decode(SessionRec.encode(l));
        check(back.size() == 2 && back.get(1).type == 1 && back.get(0).steps == 4100, "SessionRec andata e ritorno");
        check(near(back.get(0).hours(), 1.0, 1e-9), "SessionRec ore");
        check(SessionRec.decode("a;1,2;5,3,0,1,1,1").isEmpty(), "SessionRec rovinate");
        List<SessionRec> big = new ArrayList<SessionRec>();
        for (int i = 0; i < 6; i++) big = SessionRec.add(big, new SessionRec(i, i + 1, 0, 1, 1, 1), 4);
        check(big.size() == 4 && big.get(0).start == 2, "SessionRec tiene le ultime");

        TreeMap<String, Totals> days = new TreeMap<String, Totals>();
        days.put(D1, new Totals(100, 5, 70));
        days.put(D2, new Totals(200, 9, 140));
        TreeMap<String, Totals> out = new TreeMap<String, Totals>();
        Engine.decodeDays(Engine.encodeDays(days), out);
        check(out.size() == 2 && out.get(D2).steps == 200, "giorni andata e ritorno");
        Engine.decodeDays("xx=1,2,3;20261007=1,2,3;abc", out);
        check(out.size() == 1 && out.containsKey(D1), "giorni rovinati scartati");
        Engine.decodeDays(null, out);
        check(out.isEmpty(), "giorni null");
    }

    private static void gpsFilter() {
        check(GpsFilter.usable(45, 9, 10), "punto valido");
        check(GpsFilter.usable(45, 9, 0), "precisione sconosciuta accettata");
        check(!GpsFilter.usable(45, 9, 50), "punto impreciso scartato");
        check(!GpsFilter.usable(0, 0, 5), "punto 0,0 scartato");
        check(!GpsFilter.usable(95, 9, 5), "latitudine impossibile");
        check(!GpsFilter.usable(Double.NaN, 9, 5), "NaN scartato");
        check(near(GpsFilter.distance(45, 9, 46, 9), 111195, 100), "1 grado di latitudine");
        check(GpsFilter.distance(45, 9, 45, 9) == 0, "distanza zero");

        // fermo con il tremolio del GPS (circa 3 m di rumore): nessun movimento contato
        GpsFilter f = new GpsFilter();
        double total = 0;
        double[][] jitter = {{0, 0}, {0.00001, 0.00001}, {-0.00001, 0.00001}, {0.00002, -0.00001}, {-0.00001, -0.00002}, {0.00001, 0}};
        for (int i = 0; i < 60; i++) {
            double[] j = jitter[i % jitter.length];
            GpsFilter.Segment s = f.add(45 + j[0], 9 + j[1], 8, 1000L * i);
            if (s != null) total += s.distM;
        }
        check(total == 0, "fermo: nessuna distanza (" + total + ")");

        // camminata dritta a 1,4 m/s con un punto al secondo: distanza vicina a quella vera
        GpsFilter w = new GpsFilter();
        double sum = 0;
        double mPerDeg = 111195.0;
        for (int i = 0; i <= 300; i++) {
            double lat = 45 + (1.4 * i) / mPerDeg;
            GpsFilter.Segment s = w.add(lat, 9, 5, 1000L * i);
            if (s != null) sum += s.distM;
        }
        double truth = 1.4 * 300;
        check(sum > truth * 0.93 && sum <= truth * 1.001, "camminata: distanza " + sum + " vs " + truth);

        // salto impossibile: scartato e non conta
        GpsFilter j = new GpsFilter();
        j.add(45, 9, 5, 0);
        GpsFilter.Segment bad = j.add(45.1, 9, 5, 1000);
        check(bad == null, "salto di 11 km in 1 s scartato");
        GpsFilter.Segment ok = j.add(45.00005, 9, 5, 5000);
        check(ok != null && ok.distM < 10, "dopo il salto il tracciato riprende");

        // se il nuovo punto resta lontano, dopo tre rifiuti si riancora senza contare distanza
        GpsFilter r = new GpsFilter();
        r.add(45, 9, 5, 0);
        boolean none = true;
        for (int i = 1; i <= 3; i++) if (r.add(46, 9, 5, 1000L * i) != null) none = false;
        check(none, "tre salti di fila non contano");
        GpsFilter.Segment after = r.add(46.00006, 9, 5, 5000);
        check(after != null && after.distM < 10, "riancorato dopo i salti");

        // punti imprecisi ignorati
        GpsFilter p = new GpsFilter();
        p.add(45, 9, 5, 0);
        check(p.add(45.001, 9, 80, 5000) == null, "punto impreciso non conta");
    }

    private static Engine newEngine() {
        Engine e = new Engine();
        e.kg = 70;
        e.cm = 170;
        e.male = true;
        return e;
    }

    private static void engineSteps() {
        Engine e = newEngine();
        e.onStepCounter(1000, 5000, D1);
        check(e.today(D1).steps == 0 && e.lastCounter == 5000, "primo valore è solo la base");
        e.onStepCounter(2000, 5600, D1);
        check(e.today(D1).steps == 600 && e.cum.steps == 600, "600 passi");
        double expKcal = Body.stepsKcal(600, 70, e.stride());
        check(near(e.today(D1).kcal, expKcal, 1e-6), "calorie dai passi");
        check(near(e.today(D1).distM, 600 * e.stride(), 1e-6), "distanza dai passi");
        e.onStepCounter(3000, 5600, D1);
        check(e.today(D1).steps == 600, "nessun passo nuovo");
        e.onStepCounter(4000, 300, D1); // riavvio del telefono
        check(e.today(D1).steps == 900, "riavvio: contano i 300 passi dopo il riavvio");
        e.onStepCounter(5000, 400, D2); // giorno nuovo
        check(e.today(D2).steps == 100 && e.today(D1).steps == 900, "passaggio al giorno dopo");
        e.onStepCounter(6000, 400 + 70000, D2);
        check(e.today(D2).steps == 100, "salto assurdo ignorato");
        e.onStepCounter(7000, -5, D2);
        check(e.today(D2).steps == 100, "valore negativo ignorato");

        Engine many = newEngine();
        for (int i = 0; i < 70; i++) many.today(String.format("2026%04d", 1000 + i));
        check(many.days.size() == Engine.MAX_DAYS, "tiene al massimo 60 giorni");
    }

    private static void engineSession() {
        Engine e = newEngine();
        e.onStepCounter(0, 1000, D1);
        e.startSession(10000, 0);
        check(e.session != null && e.session.startMs == 10000, "sessione avviata");
        // senza GPS: i passi contano con la stima dal passo
        e.onStepCounter(20000, 1100, D1);
        check(e.session.steps == 100 && e.session.kcal > 0 && e.session.distM > 0, "sessione senza GPS usa i passi");
        double kcalNoGps = e.session.kcal;
        // con GPS fresco: i passi contano ma non le calorie (le dà il GPS)
        e.onFix(25000, 45, 9, 5, D1);
        e.onStepCounter(30000, 1200, D1);
        check(e.session.steps == 200 && near(e.session.kcal, kcalNoGps, 1e-9), "GPS fresco: passi senza doppie calorie");
        // GPS non più fresco dopo 20 s: si torna alla stima dai passi
        e.onStepCounter(60000, 1300, D1);
        check(e.session.kcal > kcalNoGps, "GPS vecchio: torna ai passi");
        Session done = e.endSession();
        check(done != null && e.session == null && done.steps == 300, "sessione chiusa");
        check(e.endSession() == null, "chiudere due volte non fa nulla");
        e.onFix(70000, 45, 9, 5, D1);
        check(e.today(D1).steps == 300, "punto GPS senza sessione ignorato");
    }

    private static void engineGpsWalk() {
        Engine e = newEngine();
        e.hasStepSensor = true;
        final long base = 1000000L;
        e.onStepCounter(base, 0, D1);
        e.startSession(base, 0);
        double mPerDeg = 111195.0;
        long t = base;
        long counter = 0;
        for (int i = 0; i <= 600; i++) { // 10 minuti a 1,4 m/s = 5,04 km/h
            t = base + 1000L * i;
            e.onFix(t, 45 + (1.4 * i) / mPerDeg, 9, 5, D1);
            if (i % 2 == 0) {
                counter += 3;
                e.onStepCounter(t, counter, D1);
            }
        }
        Session s = e.session;
        check(s.distM > 780 && s.distM <= 841, "camminata GPS distanza " + s.distM);
        double expected = Met.netMet(5.04) * 70 * (600.0 / 3600.0);
        check(s.kcal > expected * 0.9 && s.kcal < expected * 1.1, "camminata GPS kcal " + s.kcal + " vs " + expected);
        check(s.steps > 800 && s.steps < 1000, "passi in sessione");
        check(near(e.today(D1).kcal, s.kcal, 1e-6), "giorno = sessione (niente doppi conteggi)");
        check(near(e.cum.kcal, s.kcal, 1e-6), "totale = sessione");
        check(s.avgSpeedKmh(t) > 4.3 && s.avgSpeedKmh(t) < 5.4, "velocità media");

        // senza contapassi: i passi si stimano dalla distanza
        Engine ns = newEngine();
        ns.hasStepSensor = false;
        ns.startSession(base, 0);
        for (int i = 0; i <= 300; i++) ns.onFix(base + 1000L * i, 45 + (1.4 * i) / mPerDeg, 9, 5, D1);
        check(ns.today(D1).steps > 400 && ns.today(D1).steps < 650, "senza sensore: passi dalla distanza " + ns.today(D1).steps);

        // corsa: stesso tempo, più calorie
        Engine run = newEngine();
        run.startSession(base, 1);
        for (int i = 0; i <= 600; i++) run.onFix(base + 1000L * i, 45 + (3.0 * i) / mPerDeg, 9, 5, D1);
        check(run.session.kcal > s.kcal * 1.8, "corsa spende più della camminata " + run.session.kcal);
    }

    private static void logAndSince() {
        Engine e = newEngine();
        long h = 3600000L;
        e.onStepCounter(0, 0, D1);
        for (int i = 1; i <= 10; i++) e.onStepCounter(i * h, i * 1000L, D1); // 1000 passi all'ora
        check(e.cum.steps == 10000, "cumulativo 10000 passi");
        Totals all = e.since(0);
        check(all.steps == 10000, "since dall'inizio");
        Totals last3 = e.since(7 * h);
        check(last3.steps == 3000, "since ultime 3 ore: " + last3.steps);
        Totals mid = e.since(7 * h + h / 2);
        check(mid.steps >= 2495 && mid.steps <= 2505, "since interpolato a metà ora: " + mid.steps);
        check(e.since(100 * h).steps == 0, "since nel futuro è zero");
        check(e.since(-5 * h).steps == 10000, "since prima del registro conta tutto dal primo punto");
        check(near(last3.kcal, e.cum.kcal * 0.3, e.cum.kcal * 0.01), "kcal proporzionali");

        TotalsLog dec = TotalsLog.decode(e.log.encode());
        check(dec.pts.size() == e.log.pts.size(), "registro andata e ritorno");
        check(dec.at(7 * h).steps == e.log.at(7 * h).steps, "registro decodificato dà gli stessi valori");
        check(TotalsLog.decode("1:2:3").pts.isEmpty(), "registro rovinato");
        check(TotalsLog.decode("5:1:1:1;3:1:1:1").pts.size() == 1, "registro con tempo che torna indietro");
        check(TotalsLog.decode(null).pts.isEmpty(), "registro null");

        TotalsLog l = new TotalsLog();
        Totals c = new Totals();
        for (int i = 0; i < 800; i++) {
            c.steps = i;
            l.add(i * 60000L, c);
        }
        check(l.pts.size() <= TotalsLog.MAX_POINTS, "registro limitato nel numero");
        l.add(1000L * 3600000L, c);
        check(l.pts.size() <= 3, "registro scarta i punti vecchi di più di 3 giorni: " + l.pts.size());
        TotalsLog m = new TotalsLog();
        m.add(0, new Totals(0, 0, 0));
        m.add(10000, new Totals(10, 1, 7));
        m.add(20000, new Totals(20, 2, 14));
        check(m.pts.size() == 2, "punti ravvicinati si fondono (tranne il primo)");
        m.add(5000, new Totals(99, 9, 9));
        check(m.pts.size() == 2, "tempo che torna indietro ignorato");
    }

    private static void formats() {
        check(Fmt.hms(0).equals("00:00:00") && Fmt.hms(3661000).equals("01:01:01") && Fmt.hms(-5).equals("00:00:00"), "hms");
        check(Fmt.km(1250).equals("1,25 km") && Fmt.km(-3).equals("0,00 km"), "km");
        check(Fmt.num(23.456, 1).equals("23,5"), "num");
        check(Fmt.thousands(8000).equals("8.000"), "migliaia");
        check(Fmt.pace(10).equals("6:00 /km") && Fmt.pace(0).equals("—") && Fmt.pace(0.5).equals("—"), "passo");
        check(Fmt.pace(5.04).equals("11:54 /km"), "passo 5,04 km/h: " + Fmt.pace(5.04));
        check(Fmt.hm(1.5).equals("1 h 30 min") && Fmt.hm(0.25).equals("15 min") && Fmt.hm(2).equals("2 h"), "hm");
    }
}

package it.passi.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Collegamento con Digiuno. Risponde solo a Digiuno (stesso nome e stessa firma) e solo quando è Digiuno a chiedere,
 * cioè quando l'utente accende il collegamento per un digiuno. Passi non invia mai niente da sola.
 */
public class LinkProvider extends ContentProvider {
    private static final String CALLER = "it.digiuno.app";

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }

    @Override public String getType(Uri uri) { return null; }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    private boolean callerAllowed(Context c) {
        String caller = getCallingPackage();
        if (caller == null || !CALLER.equals(caller)) return false;
        try {
            return c.getPackageManager().checkSignatures(c.getPackageName(), caller) == PackageManager.SIGNATURE_MATCH;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        if (c == null || !"summary".equals(method) || !callerAllowed(c)) return null;
        Store store = new Store(c);
        Bundle b = new Bundle();
        if (!store.hasProfile()) {
            b.putBoolean("ok", false);
            b.putString("reason", "no_profile");
            return b;
        }
        boolean sensorOk = Sampler.hasSensor(c) && Sampler.canCount(c);
        if (sensorOk) freshSample(c);

        long startMs = extras == null ? 0 : extras.getLong("startMs", 0);
        long now = System.currentTimeMillis();
        Engine e = store.read();
        Totals today = e.days.get(Store.dayKey(now));
        if (today == null) today = new Totals();
        Totals since = startMs > 0 ? e.since(startMs) : new Totals();
        long sample = e.log.pts.isEmpty() ? 0 : e.log.pts.get(e.log.pts.size() - 1).ms;

        b.putBoolean("ok", true);
        b.putBoolean("sensorOk", sensorOk);
        b.putLong("sampleMs", sample);
        b.putLong("stepsToday", today.steps);
        b.putDouble("kcalToday", today.kcal);
        b.putDouble("distToday", today.distM);
        b.putLong("sinceSteps", since.steps);
        b.putDouble("sinceKcal", since.kcal);
        b.putDouble("sinceDist", since.distM);
        b.putBoolean("sessionActive", e.session != null);
        return b;
    }

    /** Una lettura fresca del contapassi prima di rispondere, al massimo 3 secondi. */
    private void freshSample(final Context c) {
        HandlerThread ht = new HandlerThread("passi-link");
        ht.start();
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            Handler h = new Handler(ht.getLooper());
            Sampler.sampleOnce(c, h, 2500, new Runnable() {
                @Override public void run() { latch.countDown(); }
            });
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            ht.quitSafely();
        }
    }
}

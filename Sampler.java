package it.passi.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;

/** Lettura del contapassi del telefono e allarmi periodici che la ripetono anche ad app chiusa. */
final class Sampler {
    static final String ACTION_SAMPLE = "it.passi.app.SAMPLE";
    static final long EVERY_MS = 20 * 60000L;

    private Sampler() {}

    static boolean hasSensor(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    /** Dalla versione 10 di Android il contapassi richiede un permesso. */
    static boolean canCount(Context c) {
        if (Build.VERSION.SDK_INT < 29) return true;
        return c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    static void applyStepCounter(Context c, final long counter) {
        final long now = System.currentTimeMillis();
        new Store(c).update(new Store.Op() {
            @Override public void run(Engine e) {
                e.onStepCounter(now, counter, Store.dayKey(now));
            }
        });
    }

    /** Una lettura sola: registra l'ascolto, prende il primo valore e smette. Tutto avviene sul thread di h. */
    static void sampleOnce(final Context c, final Handler h, final long timeoutMs, final Runnable done) {
        final Context app = c.getApplicationContext();
        final SensorManager sm = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        final Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (s == null || !canCount(app)) {
            if (done != null) done.run();
            return;
        }
        final boolean[] finished = {false};
        final SensorEventListener l = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent ev) {
                if (finished[0]) return;
                finished[0] = true;
                sm.unregisterListener(this);
                if (ev.values != null && ev.values.length > 0) applyStepCounter(app, (long) ev.values[0]);
                if (done != null) done.run();
            }

            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };
        boolean ok = sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL, h);
        if (!ok) {
            finished[0] = true;
            if (done != null) done.run();
            return;
        }
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (finished[0]) return;
                finished[0] = true;
                sm.unregisterListener(l);
                if (done != null) done.run();
            }
        }, timeoutMs);
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_SAMPLE);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Programma la prossima lettura (rimettere lo stesso allarme lo sostituisce, non lo duplica). */
    static void scheduleNext(Context c, long inMs) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + inMs, alarmIntent(c));
    }
}

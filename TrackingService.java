package it.passi.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Attività con GPS: resta attivo (con la sua notifica) anche a schermo spento, solo finché la tieni accesa tu. */
public class TrackingService extends Service {
    static final String ACTION_START = "it.passi.app.START";
    static final String ACTION_STOP = "it.passi.app.STOP";
    static final String EXTRA_TYPE = "type";
    private static final String CHANNEL = "attivita";
    private static final int NOTIF_ID = 1;
    private static final long TICK_MS = 5000;
    private static final long STALE_FIX_MS = 20000;

    private Store store;
    private Handler handler;
    private LocationManager lm;
    private SensorManager sm;
    private LocationListener locListener;
    private SensorEventListener stepListener;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification());
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        store = new Store(this);
        handler = new Handler(Looper.getMainLooper());
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Attività in corso", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Mostra tempo, distanza e calorie mentre cammini o corri");
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            finishAndStop();
            return START_NOT_STICKY;
        }
        Engine cur = store.read();
        if (intent == null && cur.session == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForegroundCompat(buildNotification());
        } catch (RuntimeException e) {
            stopSelf(); // il sistema non permette il servizio (per esempio senza permesso posizione)
            return START_NOT_STICKY;
        }
        if (cur.session == null) {
            final int type = intent != null ? intent.getIntExtra(EXTRA_TYPE, 0) : 0;
            final long now = System.currentTimeMillis();
            store.update(new Store.Op() {
                @Override public void run(Engine e) { e.startSession(now, type); }
            });
        }
        if (!running) {
            running = true;
            registerListeners();
            handler.postDelayed(tick, TICK_MS);
        }
        return START_STICKY;
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private Notification buildNotification() {
        Engine e = store.read();
        Session s = e.session;
        long now = System.currentTimeMillis();
        String title = s != null && s.type == 1 ? "Corsa in corso" : "Camminata in corso";
        String text = s == null ? "Pronto" : Fmt.km(s.distM) + " · " + Fmt.num(s.kcal, 0) + " kcal · " + Fmt.thousands(s.steps) + " passi";
        if (s != null && !s.gpsFresh(now)) text += " · GPS in cerca";
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, TrackingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stopAction = new Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat), "Ferma", stopPi).build();
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFD4AF6A)
            .setContentIntent(openPi)
            .addAction(stopAction);
        if (s != null) {
            b.setWhen(s.startMs).setShowWhen(true).setUsesChronometer(true);
        }
        return b.build();
    }

    private void registerListeners() {
        try {
            lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm != null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locListener = new LocationListener() {
                    @Override public void onLocationChanged(Location loc) { onFix(loc); }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) { }
                };
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 0f, locListener);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            locListener = null; // senza GPS si va avanti con i passi
        }
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor step = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (step != null && Sampler.canCount(this)) {
            stepListener = new SensorEventListener() {
                @Override public void onSensorChanged(SensorEvent ev) {
                    if (ev.values != null && ev.values.length > 0) Sampler.applyStepCounter(TrackingService.this, (long) ev.values[0]);
                }

                @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
            };
            sm.registerListener(stepListener, step, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void onFix(Location loc) {
        if (loc == null) return;
        final long now = System.currentTimeMillis();
        if (Math.abs(now - loc.getTime()) > STALE_FIX_MS) return; // posizione vecchia in memoria
        final double lat = loc.getLatitude();
        final double lon = loc.getLongitude();
        final double acc = loc.hasAccuracy() ? loc.getAccuracy() : 0;
        store.update(new Store.Op() {
            @Override public void run(Engine e) { e.onFix(now, lat, lon, acc, Store.dayKey(now)); }
        });
    }

    private void unregisterListeners() {
        try {
            if (lm != null && locListener != null) lm.removeUpdates(locListener);
        } catch (SecurityException ignored) {
            // niente da togliere
        }
        if (sm != null && stepListener != null) sm.unregisterListener(stepListener);
        locListener = null;
        stepListener = null;
    }

    private void finishAndStop() {
        running = false;
        handler.removeCallbacks(tick);
        unregisterListeners();
        store.finishSession(System.currentTimeMillis());
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        unregisterListeners();
        super.onDestroy();
    }
}

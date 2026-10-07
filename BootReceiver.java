package it.passi.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Dopo il riavvio del telefono rimette in piedi le letture periodiche e fa subito una lettura. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        if (in == null || !Intent.ACTION_BOOT_COMPLETED.equals(in.getAction())) return;
        final Context app = c.getApplicationContext();
        if (!new Store(app).hasProfile()) return;
        Sampler.scheduleNext(app, Sampler.EVERY_MS);
        final PendingResult pr = goAsync();
        Sampler.sampleOnce(app, new Handler(Looper.getMainLooper()), 4000, new Runnable() {
            @Override public void run() { pr.finish(); }
        });
    }
}

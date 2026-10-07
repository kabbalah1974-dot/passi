package it.passi.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Ogni 20 minuti legge il contapassi, anche con l'app chiusa, e programma la lettura successiva. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        if (in == null || !Sampler.ACTION_SAMPLE.equals(in.getAction())) return;
        final Context app = c.getApplicationContext();
        Sampler.scheduleNext(app, Sampler.EVERY_MS);
        final PendingResult pr = goAsync();
        Sampler.sampleOnce(app, new Handler(Looper.getMainLooper()), 4000, new Runnable() {
            @Override public void run() { pr.finish(); }
        });
    }
}

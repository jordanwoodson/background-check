package dev.bgcheck.fixture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import java.util.Arrays;

/** Disposable, visible workload used only by resource instrumentation tests. */
public final class ProbeActivity extends Activity {
    private byte[] residentMemory;
    private Thread worker;
    private volatile long work;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        residentMemory = new byte[8 * 1024 * 1024];
        Arrays.fill(residentMemory, (byte) 42);
        TextView label = new TextView(this);
        label.setText("Background Check test workload\n8 MiB resident memory · temporary CPU activity");
        label.setPadding(40, 80, 40, 40);
        setContentView(label);
        startService(new Intent(this, ProbeService.class));
        worker = new Thread(() -> {
            long end = SystemClock.elapsedRealtime() + 120_000;
            while (!Thread.currentThread().isInterrupted() && SystemClock.elapsedRealtime() < end) {
                long busyUntil = SystemClock.elapsedRealtime() + 50;
                while (SystemClock.elapsedRealtime() < busyUntil) work = work * 31 + residentMemory[(int) (work & 1023)];
                try { Thread.sleep(150); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }, "bgcheck-fixture-work");
        worker.start();
    }

    @Override public void onDestroy() {
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }
}

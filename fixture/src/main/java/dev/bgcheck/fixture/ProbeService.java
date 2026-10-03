package dev.bgcheck.fixture;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import java.util.Arrays;

/** A separate, idle process for verifying package memory and running-service diagnostics. */
public final class ProbeService extends Service {
    private byte[] residentMemory;

    @Override public void onCreate() {
        super.onCreate();
        residentMemory = new byte[4 * 1024 * 1024];
        Arrays.fill(residentMemory, (byte) 24);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        residentMemory = null;
        super.onDestroy();
    }
}

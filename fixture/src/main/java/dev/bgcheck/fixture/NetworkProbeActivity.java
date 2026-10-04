package dev.bgcheck.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Disposable traffic source. Connects only to the emulator host test server. */
public final class NetworkProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final String token = getIntent().getStringExtra("token");
        if (token == null || !token.matches("[0-9]+")) { finish(); return; }
        final TextView status = new TextView(this);
        status.setText("Testing fixture network access…");
        setContentView(status);
        new Thread(() -> {
            String result;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("10.0.2.2", 8765), 1500);
                socket.setSoTimeout(1500);
                socket.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                result = socket.getInputStream().read() == 'H' ? "ALLOWED" : "INVALID_RESPONSE";
            } catch (Exception blocked) { result = "BLOCKED"; }
            final String value = result;
            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "probe-" + token))) {
                out.write(value.getBytes(StandardCharsets.UTF_8));
            } catch (Exception failure) { throw new RuntimeException(failure); }
            runOnUiThread(() -> status.setText(value));
        }, "network-probe").start();
    }
}

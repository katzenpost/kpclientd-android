// SPDX-License-Identifier: AGPL-3.0-only

package org.hoi_polloi.kpdaemon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// DaemonService runs kpclientd as a process of this app, without root, for as long as the user
// keeps it on. The daemon listens only on a unix socket in the app's private directory.
public class DaemonService extends Service {

    private static final String TAG = "kpdaemon";
    private static final String CHANNEL = "daemon";
    static final String CUSTOM_CONFIG = "custom.toml";
    private static final String RESTART = "org.hoi_polloi.kpdaemon.RESTART";

    private static volatile Process process;

    static boolean running() {
        Process p = process;
        return p != null && p.isAlive();
    }

    // The socket the daemon listens on.
    static File socket(Context c) {
        return new File(c.getFilesDir(), "kpclientd.sock");
    }

    static File log(Context c) {
        return new File(c.getFilesDir(), c.getString(R.string.log_file));
    }

    static void start(Context c) {
        c.startForegroundService(new Intent(c, DaemonService.class));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, DaemonService.class));
    }

    // Stop the daemon and start it again, which ends every client's connection.
    static void restart(Context c) {
        if (running()) {
            c.startForegroundService(new Intent(c, DaemonService.class).setAction(RESTART));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        synchronized (DaemonService.class) {
            if (intent != null && RESTART.equals(intent.getAction())) {
                halt();
            }
            if (!running()) {
                try {
                    launch();
                } catch (IOException e) {
                    Log.e(TAG, "cannot start kpclientd", e);
                    stopSelf();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        synchronized (DaemonService.class) {
            halt();
        }
        super.onDestroy();
    }

    // Stop the daemon and wait for it to go.
    private static void halt() {
        Process p = process;
        process = null;
        if (p == null) {
            return;
        }
        p.destroy();
        try {
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly().waitFor();
            }
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void launch() throws IOException {
        File config = new File(getFilesDir(), getString(R.string.config_file));
        String toml = new String(readConfig(), StandardCharsets.UTF_8);
        File sock = socket(this);
        try (OutputStream out = new FileOutputStream(config)) {
            out.write(DaemonConfig.withListener(toml, sock.getAbsolutePath()).getBytes(StandardCharsets.UTF_8));
        }
        Files.deleteIfExists(sock.toPath());
        File binary = new File(getApplicationInfo().nativeLibraryDir, "libkpclientd.so");
        Process p = new ProcessBuilder(binary.getAbsolutePath(), "-c", config.getAbsolutePath())
                .directory(getFilesDir())
                .redirectErrorStream(true)
                .redirectOutput(log(this))
                .start();
        process = p;
        Thread watch = new Thread(() -> {
            try {
                int code = p.waitFor();
                Log.i(TAG, "kpclientd exited with " + code);
            } catch (InterruptedException ignored) {
            }
            synchronized (DaemonService.class) {
                if (process == p) {
                    process = null;
                    stopSelf();
                }
            }
        }, "kpclientd-watch");
        watch.setDaemon(true);
        watch.start();
    }

    // The configuration the user imported, or the built-in one.
    private byte[] readConfig() throws IOException {
        File custom = new File(getFilesDir(), CUSTOM_CONFIG);
        if (custom.exists()) {
            return Files.readAllBytes(custom.toPath());
        }
        try (InputStream in = getAssets().open(getString(R.string.config_file))) {
            return in.readAllBytes();
        }
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.channel_daemon), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.daemon_on))
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }
}

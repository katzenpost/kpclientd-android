// SPDX-License-Identifier: AGPL-3.0-only

package org.hoi_polloi.kpdaemon;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import org.hoi_polloi.kpdaemon.databinding.ActivityApprovalBinding;

// ApprovalActivity asks the user whether an app may use the daemon. Only this app opens it,
// through the PendingIntent ConnectProvider hands the asking app and the notification it posts:
// the app it names is the one Binder said was calling, never one an intent claims.
public class ApprovalActivity extends Activity {

    private static final String CHANNEL = "approvals";
    private static final String PKG = "pkg";
    private static final String DIGEST = "digest";
    // How long Allow waits before it takes a tap, so that a tap meant for something else
    // cannot land on it as the screen opens.
    private static final long SETTLE_MS = 1000;

    // The PendingIntent that opens this screen about `pkg`, and a notification with it.
    static PendingIntent ask(Context c, String pkg, String digest) {
        Intent i = new Intent(c, ApprovalActivity.class)
                .setData(Uri.fromParts("package", pkg, null))
                .putExtra(PKG, pkg)
                .putExtra(DIGEST, digest)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, pkg.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                c.getString(R.string.channel_approvals), NotificationManager.IMPORTANCE_HIGH));
        String label = Callers.label(c.getPackageManager(), pkg);
        nm.notify(pkg, 2, new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(c.getString(R.string.approval_title, label))
                .setContentText(c.getString(R.string.approval_notice))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
        return pi;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setHideOverlayWindows(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ActivityApprovalBinding b = ActivityApprovalBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.getRoot().setFilterTouchesWhenObscured(true);

        String pkg = getIntent().getStringExtra(PKG);
        String digest = getIntent().getStringExtra(DIGEST);
        PackageManager pm = getPackageManager();
        String current;
        try {
            current = pkg == null ? null : Callers.certDigest(pm, pkg);
        } catch (PackageManager.NameNotFoundException e) {
            current = null;
        }
        if (current == null || !current.equals(digest)) {
            // The app is gone, or now signed with another key: what it asked no longer stands.
            finish();
            return;
        }
        String label = Callers.label(pm, pkg);
        b.title.setText(getString(R.string.approval_title, label));
        b.pkg.setText(pkg);
        try {
            b.icon.setImageDrawable(pm.getApplicationIcon(pkg));
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        b.deny.setOnClickListener(v -> answer(pkg, digest, false));
        b.allow.setOnClickListener(v -> answer(pkg, digest, true));
        b.allow.setEnabled(false);
        b.deny.requestFocus();
        new Handler(Looper.getMainLooper()).postDelayed(() -> b.allow.setEnabled(true), SETTLE_MS);
    }

    private void answer(String pkg, String digest, boolean allow) {
        Callers.decide(this, pkg, digest, allow);
        getSystemService(NotificationManager.class).cancel(pkg, 2);
        finish();
    }
}

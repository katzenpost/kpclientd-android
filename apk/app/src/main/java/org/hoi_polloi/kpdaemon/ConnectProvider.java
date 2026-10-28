// SPDX-License-Identifier: AGPL-3.0-only

package org.hoi_polloi.kpdaemon;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import java.io.IOException;
import java.util.Arrays;

// ConnectProvider is how other apps reach the daemon. An app calls
//
//     getContentResolver().call(Uri.parse("content://org.hoi_polloi.kpdaemon.connect"),
//                               "connect", null, null)
//
// and gets a Bundle back:
//   "socket"  a ParcelFileDescriptor of a unix socket connected to kpclientd, to speak the thin
//             client protocol over, once the user has allowed the app;
//   "error"   otherwise: "approval" when the user has not yet answered, "denied" when they said
//             no, "stopped" when the daemon is not running, or "unknown" for an app that cannot
//             be named;
//   "approve" with "approval": a PendingIntent to open the screen where the user answers.
//
// The caller is known by the kernel's Binder identity, never by what it says about itself.
public class ConnectProvider extends ContentProvider {

    static final String AUTHORITY = "org.hoi_polloi.kpdaemon.connect";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"connect".equals(method)) {
            return null;
        }
        int uid = Binder.getCallingUid();
        String pkg = getCallingPackage();
        PackageManager pm = getContext().getPackageManager();
        long id = Binder.clearCallingIdentity();
        try {
            return connect(pm, uid, pkg);
        } finally {
            Binder.restoreCallingIdentity(id);
        }
    }

    private Bundle connect(PackageManager pm, int uid, String pkg) {
        Bundle out = new Bundle();
        String[] pkgs = pm.getPackagesForUid(uid);
        if (pkg == null || uid == Process.myUid() || pkgs == null || !Arrays.asList(pkgs).contains(pkg)) {
            out.putString("error", "unknown");
            return out;
        }
        String digest;
        try {
            digest = Callers.certDigest(pm, pkg);
        } catch (PackageManager.NameNotFoundException e) {
            out.putString("error", "unknown");
            return out;
        }
        switch (Callers.approvals(getContext()).decision(pkg, digest)) {
            case DENIED:
                out.putString("error", "denied");
                return out;
            case UNKNOWN:
                out.putString("error", "approval");
                out.putParcelable("approve", ApprovalActivity.ask(getContext(), pkg, digest));
                return out;
            default:
                break;
        }
        if (!DaemonService.running()) {
            out.putString("error", "stopped");
            return out;
        }
        try (LocalSocket s = new LocalSocket()) {
            s.connect(new LocalSocketAddress(DaemonService.socket(getContext()).getAbsolutePath(),
                    LocalSocketAddress.Namespace.FILESYSTEM));
            out.putParcelable("socket", ParcelFileDescriptor.dup(s.getFileDescriptor()));
        } catch (IOException e) {
            out.putString("error", "stopped");
        }
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}

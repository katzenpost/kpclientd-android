// SPDX-License-Identifier: AGPL-3.0-only

package org.hoi_polloi.kpdaemon;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Callers names the apps that ask for the daemon, and keeps the user's answers about them.
final class Callers {

    private static final String PREFS = "approvals";

    private Callers() {
    }

    // The SHA-256 of the certificate `pkg` is signed with, in hex: the current one of a rotated
    // signing lineage, or all of them, sorted and joined, for an app with several signers.
    static String certDigest(PackageManager pm, String pkg) throws PackageManager.NameNotFoundException {
        PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
        SigningInfo si = pi.signingInfo;
        if (si == null) {
            throw new PackageManager.NameNotFoundException(pkg + " is not signed");
        }
        List<String> digests = new ArrayList<>();
        if (si.hasMultipleSigners()) {
            for (Signature s : si.getApkContentsSigners()) {
                digests.add(sha256(s.toByteArray()));
            }
            Collections.sort(digests);
        } else {
            Signature[] history = si.getSigningCertificateHistory();
            digests.add(sha256(history[history.length - 1].toByteArray()));
        }
        return String.join(",", digests);
    }

    // The app's name as the user knows it, or its package name.
    static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    static Approvals approvals(Context c) {
        return new Approvals(prefs(c).getAll());
    }

    static void decide(Context c, String pkg, String digest, boolean allow) {
        prefs(c).edit().putString(pkg, Approvals.entry(digest, allow)).commit();
    }

    static void forget(Context c, String pkg) {
        prefs(c).edit().remove(pkg).commit();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String sha256(byte[] b) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
            StringBuilder s = new StringBuilder();
            for (byte x : d) {
                s.append(String.format("%02x", x));
            }
            return s.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-only

package org.hoi_polloi.kpdaemon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Approvals holds the user's answer for each app that asked to use the daemon. An app is its
// package name and the digest of the certificate it is signed with: the same name signed with
// another key is another app, and is asked about again.
final class Approvals {

    enum Decision { ALLOWED, DENIED, UNKNOWN }

    private static final String ALLOW = "allow:";
    private static final String DENY = "deny:";

    // package name -> "allow:<digest>" or "deny:<digest>"
    private final Map<String, String> entries;

    Approvals(Map<String, ?> stored) {
        entries = new TreeMap<>();
        for (Map.Entry<String, ?> e : stored.entrySet()) {
            if (e.getValue() instanceof String) {
                entries.put(e.getKey(), (String) e.getValue());
            }
        }
    }

    Decision decision(String pkg, String digest) {
        String v = entries.get(pkg);
        if (v == null) {
            return Decision.UNKNOWN;
        }
        if (v.equals(ALLOW + digest)) {
            return Decision.ALLOWED;
        }
        if (v.equals(DENY + digest)) {
            return Decision.DENIED;
        }
        return Decision.UNKNOWN;
    }

    // The entry to store for an answer about `pkg` signed with `digest`.
    static String entry(String digest, boolean allow) {
        return (allow ? ALLOW : DENY) + digest;
    }

    // The packages allowed to use the daemon, by name.
    List<String> allowed() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (e.getValue().startsWith(ALLOW)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    // The packages refused, by name.
    List<String> denied() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (e.getValue().startsWith(DENY)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    // The certificate digest an answer about `pkg` was for, or null.
    String digestOf(String pkg) {
        String v = entries.get(pkg);
        if (v == null) {
            return null;
        }
        return v.substring(v.indexOf(':') + 1);
    }
}

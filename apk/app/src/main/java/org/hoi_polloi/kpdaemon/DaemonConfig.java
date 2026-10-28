// SPDX-License-Identifier: AGPL-3.0-only

package org.hoi_polloi.kpdaemon;

// DaemonConfig makes kpclientd listen only where this app says: on a unix socket in the app's
// private directory, which no other app can reach. Client apps get to the daemon only through
// ConnectProvider, which asks the user first.
final class DaemonConfig {

    private DaemonConfig() {
    }

    // The configuration `toml` with its [Listen] tables replaced by one unix socket at `socket`.
    static String withListener(String toml, String socket) {
        StringBuilder out = new StringBuilder();
        boolean skipping = false;
        boolean inString = false;
        for (String line : toml.split("\n", -1)) {
            String t = line.trim();
            if (!inString && t.startsWith("[")) {
                skipping = isListenTable(t);
            }
            if (countTripleQuotes(line) % 2 == 1) {
                inString = !inString;
            }
            if (!skipping) {
                out.append(line).append('\n');
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.setLength(out.length() - 1);
        }
        out.append("\n\n[Listen]\n  [Listen.Unix]\n    Address = \"")
                .append(socket.replace("\\", "\\\\").replace("\"", "\\\""))
                .append("\"\n");
        return out.toString();
    }

    // Whether `header`, a trimmed table header line, opens [Listen] or one of its subtables.
    private static boolean isListenTable(String header) {
        if (header.startsWith("[[")) {
            return false;
        }
        int end = header.indexOf(']');
        if (end < 0) {
            return false;
        }
        String name = header.substring(1, end).trim();
        return name.equals("Listen") || name.startsWith("Listen.");
    }

    private static int countTripleQuotes(String line) {
        int n = 0;
        for (int i = line.indexOf("\"\"\""); i >= 0; i = line.indexOf("\"\"\"", i + 3)) {
            n++;
        }
        return n;
    }
}

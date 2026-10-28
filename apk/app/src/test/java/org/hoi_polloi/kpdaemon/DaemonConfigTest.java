package org.hoi_polloi.kpdaemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DaemonConfigTest {

    private static final String CONFIG = String.join("\n",
            "PKISignatureScheme = \"Ed25519 Sphincs+\"",
            "",
            "[Listen]",
            "  [Listen.TCP]",
            "    Address = \"127.0.0.1:64331\"",
            "  [Listen.Unix]",
            "    Address = \"@katzenpost\"",
            "",
            "[Logging]",
            "  Level = \"DEBUG\"",
            "",
            "[[PinnedGateways.Gateways]]",
            "    Name = \"haywood\"",
            "    IdentityKey = \"\"\"",
            "[Listen]",
            "\"\"\"",
            "");

    @Test
    public void onlyThePrivateSocketIsLeft() {
        String out = DaemonConfig.withListener(CONFIG, "/data/user/0/app/files/kpclientd.sock");
        assertFalse("the TCP listener stays", out.contains("64331"));
        assertFalse("the abstract socket stays", out.contains("@katzenpost"));
        assertTrue(out.endsWith("[Listen]\n  [Listen.Unix]\n    Address = \"/data/user/0/app/files/kpclientd.sock\"\n"));
        assertTrue("other tables go", out.contains("[Logging]\n  Level = \"DEBUG\""));
    }

    @Test
    public void multiLineStringsAreLeftAlone() {
        String out = DaemonConfig.withListener(CONFIG, "/s");
        assertTrue(out.contains("IdentityKey = \"\"\"\n[Listen]\n\"\"\""));
    }

    @Test
    public void aConfigWithoutAListenerGetsOne() {
        assertEquals("A = 1\n\n[Listen]\n  [Listen.Unix]\n    Address = \"/s\"\n",
                DaemonConfig.withListener("A = 1\n", "/s"));
    }
}

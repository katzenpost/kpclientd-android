package org.hoi_polloi.kpdaemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ApprovalsTest {

    @Test
    public void anAppIsItsNameAndItsKey() {
        Map<String, Object> stored = new HashMap<>();
        stored.put("network.katzenpost.hushphone", Approvals.entry("aa11", true));
        stored.put("org.example.spy", Approvals.entry("bb22", false));
        Approvals a = new Approvals(stored);
        assertEquals(Approvals.Decision.ALLOWED, a.decision("network.katzenpost.hushphone", "aa11"));
        assertEquals("another key is another app",
                Approvals.Decision.UNKNOWN, a.decision("network.katzenpost.hushphone", "ff00"));
        assertEquals(Approvals.Decision.DENIED, a.decision("org.example.spy", "bb22"));
        assertEquals(Approvals.Decision.UNKNOWN, a.decision("org.example.new", "cc33"));
        assertEquals(List.of("network.katzenpost.hushphone"), a.allowed());
        assertEquals(List.of("org.example.spy"), a.denied());
        assertEquals("aa11", a.digestOf("network.katzenpost.hushphone"));
        assertNull(a.digestOf("org.example.new"));
    }

    @Test
    public void entriesOfAnotherKindAreIgnored() {
        Map<String, Object> stored = new HashMap<>();
        stored.put("x", 3);
        assertEquals(Approvals.Decision.UNKNOWN, new Approvals(stored).decision("x", "3"));
    }
}

package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteMappingsTest {

    // Keycodes as Android numbers (android.view.KeyEvent is not available in JVM tests):
    // 4 BACK, 19-22 DPAD, 23 CENTER, 24/25 VOLUME, 82 MENU, 196 BUTTON_12, 200 BUTTON_15.
    private static RemoteMappings.Mapping app(int key, String pkg) {
        return new RemoteMappings.Mapping(key, RemoteMappings.ACTION_APP, pkg);
    }

    private static RemoteMappings.Mapping act(int key, String action) {
        return new RemoteMappings.Mapping(key, action, null);
    }

    @Test public void roundTrip() {
        List<RemoteMappings.Mapping> in = Arrays.asList(
                app(196, "org.smarttube.stable"), act(200, RemoteMappings.ACTION_INPUTS));
        List<RemoteMappings.Mapping> back = RemoteMappings.decode(RemoteMappings.encode(in));
        assertEquals(in.size(), back.size());
        assertTrue(back.containsAll(in));
    }

    @Test public void emptyRoundTrip() {
        assertEquals(0, RemoteMappings.decode(RemoteMappings.encode(Collections.emptyList())).size());
    }

    @Test public void decodeRejectsGarbage() {
        assertNull(RemoteMappings.decode("{}"));
        assertNull(RemoteMappings.decode(""));
        assertNull(RemoteMappings.decode(null));
        assertNull(RemoteMappings.decode("{\"version\":1,\"mappings\":[\n{\"key\":196,\"action\":\"nope\"}\n]}\n"));
        assertNull(RemoteMappings.decode("{\"version\":1,\"mappings\":[\n{\"key\":196,\"action\":\"app\",\"package\":\"nodots\"}\n]}\n"));
        assertNull(RemoteMappings.decode("{\"version\":2,\"mappings\":[\n]}\n"));
    }

    @Test public void decodeRejectsReservedKeysAndDuplicates() {
        // BACK (4) can never be mapped, even by a hand-edited file.
        assertNull(RemoteMappings.decode("{\"version\":1,\"mappings\":[\n{\"key\":4,\"action\":\"inputs\"}\n]}\n"));
        // One mapping per key.
        assertNull(RemoteMappings.decode("{\"version\":1,\"mappings\":[\n"
                + "{\"key\":196,\"action\":\"inputs\"},\n{\"key\":196,\"action\":\"clock\"}\n]}\n"));
    }

    @Test public void reservedKeys() {
        assertTrue(RemoteMappings.isReserved(4));    // BACK
        assertTrue(RemoteMappings.isReserved(19));   // DPAD_UP
        assertTrue(RemoteMappings.isReserved(23));   // DPAD_CENTER
        assertTrue(RemoteMappings.isReserved(24));   // VOLUME_UP
        assertTrue(RemoteMappings.isReserved(82));   // MENU
        assertTrue(RemoteMappings.isReserved(0));    // UNKNOWN
        assertFalse(RemoteMappings.isReserved(196)); // BUTTON_12
        assertFalse(RemoteMappings.isReserved(200)); // BUTTON_15
    }

    @Test public void withReplacesSameKey() {
        List<RemoteMappings.Mapping> one = RemoteMappings.with(Collections.emptyList(),
                act(196, RemoteMappings.ACTION_INPUTS));
        List<RemoteMappings.Mapping> two = RemoteMappings.with(one, act(196, RemoteMappings.ACTION_CLOCK));
        assertEquals(1, two.size());
        assertEquals(RemoteMappings.ACTION_CLOCK, RemoteMappings.find(two, 196).action);
    }

    @Test public void withRefusesInvalid() {
        List<RemoteMappings.Mapping> none = Collections.emptyList();
        assertEquals(none, RemoteMappings.with(none, act(4, RemoteMappings.ACTION_INPUTS)));
        assertEquals(none, RemoteMappings.with(none, act(196, "nope")));
        assertEquals(none, RemoteMappings.with(none, new RemoteMappings.Mapping(
                196, RemoteMappings.ACTION_APP, null)));
    }

    @Test public void withoutRemoves() {
        List<RemoteMappings.Mapping> one = RemoteMappings.with(Collections.emptyList(),
                act(196, RemoteMappings.ACTION_INPUTS));
        assertEquals(0, RemoteMappings.without(one, 196).size());
        assertEquals(1, RemoteMappings.without(one, 200).size());
        assertNull(RemoteMappings.find(one, 200));
    }
}

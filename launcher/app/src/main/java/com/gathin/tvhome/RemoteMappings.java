package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import android.view.KeyEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Remote button mappings the owner made on the Remote buttons page (RemoteRemapActivity): while
 * Home is in front, a mapped button runs a launcher action instead of doing nothing.
 *
 * Scope, honestly: this is in-app mapping only. The mappings are read and enforced by
 * MainActivity's key dispatch, so they work while CleanYourTV is the foreground app and nowhere
 * else. Buttons the system or the firmware keeps for itself (Home, Assistant, power, volume,
 * channel, the Settings button this launcher already answers, and the navigation keys Home is
 * navigated with) can never be mapped: {@link #isReserved} refuses them both when a mapping is
 * made and when one is read back, so a hand-edited file cannot take over navigation either.
 * Global remapping would need an accessibility service filtering every key on the TV; this
 * launcher deliberately does not do that for owner-made mappings (the opt-in InputKeyService
 * covers the fixed {@code app_keys} list from config instead). No new permission is involved.
 *
 * The file is {@code remote_mappings.json} in the app's private files, written atomically and
 * validated strictly on read, the same pattern as Cards' cards.json: a file that does not decode
 * is moved aside to {@code .bad} and the launcher continues with no mappings.
 */
final class RemoteMappings {

    private static final String TAG = "TvHomeRemap";
    private static final String STORE_FILE = "remote_mappings.json";
    static final int MAX_MAPPINGS = 32;

    static final String ACTION_QUICK_SETTINGS = "quick_settings";
    static final String ACTION_INPUTS = "inputs";
    static final String ACTION_LIBRARY = "library";
    static final String ACTION_CLOCK = "clock";
    static final String ACTION_HOME = "home";
    static final String ACTION_APP = "app";

    /** One mapping: a remote keycode, the action it runs, and the package for ACTION_APP (else null). */
    static final class Mapping {
        final int keyCode;
        final String action;
        final String pkg;

        Mapping(int keyCode, String action, String pkg) {
            this.keyCode = keyCode;
            this.action = action;
            this.pkg = pkg;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Mapping)) return false;
            Mapping m = (Mapping) o;
            return keyCode == m.keyCode && action.equals(m.action)
                    && (pkg == null ? m.pkg == null : pkg.equals(m.pkg));
        }

        @Override public int hashCode() {
            return keyCode * 31 + action.hashCode() + (pkg == null ? 0 : pkg.hashCode());
        }
    }

    private RemoteMappings() { }

    static boolean isAction(String action) {
        return ACTION_QUICK_SETTINGS.equals(action) || ACTION_INPUTS.equals(action)
                || ACTION_LIBRARY.equals(action) || ACTION_CLOCK.equals(action)
                || ACTION_HOME.equals(action) || ACTION_APP.equals(action);
    }

    /** A mapping is only ever stored when every field checks out; decode relies on the same test. */
    static boolean isValid(Mapping m) {
        if (m == null || !isAction(m.action) || isReserved(m.keyCode)) return false;
        return ACTION_APP.equals(m.action) ? Cards.isPackageName(m.pkg) : m.pkg == null;
    }

    /**
     * Keys a mapping may never take: the navigation cluster Home is driven with, and the buttons
     * Android or the firmware reserves for itself on most sets. A key event for one of these is
     * either never delivered to an app at all (Home, power) or must keep its system meaning
     * (volume, channel, Assistant); the rest would break the launcher's own navigation.
     */
    static boolean isReserved(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_UNKNOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_TV_INPUT:
            case KeyEvent.KEYCODE_TV:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
            case KeyEvent.KEYCODE_SEARCH:
                return true;
            default:
                return keyCode < 0;
        }
    }

    /** The mapping for a keycode, or null. */
    static Mapping find(List<Mapping> mappings, int keyCode) {
        for (Mapping m : mappings) if (m.keyCode == keyCode) return m;
        return null;
    }

    /**
     * The list with this mapping in it, sorted by keycode. One mapping per key: a key that already
     * has one is silently replaced, which the page says out loud before it saves (the conflict
     * rule; editing a mapping is the same call). Invalid mappings are refused.
     */
    static List<Mapping> with(List<Mapping> mappings, Mapping mapping) {
        if (!isValid(mapping)) return mappings;
        Map<Integer, Mapping> byKey = new TreeMap<>();
        for (Mapping m : mappings) if (isValid(m)) byKey.put(m.keyCode, m);
        byKey.put(mapping.keyCode, mapping);
        List<Mapping> out = new ArrayList<>(byKey.values());
        if (out.size() > MAX_MAPPINGS) out = new ArrayList<>(out.subList(0, MAX_MAPPINGS));
        return Collections.unmodifiableList(out);
    }

    /** The list without the mapping for a keycode (unchanged when there is none). */
    static List<Mapping> without(List<Mapping> mappings, int keyCode) {
        List<Mapping> out = new ArrayList<>();
        for (Mapping m : mappings) if (m.keyCode != keyCode) out.add(m);
        return Collections.unmodifiableList(out);
    }

    // ---- store file ------------------------------------------------------------------------------

    static File storeFile(Context ctx) {
        return new File(ctx.getFilesDir(), STORE_FILE);
    }

    /** The mappings in remote_mappings.json, or none. An invalid file is moved aside, as in Cards. */
    static List<Mapping> load(File file) {
        if (!file.exists()) return Collections.emptyList();
        String text;
        try {
            text = new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "cannot read " + file.getName() + ": " + e);
            return Collections.emptyList();
        }
        List<Mapping> mappings = decode(text);
        if (mappings == null) {
            Log.w(TAG, file.getName() + " is not valid; moved to " + STORE_FILE + ".bad");
            Cards.quarantine(file);
            return Collections.emptyList();
        }
        return mappings;
    }

    /** Writes the mappings atomically. False (and a log line) when the write failed. */
    static boolean save(File file, List<Mapping> mappings) {
        AtomicFile af = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = af.startWrite();
            out.write(encode(mappings).getBytes(StandardCharsets.UTF_8));
            af.finishWrite(out);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "cannot write " + file.getName() + ": " + e);
            if (out != null) af.failWrite(out);
            return false;
        }
    }

    private static final String HEADER = "{\"version\":1,\"mappings\":[";
    private static final String FOOTER = "]}";
    private static final String F_KEY = "{\"key\":";
    private static final String F_ACTION = ",\"action\":\"";
    private static final String F_PACKAGE = "\",\"package\":\"";

    /**
     * remote_mappings.json: one mapping per line, in the exact layout {@link #encode} writes.
     * Anything else, a reserved key, an unknown action, a bad package or more than
     * {@link #MAX_MAPPINGS} mappings is invalid (null), as in Cards.decode.
     */
    static List<Mapping> decode(String text) {
        if (text == null || text.length() > 16 * 1024) return null;
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (lines.size() < 2 || !lines.get(0).equals(HEADER)) return null;
        int last = lines.size() - 1;
        if (lines.get(last).isEmpty()) last -= 1;           // a trailing newline is fine
        if (last < 1 || !lines.get(last).equals(FOOTER)) return null;
        int count = last - 1;
        if (count > MAX_MAPPINGS) return null;
        Map<Integer, Mapping> byKey = new TreeMap<>();
        for (int i = 1; i < last; i++) {
            String line = lines.get(i);
            boolean lastRecord = i == last - 1;
            if (!lastRecord) {
                if (!line.endsWith(",")) return null;
                line = line.substring(0, line.length() - 1);
            }
            Mapping m = decodeMapping(line);
            if (m == null || byKey.containsKey(m.keyCode)) return null;
            byKey.put(m.keyCode, m);
        }
        return Collections.unmodifiableList(new ArrayList<>(byKey.values()));
    }

    /** One mapping line, or null when it is not exactly what {@link #encode} writes. */
    private static Mapping decodeMapping(String line) {
        int[] at = {0};
        if (!take(line, at, F_KEY)) return null;
        String key = until(line, at, ',');
        if (key == null || key.length() > 6 || !isDigits(key)) return null;
        int keyCode;
        try {
            keyCode = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!take(line, at, F_ACTION)) return null;
        String action = until(line, at, '"');
        if (action == null || !isAction(action)) return null;
        String pkg = null;
        if (ACTION_APP.equals(action)) {
            if (!take(line, at, F_PACKAGE)) return null;
            pkg = until(line, at, '"');
            if (pkg == null || !Cards.isPackageName(pkg)) return null;
        }
        if (!take(line, at, "\"}") || at[0] != line.length()) return null;
        Mapping m = new Mapping(keyCode, action, pkg);
        return isValid(m) ? m : null;
    }

    /** The store text for these mappings, by keycode. Every field is validated first, so no escaping is needed. */
    static String encode(List<Mapping> mappings) {
        List<Mapping> list = new ArrayList<>(mappings);
        list.sort((a, b) -> Integer.compare(a.keyCode, b.keyCode));
        if (list.size() > MAX_MAPPINGS) list = new ArrayList<>(list.subList(0, MAX_MAPPINGS));
        StringBuilder b = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < list.size(); i++) {
            Mapping m = list.get(i);
            if (!isValid(m)) throw new IllegalArgumentException("mapping for key " + m.keyCode + " is not valid");
            b.append(F_KEY).append(m.keyCode).append(F_ACTION).append(m.action);
            if (ACTION_APP.equals(m.action)) b.append(F_PACKAGE).append(m.pkg);
            b.append("\"}");
            if (i < list.size() - 1) b.append(',');
            b.append('\n');
        }
        return b.append(FOOTER).append('\n').toString();
    }

    private static boolean take(String s, int[] at, String literal) {
        if (!s.startsWith(literal, at[0])) return false;
        at[0] += literal.length();
        return true;
    }

    private static String until(String s, int[] at, char stop) {
        int end = s.indexOf(stop, at[0]);
        if (end < 0) return null;
        String out = s.substring(at[0], end);
        at[0] = end;
        return out;
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }
}

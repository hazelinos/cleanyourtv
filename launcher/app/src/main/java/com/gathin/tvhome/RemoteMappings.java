package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
 * validated strictly on read: the atomic write and the move-aside follow Cards' cards.json, the
 * parsing is org.json as in Config. A file that does not decode is moved aside to {@code .bad}
 * and the launcher continues with no mappings.
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
        return out;
    }

    /** The list without the mapping for a keycode (unchanged when there is none). */
    static List<Mapping> without(List<Mapping> mappings, int keyCode) {
        List<Mapping> out = new ArrayList<>();
        for (Mapping m : mappings) if (m.keyCode != keyCode) out.add(m);
        return out;
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

    /**
     * remote_mappings.json, parsed with org.json the way Config reads a theme overlay. Every value
     * is still checked by {@link #isValid} before it is used: a wrong version, a reserved key, an
     * unknown action, a bad package, a duplicate key or more than {@link #MAX_MAPPINGS} mappings
     * makes the whole file invalid (null), and {@link #load} moves it aside, as in Cards.
     */
    static List<Mapping> decode(String text) {
        if (text == null || text.length() > 16 * 1024) return null;
        try {
            JSONObject root = new JSONObject(text);
            if (root.getInt("version") != 1) return null;
            JSONArray list = root.getJSONArray("mappings");
            if (list.length() > MAX_MAPPINGS) return null;
            Map<Integer, Mapping> byKey = new TreeMap<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                String action = o.getString("action");
                Mapping m = new Mapping(o.getInt("key"), action,
                        ACTION_APP.equals(action) ? o.getString("package") : null);
                if (!isValid(m) || byKey.put(m.keyCode, m) != null) return null;
            }
            return new ArrayList<>(byKey.values());
        } catch (JSONException | RuntimeException e) {
            // RuntimeException: org.json is a stub under the JVM unit tests.
            return null;
        }
    }

    /** The store text for these mappings, by keycode. Every mapping is validated first. */
    static String encode(List<Mapping> mappings) {
        List<Mapping> list = new ArrayList<>(mappings);
        list.sort((a, b) -> Integer.compare(a.keyCode, b.keyCode));
        if (list.size() > MAX_MAPPINGS) list = new ArrayList<>(list.subList(0, MAX_MAPPINGS));
        for (Mapping m : list) {
            if (!isValid(m)) throw new IllegalArgumentException(
                    "mapping for key " + m.keyCode + " is not valid");
        }
        try {
            JSONArray array = new JSONArray();
            for (Mapping m : list) {
                JSONObject o = new JSONObject();
                o.put("key", m.keyCode);
                o.put("action", m.action);
                if (ACTION_APP.equals(m.action)) o.put("package", m.pkg);
                array.put(o);
            }
            return new JSONObject().put("version", 1).put("mappings", array).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}

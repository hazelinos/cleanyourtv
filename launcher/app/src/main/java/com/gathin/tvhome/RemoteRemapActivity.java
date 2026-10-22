package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Remote buttons page: add, change and remove the owner's in-app button mappings
 * ({@link RemoteMappings}). A mapping is made by pressing the button, then picking what it does
 * from the launcher's real actions; one mapping per button, and picking a new action for a mapped
 * button replaces the old one (the page says so when it happens). Mappings apply only while Home
 * is in front — the note at the top of the page says which buttons can never be mapped and that
 * nothing is remapped system-wide.
 */
public final class RemoteRemapActivity extends Activity {

    private LinearLayout rows;
    private TextView addChoice;
    private TextView resetChoice;
    private List<RemoteMappings.Mapping> mappings = new ArrayList<>();
    /** True between "Add mapping" and the button press (or Back) that ends it. */
    private boolean capturing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_remap);
        rows = findViewById(R.id.rows);
        addChoice = findViewById(R.id.addChoice);
        resetChoice = findViewById(R.id.resetChoice);
        addChoice.setOnClickListener(v -> {
            capturing = true;
            addChoice.setText(R.string.remap_press_button);
        });
        resetChoice.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.remap_reset_confirm)
                .setPositiveButton(R.string.remap_reset, (dialog, which) -> {
                    mappings = new ArrayList<>();
                    persist();
                    buildRows();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        mappings = new ArrayList<>(RemoteMappings.load(RemoteMappings.storeFile(this)));
        buildRows();
        addChoice.requestFocus();
    }

    /** While capturing, the next button press is the mapping's key; Back cancels instead. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!capturing) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            stopCapture();
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
        stopCapture();
        if (RemoteMappings.isReserved(code)) {
            Toast.makeText(this, R.string.remap_button_reserved, Toast.LENGTH_LONG).show();
            return true;
        }
        chooseAction(code, -1);
        return true;
    }

    private void stopCapture() {
        capturing = false;
        addChoice.setText(R.string.remap_add);
    }

    /** The action list for a key; {@code replaceKey} >= 0 edits that key's mapping (and offers Delete). */
    private void chooseAction(final int keyCode, final int replaceKey) {
        final String[] actions = {RemoteMappings.ACTION_QUICK_SETTINGS, RemoteMappings.ACTION_INPUTS,
                RemoteMappings.ACTION_LIBRARY, RemoteMappings.ACTION_CLOCK, RemoteMappings.ACTION_HOME,
                RemoteMappings.ACTION_APP};
        String[] labels = {getString(R.string.remap_action_quick_settings), getString(R.string.remap_action_inputs),
                getString(R.string.remap_action_library), getString(R.string.remap_action_clock),
                getString(R.string.remap_action_home), getString(R.string.remap_action_open_app)};
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.remap_choose_action, keyName(keyCode)))
                .setItems(labels, (d, which) -> {
                    if (RemoteMappings.ACTION_APP.equals(actions[which])) chooseApp(keyCode, replaceKey);
                    else save(new RemoteMappings.Mapping(keyCode, actions[which], null), replaceKey);
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (replaceKey >= 0) {
            dialog.setNeutralButton(R.string.remap_delete, (d, which) -> {
                mappings = new ArrayList<>(RemoteMappings.without(mappings, replaceKey));
                persist();
                buildRows();
            });
        }
        dialog.show();
    }

    /** The installed TV apps an "open app" mapping can point at, by their shown name. */
    private void chooseApp(final int keyCode, final int replaceKey) {
        PackageManager pm = getPackageManager();
        // Shown name -> package, sorted by the name; two apps sharing a shown name keep the first.
        Map<String, String> byLabel = new TreeMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            byLabel.putIfAbsent(AppPresentation.displayLabel(pkg, String.valueOf(ri.loadLabel(pm))), pkg);
        }
        String[] labels = byLabel.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.remap_action_open_app)
                .setItems(labels, (dialog, which) ->
                        save(new RemoteMappings.Mapping(keyCode, RemoteMappings.ACTION_APP, byLabel.get(labels[which])), replaceKey))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void save(RemoteMappings.Mapping mapping, int replaceKey) {
        boolean conflict = RemoteMappings.find(mappings, mapping.keyCode) != null
                && mapping.keyCode != replaceKey;
        mappings = new ArrayList<>(RemoteMappings.with(mappings, mapping));
        persist();
        buildRows();
        if (conflict) {
            Toast.makeText(this, getString(R.string.remap_conflict, keyName(mapping.keyCode)),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void persist() {
        if (!RemoteMappings.save(RemoteMappings.storeFile(this), mappings)) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void buildRows() {
        rows.removeAllViews();
        for (final RemoteMappings.Mapping m : mappings) {
            // The Launcher settings row style: glass background, focus and text colours included.
            TextView row = new TextView(this, null, 0, R.style.SettingsRow);
            row.setText(getString(R.string.remap_key_named, keyName(m.keyCode), m.keyCode)
                    + "  →  " + actionLabel(m));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            lp.bottomMargin = dp(10);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> chooseAction(m.keyCode, m.keyCode));
            rows.addView(row);
        }
        resetChoice.setVisibility(mappings.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String actionLabel(RemoteMappings.Mapping m) {
        switch (m.action) {
            case RemoteMappings.ACTION_QUICK_SETTINGS: return getString(R.string.remap_action_quick_settings);
            case RemoteMappings.ACTION_INPUTS: return getString(R.string.remap_action_inputs);
            case RemoteMappings.ACTION_LIBRARY: return getString(R.string.remap_action_library);
            case RemoteMappings.ACTION_CLOCK: return getString(R.string.remap_action_clock);
            case RemoteMappings.ACTION_HOME: return getString(R.string.remap_action_home);
            case RemoteMappings.ACTION_APP:
                try {
                    return AppPresentation.displayLabel(m.pkg, String.valueOf(getPackageManager()
                            .getApplicationLabel(getPackageManager().getApplicationInfo(m.pkg, 0))));
                } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                    return m.pkg;
                }
            default: return m.action;
        }
    }

    /** "BUTTON_12" for KEYCODE_BUTTON_12; the number itself for a key Android has no name for. */
    static String keyName(int keyCode) {
        String name = KeyEvent.keyCodeToString(keyCode);
        return name.startsWith("KEYCODE_") ? name.substring("KEYCODE_".length()) : name;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

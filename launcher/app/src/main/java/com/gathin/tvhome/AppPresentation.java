package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * How a few well-known third-party apps are presented on this launcher's own screens.
 *
 * THE OVERRIDE TABLE (the one place this mapping lives):
 *
 *   package                        shown as   tile banner      library badge
 *   ------------------------------ ---------- ---------------- ----------------
 *   com.liskovsoft.smarttubetv     "YouTube"  banner_youtube   ic_youtube
 *   org.smarttube.stable           "YouTube"  banner_youtube   ic_youtube
 *   org.smarttube.beta             "YouTube"  banner_youtube   ic_youtube
 *   com.movieboxpro.android        "Netflix"  banner_netflix   ic_netflix_n (on the row's dark ground)
 *   com.community.oneroom          "Netflix"  banner_netflix   ic_netflix_n (on the row's dark ground)
 *
 * SmartTube is a third-party YouTube client and MovieBox a third-party streaming app; on a TV whose
 * owner installed them as their YouTube / Netflix replacements, the stock names and icons read as
 * clutter, so the tiles carry the service's name and a small vector badge drawn by the launcher.
 *
 * This is a display override inside this launcher only. It never renames the app anywhere else,
 * never touches its package, icon or data on the system, and does not claim the app is the official
 * client: the tile still opens the app that is installed, and App info still shows its real name.
 *
 * Tiles, the dock and the hero name take the label and banner from the existing config mechanism:
 * the same rows are the built-in defaults of {@code app_labels} / {@code app_banners} in
 * res/values/config.xml, so a theme can still override or extend them there. The app library
 * resolves labels and icons itself, so it reads this table instead; keep the two in step when a
 * row is added (one line in each). Package names are the apps' long-standing public ones; a build
 * under a different package simply keeps its own name until its row is added here.
 */
final class AppPresentation {

    /** What one overridden package is shown as: its tile label and its app-library badge. */
    static final class Brand {
        final String label;
        final int badgeRes;

        Brand(String label, int badgeRes) {
            this.label = label;
            this.badgeRes = badgeRes;
        }
    }

    private static final Map<String, Brand> BRANDS = new HashMap<>();

    static {
        Brand youtube = new Brand("YouTube", R.drawable.ic_youtube);
        BRANDS.put("com.liskovsoft.smarttubetv", youtube);
        BRANDS.put("org.smarttube.stable", youtube);
        BRANDS.put("org.smarttube.beta", youtube);
        Brand netflix = new Brand("Netflix", R.drawable.ic_netflix_n);
        BRANDS.put("com.movieboxpro.android", netflix);
        BRANDS.put("com.community.oneroom", netflix);
    }

    private AppPresentation() { }

    /** The brand a package is shown as, or null when it keeps its own name and art. */
    static Brand brand(String pkg) {
        return pkg == null ? null : BRANDS.get(pkg);
    }

    /** The name a tile or library row shows: the brand's label for a table package, else the app's own. */
    static String displayLabel(String pkg, String ownLabel) {
        Brand brand = brand(pkg);
        return brand != null ? brand.label : ownLabel;
    }

    /** The badge for the app library's icon slot, or null to show the app's own icon. */
    static Drawable badge(Context ctx, String pkg) {
        Brand brand = brand(pkg);
        return brand == null ? null : ctx.getDrawable(brand.badgeRes);
    }
}

package com.gathin.tvhome;

/**
 * How a few well-known third-party apps are presented on this launcher's own screens.
 *
 * THE OVERRIDE TABLE (the one place this mapping lives; the rows below are in the same order):
 *
 *   package                        shown as   tile banner / library icon
 *   ------------------------------ ---------- --------------------------
 *   com.liskovsoft.smarttubetv     "YouTube"  banner_youtube
 *   org.smarttube.stable           "YouTube"  banner_youtube
 *   org.smarttube.beta             "YouTube"  banner_youtube
 *   com.movieboxpro.android        "Netflix"  banner_netflix
 *   com.community.oneroom          "Netflix"  banner_netflix
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

    private static final String[] PACKAGES = {
            "com.liskovsoft.smarttubetv", "org.smarttube.stable", "org.smarttube.beta",
            "com.movieboxpro.android", "com.community.oneroom",
    };
    private static final String[] LABELS = {"YouTube", "YouTube", "YouTube", "Netflix", "Netflix"};
    private static final int[] BADGES = {
            R.drawable.banner_youtube, R.drawable.banner_youtube, R.drawable.banner_youtube,
            R.drawable.banner_netflix, R.drawable.banner_netflix,
    };

    private AppPresentation() { }

    private static int indexOf(String pkg) {
        if (pkg != null) {
            for (int i = 0; i < PACKAGES.length; i++) {
                if (PACKAGES[i].equals(pkg)) return i;
            }
        }
        return -1;
    }

    /** The library icon for a package (its banner art), or 0 when it keeps its own icon. */
    static int badgeRes(String pkg) {
        int i = indexOf(pkg);
        return i >= 0 ? BADGES[i] : 0;
    }

    /** The name a tile or library row shows: the brand's label for a table package, else the app's own. */
    static String displayLabel(String pkg, String ownLabel) {
        int i = indexOf(pkg);
        return i >= 0 ? LABELS[i] : ownLabel;
    }
}

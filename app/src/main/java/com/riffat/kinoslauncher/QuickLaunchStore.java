package com.riffat.kinoslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists the user's quick-launch favorites (up to {@link #MAX_FAVORITES}).
 *
 * <p>Components are stored as an ordered, comma-separated list of
 * {@link ComponentName#flattenToShortString()} values inside the shared
 * {@code kinos_home} prefs file, so selection order is preserved.</p>
 */
public final class QuickLaunchStore {

    /** Maximum number of quick-launch favorites. */
    public static final int MAX_FAVORITES = 10;

    static final String PREFS = "kinos_home";
    static final String KEY_QUICK_LAUNCH = "quick_launch";

    private QuickLaunchStore() {
    }

    /** Returns the saved favorites in selection order (may be empty, never null). */
    public static List<ComponentName> getFavorites(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final String raw = prefs.getString(KEY_QUICK_LAUNCH, "");
        final List<ComponentName> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        final Set<ComponentName> seen = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            final String flat = part.trim();
            if (flat.isEmpty()) continue;
            try {
                final ComponentName cn = ComponentName.unflattenFromString(flat);
                if (cn != null && seen.add(cn)) {
                    out.add(cn);
                }
            } catch (RuntimeException ignored) {
                // Skip malformed entries.
            }
            if (out.size() >= MAX_FAVORITES) break;
        }
        return out;
    }

    /** Persists the favorites (truncated to {@link #MAX_FAVORITES}, order preserved). */
    public static void setFavorites(Context context, List<ComponentName> favorites) {
        final List<String> flat = new ArrayList<>();
        final Set<ComponentName> seen = new LinkedHashSet<>();
        if (favorites != null) {
            for (ComponentName cn : favorites) {
                if (cn == null || !seen.add(cn)) continue;
                flat.add(cn.flattenToShortString());
                if (flat.size() >= MAX_FAVORITES) break;
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUICK_LAUNCH, TextUtils.join(",", flat))
                .apply();
    }

    /**
     * Resolves saved favorites against the loaded model, preserving order and
     * dropping entries whose apps are no longer installed.
     */
    public static List<AppItem> resolveFavorites(AppListModel model, List<ComponentName> favorites) {
        final List<AppItem> out = new ArrayList<>();
        if (model == null || favorites == null) return out;
        for (ComponentName cn : favorites) {
            final AppItem found = find(model.apps, cn);
            if (found != null) out.add(found);
        }
        return out;
    }

    private static AppItem find(List<AppItem> apps, ComponentName cn) {
        if (apps == null || cn == null) return null;
        for (AppItem app : apps) {
            if (cn.getPackageName().equals(app.packageName)
                    && cn.getClassName().equals(app.activityName)) {
                return app;
            }
        }
        return null;
    }
}

package com.riffat.kinoslauncher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/**
 * Immutable data holder for a single launchable app.
 */
public final class AppItem {

    public final String label;
    /** Clean, lower-cased label cached for fast comparisons while searching. */
    public final String lowerLabel;
    public final String packageName;
    public final String activityName;
    public final Drawable icon;

    public AppItem(String label, String packageName, String activityName, Drawable icon) {
        this.label = label == null ? "" : label;
        this.lowerLabel = this.label.toLowerCase();
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
    }

    public ComponentName component() {
        return new ComponentName(packageName, activityName);
    }

    @Override
    public String toString() {
        return label;
    }
}

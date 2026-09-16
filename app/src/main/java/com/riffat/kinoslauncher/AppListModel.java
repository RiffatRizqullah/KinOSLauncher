package com.riffat.kinoslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches all launchable apps, sorted A-Z using locale-aware collation.
 */
public final class AppListModel {

    /** All launchable apps, sorted A-Z. */
    public final List<AppItem> apps = new ArrayList<>();

    /** First app index for every letter bucket used by the dial (A-Z; non-letters fall under '#'). */
    public final Map<Character, Integer> letterStartIndex = new HashMap<>();

    /** Last app index (+1) for every letter bucket. */
    public final Map<Character, Integer> letterEndIndex = new HashMap<>();

    private static final List<Character> LETTERS = new ArrayList<>();

    static {
        for (char c = 'A'; c <= 'Z'; c++) {
            LETTERS.add(c);
        }
        LETTERS.add('#');
    }

    /** Loads apps sorted A-Z and builds the letter buckets. Safe to call on a background thread. */
    public synchronized void load(Context context) {
        final PackageManager pm = context.getPackageManager();

        final Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> infos;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            infos = pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0));
        } else {
            infos = pm.queryIntentActivities(main, 0);
        }
        if (infos == null || infos.isEmpty()) {
            return;
        }

        final List<AppItem> found = new ArrayList<>();
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null) continue;
            final String pkg = info.activityInfo.packageName;
            if (pkg == null || context.getPackageName().equals(pkg)) continue;

            final CharSequence labelSeq = info.loadLabel(pm);
            final String label = labelSeq == null ? pkg : labelSeq.toString();
            final Drawable icon = resolveIcon(pm, info);
            if (icon == null) continue;

            found.add(new AppItem(label, pkg, info.activityInfo.name, icon));
        }

        final Collator collator = Collator.getInstance();
        Collections.sort(found, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                return collator.compare(a.label, b.label);
            }
        });

        apps.clear();
        apps.addAll(found);
        bucketize();
    }

    /** Groups sorted apps into letter buckets for the dial. */
    private void bucketize() {
        letterStartIndex.clear();
        letterEndIndex.clear();
        for (int i = 0; i < apps.size(); i++) {
            final String label = apps.get(i).label;
            final char raw = Character.toUpperCase(label.isEmpty() ? '#' : label.charAt(0));
            final Character key = (raw >= 'A' && raw <= 'Z') ? Character.valueOf(raw) : Character.valueOf('#');
            if (!letterStartIndex.containsKey(key)) {
                letterStartIndex.put(key, Integer.valueOf(i));
            }
            letterEndIndex.put(key, Integer.valueOf(i + 1));
        }
    }

    /** Returns a snapshot of the apps belonging to one letter bucket. */
    public synchronized List<AppItem> appsForLetter(Character letter) {
        final Integer start = letterStartIndex.get(letter);
        final Integer end = letterEndIndex.get(letter);
        if (start == null || end == null) return Collections.emptyList();
        return new ArrayList<>(apps.subList(start, end));
    }

    /**
     * Round-robins the first app of every letter bucket into a ring layout order,
     * so the dial spreads the alphabet evenly around the circle.
     */
    public synchronized List<Character> spreadLetters() {
        final List<Character> present = new ArrayList<>(LETTERS);
        final List<Character> ordered = new ArrayList<>();
        int added;
        int round = 0;
        do {
            added = 0;
            for (Character letter : present) {
                final List<AppItem> bucket = appsForLetter(letter);
                if (round < bucket.size()) {
                    ordered.add(letter);
                    added++;
                }
            }
            round++;
        } while (added > 0);
        return ordered;
    }

    private static Drawable resolveIcon(PackageManager pm, ResolveInfo info) {
        Drawable icon = null;
        try {
            icon = info.loadIcon(pm);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            // fall through to default icon
        }
        if (icon == null) {
            icon = pm.getDefaultActivityIcon();
        }
        if (icon instanceof BitmapDrawable) {
            return icon;
        }
        // Flatten adaptive/vector icons into a bitmap so every cell renders uniformly.
        try {
            final int size = icon.getIntrinsicWidth();
            final int w = size > 0 ? size : 48;
            final int h = icon.getIntrinsicHeight() > 0 ? icon.getIntrinsicHeight() : w;
            final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bmp);
            icon.setBounds(0, 0, w, h);
            icon.draw(canvas);
            return new BitmapDrawable(pm.getResourcesForApplication(info.activityInfo.applicationInfo), bmp);
        } catch (PackageManager.NameNotFoundException | RuntimeException | OutOfMemoryError e) {
            return icon;
        }
    }
}

package com.riffat.kinoslauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fullscreen app-drawer adapter: apps sorted A-Z with alphabet separator
 * headers, plus a settings entry as the very last row.
 */
public final class AppDrawerAdapter extends BaseAdapter {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_APP = 1;
    private static final int TYPE_SETTINGS = 2;

    private final LayoutInflater inflater;
    private final List<Object> rows = new ArrayList<>();

    public AppDrawerAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    /** Rebuilds rows from the model's sorted apps (call on the UI thread). */
    public void setApps(List<AppItem> apps) {
        rows.clear();
        if (apps != null) {
            Character lastLetter = null;
            for (AppItem app : apps) {
                final char letter = leadingLetter(app);
                if (lastLetter == null || letter != lastLetter) {
                    lastLetter = letter;
                    rows.add(Character.valueOf(letter));
                }
                rows.add(app);
            }
        }
        // Settings entry is always the last row.
        rows.add(new SettingsEntry());
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        final Object row = rows.get(position);
        if (row instanceof Character) return TYPE_HEADER;
        if (row instanceof SettingsEntry) return TYPE_SETTINGS;
        return TYPE_APP;
    }

    @Override
    public boolean isEnabled(int position) {
        // Headers are not clickable; apps and the settings row are.
        return getItemViewType(position) != TYPE_HEADER;
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    /** Returns the app at this position, or null for headers / settings row. */
    public AppItem getAppItem(int position) {
        final Object row = rows.get(position);
        return row instanceof AppItem ? (AppItem) row : null;
    }

    /** True when this position is the settings footer row. */
    public boolean isSettingsRow(int position) {
        return rows.get(position) instanceof SettingsEntry;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int type = getItemViewType(position);
        if (type == TYPE_HEADER) {
            final View row = convertView != null
                    ? convertView
                    : inflater.inflate(R.layout.item_app_header, parent, false);
            final TextView letter = row.findViewById(R.id.header_letter);
            letter.setText(String.valueOf(rows.get(position)));
            return row;
        }
        if (type == TYPE_SETTINGS) {
            final View row = convertView != null
                    ? convertView
                    : inflater.inflate(R.layout.item_app_row, parent, false);
            final ImageView icon = row.findViewById(R.id.app_icon);
            final TextView label = row.findViewById(R.id.app_label);
            icon.setImageResource(android.R.drawable.ic_menu_preferences);
            icon.setImageAlpha(0x99);
            label.setText(R.string.drawer_settings_entry);
            return row;
        }
        final View row = convertView != null
                ? convertView
                : inflater.inflate(R.layout.item_app_row, parent, false);
        final AppItem app = (AppItem) rows.get(position);
        final ImageView icon = row.findViewById(R.id.app_icon);
        final TextView label = row.findViewById(R.id.app_label);
        icon.setImageDrawable(app.icon);
        icon.setImageAlpha(0xFF);
        label.setText(app.label);
        return row;
    }

    private static char leadingLetter(AppItem app) {
        if (app == null || app.label.isEmpty()) return '#';
        final char raw = Character.toUpperCase(app.label.charAt(0));
        return (raw >= 'A' && raw <= 'Z') ? raw : '#';
    }

    /** Marker for the settings footer row. */
    private static final class SettingsEntry {
    }
}

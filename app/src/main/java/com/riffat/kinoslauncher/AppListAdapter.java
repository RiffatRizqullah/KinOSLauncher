package com.riffat.kinoslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Simple two-line list row (icon + label) used inside the dial's center slot.
 */
public final class AppListAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private List<AppItem> items;

    public AppListAdapter(Context context, List<AppItem> items) {
        this.inflater = LayoutInflater.from(context);
        this.items = items;
    }

    public void setItems(List<AppItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items == null ? 0 : items.size();
    }

    @Override
    public AppItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View row = convertView != null
                ? convertView
                : inflater.inflate(R.layout.item_app_row, parent, false);

        final AppItem app = getItem(position);

        final ImageView icon = row.findViewById(R.id.app_icon);
        final TextView label = row.findViewById(R.id.app_label);
        icon.setImageDrawable(app.icon);
        label.setText(app.label);
        return row;
    }

    /** Returns the component for an app, or null when it cannot be resolved. */
    public static ComponentName componentOf(AppItem app) {
        return app == null ? null : app.component();
    }

    /** Convenience for icons when needed outside the adapter. */
    public static Drawable iconOf(AppItem app) {
        return app == null ? null : app.icon;
    }
}

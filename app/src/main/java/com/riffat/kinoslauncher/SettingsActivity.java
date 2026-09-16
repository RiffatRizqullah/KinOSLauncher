package com.riffat.kinoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * KinOS Settings menu. Each entry opens a dedicated settings screen; add a new
 * {@link MenuEntry} to {@link #buildMenu()} to add another settings section.
 * Reached from the bottom entry of the fullscreen app drawer.
 */
public class SettingsActivity extends Activity {

    private final List<MenuEntry> menu = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        buildMenu();

        final ListView list = findViewById(R.id.settings_menu_list);
        list.setAdapter(new MenuAdapter());
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final MenuEntry entry = menu.get(position);
                startActivity(new Intent(SettingsActivity.this, entry.target));
            }
        });
    }

    /** Add new settings sections here. */
    private void buildMenu() {
        menu.add(new MenuEntry(
                R.string.settings_quick_launch,
                R.string.settings_quick_launch_sub,
                android.R.drawable.btn_star_big_on,
                QuickLaunchSettingsActivity.class));
        menu.add(new MenuEntry(
                R.string.settings_about,
                R.string.settings_about_sub,
                android.R.drawable.ic_menu_info_details,
                InfoActivity.class));
    }

    /** One row in the settings menu. */
    private static final class MenuEntry {
        final int titleRes;
        final int subtitleRes;
        final int iconRes;
        final Class<?> target;

        MenuEntry(int titleRes, int subtitleRes, int iconRes, Class<?> target) {
            this.titleRes = titleRes;
            this.subtitleRes = subtitleRes;
            this.iconRes = iconRes;
            this.target = target;
        }
    }

    private final class MenuAdapter extends BaseAdapter {

        private final LayoutInflater inflater = LayoutInflater.from(SettingsActivity.this);

        @Override
        public int getCount() {
            return menu.size();
        }

        @Override
        public MenuEntry getItem(int position) {
            return menu.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View row = convertView != null
                    ? convertView
                    : inflater.inflate(R.layout.item_settings_menu, parent, false);
            final MenuEntry entry = getItem(position);
            final ImageView icon = row.findViewById(R.id.menu_icon);
            final TextView title = row.findViewById(R.id.menu_title);
            final TextView subtitle = row.findViewById(R.id.menu_subtitle);
            icon.setImageResource(entry.iconRes);
            title.setText(entry.titleRes);
            subtitle.setText(entry.subtitleRes);
            return row;
        }
    }
}

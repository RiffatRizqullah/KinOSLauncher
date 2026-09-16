package com.riffat.kinoslauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Quick Launch App" settings screen: lets the user pick up to
 * {@link QuickLaunchStore#MAX_FAVORITES} quick-launch favorites shown around
 * the dial. Reached from the KinOS Settings menu.
 */
public class QuickLaunchSettingsActivity extends Activity {

    private final AppListModel model = new AppListModel();
    private final List<AppItem> apps = new ArrayList<>();
    private final Set<ComponentName> checked = new LinkedHashSet<>();

    private SettingsAdapter adapter;
    private TextView counter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_launch_settings);

        counter = findViewById(R.id.settings_counter);
        final ListView list = findViewById(R.id.settings_list);
        final Button save = findViewById(R.id.settings_save);

        checked.addAll(QuickLaunchStore.getFavorites(this));

        adapter = new SettingsAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final AppItem app = apps.get(position);
                final ComponentName cn = app.component();
                if (checked.contains(cn)) {
                    checked.remove(cn);
                } else {
                    if (checked.size() >= QuickLaunchStore.MAX_FAVORITES) {
                        Toast.makeText(QuickLaunchSettingsActivity.this,
                                R.string.settings_max_reached, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    checked.add(cn);
                }
                adapter.notifyDataSetChanged();
                updateCounter();
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Preserve the previous order for still-checked apps, then append
                // newly checked ones in A-Z order.
                final List<ComponentName> ordered = new ArrayList<>();
                for (ComponentName cn : QuickLaunchStore.getFavorites(QuickLaunchSettingsActivity.this)) {
                    if (checked.contains(cn) && !ordered.contains(cn)) {
                        ordered.add(cn);
                    }
                }
                for (AppItem app : apps) {
                    final ComponentName cn = app.component();
                    if (checked.contains(cn) && !ordered.contains(cn)) {
                        ordered.add(cn);
                    }
                }
                QuickLaunchStore.setFavorites(QuickLaunchSettingsActivity.this, ordered);
                Toast.makeText(QuickLaunchSettingsActivity.this,
                        R.string.settings_saved, Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        updateCounter();
        loadApps();
    }

    private void loadApps() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                model.load(QuickLaunchSettingsActivity.this);
                final List<AppItem> loaded = new ArrayList<>(model.apps);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        apps.clear();
                        apps.addAll(loaded);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void updateCounter() {
        counter.setText(getString(R.string.settings_counter,
                checked.size(), QuickLaunchStore.MAX_FAVORITES));
    }

    private final class SettingsAdapter extends BaseAdapter {

        private final LayoutInflater inflater = LayoutInflater.from(QuickLaunchSettingsActivity.this);

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public AppItem getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View row = convertView != null
                    ? convertView
                    : inflater.inflate(R.layout.item_settings_row, parent, false);
            final AppItem app = getItem(position);
            final ImageView icon = row.findViewById(R.id.app_icon);
            final TextView label = row.findViewById(R.id.app_label);
            final CheckBox check = row.findViewById(R.id.app_check);
            icon.setImageDrawable(app.icon);
            label.setText(app.label);
            check.setChecked(checked.contains(app.component()));
            return row;
        }
    }
}

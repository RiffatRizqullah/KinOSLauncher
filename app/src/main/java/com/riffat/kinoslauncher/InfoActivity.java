package com.riffat.kinoslauncher;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

/**
 * About screen: KinOS hero panel with version and copyright. Reached from the
 * KinOS Settings menu as well as the system settings / app drawer.
 */
public class InfoActivity extends android.app.Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        final TextView name = findViewById(R.id.info_app_name);
        final TextView version = findViewById(R.id.info_app_version);

        name.setText(R.string.app_name);
        version.setText(getString(R.string.info_version, versionName()));
    }

    private String versionName() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}

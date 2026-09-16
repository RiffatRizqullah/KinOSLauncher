package com.riffat.kinoslauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * The (empty) home screen: a clock centered on the wallpaper plus bottom hints.
 * All interaction is delegated to {@link KinOSDialSelectorView}, which is the
 * root view of {@code activity_home.xml}.
 *
 * <p>On first startup the user is asked to make KinOS the default home screen
 * ({@link RoleManager} dialog on Android 10+, fallback to system settings).</p>
 */
public class HomeActivity extends Activity {

    private static final String PREFS = "kinos_home";
    private static final String KEY_HINTS_SHOWN = "hints_shown";
    private static final String KEY_ROLE_DECLINED = "role_declined";
    private static final int REQUEST_HOME_ROLE = 1001;

    private KinOSDialSelectorView dialView;

    private LinearLayout timeContainer;
    private LinearLayout hintContainer;
    private boolean hintFadeRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: the scrim and drawer (direct MATCH_PARENT children of the
        // root dial view) then cover the status/navigation areas too, while
        // home_root keeps fitsSystemWindows so clock and hints stay clear of them.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_home);

        dialView = findViewById(R.id.dial_view);
        hintContainer = findViewById(R.id.hint_container);
        timeContainer = findViewById(R.id.clock_container);

        dialView.setLaunchListener(new KinOSDialSelectorView.LaunchListener() {
            @Override
            public void onAppLaunched(AppItem app) {
                launchApp(app);
            }
        });
        dialView.setDialListener(new KinOSDialSelectorView.DialListener() {
            @Override
            public void onDialOpened() {
                maybeHideTime();
            }

            @Override
            public void onDialClosed() {
                showTime();
            }
        });
        dialView.setDrawerListener(new KinOSDialSelectorView.DrawerListener() {
            @Override
            public void onDrawerOpened() {
                maybeHideTime();
            }

            @Override
            public void onDrawerClosed() {
                if (!dialView.isDialOpen()) {
                    showTime();
                }
            }

            @Override
            public void onSettingsRequested() {
                startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
            }
        });
        dialView.prepare();

        maybeHideHints();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Picks up newly installed/removed apps and re-asks for the home role
        // if the user declined earlier (they can always decline again).
        dialView.refresh();
        dialView.reloadFavorites();
        maybeRequestHomeRole();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (dialView.isDrawerOpen() || dialView.isDialOpen()) {
            dialView.close();
            return;
        }
        super.onBackPressed();
    }

    // =====================================================================================
    // Default home screen role
    // =====================================================================================

    /**
     * Asks the user to set KinOS as the default home screen. Uses the system
     * RoleManager dialog on Android 10+; falls back to the "Default apps" settings
     * screen on older versions. Only prompts until the user declines once.
     */
    private void maybeRequestHomeRole() {
        if (isDefaultHome()) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ROLE_DECLINED, false).apply();
            return;
        }
        final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ROLE_DECLINED, false)) {
            return; // user said no before; stop asking
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                final Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                startActivityForResult(intent, REQUEST_HOME_ROLE);
                return;
            }
        }
        // Fallback (API < 29 or role unavailable): send the user to default-apps settings.
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            Toast.makeText(this, R.string.set_default_home_hint, Toast.LENGTH_LONG).show();
            // Treat as "asked" so we do not loop if they back out.
            prefs.edit().putBoolean(KEY_ROLE_DECLINED, true).apply();
        } catch (RuntimeException e) {
            prefs.edit().putBoolean(KEY_ROLE_DECLINED, true).apply();
        }
    }

    /** True when this app is already the user's default home screen. */
    private boolean isDefaultHome() {
        final Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        return getPackageManager().resolveActivity(homeIntent, 0) != null
                && getPackageName().equals(
                        getPackageManager().resolveActivity(homeIntent, 0).activityInfo.packageName);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_HOME_ROLE && resultCode != RESULT_OK) {
            // User declined the system dialog; remember and stop asking.
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ROLE_DECLINED, true).apply();
        }
    }

    // =====================================================================================
    // App launch / wallpaper / hints
    // =====================================================================================

    private void launchApp(AppItem app) {
        if (app == null) return;
        try {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(app.component());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            if (dialView.isDialOpen() || dialView.isDrawerOpen()) {
                dialView.close();
            }
        } catch (RuntimeException e) {
            openAppInfo(app.packageName);
        }
    }

    private void openWallpaperPicker() {
        try {
            final Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
            final String picker = getResources().getString(R.string.wallpaper_picker_title);
            startActivity(Intent.createChooser(intent, picker));
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void openAppInfo(String packageName) {
        try {
            final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (RuntimeException ignored) {
        }
    }

    /** Fades the bottom hints out once the user has performed a gesture once. */
    private void maybeHideHints() {
        final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_HINTS_SHOWN, false) && hintContainer != null) {
            hintContainer.setVisibility(View.GONE);
            return;
        }
        dialView.setHintsShownListener(new KinOSDialSelectorView.HintsShownListener() {
            @Override
            public void onHintsShown() {
                if (hintFadeRunning) return;
                hintFadeRunning = true;
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_HINTS_SHOWN, true)
                        .apply();
                if (hintContainer != null) {
                    hintContainer.animate().alpha(0f).setDuration(300)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    hintContainer.setVisibility(View.GONE);
                                }
                            }).start();
                }
            }
        });
    }

    /** Hides the clock while the dial selector is shown so it never sits behind it. */
    private void maybeHideTime() {
        if (timeContainer == null) return;
        if (timeContainer.getVisibility() == View.GONE) return;
        timeContainer.animate().cancel();
        timeContainer.animate().alpha(0f).setDuration(150)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        timeContainer.setVisibility(View.GONE);
                    }
                }).start();
    }

    /** Unhides the clock once the dial selector is hidden. */
    private void showTime() {
        if (timeContainer == null) return;
        timeContainer.animate().cancel();
        timeContainer.setVisibility(View.VISIBLE);
        timeContainer.animate().alpha(1f).setDuration(150)
                .setListener(null).start();
    }

}

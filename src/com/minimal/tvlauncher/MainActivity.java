package com.minimal.tvlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;

/**
 * The home screen. It hosts one of several launcher looks ("themes"), chosen from the
 * settings menu inside each of them; the choice is remembered.
 */
public class MainActivity extends Activity {

    static final String[] LAUNCHER_IDS = {"apartment", "monkey_island", "vinyl"};
    static final String[] LAUNCHER_NAMES = {"Apartment", "Monkey Island", "Vinyl"};
    private static final String DEFAULT_LAUNCHER = "apartment";

    private SharedPreferences settings;
    private LauncherTheme theme;
    private String launcher;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            theme.onTimeChanged();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            theme.setApps(Apps.load(MainActivity.this));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = getSharedPreferences("launcher", MODE_PRIVATE);
        launcher = settings.getString("launcher", DEFAULT_LAUNCHER);
        if ("monkey_island".equals(launcher)) {
            theme = new MonkeyIslandTheme(this);
        } else if ("vinyl".equals(launcher)) {
            theme = new VinylTheme(this);
        } else {
            theme = new ApartmentTheme(this);
        }
        setContentView(theme.createView());
        theme.setApps(Apps.load(this));

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    String currentLauncher() {
        return launcher;
    }

    /** Remembers the chosen launcher and rebuilds the home screen with it. */
    void switchLauncher(String id) {
        if (id.equals(launcher)) {
            return;
        }
        settings.edit().putString("launcher", id).apply();
        recreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter timeFilter = new IntentFilter();
        timeFilter.addAction(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        timeFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(timeReceiver, timeFilter);
        theme.onTimeChanged();
        theme.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(timeReceiver);
        theme.onPause();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already home.
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            theme.onHome();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        theme.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; Back only closes menus.
        theme.onBack();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return theme.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}

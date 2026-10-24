package com.minimal.tvlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

/**
 * The home screen. It hosts one of several launcher looks ("themes"), chosen from the
 * settings menu inside each of them; the choice is remembered.
 *
 * Whatever the chosen launcher, while Spotify is playing the Vinyl launcher's record is
 * shown over it, and it goes away again once the music stops. Back hides the record until
 * the music next starts.
 *
 * It also runs the phone page (see RemoteServer) and picks up changes made there or in
 * EditAppsActivity.
 */
public class MainActivity extends Activity implements NowPlaying.Listener, Remote.Listener {

    static final String[] LAUNCHER_IDS = {"apartment", "window", "monkey_island", "vinyl"};
    static final String[] LAUNCHER_NAMES = {"Apartment", "Window", "Monkey Island", "Vinyl"};
    private static final String DEFAULT_LAUNCHER = "apartment";

    private static final long PAUSED_COUNTS_AS_STOPPED_MS = 30000;
    private static final long FADE_MS = 450;

    private SharedPreferences settings;
    private String launcher;
    private final Handler handler = new Handler();
    private FrameLayout container;
    private List<Apps.Entry> apps;

    private LauncherTheme base;      // the launcher the user picked
    private View baseView;
    private VinylTheme vinyl;        // shown over it while music plays (null if base is Vinyl)
    private View vinylView;
    private NowPlaying music;
    private boolean showingVinyl;
    private boolean dismissed;       // Back pressed on the record; stays hidden until music next starts
    private boolean resumed;

    private final Runnable musicStopped = new Runnable() {
        @Override
        public void run() {
            dismissed = false;
            showVinyl(false);
        }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            active().onTimeChanged();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            apps = Apps.load(MainActivity.this);
            base.setApps(apps);
            if (vinylView != null) {
                vinyl.setApps(apps);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = getSharedPreferences("launcher", MODE_PRIVATE);
        launcher = settings.getString("launcher", DEFAULT_LAUNCHER);
        if ("monkey_island".equals(launcher)) {
            base = new MonkeyIslandTheme(this);
        } else if ("vinyl".equals(launcher)) {
            base = new VinylTheme(this);
        } else if ("window".equals(launcher)) {
            base = new ApartmentTheme(this, true);
        } else {
            base = new ApartmentTheme(this, false);
        }
        container = new FrameLayout(this);
        baseView = base.createView();
        container.addView(baseView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(container);
        apps = Apps.load(this);
        base.setApps(apps);

        if (!(base instanceof VinylTheme)) {
            vinyl = new VinylTheme(this);
            music = new NowPlaying(this, this);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        Remote.start(this);
        Remote.setListener(this);
    }

    String currentLauncher() {
        return launcher;
    }

    /** Remembers the chosen launcher and rebuilds the home screen with it. */
    void switchLauncher(String id) {
        if (id.equals(launcher)) {
            if (showingVinyl) {
                dismissed = true;
                showVinyl(false);
            }
            return;
        }
        settings.edit().putString("launcher", id).apply();
        recreate();
    }

    /** Opens the TV's app editor; the home screen updates when it saves. */
    void editApps() {
        startActivity(new Intent(this, EditAppsActivity.class));
    }

    // ---- Changes from the phone page or the app editor ----

    @Override
    public void onAppsChanged() {
        apps = Apps.load(this);
        base.setApps(apps);
        if (vinylView != null) {
            vinyl.setApps(apps);
        }
    }

    @Override
    public void onLauncherChanged(String id) {
        switchLauncher(id);
    }

    @Override
    public void onBackgroundsChanged() {
        // When hidden, the slideshow re-reads the folder as it comes back on screen anyway.
        if (resumed && !showingVinyl && base instanceof MonkeyIslandTheme) {
            ((MonkeyIslandTheme) base).refreshBackground();
        }
    }

    private LauncherTheme active() {
        return showingVinyl ? vinyl : base;
    }

    // ---- The record over the chosen launcher ----

    @Override
    public void onStateChanged(int state) {
        handler.removeCallbacks(musicStopped);
        if (state == NowPlaying.PLAYING) {
            if (!dismissed) {
                showVinyl(true);
            }
        } else if (state == NowPlaying.PAUSED) {
            // A short pause keeps the (stopped) record; a long one counts as the music ending.
            handler.postDelayed(musicStopped, PAUSED_COUNTS_AS_STOPPED_MS);
        } else {
            musicStopped.run();
        }
    }

    @Override
    public void onArtChanged(Bitmap art, int[] colors) {
        // The Vinyl launcher follows the music itself.
    }

    @Override
    public void onTimingChanged(long positionMs, long updatedAt, float speed, long durationMs) {
    }

    private void showVinyl(boolean show) {
        if (vinyl == null || show == showingVinyl) {
            return;
        }
        if (resumed) {
            active().onPause();
        }
        showingVinyl = show;
        if (vinylView == null) {
            vinylView = vinyl.createView();
            vinyl.setApps(apps);
            container.addView(vinylView, new FrameLayout.LayoutParams(-1, -1));
        }
        vinylView.animate().cancel();
        if (show) {
            vinylView.setAlpha(0f);
            vinylView.setVisibility(View.VISIBLE);
            vinylView.animate().alpha(1f).setDuration(FADE_MS).withEndAction(new Runnable() {
                @Override
                public void run() {
                    baseView.setVisibility(View.GONE);
                }
            }).start();
        } else {
            baseView.setVisibility(View.VISIBLE);
            vinylView.animate().alpha(0f).setDuration(FADE_MS).withEndAction(new Runnable() {
                @Override
                public void run() {
                    vinylView.setVisibility(View.GONE);
                }
            }).start();
        }
        if (resumed) {
            active().onTimeChanged();
            active().onResume();
        }
    }

    // ---- Lifecycle ----

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter timeFilter = new IntentFilter();
        timeFilter.addAction(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        timeFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(timeReceiver, timeFilter);
        // Check the music first, so the right screen appears straight away.
        boolean was = showingVinyl;
        if (music != null) {
            music.start();
        }
        if (showingVinyl == was) {
            active().onTimeChanged();
            active().onResume();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        unregisterReceiver(timeReceiver);
        if (music != null) {
            music.stop();
        }
        handler.removeCallbacks(musicStopped);
        active().onPause();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already home.
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            active().onHome();
        }
    }

    @Override
    protected void onDestroy() {
        Remote.clearListener(this);
        unregisterReceiver(packageReceiver);
        base.onDestroy();
        if (vinylView != null) {
            vinyl.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; Back closes menus, or hides the record.
        if (!active().onBack() && showingVinyl) {
            dismissed = true;
            showVinyl(false);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return active().onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}

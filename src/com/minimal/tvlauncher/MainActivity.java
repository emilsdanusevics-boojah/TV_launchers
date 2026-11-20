package com.minimal.tvlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.List;

/**
 * The home screen. It hosts one of several launcher looks ("themes"), chosen from the
 * settings menu inside each of them; the choice is remembered.
 *
 * Whatever the chosen launcher, while Spotify is playing the Vinyl launcher's record is
 * shown over it, and it goes away again once the music stops. Over the Fireplace and Window
 * launchers it's a small record in the corner instead (MiniRecord), unless set otherwise. Back hides the record until
 * the music next starts.
 *
 * After a few minutes without the remote (set on the phone page), the launcher on screen
 * goes into ambient mode: just its picture and a dim clock. Any button brings it back; that
 * press does nothing else.
 *
 * Day Arc (see DayArc) warms and dims every launcher after sunset and fades it to black at
 * bedtime; Autopilot (see Autopilot) picks the launcher by time of day and red days.
 *
 * It also runs the phone page (see RemoteServer) and picks up changes made there or in
 * EditAppsActivity.
 */
public class MainActivity extends Activity implements NowPlaying.Listener, Remote.Listener, TimerBar.Listener {

    static final String[] LAUNCHER_IDS = {"photos", "rits", "play", "apartment", "fireplace", "window", "monkey_island", "vinyl"};
    static final String[] LAUNCHER_NAMES = {"Photos", "Rīts", "Play", "Apartment", "Fireplace", "Window", "Monkey Island", "Vinyl"};
    static final String DEFAULT_LAUNCHER = "photos";

    private static final long PAUSED_COUNTS_AS_STOPPED_MS = 30000;
    static final int DEFAULT_AMBIENT_MINUTES = 5;
    // Ambient mode keeps the screen on for this long; then the TV's own sleep takes over.
    private static final long AMBIENT_KEEPS_SCREEN_ON_MS = 2 * 60 * 60 * 1000;
    private static final long FADE_MS = 450;

    private SharedPreferences settings;
    private String launcher;
    private final Handler handler = new Handler();
    private FrameLayout container;
    private List<Apps.Entry> apps;

    private LauncherTheme base;      // the launcher the user picked
    private View baseView;
    private VinylTheme vinyl;        // shown over it while music plays (null if base is Vinyl)
    private MiniRecord mini;         // or this small record, over Fireplace and Window
    private boolean showingMini;
    private View vinylView;
    private NowPlaying music;
    private boolean showingVinyl;
    private boolean dismissed;       // Back pressed on the record; stays hidden until music next starts
    private boolean resumed;
    private boolean ambient;
    private DayArc dayArc;
    private TimerBar timerBar;
    // Set before the activity is rebuilt for Autopilot in ambient mode: go straight back into it.
    private static boolean resumeAmbient;
    private int swallowKeyUp = -1;   // the key that woke ambient mode; its release is ignored too
    private long lastPressAt;

    private final Runnable goAmbient = new Runnable() {
        @Override
        public void run() {
            if (!resumed || ambient) {
                return;
            }
            // The wait may have been changed on the phone meanwhile.
            int minutes = ambientMinutes(MainActivity.this);
            if (minutes <= 0) {
                handler.postDelayed(this, 60000);   // off; look again in case it is turned on
                return;
            }
            long left = lastPressAt + minutes * 60000L - System.currentTimeMillis();
            if (left > 1000) {
                handler.postDelayed(this, left);
                return;
            }
            // Music playing (e.g. Spotify Connect): not idle. Look again in a minute.
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio != null && audio.isMusicActive()) {
                handler.postDelayed(this, 60000);
                return;
            }
            if (autopilot(true)) {
                return;   // rebuilding with another launcher; it goes into ambient mode itself
            }
            enterAmbient();
        }
    };

    private final Runnable releaseScreen = new Runnable() {
        @Override
        public void run() {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    };

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
            dayArc.update(MainActivity.this);
            if (ambient) {
                // Nobody's using it: the launcher may change with the evening, and bedtime may come.
                if (!autopilot(true)) {
                    checkBedtime();
                }
            }
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
        String auto = Autopilot.choose(this, launcher);
        if (auto != null) {
            launcher = auto;
            settings.edit().putString("launcher", auto).apply();
        }
        if ("monkey_island".equals(launcher)) {
            base = new MonkeyIslandTheme(this);
        } else if ("vinyl".equals(launcher)) {
            base = new VinylTheme(this);
        } else if ("apartment".equals(launcher)) {
            base = new ApartmentTheme(this);
        } else if ("fireplace".equals(launcher)) {
            base = new FireplaceTheme(this);
        } else if ("window".equals(launcher)) {
            base = new WindowTheme(this);
        } else if ("rits".equals(launcher)) {
            base = new RitsTheme(this);
        } else if ("play".equals(launcher)) {
            base = new PlayTheme(this);
        } else {
            base = new PhotosTheme(this);
        }
        // Video and slow movement look smoothest at 60 Hz (a 50 Hz TV judders 30 fps loops).
        OrbitView.preferEvenRefresh(getWindow(), getWindowManager().getDefaultDisplay());
        container = new FrameLayout(this);
        baseView = base.createView();
        container.addView(baseView, new FrameLayout.LayoutParams(-1, -1));
        if (base instanceof FireplaceTheme || base instanceof WindowTheme) {
            mini = new MiniRecord(this);
            int m = PanelMenu.dp(this, 36);
            FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(PanelMenu.dp(this, 460), PanelMenu.dp(this, 110),
                    android.view.Gravity.BOTTOM | android.view.Gravity.END);
            mp.setMargins(0, 0, m, m);
            container.addView(mini, mp);
        }
        dayArc = new DayArc(this);
        dayArc.setStrength(veilStrength(launcher));
        container.addView(dayArc, new FrameLayout.LayoutParams(-1, -1));
        // Kitchen timers from the phone, over everything.
        timerBar = new TimerBar(this, this);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP);
        tp.topMargin = PanelMenu.dp(this, 18);
        container.addView(timerBar, tp);
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
        String learned = Autopilot.choseByHand(this, id);
        if (learned != null) {
            android.widget.Toast.makeText(this, learned, android.widget.Toast.LENGTH_LONG).show();
        }
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
        if (base instanceof PhotosTheme) {
            ((PhotosTheme) base).refreshPhotos();
        }
    }

    @Override
    public void onSettingsChanged() {
        dayArc.update(this);
        timerBar.refresh();
        if (base instanceof PlayTheme) {
            ((PlayTheme) base).refreshTiles();
        }
        if (base instanceof PhotosTheme) {
            ((PhotosTheme) base).refreshEffects();
            ((PhotosTheme) base).refreshBoard();
        }
    }

    private LauncherTheme active() {
        return showingVinyl ? vinyl : base;
    }

    // ---- The record over the chosen launcher ----

    @Override
    public void onStateChanged(int state) {
        handler.removeCallbacks(musicStopped);
        if (mini != null) {
            mini.setPlaying(state == NowPlaying.PLAYING);
        }
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
        // The Vinyl launcher follows the music itself; the small record needs telling.
        if (mini != null && music != null) {
            mini.setTrack(art, colors, music.title(), music.artist());
        }
    }

    @Override
    public void onTimingChanged(long positionMs, long updatedAt, float speed, long durationMs) {
    }

    // The small corner record, used instead of the full Vinyl screen over Fireplace and Window.
    private boolean useMini() {
        return mini != null && !"full".equals(settings.getString("music_overlay", "corner"));
    }

    private void showVinyl(boolean show) {
        if (useMini() || (showingMini && !show)) {
            if (show != showingMini) {
                showingMini = show;
                if (show && music != null) {
                    mini.setText(music.title(), music.artist());
                }
                mini.show(show);
            }
            if (!show || useMini()) {
                return;
            }
        }
        if (vinyl == null || show == showingVinyl) {
            return;
        }
        leaveAmbient();
        if (resumed) {
            active().onPause();
        }
        showingVinyl = show;
        dayArc.setStrength(veilStrength(show ? "vinyl" : launcher));
        if (vinylView == null) {
            vinylView = vinyl.createView();
            vinyl.setApps(apps);
            container.addView(vinylView, container.indexOfChild(dayArc), new FrameLayout.LayoutParams(-1, -1));
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
        dayArc.update(this);
        timerBar.start();
        restartIdleTimer();
        if (resumeAmbient) {
            resumeAmbient = false;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (resumed && !ambient) {
                        enterAmbient();
                    }
                }
            });
        } else {
            autopilot(false);
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
        handler.removeCallbacks(goAmbient);
        leaveAmbient();
        timerBar.stop();
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
        if (!active().onBack() && (showingVinyl || showingMini)) {
            dismissed = true;
            showVinyl(false);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return active().onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    // ---- Ambient mode ----

    // Every press restarts the wait; the first press in ambient mode only wakes the launcher.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == swallowKeyUp) {
            swallowKeyUp = -1;
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // Faded to black (bedtime or a burnt-down fire): this press just brings it back.
            if (dayArc != null && dayArc.isOut() && !ambient) {
                dayArc.relight();
                swallowKeyUp = event.getKeyCode();
                restartIdleTimer();
                return true;
            }
            // A finished timer is ringing: this press just stops it.
            if (Timers.anyDone()) {
                Timers.dismissDone();
                timerBar.refresh();
                swallowKeyUp = event.getKeyCode();
                restartIdleTimer();
                return true;
            }
            if (ambient) {
                swallowKeyUp = event.getKeyCode();
                leaveAmbient();
                restartIdleTimer();
                return true;
            }
            restartIdleTimer();
        }
        return super.dispatchKeyEvent(event);
    }

    /** The fire has burnt down: fade to black over a minute, then let the TV sleep. */
    void sleepNow() {
        dayArc.candleOut(60000);
        handler.removeCallbacks(releaseScreen);
        handler.postDelayed(releaseScreen, 60000);
    }

    // ---- Timers ----

    @Override
    public void onTimerDone() {
        if (ambient) {
            leaveAmbient();
            restartIdleTimer();
        }
    }

    @Override
    public void onTimersActive(boolean active) {
        // Keep the screen on while a timer counts down, so its end isn't missed.
        if (active) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else if (!ambient) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /** Minutes without the remote before ambient mode; 0 = never. */
    static int ambientMinutes(Context context) {
        return context.getSharedPreferences("launcher", MODE_PRIVATE).getInt("ambient_minutes", DEFAULT_AMBIENT_MINUTES);
    }

    private void restartIdleTimer() {
        handler.removeCallbacks(goAmbient);
        lastPressAt = System.currentTimeMillis();
        if (resumed) {
            // First look after a minute, so a wait changed on the phone applies soon.
            handler.postDelayed(goAmbient, 60000L);
        }
    }

    private void enterAmbient() {
        ambient = true;
        active().setAmbient(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.postDelayed(releaseScreen, AMBIENT_KEEPS_SCREEN_ON_MS);
        checkBedtime();
    }

    // Past bedtime with nobody watching: fade to black, then let the TV sleep.
    private void checkBedtime() {
        if (ambient && DayArc.pastBedtime(this, new java.util.Date())) {
            dayArc.candleOut();
            handler.removeCallbacks(releaseScreen);
            handler.postDelayed(releaseScreen, DayArc.CANDLE_OUT_MS);
        }
    }

    /**
     * Switches to the launcher Autopilot wants, if it's another one. Returns true if the home
     * screen is being rebuilt. {@code thenAmbient}: the new launcher starts in ambient mode.
     */
    private boolean autopilot(boolean thenAmbient) {
        String next = Autopilot.choose(this, launcher);
        if (next == null || showingVinyl) {
            return false;
        }
        settings.edit().putString("launcher", next).commit();
        resumeAmbient = thenAmbient;
        recreate();
        return true;
    }

    // How strongly Day Arc's evening veil shows on each launcher.
    private static float veilStrength(String id) {
        if ("apartment".equals(id)) {
            return 0.4f;    // has its own evening lamps
        } else if ("fireplace".equals(id) || "window".equals(id) || "rits".equals(id)) {
            return 0.5f;    // evening scenes already, or Rīts's own dark paper
        } else if ("vinyl".equals(id)) {
            return 0.7f;
        } else if ("monkey_island".equals(id)) {
            return 0.8f;
        }
        return 1f;
    }

    private void leaveAmbient() {
        handler.removeCallbacks(releaseScreen);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (dayArc != null) {
            dayArc.relight();
        }
        if (ambient) {
            ambient = false;
            active().setAmbient(false);
        }
    }
}

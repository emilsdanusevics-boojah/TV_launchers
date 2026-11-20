package com.minimal.tvlauncher;

import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A cozy launcher: a crackling fire filling the screen (a pre-rendered loop, see
 * web/fireplace.html), the clock dim in the corner, and, if chosen, the quiet sound of the
 * fire or of rain. The apps stay out of the way: any button on the remote brings up the app
 * strip, which fades away again after a few seconds without a press.
 *
 * The fire burns down with the evening: from 23:00 (or an hour before the bedtime set on the
 * phone page) it settles into glowing embers (a second loop, downloaded the first time). The
 * menu's "Burn down" lets it die out over 30, 60 or 90 minutes: embers at once, the sound
 * fading, then the screen fades to black and the TV may sleep.
 */
class FireplaceTheme implements LauncherTheme {

    private static final long STRIP_SHOWN_MS = 10000;
    private static final float SOUND_VOLUME = 0.35f;
    private static final String[] SOUNDS = {"off", "fire", "rain", "auto"};   // auto: rain when it rains outside
    private static final int FG = PanelMenu.FG;
    private static final int MUTED = PanelMenu.MUTED;
    private static final int ACCENT = 0xFFE59A4F;   // firelight
    private static final int ACCENT_INK = 0xFF1D1A15;
    private static final String FIRE = "fireplace/fireplace.mp4";
    private static final String EMBERS = "fireplace/embers.mp4";
    private static final int[] BURN_MINUTES = {0, 30, 60, 90};

    private final MainActivity activity;
    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private Typeface medium;
    private FireView fire;
    private ImageView still;
    private TextView time;
    private TextView date;
    private LinearLayout clock;
    private AmbientClock ambientClock;
    private LinearLayout strip;
    private LinearLayout appRow;
    private ImageView gear;
    private PanelMenu menu;
    private MediaPlayer player;
    private String playing = FIRE;
    private long burnStart;          // the burn-down started (0 = none)
    private long burnEnd;

    // Every ten seconds while burning down: fade the sound, and at the end put the TV to sleep.
    private final Runnable burnTick = new Runnable() {
        @Override
        public void run() {
            if (burnEnd == 0) {
                return;
            }
            long now = System.currentTimeMillis();
            float left = Math.max(0, (burnEnd - now) / (float) (burnEnd - burnStart));
            if (player != null) {
                player.setVolume(SOUND_VOLUME * left, SOUND_VOLUME * left);
            }
            if (now >= burnEnd) {
                burnStart = burnEnd = 0;
                activity.sleepNow();
                return;
            }
            handler.postDelayed(this, 10000);
        }
    };

    private final Runnable hideStrip = new Runnable() {
        @Override
        public void run() {
            if (menu.isOpen()) {
                handler.postDelayed(this, STRIP_SHOWN_MS);
                return;
            }
            strip.animate().alpha(0f).setDuration(600).withEndAction(new Runnable() {
                @Override
                public void run() {
                    strip.setVisibility(View.INVISIBLE);
                }
            }).start();
        }
    };

    FireplaceTheme(MainActivity activity) {
        this.activity = activity;
    }

    private int dp(float v) {
        return PanelMenu.dp(activity, v);
    }

    @Override
    public View createView() {
        prefs = activity.getSharedPreferences("launcher", MainActivity.MODE_PRIVATE);
        Typeface light = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-300.ttf");
        medium = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-500.ttf");

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xFF000000);
        playing = wantEmbers() && Scenes.available(activity, EMBERS) ? EMBERS : FIRE;
        fire = new FireView(activity, playing, new Runnable() {
            @Override
            public void run() {
                still.animate().alpha(0f).setDuration(400).start();   // the fire is moving now
            }
        });
        root.addView(fire, new FrameLayout.LayoutParams(-1, -1));
        // A still of the fire, shown at once while the video starts.
        still = new ImageView(activity);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            still.setImageBitmap(BitmapFactory.decodeStream(activity.getAssets().open(playing.replace(".mp4", ".jpg"))));
        } catch (Exception ignored) {
            // Black until the fire starts.
        }
        root.addView(still, new FrameLayout.LayoutParams(-1, -1));

        // The clock, dim in the corner.
        clock = new LinearLayout(activity);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setAlpha(0.55f);
        time = PanelMenu.text(activity, light, 64, FG);
        time.setIncludeFontPadding(false);
        date = PanelMenu.text(activity, medium, 13, FG);
        date.setLetterSpacing(0.14f);
        date.setAllCaps(true);
        clock.addView(time);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
        dateParams.topMargin = dp(6);
        dateParams.leftMargin = dp(3);
        clock.addView(date, dateParams);
        for (TextView t : new TextView[] {time, date}) {
            t.setShadowLayer(dp(6), 0, dp(1), 0xCC000000);
        }
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        clockParams.setMargins(dp(36), dp(30), 0, 0);
        root.addView(clock, clockParams);

        // The apps, on a dark strip that only shows when the remote is used.
        strip = new LinearLayout(activity);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(10), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC120D0A);
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), 0x1FFFFFFF);
        strip.setBackground(bg);
        appRow = new LinearLayout(activity);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(appRow, new LinearLayout.LayoutParams(0, -2, 1));
        gear = new ImageView(activity);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setImageTintList(PanelMenu.focusColors(ACCENT_INK, MUTED));
        gear.setBackground(PanelMenu.pill(activity, ACCENT, 0));
        gear.setPadding(dp(11), dp(11), dp(11), dp(11));
        gear.setFocusable(true);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu();
            }
        });
        strip.addView(gear, new LinearLayout.LayoutParams(dp(46), dp(46)));
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        stripParams.setMargins(dp(32), 0, dp(32), dp(28));
        root.addView(strip, stripParams);
        strip.setVisibility(View.INVISIBLE);
        strip.setAlpha(0f);
        // Moving around the strip keeps it up.
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                if (strip.getVisibility() == View.VISIBLE) {
                    handler.removeCallbacks(hideStrip);
                    handler.postDelayed(hideStrip, STRIP_SHOWN_MS);
                }
            }
        });

        menu = new PanelMenu(activity, medium, strip, new PanelMenu.OnClose() {
            @Override
            public void onClose() {
                gear.requestFocus();
            }
        });
        menu.setColors(ACCENT, ACCENT_INK);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(300), -2, Gravity.BOTTOM | Gravity.END);
        menuParams.setMargins(0, dp(24), dp(44), dp(100));
        root.addView(menu.view, menuParams);

        ambientClock = new AmbientClock(activity);
        root.addView(ambientClock, ambientClock.layout());
        return root;
    }

    @Override
    public void setAmbient(boolean ambient) {
        // Just the fire; the clock moves to the small one in the other corner.
        if (ambient) {
            menu.close();
            hideStripNow();
        }
        clock.animate().cancel();
        clock.animate().alpha(ambient ? 0f : 0.55f).setDuration(ambient ? 800 : 300).start();
        ambientClock.show(ambient);
    }

    @Override
    public void setApps(List<Apps.Entry> apps) {
        appRow.removeAllViews();
        for (final Apps.Entry app : apps) {
            TextView item = PanelMenu.text(activity, medium, 18, 0);
            item.setText(app.label);
            item.setTextColor(PanelMenu.focusColors(ACCENT_INK, FG));
            item.setBackground(PanelMenu.pill(activity, ACCENT, 0));
            item.setPadding(dp(20), dp(10), dp(20), dp(11));
            item.setFocusable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Apps.launch(activity, app);
                }
            });
            item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return Apps.openAppInfo(activity, app);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(6);
            appRow.addView(item, lp);
        }
    }

    // Late in the evening (or burning down): the embers.
    private boolean wantEmbers() {
        if (burnEnd != 0) {
            return true;
        }
        java.util.Calendar c = java.util.Calendar.getInstance();
        int h = c.get(java.util.Calendar.HOUR_OF_DAY);
        int bed = DayArc.bedtime(activity);
        boolean nearBed = bed >= 0 && (h == (bed + 23) % 24 || DayArc.pastBedtime(activity, new Date()));
        return h >= 23 || h < 5 || nearBed;
    }

    // Changes between flames and embers, softly: the new still fades in, then the video follows.
    private void updateFire() {
        String want = wantEmbers() ? EMBERS : FIRE;
        if (want.equals(playing)) {
            return;
        }
        if (!Scenes.available(activity, want)) {
            Scenes.fetch(activity, want);   // next minute, perhaps
            return;
        }
        playing = want;
        try {
            final android.graphics.Bitmap b = BitmapFactory.decodeStream(activity.getAssets().open(want.replace(".mp4", ".jpg")));
            still.setImageBitmap(b);
        } catch (Exception ignored) {
            // straight to the video
        }
        still.animate().cancel();
        still.animate().alpha(1f).setDuration(1500).withEndAction(new Runnable() {
            @Override
            public void run() {
                fire.setPath(playing);
            }
        }).start();
    }

    @Override
    public void onTimeChanged() {
        Date now = new Date();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
        ambientClock.tick();
        if (fire != null) {
            updateFire();
            if (player != null && "auto".equals(sound()) && !soundFile().equals(soundPlaying)) {
                startSound();   // the rain started or stopped
            }
        }
    }

    @Override
    public void onResume() {
        fire.onResume();
        startSound();
        updateFire();
        if (burnEnd != 0) {
            handler.removeCallbacks(burnTick);
            burnTick.run();
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(burnTick);
        handler.removeCallbacks(hideStrip);
        fire.onPause();
        stopSound();
    }

    @Override
    public void onHome() {
        menu.close();
        hideStripNow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showStrip();
            showSettingsMenu();
            return true;
        }
        // With the strip hidden nothing has focus: the first press just brings it up.
        if (strip.getVisibility() != View.VISIBLE && keyCode != KeyEvent.KEYCODE_BACK) {
            showStrip();
            return true;
        }
        return false;
    }

    @Override
    public boolean onBack() {
        if (menu.isOpen()) {
            menu.close();
            return true;
        }
        if (strip.getVisibility() == View.VISIBLE) {
            hideStripNow();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(burnTick);
        handler.removeCallbacks(hideStrip);
        stopSound();
    }

    // ---- The app strip ----

    private void showStrip() {
        strip.animate().cancel();
        strip.setVisibility(View.VISIBLE);
        strip.animate().alpha(1f).setDuration(250).start();
        View first = appRow.getChildCount() > 0 ? appRow.getChildAt(0) : gear;
        first.requestFocus();
        handler.removeCallbacks(hideStrip);
        handler.postDelayed(hideStrip, STRIP_SHOWN_MS);
    }

    private void hideStripNow() {
        handler.removeCallbacks(hideStrip);
        strip.animate().cancel();
        strip.setAlpha(0f);
        strip.setVisibility(View.INVISIBLE);
    }

    // ---- Sound ----

    private String sound() {
        return prefs.getString("fireplace_sound", "off");
    }

    private String soundPlaying;

    // "auto" follows the weather: rain on the window when it's raining, otherwise the fire.
    private String soundFile() {
        String s = sound();
        if ("auto".equals(s)) {
            return PixelWeather.kindFor(Weather.cached()) == PixelWeather.RAIN ? "rain" : "fire";
        }
        return s;
    }

    private void startSound() {
        stopSound();
        soundPlaying = soundFile();
        if ("off".equals(soundPlaying)) {
            return;
        }
        try {
            AssetFileDescriptor fd = activity.getAssets().openFd("fireplace/" + soundPlaying + ".ogg");
            player = new MediaPlayer();
            player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            player.setLooping(true);
            player.setVolume(SOUND_VOLUME, SOUND_VOLUME);
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopSound();
        }
    }

    private void stopSound() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // ---- Settings ----

    private void showSettingsMenu() {
        menu.open("Settings");
        menu.add("TV settings", new Runnable() {
            @Override
            public void run() {
                menu.close();
                Apps.openSettings(activity);
            }
        });
        menu.add("Edit apps", new Runnable() {
            @Override
            public void run() {
                menu.close();
                activity.editApps();
            }
        });
        menu.add(PanelMenu.note("Sound", sound()), new Runnable() {
            @Override
            public void run() {
                // Off → fire → rain → off.
                String now = sound();
                int i = 0;
                while (i < SOUNDS.length && !SOUNDS[i].equals(now)) {
                    i++;
                }
                prefs.edit().putString("fireplace_sound", SOUNDS[(i + 1) % SOUNDS.length]).apply();
                startSound();
                showSettingsMenu();
                menu.focus(2);
            }
        });
        final int burning = burnEnd == 0 ? 0 : (int) Math.ceil((burnEnd - System.currentTimeMillis()) / 60000.0);
        menu.add(PanelMenu.note("Burn down", burning == 0 ? "off" : burning + " min left"), new Runnable() {
            @Override
            public void run() {
                // Off → 30 → 60 → 90 minutes → off.
                int next = 0;
                for (int i = 0; i < BURN_MINUTES.length; i++) {
                    if (burning > 0 && burning <= BURN_MINUTES[i]) {
                        next = (i + 1) % BURN_MINUTES.length;
                        break;
                    }
                    if (burning == 0) {
                        next = 1;
                        break;
                    }
                }
                handler.removeCallbacks(burnTick);
                if (BURN_MINUTES[next] == 0) {
                    burnStart = burnEnd = 0;
                    if (player != null) {
                        player.setVolume(SOUND_VOLUME, SOUND_VOLUME);
                    }
                } else {
                    burnStart = System.currentTimeMillis();
                    burnEnd = burnStart + BURN_MINUTES[next] * 60000L;
                    burnTick.run();
                }
                updateFire();
                showSettingsMenu();
                menu.focus(3);
            }
        });
        menu.add("Change launcher", new Runnable() {
            @Override
            public void run() {
                menu.showLaunchers(activity);
            }
        });
        menu.phoneFooter();
        if (menu.view.findFocus() == null) {
            menu.focus(0);
        }
    }
}

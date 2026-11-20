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
 * A quiet launcher: the view from a window in a dim room, matched to the real weather, the
 * time of day and the season (pre-rendered loops, see web/window.html):
 *   raining          rain running down the glass, the city's lights blurred behind it
 *   evening, winter  a candle on the sill, snow falling past, frost on the glass
 *   evening          the candle on a clear summer evening
 *   day              a birch in the wind (summer), or bare and frosted (winter)
 * The videos download from GitHub the first time (see Scenes); until then the still shows.
 * Like the Fireplace, any button brings up the app strip, which fades away again.
 */
class WindowTheme implements LauncherTheme {

    private static final long STRIP_SHOWN_MS = 10000;
    private static final float SOUND_VOLUME = 0.35f;
    private static final String[] SOUNDS = {"off", "fire", "rain"};
    private static final int FG = PanelMenu.FG;
    private static final int MUTED = PanelMenu.MUTED;
    private static final int ACCENT = 0xFFC9B48A;   // candle on glass
    private static final int ACCENT_INK = 0xFF15171C;
    private static final long WEATHER_EVERY_MS = 15 * 60 * 1000;

    private final MainActivity activity;
    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private Typeface medium;
    private FireView fire;
    private ImageView still;
    private TextView time;
    private TextView date;
    private TextView fetchLine;
    private String scene;
    private MistView mist;
    private LinearLayout clock;
    private AmbientClock ambientClock;
    private LinearLayout strip;
    private LinearLayout appRow;
    private ImageView gear;
    private PanelMenu menu;
    private MediaPlayer player;

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

    // While the scene's video downloads (once), wait for it with the still showing.
    private final Runnable waitForVideo = new Runnable() {
        @Override
        public void run() {
            String path = path(scene);
            if (Scenes.available(activity, path)) {
                fetchLine.setVisibility(View.GONE);
                fire.setPath(path);
                return;
            }
            Scenes.fetch(activity, path);
            int p = Scenes.progress(path);
            fetchLine.setVisibility(View.VISIBLE);
            fetchLine.setText(p >= 0 ? "Fetching the view · " + p + "%" : "Fetching the view…");
            handler.postDelayed(this, 2000);
        }
    };

    private final Runnable weatherTimer = new Runnable() {
        @Override
        public void run() {
            Weather.refresh(new Weather.Callback() {
                @Override
                public void onWeather(Weather.Now now) {
                    updateScene();
                }
            });
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    WindowTheme(MainActivity activity) {
        this.activity = activity;
    }

    /** Which view suits the weather, the time of day and the season. */
    static String sceneFor(Date now, Weather.Now w) {
        String code = w == null ? "" : w.code;
        boolean wet = "rain".equals(code) || "drizzle".equals(code) || "heavy_rain".equals(code)
                || "thunder".equals(code) || "sleet".equals(code);
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(now);
        int month = c.get(java.util.Calendar.MONTH);
        boolean winter = month >= java.util.Calendar.NOVEMBER || month <= java.util.Calendar.MARCH
                || "snow".equals(code) || (w != null && w.tempC <= 0);
        if (wet) {
            return "rain";
        }
        if (DayArc.dark(now)) {
            return winter ? "snow" : "candle";
        }
        return winter ? "winter" : "summer";
    }

    private static String path(String scene) {
        return "window/" + scene + ".mp4";
    }

    private void updateMist() {
        Weather.Now w = Weather.cached();
        boolean cold = w != null && w.tempC <= 3;
        java.util.List<String> people = Board.peopleToday(activity, java.util.Calendar.getInstance());
        String text = "";
        if (!people.isEmpty()) {
            text = people.get(0) + " \u2665";
        } else if (activity.getSharedPreferences("launcher", MainActivity.MODE_PRIVATE).getBoolean("holidays", true)
                && Holidays.today() != null) {
            text = "\u2665";
        }
        mist.set(cold, text);
    }

    private void updateScene() {
        updateMist();
        String next = sceneFor(new Date(), Weather.cached());
        if (next.equals(scene)) {
            return;
        }
        scene = next;
        showStill();
        handler.removeCallbacks(waitForVideo);
        waitForVideo.run();
    }

    // The still for the scene, decoded off the main thread; shown at once while the video starts.
    private void showStill() {
        final String which = scene;
        still.animate().cancel();
        still.setAlpha(1f);
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.graphics.Bitmap b = null;
                try {
                    b = BitmapFactory.decodeStream(activity.getAssets().open("window/" + which + ".jpg"));
                } catch (Exception ignored) {
                    b = null;
                }
                final android.graphics.Bitmap bitmap = b;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (which.equals(scene) && !activity.isDestroyed()) {
                            still.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        }, "WindowStill").start();
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
        scene = sceneFor(new Date(), Weather.cached());
        fire = new FireView(activity, path(scene), new Runnable() {
            @Override
            public void run() {
                still.animate().alpha(0f).setDuration(600).start();   // the view is moving now
            }
        });
        root.addView(fire, new FrameLayout.LayoutParams(-1, -1));
        // A still of the view, shown at once while the video starts (or downloads).
        still = new ImageView(activity);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(still, new FrameLayout.LayoutParams(-1, -1));
        showStill();
        // Condensation on cold days, with a name traced in it on someone's day.
        mist = new MistView(activity);
        root.addView(mist, new FrameLayout.LayoutParams(-1, -1));

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
        fetchLine = PanelMenu.text(activity, medium, 12, FG);
        fetchLine.setVisibility(View.GONE);
        LinearLayout.LayoutParams fetchParams = new LinearLayout.LayoutParams(-2, -2);
        fetchParams.topMargin = dp(8);
        fetchParams.leftMargin = dp(3);
        clock.addView(fetchLine, fetchParams);
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
        bg.setColor(0xCC0E1014);
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

    @Override
    public void onTimeChanged() {
        Date now = new Date();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
        ambientClock.tick();
        if (fire != null) {
            updateScene();   // dusk, dawn or the season may have come
        }
    }

    @Override
    public void onResume() {
        fire.onResume();
        startSound();
        handler.removeCallbacks(weatherTimer);
        weatherTimer.run();
        handler.removeCallbacks(waitForVideo);
        if (!Scenes.available(activity, path(scene))) {
            waitForVideo.run();
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(weatherTimer);
        handler.removeCallbacks(waitForVideo);
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
        handler.removeCallbacks(weatherTimer);
        handler.removeCallbacks(waitForVideo);
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
        return prefs.getString("window_sound", "off");
    }

    private void startSound() {
        stopSound();
        if ("off".equals(sound())) {
            return;
        }
        try {
            AssetFileDescriptor fd = activity.getAssets().openFd("fireplace/" + sound() + ".ogg");
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
                prefs.edit().putString("window_sound", SOUNDS[(i + 1) % SOUNDS.length]).apply();
                startSound();
                showSettingsMenu();
                menu.focus(2);
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

package com.minimal.tvlauncher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A cozy launcher: a slow loop at eye level through the apartment's living room (a
 * pre-rendered video, so it's perfectly smooth; see {@link OrbitView}), with the live sky
 * over Riga in the windows: its colour by time of day, the sun, stars, rain, snow and fog,
 * from Open-Meteo's current weather. In the evening the room is lit by its own orange lamps.
 * The clock, today's weather and the apps (on a dark strip) are laid over it.
 *
 * On Latvia's red days the picture gets that holiday's decoration, a greeting appears under
 * the weather and the buttons take the holiday's colour (Settings → Holidays turns this off,
 * and shows any of the days as a preview).
 */
class ApartmentTheme implements LauncherTheme {

    private static final long WEATHER_EVERY_MS = 15 * 60 * 1000;

    private static final int SCENE = 0xFF1D1E20;
    private static final int FG = PanelMenu.FG;
    private static final int MUTED = PanelMenu.MUTED;
    private static final int ACCENT = 0xFFD4A464;
    private static final int ACCENT_INK = 0xFF1D1A15;

    private final MainActivity activity;
    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private int accent = ACCENT;
    private int ink = ACCENT_INK;
    private List<Apps.Entry> apps;
    private Holidays.Day preview;    // a holiday shown from the menu, until Back or Home
    private String shownHoliday = "";
    private Typeface light;
    private Typeface medium;
    private OrbitView orbit;
    private TextView time;
    private TextView date;
    private TextView weatherLine;
    private TextView holidayLine;
    private TextView fetchLine;      // "Fetching the room video · 34%" the first time
    private TextView glanceLine;     // today's or tomorrow's weather, like Photos' card
    private LinearLayout bar;
    private LinearLayout appRow;
    private ImageView gear;
    private PanelMenu menu;
    private View shade;
    private LinearLayout clock;
    private AmbientClock ambientClock;

    // While the room video downloads (the first time only), say so under the clock.
    private final Runnable fetchTimer = new Runnable() {
        @Override
        public void run() {
            String p = Scenes.anyProgress();
            fetchLine.setVisibility(p == null ? View.GONE : View.VISIBLE);
            if (p != null) {
                fetchLine.setText("Fetching the room video · " + p);
            }
            handler.postDelayed(this, 2000);
        }
    };

    private final Runnable weatherTimer = new Runnable() {
        @Override
        public void run() {
            refreshWeather();
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    ApartmentTheme(MainActivity activity) {
        this.activity = activity;
    }

    private int dp(float v) {
        return PanelMenu.dp(activity, v);
    }

    @Override
    public View createView() {
        prefs = activity.getSharedPreferences("launcher", MainActivity.MODE_PRIVATE);
        light = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-300.ttf");
        medium = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-500.ttf");

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(SCENE);

        // The room and the sky.
        OrbitView.preferEvenRefresh(activity.getWindow(), activity.getWindowManager().getDefaultDisplay());
        orbit = new OrbitView(activity);
        orbit.setFocusable(false);
        orbit.setWeather(Weather.cached());
        root.addView(orbit, new FrameLayout.LayoutParams(-1, -1));

        // A soft shade behind the clock, from the top-left corner.
        shade = PanelMenu.cornerShade(activity, 0xE01D1E20, 580);   // dark enough for the brighter desk view
        root.addView(shade, new FrameLayout.LayoutParams(dp(580), dp(580), Gravity.TOP | Gravity.START));

        // Clock, date, weather and any holiday greeting, in one block.
        clock = new LinearLayout(activity);
        clock.setOrientation(LinearLayout.VERTICAL);
        time = PanelMenu.text(activity, light, 88, FG);
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        date = PanelMenu.text(activity, medium, 14, 0xD9ECEBE8);
        date.setLetterSpacing(0.14f);
        date.setAllCaps(true);
        clock.addView(time);
        clock.addView(date, under(10));
        weatherLine = PanelMenu.text(activity, medium, 16, FG);
        weatherLine.setAlpha(0.9f);
        weatherLine.setCompoundDrawablePadding(dp(8));
        weatherLine.setGravity(Gravity.CENTER_VERTICAL);
        clock.addView(weatherLine, under(6));
        showWeather(Weather.cached());
        holidayLine = PanelMenu.text(activity, medium, 16, ACCENT);
        holidayLine.setVisibility(View.GONE);
        clock.addView(holidayLine, under(6));
        glanceLine = PanelMenu.text(activity, medium, 14, FG);
        glanceLine.setAlpha(0.85f);
        glanceLine.setSingleLine(false);
        glanceLine.setMaxLines(2);
        glanceLine.setShadowLayer(dp(6), 0, dp(1), 0x99000000);
        glanceLine.setVisibility(View.GONE);
        clock.addView(glanceLine, under(6));
        fetchLine = PanelMenu.text(activity, medium, 13, MUTED);
        fetchLine.setVisibility(View.GONE);
        clock.addView(fetchLine, under(10));
        // A soft shadow keeps the text readable over the room.
        for (TextView t : new TextView[] {time, date, weatherLine, holidayLine}) {
            t.setShadowLayer(dp(6), 0, dp(1), 0x99000000);
        }
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        clockParams.setMargins(dp(32), dp(40), 0, 0);
        root.addView(clock, clockParams);

        // Apps along the bottom on a dark, see-through strip, settings at the far right.
        bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(8), dp(8));
        GradientDrawable strip = new GradientDrawable();
        strip.setColor(0xB3141416);
        strip.setCornerRadius(dp(999));
        strip.setStroke(dp(1), 0x1FFFFFFF);
        bar.setBackground(strip);
        appRow = new LinearLayout(activity);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(appRow, new LinearLayout.LayoutParams(0, -2, 1));
        gear = new ImageView(activity);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setPadding(dp(11), dp(11), dp(11), dp(11));
        gear.setFocusable(true);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu();
            }
        });
        bar.addView(gear, new LinearLayout.LayoutParams(dp(46), dp(46)));
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        barParams.setMargins(dp(32), 0, dp(32), dp(28));
        root.addView(bar, barParams);

        // The settings menu: a small panel above the gear.
        menu = new PanelMenu(activity, medium, bar, new PanelMenu.OnClose() {
            @Override
            public void onClose() {
                gear.requestFocus();
            }
        });
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(300), -2, Gravity.BOTTOM | Gravity.END);
        menuParams.setMargins(0, dp(24), dp(44), dp(100));
        root.addView(menu.view, menuParams);
        paintAccent();

        ambientClock = new AmbientClock(activity);
        root.addView(ambientClock, ambientClock.layout());
        return root;
    }

    private LinearLayout.LayoutParams under(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(top);
        lp.leftMargin = dp(4);
        return lp;
    }

    @Override
    public void setApps(List<Apps.Entry> apps) {
        this.apps = apps;
        appRow.removeAllViews();
        for (final Apps.Entry app : apps) {
            TextView item = PanelMenu.text(activity, medium, 18, 0);
            item.setText(app.label);
            item.setTextColor(PanelMenu.focusColors(ink, FG));
            item.setBackground(PanelMenu.pill(activity, accent, 0));
            item.setPadding(dp(20), dp(10), dp(20), dp(11));
            item.setFocusable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Apps.launch(activity, app);
                }
            });
            // Long-press OK: the app's info page (uninstall, force stop, storage).
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
        if (!gear.hasFocus() && !menu.isOpen()) {
            focusFirst();
        }
    }

    // The glance card's first lines, so choosing the cozy room doesn't mean losing the forecast.
    private void showGlance() {
        Date now = new Date();
        java.util.List<String> lines = Glance.lines(activity, Weather.cached(), now);
        glanceLine.setVisibility(lines.isEmpty() ? View.GONE : View.VISIBLE);
        if (!lines.isEmpty()) {
            glanceLine.setText(Glance.title(now) + ": " + android.text.TextUtils.join(" · ", lines.subList(0, Math.min(2, lines.size()))));
        }
    }

    @Override
    public void setAmbient(boolean ambient) {
        // Just the room and the sky outside.
        if (ambient) {
            menu.close();
        }
        AmbientClock.fade(ambient, shade, clock, bar);
        ambientClock.show(ambient);
    }

    @Override
    public void onTimeChanged() {
        Date now = new Date();
        String fmt = DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm";
        time.setText(DateFormat.format(fmt, now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
        showHoliday();   // a new day may be a red day
        showGlance();
        ambientClock.tick();
    }

    @Override
    public void onResume() {
        handler.removeCallbacks(weatherTimer);
        weatherTimer.run();
        showHoliday();
        orbit.onResume();
        handler.removeCallbacks(fetchTimer);
        handler.postDelayed(fetchTimer, 1000);
        if (!menu.isOpen() && !gear.hasFocus()) {
            focusFirst();
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(weatherTimer);
        handler.removeCallbacks(fetchTimer);
        // Stop the video and drawing while an app is in front.
        orbit.onPause();
    }

    @Override
    public void onHome() {
        menu.close();
        stopPreview();
        focusFirst();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettingsMenu();
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
        if (preview != null) {
            stopPreview();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(weatherTimer);
        handler.removeCallbacks(fetchTimer);
        orbit.release();
    }

    // ---- Weather ----

    private void refreshWeather() {
        Weather.refresh(new Weather.Callback() {
            @Override
            public void onWeather(Weather.Now now) {
                showWeather(now);
                showGlance();
                orbit.setWeather(now);
            }
        });
    }

    private void showWeather(Weather.Now now) {
        weatherLine.setText(now == null ? "" : now.line());
        weatherLine.setVisibility(now == null ? View.GONE : View.VISIBLE);
        if (now != null) {
            WeatherIcon icon = new WeatherIcon(now.code, Sky.at(new Date(), now).lampsOn(), FG);
            icon.setBounds(0, 0, dp(22), dp(22));
            weatherLine.setCompoundDrawablesRelative(icon, null, null, null);
        }
    }

    // ---- Settings menu ----

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
        menu.add("Holidays", new Runnable() {
            @Override
            public void run() {
                showHolidaysMenu();
            }
        });
        menu.add("Screensaver", new Runnable() {
            @Override
            public void run() {
                // Where the TV's screensaver is chosen; "Apartment" is this launcher's.
                menu.close();
                if (!Apps.start(activity, new Intent("android.settings.DREAM_SETTINGS"))) {
                    Apps.openSettings(activity);
                }
            }
        });
        menu.add("Change launcher", new Runnable() {
            @Override
            public void run() {
                menu.showLaunchers(activity);
            }
        });
        menu.phoneFooter();
        menu.focus(0);
    }

    // Holidays: whether red days get their decoration, and a preview of each one.
    private void showHolidaysMenu() {
        menu.open("Holidays");
        final boolean on = holidaysOn();
        menu.add(PanelMenu.note("On red days", on ? "on" : "off"), new Runnable() {
            @Override
            public void run() {
                prefs.edit().putBoolean("holidays", !on).apply();
                showHoliday();
                showHolidaysMenu();
            }
        });
        for (final Holidays.Day day : Holidays.year(Calendar.getInstance().get(Calendar.YEAR))) {
            menu.add(PanelMenu.note(day.name, day.date()), new Runnable() {
                @Override
                public void run() {
                    menu.close();
                    preview = day;
                    showHoliday();
                }
            });
        }
        menu.setFooter("Pick a day to see it. Back ends the preview.");
        menu.focus(0);
    }

    private boolean holidaysOn() {
        return prefs.getBoolean("holidays", true);
    }

    private void stopPreview() {
        if (preview != null) {
            preview = null;
            showHoliday();
        }
    }

    // The holiday to show (a preview, or today's red day), in the picture, the greeting and
    // the buttons' colour.
    private void showHoliday() {
        Holidays.Day day = preview != null ? preview : holidaysOn() ? Holidays.today() : null;
        String key = day == null ? "" : day.id + (preview != null ? ":preview" : "");
        if (key.equals(shownHoliday)) {
            return;
        }
        shownHoliday = key;
        orbit.setHoliday(day);
        if (day == null) {
            holidayLine.setVisibility(View.GONE);
        } else {
            holidayLine.setText(preview != null ? day.greeting + "  ·  preview" : day.greeting);
            holidayLine.setTextColor(day.textColor());
            holidayLine.setVisibility(View.VISIBLE);
        }
        accent = day != null ? day.accent : ACCENT;
        ink = day != null ? day.ink : ACCENT_INK;
        paintAccent();
        if (apps != null) {
            boolean focused = appRow.hasFocus();
            int index = focused ? appRow.indexOfChild(appRow.getFocusedChild()) : -1;
            setApps(apps);
            if (index >= 0 && index < appRow.getChildCount()) {
                appRow.getChildAt(index).requestFocus();
            }
        }
    }

    // The accent colour on the gear and in the menu (the app buttons get it in setApps).
    private void paintAccent() {
        gear.setImageTintList(PanelMenu.focusColors(ink, MUTED));
        gear.setBackground(PanelMenu.pill(activity, accent, 0));
        menu.setColors(accent, ink);
    }

    // ---- Helpers ----

    private void focusFirst() {
        View first = appRow.getChildCount() > 0 ? appRow.getChildAt(0) : gear;
        first.requestFocus();
    }
}

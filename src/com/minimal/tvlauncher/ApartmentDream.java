package com.minimal.tvlauncher;

import android.graphics.Typeface;
import android.os.Handler;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * The TV's screensaver, Apartment style: the turning flat over the live Riga sky (with the
 * evening lamps and holiday decorations, as on the home screen) and a large, dim clock.
 * The clock drifts a little every minute so nothing stays in one place on the screen.
 *
 * Chosen in the TV's settings under Screen saver (the Apartment launcher's Settings menu has
 * a shortcut).
 */
public class ApartmentDream extends DreamService {

    private static final long WEATHER_EVERY_MS = 15 * 60 * 1000;

    private final Handler handler = new Handler();
    private final Random random = new Random();
    private OrbitView orbit;
    private LinearLayout clock;
    private TextView time;
    private TextView date;
    private TextView greeting;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showTime();
            // Next minute, on the minute.
            handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
        }
    };

    private final Runnable weatherTimer = new Runnable() {
        @Override
        public void run() {
            Weather.refresh(new Weather.Callback() {
                @Override
                public void onWeather(Weather.Now now) {
                    if (orbit != null) {
                        orbit.setWeather(now);
                    }
                }
            });
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        OrbitView.preferEvenRefresh(getWindow(), getWindow().getWindowManager().getDefaultDisplay());

        Typeface light = Typeface.createFromAsset(getAssets(), "fonts/HankenGrotesk-300.ttf");
        Typeface medium = Typeface.createFromAsset(getAssets(), "fonts/HankenGrotesk-500.ttf");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF1D1E20);
        orbit = new OrbitView(this);
        orbit.setWeather(Weather.cached());
        root.addView(orbit, new FrameLayout.LayoutParams(-1, -1));

        clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setAlpha(0.6f);   // dim: it's a screensaver
        time = text(light, 120, 0xFFECEBE8);
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        date = text(medium, 16, 0xFFECEBE8);
        date.setLetterSpacing(0.14f);
        date.setAllCaps(true);
        greeting = text(medium, 16, 0xFFD4A464);
        greeting.setVisibility(View.GONE);
        clock.addView(time);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-2, -2);
        under.topMargin = dp(8);
        under.leftMargin = dp(6);
        clock.addView(date, under);
        LinearLayout.LayoutParams under2 = new LinearLayout.LayoutParams(under);
        under2.topMargin = dp(6);
        clock.addView(greeting, under2);
        for (TextView t : new TextView[] {time, date, greeting}) {
            t.setShadowLayer(dp(8), 0, dp(1), 0x99000000);
        }
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        clockParams.setMargins(dp(56), 0, 0, dp(48));
        root.addView(clock, clockParams);

        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        Holidays.Day day = getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("holidays", true) ? Holidays.today() : null;
        orbit.setHoliday(day);
        greeting.setText(day != null ? day.greeting : "");
        greeting.setTextColor(day != null ? day.textColor() : 0xFFD4A464);
        greeting.setVisibility(day != null ? View.VISIBLE : View.GONE);
        orbit.onResume();
        tick.run();
        weatherTimer.run();
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(weatherTimer);
        orbit.onPause();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(weatherTimer);
        if (orbit != null) {
            orbit.release();
        }
        super.onDetachedFromWindow();
    }

    private void showTime() {
        Date now = new Date();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
        // Drift a little so the clock never sits in exactly the same place.
        clock.setTranslationX(dp(random.nextInt(49) - 24));
        clock.setTranslationY(dp(-random.nextInt(25)));
    }

    private TextView text(Typeface face, float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        return t;
    }
}

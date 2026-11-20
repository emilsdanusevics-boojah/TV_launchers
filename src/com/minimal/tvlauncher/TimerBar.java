package com.minimal.tvlauncher;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The running kitchen timers, as small pills at the top of the screen over any launcher:
 * "Tea  3:59". When one is done its pill turns bright ("Tea · done") and a soft chime plays,
 * again every 20 seconds up to three times, until any button on the remote is pressed.
 */
final class TimerBar extends LinearLayout {

    interface Listener {
        /** A timer has just finished (to wake the screen from ambient mode). */
        void onTimerDone();

        /** Whether any timer is running or ringing (to keep the screen on). */
        void onTimersActive(boolean active);
    }

    private static final int RING_EVERY_MS = 20000;
    private static final int RINGS = 3;

    private final Handler handler = new Handler();
    private final Typeface medium;
    private final Listener listener;
    private MediaPlayer chime;
    private boolean wasDone;
    private int rings;
    private long lastRingAt;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            if (running && !Timers.all().isEmpty()) {
                handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000 + 5);
            }
        }
    };

    TimerBar(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        medium = Typeface.createFromAsset(context.getAssets(), "fonts/HankenGrotesk-500.ttf");
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(false);
    }

    void start() {
        running = true;
        refresh();
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
        stopChime();
    }

    /** Timers were added or removed (from the phone, or by a button press). */
    void refresh() {
        handler.removeCallbacks(tick);
        tick.run();
    }

    private void update() {
        List<Timers.Timer> list = Timers.all();
        removeAllViews();
        boolean done = false;
        for (Timers.Timer t : list) {
            boolean d = t.done();
            done |= d;
            TextView pill = PanelMenu.text(getContext(), medium, 18, d ? 0xFF1D1A15 : PanelMenu.FG);
            pill.setText(d ? t.label + "  ·  done" : t.label + "   " + Timers.format(t.leftMs()));
            pill.setPadding(PanelMenu.dp(getContext(), 18), PanelMenu.dp(getContext(), 8),
                    PanelMenu.dp(getContext(), 18), PanelMenu.dp(getContext(), 9));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(PanelMenu.dp(getContext(), 999));
            bg.setColor(d ? 0xFFF2C46B : 0xD9141416);
            bg.setStroke(PanelMenu.dp(getContext(), 1), d ? 0x00000000 : 0x33FFFFFF);
            pill.setBackground(bg);
            LayoutParams lp = new LayoutParams(-2, -2);
            lp.leftMargin = lp.rightMargin = PanelMenu.dp(getContext(), 5);
            addView(pill, lp);
        }
        listener.onTimersActive(!list.isEmpty());
        if (done && !wasDone) {
            rings = 0;
            lastRingAt = 0;
            listener.onTimerDone();
        }
        wasDone = done;
        long now = System.currentTimeMillis();
        if (done && rings < RINGS && now - lastRingAt >= RING_EVERY_MS) {
            rings++;
            lastRingAt = now;
            ring();
        }
        if (!done) {
            stopChime();
        }
    }

    private void ring() {
        stopChime();
        try {
            AssetFileDescriptor fd = getContext().getAssets().openFd("timer.ogg");
            chime = new MediaPlayer();
            chime.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            chime.setVolume(0.8f, 0.8f);
            chime.prepare();
            chime.start();
        } catch (Exception e) {
            stopChime();
        }
    }

    private void stopChime() {
        if (chime != null) {
            chime.release();
            chime = null;
        }
    }
}

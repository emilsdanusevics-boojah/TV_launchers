package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.Random;

/**
 * The small, dim clock shown in ambient mode, in the bottom-right corner. It drifts a little
 * each minute so it never sits in exactly the same place on the screen. Hidden until shown.
 */
final class AmbientClock extends TextView {

    private final Random random = new Random();

    AmbientClock(Context context) {
        super(context);
        setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/HankenGrotesk-300.ttf"));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        setTextColor(0xFFECEBE8);
        setShadowLayer(PanelMenu.dp(context, 6), 0, PanelMenu.dp(context, 1), 0xCC000000);
        setAlpha(0f);
        setVisibility(View.GONE);
    }

    /** Lays it out in the bottom-right corner of a FrameLayout. */
    FrameLayout.LayoutParams layout() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        int m = PanelMenu.dp(getContext(), 40);
        lp.setMargins(0, 0, m, m);
        return lp;
    }

    /** Fades it in or out. */
    void show(boolean on) {
        tick();
        animate().cancel();
        if (on) {
            setVisibility(View.VISIBLE);
            animate().alpha(0.5f).setDuration(1200).start();
        } else {
            animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override
                public void run() {
                    setVisibility(View.GONE);
                }
            }).start();
        }
    }

    /** The new time, and a small move. Call every minute. */
    void tick() {
        setText(DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm", new Date()));
        int d = PanelMenu.dp(getContext(), 18);
        setTranslationX(-random.nextInt(d));
        setTranslationY(-random.nextInt(d));
    }

    /** Fades the given views out (ambient on) or back in. Only their opacity changes. */
    static void fade(boolean ambient, View... views) {
        for (View v : views) {
            if (v != null) {
                v.animate().cancel();
                v.animate().alpha(ambient ? 0f : 1f).setDuration(ambient ? 800 : 300).start();
            }
        }
    }
}

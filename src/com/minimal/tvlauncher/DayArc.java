package com.minimal.tvlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.view.View;

import java.util.Calendar;
import java.util.Date;

/**
 * The launcher follows the real sun over Riga. By day it looks as it always does. From sunset
 * a warm, dim veil comes over it, deeper late at night, and lifts again at dawn. At bedtime,
 * if nobody is using the TV (ambient mode), the screen goes down to black over ten minutes,
 * like blowing out a candle.
 *
 * It's one view laid over the whole launcher, so it works the same for all of them; each
 * launcher has its own strength (the Fireplace and Apartment are evening scenes already).
 */
final class DayArc extends View {

    private static final int WARM = 0x2A1200;            // the colour of the veil: deep amber
    private static final float EVENING_ALPHA = 0.22f;
    private static final float LATE_ALPHA = 0.40f;
    static final long CANDLE_OUT_MS = 10 * 60 * 1000;

    private float strength = 1f;     // per launcher
    private float veil;              // 0..1 of the warm veil, from the clock
    private float candle;            // 0..1 of the fade to black at bedtime
    private ValueAnimator candleAnim;

    DayArc(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setWillNotDraw(false);
    }

    void setStrength(float s) {
        strength = s;
        invalidate();
    }

    /** Call every minute: works out the veil for this time of day. */
    void update(Context context) {
        veil = enabled(context) ? veil(new Date()) : 0;
        invalidate();
    }

    /** At bedtime in ambient mode: down to black over ten minutes. */
    void candleOut() {
        if (candleAnim != null) {
            return;
        }
        candleAnim = ValueAnimator.ofFloat(candle, 1f);
        candleAnim.setDuration(CANDLE_OUT_MS);
        candleAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                candle = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        candleAnim.start();
    }

    /** Someone pressed a button: light again at once. */
    void relight() {
        if (candleAnim != null) {
            candleAnim.cancel();
            candleAnim = null;
        }
        candle = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float a = veil * LATE_ALPHA * strength;   // veil is 0..1 of the late-night veil
        if (a > 0.001f) {
            canvas.drawColor(((int) (a * 255) << 24) | WARM);
        }
        if (candle > 0.001f) {
            canvas.drawColor((int) (candle * 255) << 24);
        }
    }

    // ---- The sun and the clock ----

    static boolean enabled(Context context) {
        return prefs(context).getBoolean("day_arc", true);
    }

    /** The bedtime hour (0-23), or -1 for none. */
    static int bedtime(Context context) {
        return prefs(context).getInt("bedtime", -1);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** True after sunset (and before sunrise). */
    static boolean dark(Date when) {
        return dusk(when) > 0.5f;
    }

    // 0 by day; 1 once the sun is 6° below the horizon (the end of civil twilight).
    private static float dusk(Date when) {
        float alt = Sky.at(when, null).sunAlt;
        float t = Math.max(0, Math.min(1, (0.03f - alt) / (0.03f + 0.105f)));
        return t * t * (3 - 2 * t);
    }

    // 0..1 between 22:30 and 23:30, staying up until 05:00 and gone by 06:00.
    private static float lateness(Date when) {
        Calendar c = Calendar.getInstance();
        c.setTime(when);
        float h = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f;
        if (h >= 22.5f) {
            return Math.min(1, h - 22.5f);
        }
        if (h < 5) {
            return 1;
        }
        if (h < 6) {
            return 6 - h;
        }
        return 0;
    }

    /** The veil's opacity, 0..1 of the full late-night veil. */
    static float veil(Date when) {
        float d = dusk(when);
        float alpha = d * (EVENING_ALPHA + (LATE_ALPHA - EVENING_ALPHA) * lateness(when));
        return alpha / LATE_ALPHA;
    }

    /** True if it is past bedtime now (until 05:00). */
    static boolean pastBedtime(Context context, Date when) {
        int bed = bedtime(context);
        if (bed < 0) {
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(when);
        int h = c.get(Calendar.HOUR_OF_DAY);
        if (bed >= 12) {
            return h >= bed || h < 5;     // e.g. 23:00: from 23:00 until 05:00
        }
        return h >= bed && h < 5;         // e.g. 01:00: from 01:00 until 05:00
    }
}

package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.Random;

/**
 * The Window launcher's glass on a cold day: a faint mist of condensation over the panes,
 * heavier towards the bottom. On the name day or birthday of someone on the phone page's
 * People list, their name is traced in the mist with a finger (with a heart), and on a red
 * day just a heart. Drawn once; it only changes when the day or the weather does.
 */
final class MistView extends View {

    // Where the glass is in the Window videos (see web/window.html), as fractions of the screen.
    private static final float GLASS_L = 0.16f, GLASS_R = 0.84f, GLASS_T = 0.03f, GLASS_B = 0.75f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean misted;
    private String traced = "";

    MistView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clear.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/HankenGrotesk-300.ttf"));
        clear.setTextSize(58 * density);
        setLayerType(LAYER_TYPE_HARDWARE, null);   // so the traced letters clear only the mist
        setFocusable(false);
    }

    /** Misted when it's cold out; {@code text} traced in it ("" for none). */
    void set(boolean misted, String text) {
        if (misted == this.misted && text.equals(traced)) {
            return;
        }
        this.misted = misted;
        traced = text;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!misted) {
            return;
        }
        float w = getWidth(), h = getHeight();
        float l = w * GLASS_L, r = w * GLASS_R, t = h * GLASS_T, b = h * GLASS_B;
        // The mist: thicker low on the glass, with a few softer and denser patches.
        paint.setShader(new LinearGradient(0, t, 0, b, 0x14E8EEF4, 0x3AE8EEF4, Shader.TileMode.CLAMP));
        c.drawRect(l, t, r, b, paint);
        Random rnd = new Random(7);
        for (int i = 0; i < 9; i++) {
            float x = l + rnd.nextFloat() * (r - l), y = t + (0.3f + 0.7f * rnd.nextFloat()) * (b - t);
            float rad = (60 + rnd.nextFloat() * 140) * density;
            paint.setShader(new RadialGradient(x, y, rad, 0x18E8EEF4, 0x00E8EEF4, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, paint);
        }
        paint.setShader(null);
        if (traced.isEmpty()) {
            return;
        }
        // Traced with a finger: the letters wipe the mist away, a little tilted, with drips.
        float tx = r - (r - l) * 0.47f, ty = b - (b - t) * 0.2f;
        c.save();
        c.rotate(-5, tx, ty);
        c.drawText(traced, tx, ty, clear);
        float width = clear.measureText(traced);
        clear.setStrokeWidth(2.2f * density);
        Random drips = new Random(traced.hashCode());
        for (int i = 0; i < 4; i++) {
            float dx = tx + drips.nextFloat() * width;
            c.drawLine(dx, ty + 4 * density, dx, ty + (12 + drips.nextFloat() * 26) * density, clear);
        }
        c.restore();
    }
}

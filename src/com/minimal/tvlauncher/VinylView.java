package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

import java.util.Random;

/**
 * A record turning at 33⅓ rpm with the album cover as its label, over a slowly turning
 * gradient in the cover's colours. The disc, its shadow and the light's sheen are drawn
 * once per track into bitmaps; each frame only rotates the disc, so it stays smooth.
 */
class VinylView extends View {

    private static final float RPM_DEGREES_PER_SECOND = 200f;   // 33⅓ rpm
    private static final float SPIN_EASE_SECONDS = 0.7f;        // like a turntable spinning up/down
    private static final float GRADIENT_TURN_SECONDS = 90f;
    private static final float COLOR_FADE_SECONDS = 1.6f;
    private static final float LABEL_RATIO = 0.44f;             // label diameter / record diameter

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint background = new Paint();
    private final Matrix gradientMatrix = new Matrix();

    private Bitmap cover;
    private Bitmap disc;
    private Bitmap shadow;
    private Bitmap sheen;
    private int builtSize;

    private int[] colors = {0xFF18181B, 0xFF26262B, 0xFF0E0E10};
    private int[] fromColors = colors;
    private int[] toColors = colors;
    private float colorFade = 1f;
    private LinearGradient gradient;

    private boolean playing;
    private boolean running;
    private float speed;        // degrees per second
    private float angle;
    private float gradientAngle;
    private long lastFrame;

    VinylView(Context context) {
        super(context);
        // Holds focus while only the record is showing, so the remote's keys have a home.
        setFocusable(true);
        setDefaultFocusHighlightEnabledCompat();
    }

    private void setDefaultFocusHighlightEnabledCompat() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                View.class.getMethod("setDefaultFocusHighlightEnabled", boolean.class).invoke(this, false);
            } catch (Exception ignored) {
                // Older Android has no focus highlight to turn off.
            }
        }
    }

    /** The album cover for the label, or null for a plain label. */
    void setCover(Bitmap art) {
        cover = art;
        disc = null;
        invalidate();
    }

    /** Fades the background to these colours (most prominent first). */
    void setColors(int[] next) {
        fromColors = colors.clone();
        toColors = next;
        colorFade = 0f;
        invalidate();
    }

    void setPlaying(boolean value) {
        playing = value;
        invalidate();
    }

    /** Animate only while the launcher is on screen. */
    void setRunning(boolean value) {
        running = value;
        lastFrame = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        disc = null;
        gradient = null;
    }

    private int size() {
        return Math.round(Math.min(getWidth(), getHeight()) * 0.78f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min((now - lastFrame) / 1e9f, 0.1f);
        lastFrame = now;
        step(dt);

        drawBackground(canvas);

        int d = size();
        if (disc == null || builtSize != d) {
            build(d);
        }
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawBitmap(shadow, cx - shadow.getWidth() / 2f, cy - shadow.getHeight() / 2f + d * 0.02f, paint);
        canvas.save();
        canvas.rotate(angle, cx, cy);
        canvas.drawBitmap(disc, cx - d / 2f, cy - d / 2f, paint);
        canvas.restore();
        canvas.drawBitmap(sheen, cx - d / 2f, cy - d / 2f, paint);

        if (running) {
            postInvalidateOnAnimation();
        }
    }

    private void step(float dt) {
        float target = playing ? RPM_DEGREES_PER_SECOND : 0f;
        speed += (target - speed) * (1f - (float) Math.exp(-dt / SPIN_EASE_SECONDS * 3f));
        if (!playing && speed < 0.5f) {
            speed = 0f;
        }
        angle = (angle + speed * dt) % 360f;
        gradientAngle = (gradientAngle + dt * 360f / GRADIENT_TURN_SECONDS) % 360f;
        if (colorFade < 1f) {
            colorFade = Math.min(1f, colorFade + dt / COLOR_FADE_SECONDS);
            float t = colorFade * colorFade * (3 - 2 * colorFade);   // smoothstep
            colors = new int[toColors.length];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = lerp(fromColors[Math.min(i, fromColors.length - 1)], toColors[i], t);
            }
            gradient = null;
        }
    }

    // A wide linear gradient through the cover's colours that turns very slowly.
    private void drawBackground(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (gradient == null) {
            float r = (float) Math.hypot(w, h) / 2f;
            int[] stops = colors.length >= 3
                    ? new int[] {colors[1], colors[0], colors[0], colors[2]}
                    : new int[] {colors[0], colors[colors.length - 1]};
            float[] pos = stops.length == 4 ? new float[] {0f, 0.4f, 0.6f, 1f} : null;
            gradient = new LinearGradient(-r, 0, r, 0, stops, pos, Shader.TileMode.CLAMP);
        }
        gradientMatrix.setRotate(gradientAngle);
        gradientMatrix.postTranslate(w / 2f, h / 2f);
        gradient.setLocalMatrix(gradientMatrix);
        background.setShader(gradient);
        canvas.drawRect(0, 0, w, h, background);
    }

    private void build(int d) {
        builtSize = d;
        float r = d / 2f;
        float labelR = r * LABEL_RATIO;

        // The record: black vinyl with fine grooves, a few gaps between tracks, and the label.
        disc = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(disc);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(r, r, r, new int[] {0xFF151517, 0xFF0B0B0C, 0xFF111113},
                new float[] {0f, 0.85f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        Random random = new Random(7);
        float grooveStart = labelR + r * 0.05f;
        float grooveEnd = r * 0.965f;
        for (float gr = grooveStart; gr < grooveEnd; gr += Math.max(1f, d / 600f)) {
            p.setStrokeWidth(1f);
            p.setColor(Color.argb(6 + random.nextInt(12), 255, 255, 255));
            c.drawCircle(r, r, gr, p);
        }
        p.setStrokeWidth(Math.max(2f, d / 300f));
        for (float gap : new float[] {0.58f, 0.69f, 0.78f, 0.88f}) {
            p.setColor(0xFF050506);
            c.drawCircle(r, r, r * gap, p);
        }
        p.setStrokeWidth(Math.max(2f, d / 250f));
        p.setColor(0xFF26262A);
        c.drawCircle(r, r, r - p.getStrokeWidth() / 2f, p);   // rim

        p.setStyle(Paint.Style.FILL);
        if (cover != null) {
            BitmapShader art = new BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float side = Math.min(cover.getWidth(), cover.getHeight());
            Matrix m = new Matrix();
            m.setTranslate(-(cover.getWidth() - side) / 2f, -(cover.getHeight() - side) / 2f);
            m.postScale(2 * labelR / side, 2 * labelR / side);
            m.postTranslate(r - labelR, r - labelR);
            art.setLocalMatrix(m);
            p.setShader(art);
            p.setFilterBitmap(true);
        } else {
            p.setColor(0xFF2A2A2E);
        }
        c.drawCircle(r, r, labelR, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, d / 220f));
        p.setColor(0x66000000);
        c.drawCircle(r, r, labelR, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0A0A0B);
        c.drawCircle(r, r, r * 0.022f, p);   // spindle hole
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0x33FFFFFF);
        c.drawCircle(r, r, r * 0.022f, p);

        // Soft shadow underneath.
        int pad = Math.round(d * 0.08f);
        shadow = Bitmap.createBitmap(d + 2 * pad, d + 2 * pad, Bitmap.Config.ARGB_8888);
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setColor(0x73000000);
        sp.setMaskFilter(new BlurMaskFilter(d * 0.035f, BlurMaskFilter.Blur.NORMAL));
        new Canvas(shadow).drawCircle(r + pad, r + pad, r * 0.99f, sp);

        // The light's reflection on the grooves. It stays put while the record turns.
        sheen = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheen);
        Path ring = new Path();
        ring.setFillType(Path.FillType.EVEN_ODD);
        ring.addCircle(r, r, r * 0.99f, Path.Direction.CW);
        ring.addCircle(r, r, labelR * 1.02f, Path.Direction.CW);
        sc.clipPath(ring);
        Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        int glint = 0x2EFFFFFF;
        shine.setShader(new SweepGradient(r, r,
                new int[] {0, 0, glint, 0, 0, 0, glint, 0, 0},
                new float[] {0f, 0.06f, 0.12f, 0.2f, 0.5f, 0.56f, 0.62f, 0.7f, 1f}));
        sc.drawCircle(r, r, r, shine);
    }

    private static int lerp(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}

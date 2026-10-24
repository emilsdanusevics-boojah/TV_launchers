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
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * A record turning at 33⅓ rpm with the album cover as its label, over a slowly turning
 * gradient in the cover's colours. The disc, its shadow and the light's sheen are drawn
 * once per track into bitmaps; each frame only rotates the disc, so it stays smooth.
 *
 * A tonearm behaves like a real one: when music starts it swings over from its rest and
 * lowers onto the record; pause lifts it in place (like a cue lever); when the music
 * stops it goes back to its rest. While playing, the needle moves from the edge towards
 * the label as the song goes on.
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

    // Tonearm. Geometry is in units of the record's radius, from the record's centre.
    private static final float PIVOT_X = 1.12f, PIVOT_Y = -0.80f, ARM_LENGTH = 1.25f;
    private static final float REST_ANGLE = (float) Math.toRadians(76);
    private static final float NEEDLE_START = 0.93f;   // lead-in groove, as a share of the radius
    private boolean loaded;      // a track is playing or paused
    private float swing;         // 0 = on its rest, 1 = over the record
    private float lift = 0f;     // 0 = down, 1 = raised
    private float trackAngle;    // where the needle is on the record
    private float startAngle;    // needle on the lead-in groove
    private float endAngle;      // needle near the label
    private long positionMs, positionAt, durationMs;
    private float playbackSpeed;
    private final Paint armPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Shader tubeShader, weightShader, baseShader, gimbalShader;
    private float armBuiltFor;   // record radius the arm's shading was made for

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

    /** Whether a track is loaded (playing or paused). With nothing loaded the arm rests. */
    void setLoaded(boolean value) {
        loaded = value;
        invalidate();
    }

    /** Playback position, for moving the needle across the record as the song plays. */
    void setTiming(long position, long updatedAt, float speed, long duration) {
        positionMs = position;
        positionAt = updatedAt;
        playbackSpeed = speed;
        durationMs = duration;
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
        drawArm(canvas, cx, cy, d / 2f);

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
        stepArm(dt);
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

    // Moves the arm one frame towards where it should be. It only swings while raised, and
    // only lowers once it is over the record (or back on its rest).
    private void stepArm(float dt) {
        float targetSwing = loaded ? 1f : 0f;
        boolean swinging = Math.abs(swing - targetSwing) > 0.002f;
        float targetLift;
        if (swinging) {
            targetLift = 1f;                       // raise before moving
        } else if (loaded) {
            targetLift = playing ? 0f : 1f;        // play lowers, pause lifts (cue lever)
        } else {
            targetLift = 0f;                       // settle onto the rest
        }
        float liftTime = targetLift < lift ? 0.32f : 0.12f;   // lowering is slow and damped
        lift = approach(lift, targetLift, dt, liftTime);
        if (swinging && lift > 0.97f) {
            swing = approach(swing, targetSwing, dt, 0.38f);
        }

        // Needle position from how far through the song we are.
        float progress = 0.15f;
        if (durationMs > 0) {
            long pos = positionMs;
            if (playing && positionAt > 0) {
                pos += (long) ((SystemClock.elapsedRealtime() - positionAt) * playbackSpeed);
            }
            progress = Math.max(0f, Math.min(1f, pos / (float) durationMs));
        }
        float target = startAngle + (endAngle - startAngle) * progress;
        trackAngle = trackAngle == 0f ? target : approach(trackAngle, target, dt, 0.45f);
    }

    private static float approach(float value, float target, float dt, float seconds) {
        float next = value + (target - value) * (1f - (float) Math.exp(-dt / seconds));
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    // The arm angle (radians, screen coordinates) at which the needle is this far from the
    // record's centre. The needle's distance shrinks steadily from 90° to 150°.
    private static float angleForRadius(float rho) {
        float lo = (float) Math.toRadians(90), hi = (float) Math.toRadians(150);
        for (int i = 0; i < 40; i++) {
            float mid = (lo + hi) / 2f;
            double x = PIVOT_X + ARM_LENGTH * Math.cos(mid), y = PIVOT_Y + ARM_LENGTH * Math.sin(mid);
            if (Math.hypot(x, y) > rho) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2f;
    }

    private void buildArm(float r) {
        startAngle = angleForRadius(NEEDLE_START);
        endAngle = angleForRadius(LABEL_RATIO * 1.12f);
        float tube = 0.018f * r;
        tubeShader = new LinearGradient(0, -tube, 0, tube,
                new int[] {0xFFF4F4F6, 0xFFB9B9C0, 0xFF6E6E76}, new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        float w = 0.065f * r;
        weightShader = new LinearGradient(0, -w, 0, w,
                new int[] {0xFF5A5A60, 0xFF2A2A2E, 0xFF121214}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        baseShader = new RadialGradient(-0.03f * r, -0.03f * r, 0.15f * r,
                new int[] {0xFF3A3A40, 0xFF1C1C20, 0xFF0E0E10}, new float[] {0f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        gimbalShader = new RadialGradient(-0.02f * r, -0.02f * r, 0.08f * r,
                new int[] {0xFFF0F0F2, 0xFFA0A0A8, 0xFF5E5E66}, new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
    }

    private void drawArm(Canvas canvas, float cx, float cy, float r) {
        if (armBuiltFor != r) {
            buildArm(r);
            armBuiltFor = r;
        }
        if (trackAngle == 0f) {
            trackAngle = startAngle;
        }
        float px = cx + PIVOT_X * r;
        float py = cy + PIVOT_Y * r;
        float s = swing * swing * (3 - 2 * swing);   // ease in and out
        float theta = REST_ANGLE + (trackAngle - REST_ANGLE) * s;
        float degrees = (float) Math.toDegrees(theta);

        // Base the arm turns on (it doesn't move).
        armPaint.setShader(null);
        armShadow.setColor(0x59000000);
        canvas.drawCircle(px + 0.02f * r, py + 0.03f * r, 0.15f * r, armShadow);
        canvas.save();
        canvas.translate(px, py);
        armPaint.setShader(baseShader);
        canvas.drawCircle(0, 0, 0.15f * r, armPaint);
        armPaint.setShader(null);
        armPaint.setStyle(Paint.Style.STROKE);
        armPaint.setStrokeWidth(Math.max(1.5f, 0.006f * r));
        armPaint.setColor(0x33FFFFFF);
        canvas.drawCircle(0, 0, 0.11f * r, armPaint);
        armPaint.setStyle(Paint.Style.FILL);
        canvas.restore();

        // The rest the arm sits on when not playing.
        float restX = px + (float) Math.cos(REST_ANGLE) * ARM_LENGTH * 0.78f * r;
        float restY = py + (float) Math.sin(REST_ANGLE) * ARM_LENGTH * 0.78f * r;
        canvas.drawCircle(restX + 0.012f * r, restY + 0.018f * r, 0.035f * r, armShadow);
        armPaint.setShader(baseShader);
        canvas.save();
        canvas.translate(restX, restY);
        canvas.drawCircle(0, 0, 0.035f * r, armPaint);
        canvas.restore();
        armPaint.setShader(null);

        // Shadow: further away and fainter as the arm rises.
        armShadow.setColor(Color.argb(Math.round(95 - 35 * lift), 0, 0, 0));
        canvas.save();
        canvas.translate(px + (0.02f + 0.05f * lift) * r, py + (0.03f + 0.07f * lift) * r);
        canvas.rotate(degrees);
        armShape(canvas, r, armShadow, true);
        canvas.restore();

        // The arm itself, a touch larger when raised (closer to the eye).
        float scale = 1f + 0.03f * lift;
        canvas.save();
        canvas.translate(px, py);
        canvas.rotate(degrees);
        canvas.scale(scale, scale);
        armShape(canvas, r, armPaint, false);
        canvas.restore();
    }

    // Drawn along +x from the pivot: counterweight, gimbal, tube, headshell with the needle at x = L.
    private void armShape(Canvas canvas, float r, Paint p, boolean silhouette) {
        float L = ARM_LENGTH * r;
        // Counterweight behind the pivot.
        rect.set(-0.30f * r, -0.065f * r, -0.10f * r, 0.065f * r);
        if (!silhouette) {
            p.setShader(weightShader);
        }
        canvas.drawRoundRect(rect, 0.02f * r, 0.02f * r, p);
        // Tube.
        rect.set(-0.08f * r, -0.018f * r, 0.88f * L, 0.018f * r);
        if (!silhouette) {
            p.setShader(tubeShader);
        }
        canvas.drawRoundRect(rect, 0.018f * r, 0.018f * r, p);
        // Gimbal at the pivot.
        if (!silhouette) {
            p.setShader(gimbalShader);
        }
        canvas.drawCircle(0, 0, 0.075f * r, p);
        // Headshell, angled like a real one, with the needle at its tip on the arm's line.
        canvas.save();
        canvas.translate(L, 0);
        canvas.rotate(-18);
        rect.set(-0.17f * r, -0.035f * r, 0.012f * r, 0.035f * r);
        if (!silhouette) {
            p.setShader(null);
            p.setColor(0xFF1B1B1E);
        }
        canvas.drawRoundRect(rect, 0.012f * r, 0.012f * r, p);
        // Finger lift.
        rect.set(-0.12f * r, -0.075f * r, -0.105f * r, -0.03f * r);
        if (!silhouette) {
            p.setColor(0xFFB9B9C0);
        }
        canvas.drawRect(rect, p);
        canvas.restore();
        if (!silhouette) {
            p.setShader(null);
        }
    }

    private static int lerp(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}

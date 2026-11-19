package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Weather and holidays on the Photos launcher's glass, laid over the photos:
 *   rain        drops bead on the screen and now and then one runs down
 *   snow        flakes drift past (also on the Christmas days)
 *   below 0°    frost creeps in from the corners over a minute and stays
 *   Jāņi        embers rise from the bonfire
 *   New Year's  gold sparkles twinkle
 *   spring days petals drift down; carmine and white ones on 4 May and 18 November
 *
 * Drawn with a few dozen small sprites at 30 frames a second, and nothing at all when there's
 * nothing moving; it stops whenever the launcher is hidden.
 */
final class PhotoEffects extends View {

    private static final long FRAME_MS = 33;
    private static final int NONE = 0, RAIN = 1, SNOW = 2, EMBERS = 3, SPARKLES = 4, PETALS = 5, FLAG = 6;

    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final List<float[]> parts = new ArrayList<float[]>();   // per effect: x, y, size, speed, phase, life...
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final float density;

    private int effect = NONE;
    private String weatherCode;
    private double tempC = 99;
    private String holiday;
    private boolean running;
    private long lastNs;
    private Bitmap drop;
    private Bitmap frost;
    private float frostAlpha;
    private boolean frosty;
    private int heaviness = 1;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (running && isAnimating()) {
                invalidate();
                postDelayed(this, FRAME_MS);
            }
        }
    };

    PhotoEffects(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
    }

    static boolean enabled(Context context) {
        return context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean("photo_effects", true);
    }

    void setWeather(Weather.Now now) {
        weatherCode = now == null ? null : now.code;
        tempC = now == null ? 99 : now.tempC;
        choose();
    }

    /** Today's holiday id, or null. */
    void setHoliday(String id) {
        holiday = id;
        choose();
    }

    void refresh() {
        choose();
    }

    void start() {
        running = true;
        lastNs = 0;
        choose();
        kick();
    }

    void stop() {
        running = false;
        removeCallbacks(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private boolean isAnimating() {
        return effect != NONE || (frosty && frostAlpha < 1);
    }

    private void kick() {
        removeCallbacks(frame);
        if (running && isAnimating()) {
            post(frame);
        }
        invalidate();
    }

    private void choose() {
        int e = NONE;
        heaviness = 1;
        boolean on = enabled(getContext());
        if (on && holiday != null) {
            switch (holiday) {
                case "ligo": case "jani": e = EMBERS; break;
                case "new_year": case "old_year": e = SPARKLES; break;
                case "christmas_eve": case "christmas": case "second_christmas": e = SNOW; break;
                case "independence": case "proclamation": e = FLAG; break;
                case "may_day": case "mothers_day": case "pentecost": case "easter": case "easter_monday": e = PETALS; break;
                default: break;
            }
        }
        if (on && e == NONE && weatherCode != null) {
            switch (weatherCode) {
                case "drizzle": e = RAIN; heaviness = 0; break;
                case "rain": case "sleet": e = RAIN; break;
                case "heavy_rain": case "thunder": e = RAIN; heaviness = 2; break;
                case "snow": e = SNOW; break;
                default: break;
            }
        }
        boolean wasFrosty = frosty;
        frosty = on && tempC < 0;
        if (frosty && !wasFrosty) {
            frostAlpha = 0;
        }
        if (e != effect) {
            effect = e;
            parts.clear();
        }
        kick();
    }

    // ---- Drawing ----

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        if (frosty) {
            frostAlpha = Math.min(1, frostAlpha + dt / 60f);
            drawFrost(canvas, w, h);
        }
        switch (effect) {
            case RAIN: rain(canvas, w, h, dt); break;
            case SNOW: snow(canvas, w, h, dt); break;
            case EMBERS: embers(canvas, w, h, dt); break;
            case SPARKLES: sparkles(canvas, w, h, dt); break;
            case PETALS: petals(canvas, w, h, dt, false); break;
            case FLAG: petals(canvas, w, h, dt, true); break;
            default: break;
        }
    }

    private float dp(float v) {
        return v * density;
    }

    // Beads: [x, y, radius, age, lifetime, runner speed (0 = still)]
    private void rain(Canvas c, int w, int h, float dt) {
        if (drop == null) {
            drop = makeDrop();
        }
        int target = heaviness == 0 ? 28 : heaviness == 1 ? 55 : 85;
        if (parts.size() < target && random.nextFloat() < dt * (4 + heaviness * 4)) {
            parts.add(new float[] {random.nextFloat() * w, random.nextFloat() * h,
                    dp(2.5f + random.nextFloat() * (heaviness == 0 ? 3 : 6)), 0, 20 + random.nextFloat() * 40, 0});
        }
        src.set(0, 0, drop.getWidth(), drop.getHeight());
        int runners = 0;
        for (int i = parts.size() - 1; i >= 0; i--) {
            float[] p = parts.get(i);
            p[3] += dt;
            if (p[5] > 0) {
                runners++;
                p[1] += p[5] * dt;
                p[0] += (random.nextFloat() - 0.5f) * dp(20) * dt;
                p[5] = Math.max(dp(30), p[5] + (random.nextFloat() - 0.45f) * dp(200) * dt);
                if (random.nextFloat() < dt * 3) {   // a little trail behind it
                    parts.add(new float[] {p[0], p[1] - p[2] * 2, p[2] * 0.4f, 0, 6 + random.nextFloat() * 6, 0});
                }
                if (p[1] - p[2] > h) {
                    parts.remove(i);
                    continue;
                }
            } else if (p[3] > p[4]) {
                // Big old drops sometimes let go and run; the rest dry up.
                if (p[2] > dp(5) && runners < 3 && random.nextFloat() < 0.5f) {
                    p[5] = dp(60);
                    p[3] = 0;
                } else {
                    parts.remove(i);
                    continue;
                }
            }
            float a = Math.min(1, p[3] / 1.5f);
            if (p[5] == 0 && p[4] - p[3] < 2) {
                a = Math.max(0, (p[4] - p[3]) / 2);
            }
            paint.setAlpha((int) (a * 230));
            float r = p[2];
            rect.set(p[0] - r, p[1] - r * (p[5] > 0 ? 1.25f : 1), p[0] + r, p[1] + r);
            c.drawBitmap(drop, src, rect, paint);
        }
        paint.setAlpha(255);
    }

    // A water drop: clear in the middle, a dark rim at the bottom, light caught at the top.
    private static Bitmap makeDrop() {
        int s = 64;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(s * 0.5f, s * 0.42f, s * 0.5f,
                new int[] {0x18FFFFFF, 0x10FFFFFF, 0x66000000, 0x00000000},
                new float[] {0f, 0.62f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        p.setShader(new RadialGradient(s * 0.5f, s * 0.75f, s * 0.3f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(s / 2f, s * 0.72f, s * 0.28f, p);   // light through the drop
        p.setShader(null);
        p.setColor(0xD8FFFFFF);
        c.drawOval(new RectF(s * 0.30f, s * 0.20f, s * 0.46f, s * 0.32f), p);   // highlight
        return b;
    }

    // Flakes: [x, y, radius, speed, phase]
    private void snow(Canvas c, int w, int h, float dt) {
        while (parts.size() < 70) {
            parts.add(new float[] {random.nextFloat() * w, parts.isEmpty() || random.nextBoolean() ? random.nextFloat() * h : -dp(10),
                    dp(1.2f + random.nextFloat() * 2.8f), 0, random.nextFloat() * 6.28f});
        }
        for (float[] p : parts) {
            p[3] = p[2] * 12;   // bigger flakes are nearer and fall faster
            p[4] += dt;
            p[1] += p[3] * dt;
            p[0] += (float) Math.sin(p[4] * 0.8f + p[2]) * dp(14) * dt;
            if (p[1] > h + dp(10)) {
                p[1] = -dp(10);
                p[0] = random.nextFloat() * w;
            }
            paint.setColor(Color.argb((int) (120 + p[2] / dp(4) * 120), 255, 255, 255));
            c.drawCircle(p[0], p[1], p[2], paint);
        }
    }

    // Embers: [x, y, radius, speed, phase, life]
    private void embers(Canvas c, int w, int h, float dt) {
        while (parts.size() < 45) {
            parts.add(new float[] {random.nextFloat() * w, h + random.nextFloat() * h * 0.5f,
                    dp(1 + random.nextFloat() * 2.2f), dp(30 + random.nextFloat() * 50), random.nextFloat() * 6.28f, 0});
        }
        for (float[] p : parts) {
            p[4] += dt;
            p[1] -= p[3] * dt;
            p[0] += (float) Math.sin(p[4] * 1.7f) * dp(22) * dt;
            float fade = Math.max(0, Math.min(1, p[1] / (h * 0.7f)));   // dying out as they rise
            if (p[1] < -dp(10) || fade <= 0) {
                p[1] = h + dp(10);
                p[0] = random.nextFloat() * w;
                continue;
            }
            float flicker = 0.6f + 0.4f * (float) Math.sin(p[4] * 9 + p[2]);
            int a = (int) (fade * flicker * 255);
            paint.setColor(Color.argb(a / 4, 255, 120, 30));
            c.drawCircle(p[0], p[1], p[2] * 3, paint);
            paint.setColor(Color.argb(a, 255, 190, 90));
            c.drawCircle(p[0], p[1], p[2], paint);
        }
    }

    // Sparkles: [x, y, size, age, lifetime]
    private void sparkles(Canvas c, int w, int h, float dt) {
        while (parts.size() < 30) {
            parts.add(new float[] {random.nextFloat() * w, random.nextFloat() * h * 0.75f,
                    dp(3 + random.nextFloat() * 6), -random.nextFloat() * 3, 1.2f + random.nextFloat() * 1.2f});
        }
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (float[] p : parts) {
            p[3] += dt;
            if (p[3] > p[4]) {
                p[0] = random.nextFloat() * w;
                p[1] = random.nextFloat() * h * 0.75f;
                p[3] = -random.nextFloat() * 2;
            }
            if (p[3] < 0) {
                continue;
            }
            float t = p[3] / p[4];
            float k = (float) Math.sin(t * Math.PI);
            float r = p[2] * k;
            paint.setColor(Color.argb((int) (k * 230), 255, 214, 120));
            paint.setStrokeWidth(dp(1.5f));
            c.drawLine(p[0] - r, p[1], p[0] + r, p[1], paint);
            c.drawLine(p[0], p[1] - r, p[0], p[1] + r, paint);
            paint.setColor(Color.argb((int) (k * 255), 255, 250, 225));
            c.drawCircle(p[0], p[1], dp(1.4f) * k + dp(0.5f), paint);
        }
    }

    // Petals: [x, y, size, speed, phase, spin, colour index]
    private void petals(Canvas c, int w, int h, float dt, boolean flag) {
        int[] colours = flag ? new int[] {0xE69E3039, 0xE6F2EEE8} : new int[] {0xE6F6C9D3, 0xE6FFF2F2, 0xE6F2B6C6};
        while (parts.size() < 26) {
            parts.add(new float[] {random.nextFloat() * w, parts.size() < 13 ? random.nextFloat() * h : -dp(20),
                    dp(5 + random.nextFloat() * 5), dp(25 + random.nextFloat() * 25), random.nextFloat() * 6.28f,
                    random.nextFloat() * 360, random.nextInt(colours.length)});
        }
        for (float[] p : parts) {
            p[4] += dt;
            p[1] += p[3] * dt;
            p[0] += (float) Math.sin(p[4] * 0.9f) * dp(30) * dt;
            p[5] += dt * 60;
            if (p[1] > h + dp(20)) {
                p[1] = -dp(20);
                p[0] = random.nextFloat() * w;
            }
            paint.setColor(colours[(int) p[6] % colours.length]);
            c.save();
            c.translate(p[0], p[1]);
            c.rotate(p[5]);
            float squash = 0.35f + 0.65f * Math.abs((float) Math.cos(p[4] * 1.3f));   // tumbling
            rect.set(-p[2], -p[2] * 0.55f * squash, p[2], p[2] * 0.55f * squash);
            c.drawOval(rect, paint);
            c.restore();
        }
    }

    // ---- Frost ----

    private void drawFrost(Canvas c, int w, int h) {
        if (frost == null) {
            frost = makeFrost(new Random(19));
        }
        paint.setAlpha((int) (frostAlpha * 200));
        src.set(0, 0, frost.getWidth(), frost.getHeight());
        rect.set(0, 0, w, h);
        c.drawBitmap(frost, src, rect, paint);
        paint.setAlpha(255);
    }

    // Ice ferns growing in from the edges and thickest in the corners, drawn once at low size.
    private static Bitmap makeFrost(Random r) {
        int w = 480, h = 270;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        // A pale haze in the corners.
        float[][] corners = {{0, 0}, {w, 0}, {0, h}, {w, h}};
        for (float[] k : corners) {
            p.setShader(new RadialGradient(k[0], k[1], 140, 0x70E8F2FF, 0x00E8F2FF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        p.setShader(null);
        for (int i = 0; i < 70; i++) {
            float[] k = corners[i % 4];
            // Start near a corner or along an edge, and grow inwards.
            float x, y;
            if (r.nextBoolean()) {
                x = k[0] + (k[0] == 0 ? 1 : -1) * r.nextFloat() * 90;
                y = k[1] + (k[1] == 0 ? 1 : -1) * r.nextFloat() * 20;
            } else {
                x = k[0] + (k[0] == 0 ? 1 : -1) * r.nextFloat() * 20;
                y = k[1] + (k[1] == 0 ? 1 : -1) * r.nextFloat() * 70;
            }
            double angle = Math.atan2(h / 2f - y, w / 2f - x) + (r.nextFloat() - 0.5f) * 1.6f;
            fern(c, p, r, x, y, angle, 26 + r.nextFloat() * 36, 3);
        }
        return b;
    }

    private static void fern(Canvas c, Paint p, Random r, float x, float y, double angle, float length, int depth) {
        float x2 = x + (float) Math.cos(angle) * length;
        float y2 = y + (float) Math.sin(angle) * length;
        p.setColor(Color.argb(90 + depth * 35, 236, 244, 255));
        p.setStrokeWidth(0.5f + depth * 0.35f);
        c.drawLine(x, y, x2, y2, p);
        if (depth == 0) {
            return;
        }
        int branches = 3 + r.nextInt(3);
        for (int i = 1; i <= branches; i++) {
            float t = i / (float) (branches + 1);
            float bx = x + (x2 - x) * t;
            float by = y + (y2 - y) * t;
            float len = length * (0.45f - t * 0.2f);
            fern(c, p, r, bx, by, angle + 0.9f, len, depth - 1);
            fern(c, p, r, bx, by, angle - 0.9f, len, depth - 1);
        }
    }
}

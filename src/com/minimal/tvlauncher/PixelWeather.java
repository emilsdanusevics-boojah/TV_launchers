package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import java.util.Random;

/**
 * Pixel-art weather for the Monkey Island launcher, drawn on the same low-resolution grid as
 * the scenes so every drop and flake is a crisp block: slanted rain streaks when it rains in
 * Riga, slow drifting flakes when it snows, nothing otherwise. Used over the launcher's scene
 * (as a view) and inside the pirate's ambient walk (drawn straight onto its grid).
 */
final class PixelWeather {

    static final int NONE = 0, RAIN = 1, SNOW = 2;

    private final Random random = new Random();
    private final Paint paint = new Paint();
    private float[] x = new float[0];
    private float[] y = new float[0];
    private float[] speed = new float[0];
    private int kind = NONE;
    private int gw, gh;

    /** RAIN, SNOW or NONE for this weather. */
    static int kindFor(Weather.Now w) {
        String code = w == null || w.code == null ? "" : w.code;
        if (code.equals("snow")) {
            return SNOW;
        }
        if (code.contains("rain") || code.equals("drizzle") || code.equals("thunder") || code.equals("sleet")) {
            return RAIN;
        }
        return NONE;
    }

    int kind() {
        return kind;
    }

    void setKind(int k) {
        if (k != kind) {
            kind = k;
            x = new float[0];   // new particles next frame
        }
    }

    /** Moves everything on by {@code dt} seconds and draws it on a grid {@code w} by {@code h}. */
    void draw(Canvas c, int w, int h, float dt) {
        if (kind == NONE || w <= 0 || h <= 0) {
            return;
        }
        int n = kind == RAIN ? w / 4 : w / 6;
        if (x.length != n || gw != w || gh != h) {
            gw = w;
            gh = h;
            x = new float[n];
            y = new float[n];
            speed = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = random.nextFloat() * w;
                y[i] = random.nextFloat() * h;
                speed[i] = kind == RAIN ? 140 + random.nextFloat() * 60 : 10 + random.nextFloat() * 14;
            }
        }
        for (int i = 0; i < n; i++) {
            y[i] += speed[i] * dt;
            if (kind == RAIN) {
                x[i] -= speed[i] * dt * 0.25f;   // slanting in the wind
            } else {
                x[i] += (float) Math.sin((y[i] + i * 7) * 0.08f) * 6 * dt;
            }
            if (y[i] > h + 4) {
                y[i] = -4;
                x[i] = random.nextFloat() * (w + 20);
            }
            if (x[i] < -4) {
                x[i] += w + 8;
            }
            int px = Math.round(x[i]), py = Math.round(y[i]);
            if (kind == RAIN) {
                paint.setColor(i % 3 == 0 ? 0xB0C8D8F0 : 0x80A8B8D8);
                // A three-pixel streak, stepping one pixel left every two down.
                c.drawRect(px, py, px + 1, py + 2, paint);
                c.drawRect(px - 1, py + 2, px, py + 3, paint);
            } else {
                paint.setColor(i % 4 == 0 ? 0xFFFFFFFF : 0xD8E8EEF8);
                int s = i % 5 == 0 ? 2 : 1;   // a few nearer, bigger flakes
                c.drawRect(px, py, px + s, py + s, paint);
            }
        }
    }

    /** The weather as a view over the launcher's scene, at the launcher's pixel scale. */
    static final class Overlay extends View {
        private final PixelWeather weather = new PixelWeather();
        private final int unit;
        private final Paint blit = new Paint();
        private final Rect dst = new Rect();
        private Bitmap grid;
        private Canvas gridCanvas;
        private long lastNs;
        private boolean running;

        Overlay(Context context) {
            super(context);
            unit = Pixel.unit(context);
            blit.setFilterBitmap(false);
            setFocusable(false);
        }

        void setWeather(Weather.Now w) {
            weather.setKind(kindFor(w));
            invalidate();
        }

        void setRunning(boolean on) {
            running = on;
            lastNs = 0;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (weather.kind() == NONE || !running) {
                return;
            }
            int gw = Math.max(1, getWidth() / unit), gh = Math.max(1, getHeight() / unit);
            if (grid == null || grid.getWidth() != gw || grid.getHeight() != gh) {
                grid = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
                gridCanvas = new Canvas(grid);
            }
            long now = System.nanoTime();
            float dt = lastNs == 0 ? 0 : Math.min(0.2f, (now - lastNs) / 1e9f);
            lastNs = now;
            grid.eraseColor(0);
            weather.draw(gridCanvas, gw, gh, dt);
            dst.set(0, 0, gw * unit, gh * unit);
            canvas.drawBitmap(grid, null, dst, blit);
            postInvalidateDelayed(66);   // fifteen frames a second is plenty for pixel weather
        }
    }
}

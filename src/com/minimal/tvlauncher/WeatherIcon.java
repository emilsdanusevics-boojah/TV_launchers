package com.minimal.tvlauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * A small line-drawn weather symbol for one of Weather's scene codes (clear, partly,
 * cloudy, overcast, fog, drizzle, rain, heavy_rain, snow, sleet, thunder), with a moon
 * instead of the sun at night. Drawn in one colour, sized by its bounds.
 */
final class WeatherIcon extends Drawable {

    private final String code;
    private final boolean night;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    WeatherIcon(String code, boolean night, int color) {
        this.code = code == null ? "cloudy" : code;
        this.night = night;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(color);
        fill.setColor(color);
    }

    @Override
    public void draw(Canvas c) {
        float s = Math.min(getBounds().width(), getBounds().height());
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.scale(s / 24f, s / 24f);   // drawn on a 24 × 24 grid
        line.setStrokeWidth(1.8f);

        boolean clear = code.equals("clear");
        boolean partly = code.equals("partly");
        if (clear) {
            body(c, 12, 12, 4.5f);
        } else if (partly) {
            body(c, 8.5f, 8.5f, 3.6f);
        }
        if (!clear) {
            if (code.equals("fog")) {
                c.drawLine(4, 9, 20, 9, line);
                c.drawLine(3, 13, 21, 13, line);
                c.drawLine(5, 17, 19, 17, line);
            } else {
                boolean small = partly;
                cloud(c, small ? 3.5f : 0, small ? 3 : 0, small ? 0.85f : 1);
            }
        }
        if (code.equals("drizzle") || code.equals("rain") || code.equals("heavy_rain") || code.equals("sleet")) {
            int drops = code.equals("heavy_rain") ? 3 : code.equals("drizzle") ? 1 : 2;
            for (int i = 0; i < drops; i++) {
                float x = 9 + i * 3.5f - (drops - 1) * 0.5f;
                c.drawLine(x, 17.5f, x - 1.2f, 21, line);
            }
        }
        if (code.equals("snow") || code.equals("sleet")) {
            float[] xs = code.equals("snow") ? new float[] {8.5f, 12, 15.5f} : new float[] {15.5f};
            for (float x : xs) {
                c.drawCircle(x, 19.5f, 1.1f, fill);
            }
        }
        if (code.equals("thunder")) {
            Path bolt = new Path();
            bolt.moveTo(13, 15);
            bolt.lineTo(10.5f, 19);
            bolt.lineTo(13.5f, 19);
            bolt.lineTo(11, 23);
            c.drawPath(bolt, line);
        }
        c.restore();
    }

    // The sun (rays around a disc) or, at night, a crescent moon.
    private void body(Canvas c, float cx, float cy, float r) {
        if (night) {
            Path moon = new Path();
            moon.addCircle(cx, cy, r + 1, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(cx + r * 0.8f, cy - r * 0.6f, r, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            c.drawPath(moon, line);
            return;
        }
        c.drawCircle(cx, cy, r, line);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float x0 = cx + (float) Math.cos(a) * (r + 2), y0 = cy + (float) Math.sin(a) * (r + 2);
            float x1 = cx + (float) Math.cos(a) * (r + 4), y1 = cy + (float) Math.sin(a) * (r + 4);
            c.drawLine(x0, y0, x1, y1, line);
        }
    }

    // A cloud: three bumps on a flat base, moved and scaled.
    private void cloud(Canvas c, float dx, float dy, float k) {
        c.save();
        c.translate(dx, dy);
        c.scale(k, k, 12, 12);
        Path p = new Path();
        p.moveTo(7, 16);
        p.arcTo(new RectF(3, 10, 9, 16), 90, 180);
        p.arcTo(new RectF(7, 5, 15, 13), 180, 180);
        p.arcTo(new RectF(13, 8.5f, 20.5f, 16), 260, 190);
        p.close();
        c.drawPath(p, line);
        c.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        line.setAlpha(alpha);
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        line.setColorFilter(cf);
        fill.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

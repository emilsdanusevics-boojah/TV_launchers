package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * "Fireside listening": while music plays over the Fireplace or the Window launcher, a small
 * record turns in the corner with the cover on its label and the track beside it, instead of
 * the full Vinyl screen taking over. Only this little view redraws, and only while playing.
 */
final class MiniRecord extends View {

    private static final float RPM = 33.3f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint artistPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final float density;
    private Bitmap art;
    private BitmapShader artShader;
    private int labelColor = 0xFF8A6F4D;
    private String title = "";
    private String artist = "";
    private boolean playing;
    private float angle;
    private long lastNs;

    MiniRecord(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        Typeface medium = Typeface.createFromAsset(context.getAssets(), "fonts/HankenGrotesk-500.ttf");
        titlePaint.setTypeface(medium);
        titlePaint.setTextSize(17 * density);
        titlePaint.setColor(PanelMenu.FG);
        titlePaint.setShadowLayer(4 * density, 0, density, 0xAA000000);
        artistPaint.setTypeface(medium);
        artistPaint.setTextSize(14 * density);
        artistPaint.setColor(PanelMenu.MUTED);
        artistPaint.setShadowLayer(4 * density, 0, density, 0xAA000000);
        setAlpha(0f);
        setVisibility(GONE);
    }

    void setTrack(Bitmap cover, int[] colors, String title, String artist) {
        art = cover;
        artShader = cover == null ? null : new BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        if (colors != null && colors.length > 0) {
            labelColor = colors[0];
        }
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        invalidate();
    }

    void setText(String title, String artist) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        invalidate();
    }

    void setPlaying(boolean on) {
        playing = on;
        lastNs = 0;
        invalidate();
    }

    void show(boolean on) {
        animate().cancel();
        if (on) {
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(450).start();
        } else {
            animate().alpha(0f).setDuration(450).withEndAction(new Runnable() {
                @Override
                public void run() {
                    setVisibility(GONE);
                }
            }).start();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight();
        float w = getWidth();
        float r = h / 2f - 4 * density;
        float cx = w - r - 4 * density;
        float cy = h / 2f;

        long now = System.nanoTime();
        if (playing && lastNs != 0) {
            angle = (angle + (now - lastNs) / 1e9f * RPM / 60f * 360f) % 360f;
        }
        lastNs = now;

        // The record: a black disc with a sheen and grooves.
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0C0C0E);
        c.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, density * 1.2f));
        paint.setColor(0x40FFFFFF);
        c.drawCircle(cx, cy, r, paint);   // a faint rim, so it reads against the fire
        paint.setStrokeWidth(Math.max(1, density * 0.6f));
        for (float g = r * 0.42f; g < r * 0.96f; g += r * 0.06f) {
            paint.setColor(0x16FFFFFF);
            c.drawCircle(cx, cy, g, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        // The label: the cover, turning with the record.
        float lr = r * 0.38f;
        c.save();
        c.rotate(angle, cx, cy);
        if (artShader != null) {
            float s = Math.max(2 * lr / art.getWidth(), 2 * lr / art.getHeight());
            matrix.setScale(s, s);
            matrix.postTranslate(cx - art.getWidth() * s / 2, cy - art.getHeight() * s / 2);
            artShader.setLocalMatrix(matrix);
            paint.setShader(artShader);
        } else {
            paint.setColor(labelColor);
        }
        c.drawCircle(cx, cy, lr, paint);
        paint.setShader(null);
        // A glint that stays put while the record turns under it.
        c.restore();
        paint.setColor(0x14FFFFFF);
        rect.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(rect, 200, 50, true, paint);
        paint.setColor(0xFF1A1A1C);
        c.drawCircle(cx, cy, r * 0.04f, paint);

        // The track, to the left of the record.
        float textRight = cx - r - 16 * density;
        float avail = textRight - 4 * density;
        if (avail > 40 * density) {
            String t = TextUtils.ellipsize(title, titlePaint, avail, TextUtils.TruncateAt.END).toString();
            String a = TextUtils.ellipsize(artist, artistPaint, avail, TextUtils.TruncateAt.END).toString();
            c.drawText(t, textRight - titlePaint.measureText(t), cy - 3 * density, titlePaint);
            c.drawText(a, textRight - artistPaint.measureText(a), cy + 18 * density, artistPaint);
        }

        if (playing && getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }
}

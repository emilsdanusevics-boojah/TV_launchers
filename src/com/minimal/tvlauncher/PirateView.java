package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Monkey Island launcher's ambient mode: a pirate (an original character, drawn by
 * make_pirate.py) strolls through a string of full-screen scenes along a coast (drawn by
 * make_scenes.py). He walks off one side of the screen into the next place, now and then
 * stops to look around, and sometimes says something (on red days, about the holiday).
 *
 * Like the rest of the launcher it is drawn on the 320-wide pixel grid and scaled up without
 * smoothing. It only redraws a few times a second, which is all a walk cycle needs.
 */
final class PirateView extends View {

    private static final int[] SCENES = {
        R.drawable.scene_harbour, R.drawable.scene_jungle, R.drawable.scene_beach, R.drawable.scene_town,
    };
    private static final int SCENE_H = 180;
    private static final int GROUND = 168;        // row his feet are on, in the scene art
    private static final int FRAME_W = 24;
    private static final int FRAME_H = 40;
    private static final int WALK_FRAMES = 6;
    private static final int STAND = 6;
    private static final int BLINK = 7;
    private static final long TICK_MS = 125;      // eight animation frames a second
    private static final int STEP = 3;            // grid pixels per walk frame
    private static final int FADE_TICKS = 3;      // through black between scenes
    private static final int SPEECH_COLOR = 0xFFFFFFFF;
    private static final int OUTLINE = 0xFF000000;
    private static final String[] MUTTERINGS = {
        "I wonder what's past the next screen.",
        "Still no treasure. Onward!",
        "Pirating is mostly walking, it turns out.",
        "Lovely evening for a stroll.",
        "If anyone needs me, I'll be over there.",
        "Press any button. I'll wait.",
        "I could have sworn this path went somewhere.",
        "Note to self: buy a map.",
    };

    private final int unit;
    private final Random random = new Random();
    private final Paint blitPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint fillPaint = new Paint();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private final Bitmap[] scenes = new Bitmap[SCENES.length];
    private Bitmap sheet;
    private Bitmap grid;
    private Canvas gridCanvas;

    private boolean running;
    private int scene;
    private int x;                 // left edge of his frame, in grid pixels
    private int dir = 1;           // +1 walking right, -1 left
    private int frame;
    private int pauseAt = -1000;   // where in this scene he stops for a while; -1000 = not here
    private int idleTicks;         // > 0 while standing still
    private int idleLength;
    private boolean turnBack;      // at the end of this stop, walk back the way he came
    private boolean glance;        // during this stop, look over his shoulder
    private int fade;              // > 0 while fading out, < 0 while fading in
    private int pendingScene = -1;
    private String speech;
    private int speechTicks;
    private String[] lines;        // today's holiday lines, or null
    private int lineIndex;
    private String clock = "";
    private int clockDx;
    private int clockDy;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            step();
            invalidate();
            postDelayed(this, TICK_MS);
        }
    };

    PirateView(Context context) {
        super(context);
        unit = Pixel.unit(context);
        blitPaint.setFilterBitmap(false);
        blitPaint.setAntiAlias(false);
        textPaint.setTypeface(Pixel.font(context));
        textPaint.setTextSize(Pixel.FONT_SIZE);
        textPaint.setAntiAlias(false);
        textPaint.setSubpixelText(false);
        textPaint.setHinting(Paint.HINTING_OFF);
        textPaint.setColor(0xFFFFFFFF);
        setVisibility(GONE);
    }

    /** Today's holiday lines (said now and then instead of the usual mutterings), or null. */
    void setLines(String[] lines) {
        this.lines = lines;
    }

    void setTime(String time) {
        clock = time;
        // Move the clock a little each minute so it never stays in one place.
        clockDx = random.nextInt(4);
        clockDy = random.nextInt(4);
    }

    /** Fades in and starts walking: he comes on from the left of the scene he was last in. */
    void start() {
        if (running) {
            return;
        }
        running = true;
        enterScene(scene, 1);
        speech = null;
        fade = 0;
        setVisibility(VISIBLE);
        animate().cancel();
        setAlpha(0f);
        animate().alpha(1f).setDuration(800).start();
        removeCallbacks(tick);
        postDelayed(tick, TICK_MS);
    }

    void stop() {
        running = false;
        removeCallbacks(tick);
        animate().cancel();
        animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    setVisibility(GONE);
                    releaseBitmaps();
                }
            }
        }).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(tick);
        releaseBitmaps();
        super.onDetachedFromWindow();
    }

    private void releaseBitmaps() {
        for (int i = 0; i < scenes.length; i++) {
            scenes[i] = null;   // a few small bitmaps; loaded again next time
        }
        sheet = null;
    }

    // ---- Walking ----

    private int gridWidth() {
        return Math.max(1, getWidth() / unit);
    }

    private void enterScene(int index, int direction) {
        scene = ((index % SCENES.length) + SCENES.length) % SCENES.length;
        dir = direction;
        x = direction > 0 ? -FRAME_W : gridWidth();
        frame = 0;
        idleTicks = 0;
        // Most of the time he stops somewhere in the middle third of the screen.
        int w = gridWidth();
        pauseAt = random.nextInt(3) == 0 ? -1000 : w / 3 + random.nextInt(Math.max(1, w / 3)) - FRAME_W / 2;
    }

    private void step() {
        if (speechTicks > 0 && --speechTicks == 0) {
            speech = null;
        }
        if (fade > 0) {
            if (--fade == 0) {
                enterScene(pendingScene, dir);
                fade = -FADE_TICKS;
            }
            return;
        }
        if (fade < 0) {
            fade++;
        }

        if (idleTicks > 0) {
            idleTicks--;
            frame = random.nextInt(14) == 0 ? BLINK : STAND;
            if (glance && (idleTicks == idleLength * 2 / 3 || idleTicks == idleLength / 3)) {
                dir = -dir;
            }
            if (idleTicks == 0 && turnBack) {
                dir = -dir;
            }
            return;
        }

        frame = (frame + 1) % WALK_FRAMES;
        int before = x;
        x += dir * STEP;
        if (pauseAt != -1000 && (before - pauseAt) * (x - pauseAt) <= 0) {
            pauseAt = -1000;
            stopForAWhile();
            return;
        }
        int w = gridWidth();
        if (x > w || x < -FRAME_W) {
            // Off the edge: on to the next place along the coast (or back to the last one).
            pendingScene = scene + dir;
            fade = FADE_TICKS;
            speech = null;
        }
    }

    private void stopForAWhile() {
        idleLength = 16 + random.nextInt(32);   // 2 to 6 seconds
        idleTicks = idleLength;
        frame = STAND;
        turnBack = random.nextInt(4) == 0;
        glance = !turnBack && random.nextInt(3) == 0;
        String line = null;
        if (lines != null && lines.length > 0 && random.nextInt(3) != 0) {
            line = lines[lineIndex++ % lines.length];
        } else if (random.nextInt(4) == 0) {
            line = MUTTERINGS[random.nextInt(MUTTERINGS.length)];
        }
        if (line != null) {
            speech = line;
            speechTicks = (int) Math.max(idleLength + 8, 24 + line.length() * 1.2);
            idleTicks = idleLength = Math.max(idleLength, speechTicks - 8);
        }
    }

    // ---- Drawing ----

    private Bitmap scene(int i) {
        if (scenes[i] == null) {
            scenes[i] = decode(SCENES[i]);
        }
        return scenes[i];
    }

    private Bitmap decode(int res) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        return BitmapFactory.decodeResource(getResources(), res, opts);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int gw = getWidth() / unit;
        int gh = getHeight() / unit;
        if (gw <= 0 || gh <= 0) {
            return;
        }
        if (grid == null || grid.getWidth() != gw || grid.getHeight() != gh) {
            grid = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
            gridCanvas = new Canvas(grid);
        }
        if (sheet == null) {
            sheet = decode(R.drawable.pirate);
        }
        Canvas c = gridCanvas;
        c.drawColor(0xFF000000);
        int top = (gh - SCENE_H) / 2;   // the scenes are 180 rows; a 16:9 screen is exactly that

        Bitmap art = scene(scene);
        src.set(0, 0, art.getWidth(), art.getHeight());
        dst.set(0, top, gw, top + SCENE_H);
        c.drawBitmap(art, src, dst, blitPaint);

        // The pirate, flipped when walking left.
        int feet = top + GROUND;
        src.set(frame * FRAME_W, 0, (frame + 1) * FRAME_W, FRAME_H);
        dst.set(x, feet - FRAME_H + 1, x + FRAME_W, feet + 1);
        if (dir < 0) {
            c.save();
            c.scale(-1, 1, x + FRAME_W / 2f, 0);
            c.drawBitmap(sheet, src, dst, blitPaint);
            c.restore();
        } else {
            c.drawBitmap(sheet, src, dst, blitPaint);
        }

        if (speech != null) {
            drawSpeech(c, gw, feet - FRAME_H - 3);
        }

        // A small clock in the top-right corner.
        int cw = (int) Math.ceil(textPaint.measureText(clock)) * 2;
        text(c, clock, gw - cw - 8 - clockDx, 6 + clockDy, 2, 0xFFFFFFFF);

        if (fade != 0) {
            float t = fade > 0 ? 1f - fade / (float) FADE_TICKS : -fade / (float) FADE_TICKS;
            fillPaint.setColor(((int) (t * 255) << 24));
            c.drawRect(0, 0, gw, gh, fillPaint);
        }

        int left = (getWidth() - gw * unit) / 2;
        int screenTop = (getHeight() - gh * unit) / 2;
        dst.set(left, screenTop, left + gw * unit, screenTop + gh * unit);
        canvas.drawBitmap(grid, null, dst, blitPaint);
    }

    // Wrapped lines above his head, centred on him but kept on screen.
    private void drawSpeech(Canvas c, int gw, int bottom) {
        Paint.FontMetricsInt fm = textPaint.getFontMetricsInt();
        int lineH = fm.descent - fm.ascent;
        List<String> rows = wrap(speech, 150);
        int y = bottom - rows.size() * lineH;
        int centre = x + FRAME_W / 2;
        for (String row : rows) {
            int w = (int) Math.ceil(textPaint.measureText(row));
            int left = Math.max(4, Math.min(gw - w - 4, centre - w / 2));
            text(c, row, left, y, 1, SPEECH_COLOR);
            y += lineH;
        }
    }

    private List<String> wrap(String s, int width) {
        List<String> rows = new ArrayList<String>();
        String row = "";
        for (String word : s.split(" ")) {
            String longer = row.isEmpty() ? word : row + " " + word;
            if (!row.isEmpty() && textPaint.measureText(longer) > width) {
                rows.add(row);
                row = word;
            } else {
                row = longer;
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    // Pixel-font text with a one-pixel black outline, top-left at (x, y).
    private void text(Canvas c, String s, int x, int y, int scale, int color) {
        if (s == null || s.isEmpty()) {
            return;
        }
        Paint.FontMetricsInt fm = textPaint.getFontMetricsInt();
        int ascent = -fm.ascent;
        int h = ascent + fm.descent;
        int w = (int) Math.ceil(textPaint.measureText(s));
        Bitmap mask = Bitmap.createBitmap(Math.max(1, w), h, Bitmap.Config.ALPHA_8);
        new Canvas(mask).drawText(s, 0, ascent, textPaint);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dy != 0) {
                    blit(c, mask, x + dx, y + dy, scale, OUTLINE);
                }
            }
        }
        blit(c, mask, x, y, scale, color);
        mask.recycle();
    }

    private void blit(Canvas c, Bitmap mask, int x, int y, int scale, int color) {
        c.save();
        c.translate(x, y);
        c.scale(scale, scale);
        blitPaint.setColor(color);
        c.drawBitmap(mask, 0, 0, blitPaint);
        c.restore();
    }
}

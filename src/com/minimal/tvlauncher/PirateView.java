package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Random;

/**
 * The Monkey Island launcher's ambient mode: a pirate (an original character, drawn by
 * make_pirate.py) strolls through the Monkey Island backgrounds uploaded on the phone page,
 * in their slideshow order, or, if there are none, through a string of built-in scenes along a
 * coast (drawn by make_scenes.py). In an uploaded background his feet are 90% of the way down
 * the picture, unless its name gives the row, e.g. dock_g130.png. He walks off one side of the screen into the next place, now and then
 * stops to look around, and sometimes says something (on red days, about the holiday).
 *
 * A walk cycle uploaded on the phone page replaces the built-in pirate, in one of two forms:
 *   - one row of equal frames facing right, saved as files/pirate/walk_N.png where N is the
 *     number of frames (all walking; the first is also used for standing);
 *   - separate frames for each direction, from a ZIP, saved as files/pirate/f_right_01.png,
 *     f_left_.., f_front_.., f_back_..: he walks with the side frames (flipping one side if
 *     only the other is there), stands and talks facing you (the first front frame), and
 *     looks into the scene now and then (the first back frame).
 * A solid background colour (whatever the top-left pixel is) is made transparent.
 *
 * Like the rest of the launcher it is drawn on the 320-wide pixel grid and scaled up without
 * smoothing. It only redraws a few times a second, which is all a walk cycle needs.
 */
final class PirateView extends View {

    private static final int[] SCENES = {
        R.drawable.scene_harbour, R.drawable.scene_jungle, R.drawable.scene_beach, R.drawable.scene_town,
    };
    private static final int BUILT_IN_GROUND = 168;   // row his feet are on, in the built-in scenes
    // In uploaded backgrounds: this far down the picture, unless its name says (…_g130.png).
    private static final float DEFAULT_GROUND = 0.9f;
    private static final Pattern GROUND_IN_NAME = Pattern.compile(".*_g(\\d{1,4})\\.[A-Za-z]+");
    static final String CUSTOM_DIR = "pirate";
    static final Pattern CUSTOM_NAME = Pattern.compile("walk_(\\d{1,2})\\.(png|gif|webp)");
    static final Pattern FRAME_NAME = Pattern.compile("f_(right|left|front|back)_(\\d{2})\\.(png|gif|webp)");
    private static final int SIDE = 0, FRONT = 1, BACK = 2;
    private static final int MAX_HEIGHT = 90;     // taller sprites are shrunk by a whole factor
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
    // The places he walks through: the Monkey Island backgrounds uploaded on the phone page,
    // or the built-in coast if there are none. Only the one on screen is kept in memory.
    private final List<File> uploaded = new ArrayList<File>();
    private int sceneCount = SCENES.length;
    private Bitmap sceneArt;
    private int sceneArtIndex = -1;
    private int sceneW;            // the picture's own size (before any downsampling on load)
    private int sceneH;
    private int sceneGround;       // row his feet are on, in the picture's own pixels
    private Bitmap sheet;
    // The sprite sheet's layout: the built-in pirate's, or an uploaded one's.
    private int frameW = 24;
    private int frameH = 40;
    private int walkFrames = 6;
    private int standFrame = 6;
    private int blinkFrame = 7;
    private int shrink = 1;
    // Separate frames by direction (from a ZIP), or null for a single sheet.
    private java.util.List<Bitmap> right, left, front, back;
    private int pose = SIDE;
    private Bitmap grid;
    private Canvas gridCanvas;

    private boolean running;
    private final PixelWeather weather = new PixelWeather();   // the real rain or snow, in pixels
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
        // Re-read the uploaded backgrounds each time, so changes from the phone show up.
        uploaded.clear();
        uploaded.addAll(MonkeyIslandTheme.backgrounds(getContext()));
        sceneCount = uploaded.isEmpty() ? SCENES.length : uploaded.size();
        sceneArt = null;
        sceneArtIndex = -1;
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
        sceneArt = null;   // loaded again next time
        sceneArtIndex = -1;
        sheet = null;
    }

    // ---- Walking ----

    private int drawW() {
        return frameW / shrink;
    }

    private int drawH() {
        return frameH / shrink;
    }

    private int gridWidth() {
        return Math.max(1, getWidth() / unit);
    }

    private void enterScene(int index, int direction) {
        scene = ((index % sceneCount) + sceneCount) % sceneCount;
        dir = direction;
        x = direction > 0 ? -drawW() : gridWidth();
        frame = 0;
        idleTicks = 0;
        // Most of the time he stops somewhere in the middle third of the screen.
        int w = gridWidth();
        pauseAt = random.nextInt(3) == 0 ? -1000 : w / 3 + random.nextInt(Math.max(1, w / 3)) - drawW() / 2;
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
            frame = random.nextInt(14) == 0 ? blinkFrame : standFrame;
            if (right != null) {
                // Facing you while he stands; during a glance, looking into the scene.
                boolean looking = glance && idleTicks < idleLength * 2 / 3 && idleTicks > idleLength / 3 && speech == null;
                pose = looking && !back.isEmpty() ? BACK : (front.isEmpty() ? SIDE : FRONT);
                frame = 0;
            } else if (glance && (idleTicks == idleLength * 2 / 3 || idleTicks == idleLength / 3)) {
                dir = -dir;
            }
            if (idleTicks == 0 && turnBack) {
                dir = -dir;
            }
            return;
        }

        pose = SIDE;
        frame = (frame + 1) % walkFrames;
        int before = x;
        x += dir * STEP;
        if (pauseAt != -1000 && (before - pauseAt) * (x - pauseAt) <= 0) {
            pauseAt = -1000;
            stopForAWhile();
            return;
        }
        int w = gridWidth();
        if (x > w || x < -drawW()) {
            // Off the edge: on to the next place along the coast (or back to the last one).
            pendingScene = scene + dir;
            preload(pendingScene);
            fade = FADE_TICKS;
            speech = null;
        }
    }

    private void stopForAWhile() {
        idleLength = 16 + random.nextInt(32);   // 2 to 6 seconds
        idleTicks = idleLength;
        frame = standFrame;
        turnBack = random.nextInt(4) == 0;
        glance = !turnBack && random.nextInt(3) == 0;
        String line = null;
        if (lines != null && lines.length > 0 && random.nextInt(2) == 0) {
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

    // A scene's picture and where his feet go in it.
    private static final class SceneArt {
        int index;
        Bitmap bitmap;
        int w;
        int h;
        int ground;
    }

    private volatile SceneArt preloaded;

    private Bitmap scene(int i) {
        if (sceneArtIndex == i && sceneArt != null) {
            return sceneArt;
        }
        SceneArt a = preloaded;
        if (a == null || a.index != i) {
            a = load(i);   // not ready in time: load it now
        }
        preloaded = null;
        sceneArt = a.bitmap;
        sceneW = a.w;
        sceneH = a.h;
        sceneGround = a.ground;
        sceneArtIndex = i;
        return sceneArt;
    }

    // While the screen fades to black, the next scene loads in the background, so the walk
    // doesn't hitch when it changes.
    private void preload(final int index) {
        final int i = ((index % sceneCount) + sceneCount) % sceneCount;
        new Thread(new Runnable() {
            @Override
            public void run() {
                preloaded = load(i);
            }
        }, "PirateScene").start();
    }

    private SceneArt load(int i) {
        SceneArt a = new SceneArt();
        a.index = i;
        if (i < uploaded.size()) {
            File f = uploaded.get(i);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), bounds);
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                while (bounds.outWidth / (opts.inSampleSize * 2) >= 640) {
                    opts.inSampleSize *= 2;   // a big photo: no need for more than the grid shows
                }
                try {
                    a.bitmap = BitmapFactory.decodeFile(f.getPath(), opts);
                } catch (OutOfMemoryError ignored) {
                    a.bitmap = null;
                }
                a.w = bounds.outWidth;
                a.h = bounds.outHeight;
                Matcher m = GROUND_IN_NAME.matcher(f.getName());
                a.ground = m.matches() ? Math.min(a.h - 1, Integer.parseInt(m.group(1)))
                        : Math.round(a.h * DEFAULT_GROUND);
            }
        }
        if (a.bitmap == null) {
            a.bitmap = decode(SCENES[i % SCENES.length]);
            a.w = a.bitmap.getWidth();
            a.h = a.bitmap.getHeight();
            a.ground = BUILT_IN_GROUND;
        }
        return a;
    }

    /** The uploaded sprite sheet if there is a usable one, otherwise the built-in pirate. */
    static File customSheet(Context context) {
        File root = context.getExternalFilesDir(null);
        File[] files = root == null ? null : new File(root, CUSTOM_DIR).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && CUSTOM_NAME.matcher(f.getName()).matches()) {
                    return f;
                }
            }
        }
        return null;
    }

    /** The uploaded per-direction frames, in order, or an empty list. */
    static java.util.List<File> customFrames(Context context) {
        java.util.List<File> out = new java.util.ArrayList<File>();
        File root = context.getExternalFilesDir(null);
        File[] files = root == null ? null : new File(root, CUSTOM_DIR).listFiles();
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                if (f.isFile() && FRAME_NAME.matcher(f.getName()).matches()) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    // Loads the per-direction frames, if there are any usable ones. Returns true if so.
    private boolean loadFrames() {
        java.util.List<File> files = customFrames(getContext());
        if (files.isEmpty()) {
            return false;
        }
        java.util.List<Bitmap> r = new java.util.ArrayList<Bitmap>(), l = new java.util.ArrayList<Bitmap>(),
                f = new java.util.ArrayList<Bitmap>(), b = new java.util.ArrayList<Bitmap>();
        int maxW = 1, maxH = 1;
        for (File file : files) {
            Matcher m = FRAME_NAME.matcher(file.getName());
            if (!m.matches()) {
                continue;
            }
            Bitmap bmp;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), bounds);
                if (bounds.outWidth <= 0 || (long) bounds.outWidth * bounds.outHeight > 1024L * 1024) {
                    continue;   // not an image, or far too big for a sprite
                }
                bmp = BitmapFactory.decodeFile(file.getPath());
            } catch (OutOfMemoryError e) {
                continue;
            }
            if (bmp == null) {
                continue;
            }
            bmp = clearBackground(bmp);
            maxW = Math.max(maxW, bmp.getWidth());
            maxH = Math.max(maxH, bmp.getHeight());
            String dirName = m.group(1);
            (dirName.equals("right") ? r : dirName.equals("left") ? l : dirName.equals("front") ? f : b).add(bmp);
        }
        if (r.isEmpty() && l.isEmpty()) {
            return false;   // nothing to walk with
        }
        right = r;
        left = l;
        front = f;
        back = b;
        sheet = r.isEmpty() ? l.get(0) : r.get(0);   // anything non-null: the frames draw themselves
        frameW = maxW;
        frameH = maxH;
        walkFrames = Math.max(r.size(), l.size());
        standFrame = 0;
        blinkFrame = 0;
        shrink = (frameH + MAX_HEIGHT - 1) / MAX_HEIGHT;
        return true;
    }

    private void loadSheet() {
        right = left = front = back = null;
        if (loadFrames()) {
            return;
        }
        File custom = customSheet(getContext());
        if (custom != null) {
            Matcher m = CUSTOM_NAME.matcher(custom.getName());
            m.matches();
            int frames = Integer.parseInt(m.group(1));
            Bitmap b = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(custom.getPath(), bounds);
                if ((long) bounds.outWidth * bounds.outHeight <= 4L * 1024 * 1024) {   // no giant sheets
                    b = BitmapFactory.decodeFile(custom.getPath());
                }
            } catch (OutOfMemoryError ignored) {
                // fall back to the built-in pirate
            }
            if (b != null && frames > 0 && b.getWidth() >= frames) {
                sheet = clearBackground(b);
                frameW = b.getWidth() / frames;
                frameH = b.getHeight();
                walkFrames = frames;
                standFrame = 0;
                blinkFrame = 0;
                shrink = (frameH + MAX_HEIGHT - 1) / MAX_HEIGHT;
                return;
            }
        }
        sheet = decode(R.drawable.pirate);
        frameW = 24;
        frameH = 40;
        walkFrames = 6;
        standFrame = 6;
        blinkFrame = 7;
        shrink = 1;
    }

    // Sprite sheets often come on a flat colour instead of transparency: drop that colour.
    private static Bitmap clearBackground(Bitmap b) {
        int key = b.getPixel(0, 0);
        if ((key >>> 24) != 0xFF) {
            return b;   // already transparent
        }
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            if (px[i] == key) {
                px[i] = 0;
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
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
            loadSheet();
        }
        Canvas c = gridCanvas;
        c.drawColor(0xFF000000);
        // The scene fills the width; a short one (like a 320x144 game screen) sits in the middle
        // with black above and below, a tall one is cropped top and bottom.
        Bitmap art = scene(scene);
        float scale = gw / (float) sceneW;
        int sceneRows = Math.round(sceneH * scale);
        int top = (gh - sceneRows) / 2;
        src.set(0, 0, art.getWidth(), art.getHeight());
        dst.set(0, top, gw, top + sceneRows);
        c.drawBitmap(art, src, dst, blitPaint);

        // The pirate, flipped when walking left.
        int feet = Math.min(gh - 1, top + Math.round(sceneGround * scale));
        if (right != null) {
            drawFrame(c, feet);
        } else {
            src.set(frame * frameW, 0, (frame + 1) * frameW, frameH);
            dst.set(x, feet - drawH() + 1, x + drawW(), feet + 1);
        }
        if (right != null) {
            // drawn above
        } else if (dir < 0) {
            c.save();
            c.scale(-1, 1, x + drawW() / 2f, 0);
            c.drawBitmap(sheet, src, dst, blitPaint);
            c.restore();
        } else {
            c.drawBitmap(sheet, src, dst, blitPaint);
        }

        if (speech != null) {
            drawSpeech(c, gw, feet - drawH() - 3);
        }

        // Rain or snow, if it's really raining or snowing outside.
        weather.setKind(PixelWeather.kindFor(Weather.cached()));
        weather.draw(c, gw, gh, TICK_MS / 1000f);

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

    // One of the per-direction frames, its feet on the ground, centred in his place.
    private void drawFrame(Canvas c, int feet) {
        Bitmap b;
        boolean flip = false;
        if (pose == FRONT && !front.isEmpty()) {
            b = front.get(0);
        } else if (pose == BACK && !back.isEmpty()) {
            b = back.get(0);
        } else {
            java.util.List<Bitmap> want = dir > 0 ? right : left;
            java.util.List<Bitmap> other = dir > 0 ? left : right;
            if (want.isEmpty()) {
                want = other;
                flip = true;
            }
            b = want.get(frame % want.size());
        }
        int w = b.getWidth() / shrink, h = b.getHeight() / shrink;
        int left0 = x + (drawW() - w) / 2;
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(left0, feet - h + 1, left0 + w, feet + 1);
        if (flip) {
            c.save();
            c.scale(-1, 1, left0 + w / 2f, 0);
            c.drawBitmap(b, src, dst, blitPaint);
            c.restore();
        } else {
            c.drawBitmap(b, src, dst, blitPaint);
        }
    }

    // Wrapped lines above his head, centred on him but kept on screen.
    private void drawSpeech(Canvas c, int gw, int bottom) {
        Paint.FontMetricsInt fm = textPaint.getFontMetricsInt();
        int lineH = fm.descent - fm.ascent;
        List<String> rows = wrap(speech, 150);
        int y = bottom - rows.size() * lineH;
        int centre = x + drawW() / 2;
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

package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's interface, laid out like the VGA Monkey Island screen: the scene on top
 * (the background shows through, with the clock in the corner), then a black panel with
 * a sentence line, a 3x3 grid of verbs on the left and an inventory of app names on the right.
 *
 * Everything is drawn into a small bitmap on the launcher's pixel grid (320x180 on a 16:9
 * TV) and scaled up by a whole number without smoothing. Like a conversation in the game,
 * {@link #showChoices} can swap the verbs and inventory for lines to pick from. Verbs use {@link VerbFont}, a
 * recreation of the game's bold verb font; other text uses the Tiny5 pixel font. The remote's D-pad moves between
 * verbs and inventory items; OK picks a verb or uses the current verb on an app.
 */
public class ScummView extends View {

    interface ChoiceListener {
        void onChoice(int index);
    }

    interface Listener {
        /** A verb that does something by itself (e.g. open Wi-Fi settings). */
        void onVerb(Verb verb);

        /** A verb used on an app (e.g. "Open" + Spotify). */
        void onItem(Verb verb, Item item);
    }

    static class Verb {
        final int id;
        final String label;
        final String hint;     // shown in the sentence line for verbs that act by themselves
        final boolean needsItem;

        Verb(int id, String label, String hint, boolean needsItem) {
            this.id = id;
            this.label = label;
            this.hint = hint;
            this.needsItem = needsItem;
        }
    }

    static class Item {
        final String label;
        final Object tag;

        Item(String label, Object tag) {
            this.label = label;
            this.tag = tag;
        }
    }

    // Colours from the VGA Monkey Island interface.
    private static final int VERB = 0xFF9E3D9E;
    private static final int VERB_SHADOW = 0xFF3A0838;
    private static final int BOX = 0xFF1C061A;
    private static final int VERB_ACTIVE = 0xFFFCA8FC;
    private static final int VERB_FOCUS = 0xFFFC54FC;
    private static final int SENTENCE = 0xFFD27BD2;
    private static final int ARROW = 0xFF9E3D9E;
    private static final int ARROW_OFF = 0xFF3C183C;
    private static final int CLOCK = 0xFFFFFFFF;
    private static final int DATE = 0xFFE0E0F0;
    private static final int OUTLINE = 0xFF000000;

    // Layout, in grid pixels.
    static final int PANEL_HEIGHT = 48;
    private static final int SENTENCE_HEIGHT = 11;
    private static final int VERB_COLS = 3;
    private static final int VERB_ROWS = 3;
    private static final int VERB_X = 2;
    private static final int VERB_H = 12;
    private static final int VERB_PAD = 3;
    private static final int ARROW_X = 172;
    private static final int INV_X = 188;
    private static final int INV_COLS = 2;
    private static final int INV_ROWS = 3;
    private static final int SLOT_W = 65;
    private static final int SLOT_H = 12;
    private static final int CLOCK_SCALE = 3;
    private static final long MESSAGE_MS = 3000;

    private static final String[] ARROW_UP = {
            ".....#.....",
            "....#.#....",
            "...#...#...",
            "..#.....#..",
            ".#.......#.",
            "####...####",
            "...#...#...",
            "...#...#...",
            "...#...#...",
            "...#####...",
    };

    private final Paint textPaint = new Paint();
    private final Paint blitPaint = new Paint();
    private final Paint fillPaint = new Paint();
    private final Rect dest = new Rect();
    private final int unit;

    private final List<Verb> verbs = new ArrayList<Verb>();
    private final List<Item> items = new ArrayList<Item>();
    private Listener listener;
    private Verb defaultVerb;
    private Verb activeVerb;

    private boolean onVerbs;
    private int verbFocus;
    private int itemFocus;
    private int scroll;           // first visible inventory row

    private String clock = "";
    private String date = "";
    private String message;
    private long messageUntil;

    private List<String> choices;
    private String choicePrompt = "";
    private ChoiceListener choiceListener;
    private int choiceFocus;

    private Bitmap ui;
    private Canvas uiCanvas;

    private final Runnable clearMessage = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    public ScummView(Context context) {
        this(context, null);
    }

    public ScummView(Context context, AttributeSet attrs) {
        super(context, attrs);
        unit = Pixel.unit(context);
        textPaint.setTypeface(Pixel.font(context));
        textPaint.setTextSize(Pixel.FONT_SIZE);
        textPaint.setAntiAlias(false);
        textPaint.setSubpixelText(false);
        textPaint.setHinting(Paint.HINTING_OFF);
        textPaint.setColor(0xFFFFFFFF);
        blitPaint.setFilterBitmap(false);
        blitPaint.setAntiAlias(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setVerbs(List<Verb> list) {
        verbs.clear();
        verbs.addAll(list);
        defaultVerb = verbs.isEmpty() ? null : verbs.get(0);
        activeVerb = defaultVerb;
        verbFocus = 0;
        invalidate();
    }

    void setItems(List<Item> list) {
        items.clear();
        items.addAll(list);
        itemFocus = Math.min(itemFocus, Math.max(0, items.size() - 1));
        scroll = Math.min(scroll, maxScroll());
        if (items.isEmpty()) {
            onVerbs = true;
        }
        invalidate();
    }

    void setTime(String clock, String date) {
        this.clock = clock;
        this.date = date;
        invalidate();
    }

    /** Shows a line of text in the sentence line for a few seconds. */
    void showMessage(String text) {
        message = text;
        messageUntil = SystemClock.uptimeMillis() + MESSAGE_MS;
        removeCallbacks(clearMessage);
        postDelayed(clearMessage, MESSAGE_MS + 50);
        invalidate();
    }

    /** Back to the first app with the default verb, as after pressing Home. */
    /**
     * Replaces the verbs and inventory with lines to choose from, until one is picked or Back.
     * {@code prompt} is shown in the sentence line meanwhile.
     */
    void showChoices(String prompt, List<String> lines, ChoiceListener listener) {
        choicePrompt = prompt;
        choices = new ArrayList<String>(lines);
        choiceListener = listener;
        choiceFocus = 0;
        message = null;
        invalidate();
    }

    void reset() {
        choices = null;
        activeVerb = defaultVerb;
        onVerbs = items.isEmpty();
        itemFocus = 0;
        scroll = 0;
        message = null;
        invalidate();
    }

    /** Height of the scene above the panel, in screen pixels. */
    static int sceneHeight(Context context, int screenHeight) {
        int u = Pixel.unit(context);
        return (screenHeight / u - PANEL_HEIGHT) * u;
    }

    // ---- Input ----

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (choices != null) {
            return choiceKey(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (event.getRepeatCount() == 0) {
                    activate();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                // Like right-clicking in the game: drop the chosen verb.
                if (activeVerb != defaultVerb) {
                    activeVerb = defaultVerb;
                    invalidate();
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private boolean choiceKey(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                choiceFocus = Math.max(0, choiceFocus - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                choiceFocus = Math.min(choices.size() - 1, choiceFocus + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (event.getRepeatCount() == 0) {
                    // Clear first: the listener may show the next set of choices.
                    ChoiceListener listener = choiceListener;
                    int picked = choiceFocus;
                    choices = null;
                    choiceListener = null;
                    listener.onChoice(picked);
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                choices = null;
                choiceListener = null;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        invalidate();
        return true;
    }

    private int maxScroll() {
        int rows = (items.size() + INV_COLS - 1) / INV_COLS;
        return Math.max(0, rows - INV_ROWS);
    }

    private void move(int dx, int dy) {
        message = null;
        if (onVerbs) {
            int col = verbFocus % VERB_COLS + dx;
            int row = verbFocus / VERB_COLS + dy;
            if (col >= VERB_COLS && !items.isEmpty()) {
                // Into the inventory, on the matching row.
                int r = Math.min(row, INV_ROWS - 1);
                int index = Math.min((scroll + r) * INV_COLS, items.size() - 1);
                focusItem(index);
            } else if (col >= 0 && col < VERB_COLS && row >= 0 && row < VERB_ROWS
                    && row * VERB_COLS + col < verbs.size()) {
                verbFocus = row * VERB_COLS + col;
            }
        } else {
            int col = itemFocus % INV_COLS + dx;
            int row = itemFocus / INV_COLS + dy;
            if (col < 0) {
                onVerbs = true;
                int r = Math.min(itemFocus / INV_COLS - scroll, VERB_ROWS - 1);
                verbFocus = Math.min(r * VERB_COLS + VERB_COLS - 1, verbs.size() - 1);
            } else if (col < INV_COLS && row >= 0) {
                int index = row * INV_COLS + col;
                if (index >= items.size() && dy > 0 && row * INV_COLS < items.size()) {
                    index = items.size() - 1;   // last row is short: land on its last item
                }
                if (index < items.size()) {
                    focusItem(index);
                }
            }
        }
        invalidate();
    }

    private void focusItem(int index) {
        onVerbs = false;
        itemFocus = index;
        int row = index / INV_COLS;
        if (row < scroll) {
            scroll = row;
        } else if (row >= scroll + INV_ROWS) {
            scroll = row - INV_ROWS + 1;
        }
    }

    private void activate() {
        message = null;
        if (onVerbs) {
            if (verbFocus >= verbs.size()) {
                return;
            }
            Verb verb = verbs.get(verbFocus);
            if (verb.needsItem) {
                // Pick the verb, then go and choose an app for it.
                activeVerb = verb;
                if (!items.isEmpty()) {
                    focusItem(Math.min(itemFocus, items.size() - 1));
                }
            } else if (listener != null) {
                listener.onVerb(verb);
            }
        } else if (itemFocus < items.size() && listener != null && activeVerb != null) {
            Verb verb = activeVerb;
            activeVerb = defaultVerb;
            listener.onItem(verb, items.get(itemFocus));
        }
        invalidate();
    }

    // ---- Drawing ----

    @Override
    protected void onDraw(Canvas canvas) {
        int gw = getWidth() / unit;
        int gh = getHeight() / unit;
        if (gw <= 0 || gh <= PANEL_HEIGHT) {
            return;
        }
        if (ui == null || ui.getWidth() != gw || ui.getHeight() != gh) {
            ui = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
            uiCanvas = new Canvas(ui);
        }
        ui.eraseColor(0);
        render(uiCanvas, gw, gh);

        // Black panel all the way to the screen edges, then the pixel layer on top.
        int left = (getWidth() - gw * unit) / 2;
        int top = (getHeight() - gh * unit) / 2;
        fillPaint.setColor(0xFF000000);
        canvas.drawRect(0, top + (gh - PANEL_HEIGHT) * unit, getWidth(), getHeight(), fillPaint);
        dest.set(left, top, left + gw * unit, top + gh * unit);
        canvas.drawBitmap(ui, null, dest, blitPaint);
    }

    private void render(Canvas c, int gw, int gh) {
        // Clock in the corner of the scene, like character dialogue.
        int clockH = text(c, clock, 6, 3, CLOCK_SCALE, CLOCK, true);
        text(c, date, 6, 3 + clockH, 1, DATE, true);

        int panelTop = gh - PANEL_HEIGHT;
        fillPaint.setColor(0xFF000000);
        c.drawRect(0, panelTop, gw, gh, fillPaint);

        // Sentence line.
        text(c, sentence(), 0, panelTop + 1, 1, SENTENCE, false, gw);

        int verbTop = panelTop + SENTENCE_HEIGHT;
        if (choices != null) {
            // Conversation: one line per choice, scrolled so the focused one is visible.
            int first = Math.max(0, choiceFocus - (VERB_ROWS - 1));
            for (int i = first; i < choices.size() && i < first + VERB_ROWS; i++) {
                int y = verbTop + (i - first) * VERB_H;
                verbText(c, choices.get(i), 7, y + 2, VERB_SHADOW);
                verbText(c, choices.get(i), 6, y + 1, i == choiceFocus ? VERB_FOCUS : VERB);
            }
            return;
        }

        // Verbs, each on its own dark box. Columns are as wide as their longest verb.
        boolean itemFocused = !onVerbs;
        int[] colX = new int[VERB_COLS + 1];
        colX[0] = VERB_X;
        for (int col = 0; col < VERB_COLS; col++) {
            int widest = 0;
            for (int i = col; i < verbs.size() && i < VERB_COLS * VERB_ROWS; i += VERB_COLS) {
                widest = Math.max(widest, verbWidth(verbs.get(i).label));
            }
            colX[col + 1] = colX[col] + widest + 2 * VERB_PAD + 1;
        }
        // Spread any spare room evenly so the boxes reach the arrows, as in the game.
        int spare = ARROW_X - 2 - colX[VERB_COLS];
        if (spare > 0) {
            for (int col = 1; col <= VERB_COLS; col++) {
                colX[col] += spare * col / VERB_COLS;
            }
        }
        for (int i = 0; i < VERB_COLS * VERB_ROWS; i++) {
            int col = i % VERB_COLS;
            int x = colX[col];
            int w = colX[col + 1] - x - 1;
            int y = verbTop + (i / VERB_COLS) * VERB_H;
            fillPaint.setColor(BOX);
            c.drawRect(x, y, x + w, y + VERB_H - 1, fillPaint);
            if (i >= verbs.size()) {
                continue;
            }
            Verb verb = verbs.get(i);
            int color = VERB;
            if (onVerbs && i == verbFocus) {
                color = VERB_FOCUS;
            } else if (verb == activeVerb && (itemFocused || verb != defaultVerb)) {
                color = VERB_ACTIVE;
            }
            int tx = x + (w - verbWidth(verb.label)) / 2;
            verbText(c, verb.label, tx + 1, y + 2, VERB_SHADOW);
            verbText(c, verb.label, tx, y + 1, color);
        }

        // Scroll arrows.
        int max = maxScroll();
        fillPaint.setColor(BOX);
        int panelBottom = verbTop + VERB_ROWS * VERB_H - 1;
        c.drawRect(ARROW_X - 2, verbTop, INV_X - 1, panelBottom, fillPaint);
        icon(c, ARROW_UP, ARROW_X, verbTop + 3, scroll > 0 ? ARROW : ARROW_OFF, false);
        icon(c, ARROW_UP, ARROW_X, panelBottom - 3 - ARROW_UP.length, scroll < max ? ARROW : ARROW_OFF, true);

        // Inventory: app names in the verb font, each on its own box.
        for (int r = 0; r < INV_ROWS; r++) {
            for (int col = 0; col < INV_COLS; col++) {
                int index = (scroll + r) * INV_COLS + col;
                int x = INV_X + col * SLOT_W;
                int y = verbTop + r * SLOT_H;
                int w = SLOT_W - 1;
                fillPaint.setColor(BOX);
                c.drawRect(x, y, x + w, y + SLOT_H - 1, fillPaint);
                if (index < items.size()) {
                    String name = fit(items.get(index).label, w - 2);
                    int color = !onVerbs && index == itemFocus ? VERB_FOCUS : VERB;
                    int tx = x + (w - verbWidth(name)) / 2;
                    verbText(c, name, tx + 1, y + 2, VERB_SHADOW);
                    verbText(c, name, tx, y + 1, color);
                }
            }
        }
    }

    /** Shortens a name until it fits in {@code width} pixels of the verb font. */
    private static String fit(String s, int width) {
        while (s.length() > 1 && verbWidth(s) > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s.trim();
    }

    private static String[] glyph(char ch) {
        int index = VerbFont.CHARS.indexOf(ch);
        return VerbFont.GLYPHS[index >= 0 ? index : VerbFont.CHARS.indexOf(' ')];
    }

    private static int verbWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += glyph(s.charAt(i))[0].length() + 1;
        }
        return Math.max(0, w - 1);
    }

    /** Draws a verb in the recreated verb font with its top-left at (x, y). */
    private void verbText(Canvas c, String s, int x, int y, int color) {
        fillPaint.setColor(color);
        for (int i = 0; i < s.length(); i++) {
            String[] rows = glyph(s.charAt(i));
            for (int r = 0; r < rows.length; r++) {
                String row = rows[r];
                for (int k = 0; k < row.length(); k++) {
                    if (row.charAt(k) == '#') {
                        c.drawRect(x + k, y + r, x + k + 1, y + r + 1, fillPaint);
                    }
                }
            }
            x += rows[0].length() + 1;
        }
    }

    private String sentence() {
        if (message != null && SystemClock.uptimeMillis() < messageUntil) {
            return message;
        }
        if (choices != null) {
            return choicePrompt;
        }
        if (onVerbs) {
            if (verbFocus >= verbs.size()) {
                return "";
            }
            Verb verb = verbs.get(verbFocus);
            return verb.needsItem ? verb.label : verb.hint;
        }
        if (itemFocus < items.size() && activeVerb != null) {
            return activeVerb.label + " " + items.get(itemFocus).label;
        }
        return "";
    }

    private int text(Canvas c, String s, int x, int y, int scale, int color, boolean outline) {
        return text(c, s, x, y, scale, color, outline, -1);
    }

    /**
     * Draws pixel-font text with its top-left at (x, y), centred in {@code centerWidth} if that
     * is positive. Returns the height used.
     */
    private int text(Canvas c, String s, int x, int y, int scale, int color, boolean outline, int centerWidth) {
        Paint.FontMetricsInt fm = textPaint.getFontMetricsInt();
        int ascent = -fm.ascent;
        int h = ascent + fm.descent;
        if (s == null || s.length() == 0) {
            return h * scale;
        }
        int w = (int) Math.ceil(textPaint.measureText(s));
        Bitmap mask = Bitmap.createBitmap(Math.max(1, w), h, Bitmap.Config.ALPHA_8);
        new Canvas(mask).drawText(s, 0, ascent, textPaint);
        if (centerWidth > 0) {
            x += Math.max(0, (centerWidth - w * scale) / 2);
        }
        if (outline) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx != 0 || dy != 0) {
                        blit(c, mask, x + dx, y + dy, scale, OUTLINE);
                    }
                }
            }
        }
        blit(c, mask, x, y, scale, color);
        mask.recycle();
        return h * scale;
    }

    private void blit(Canvas c, Bitmap mask, int x, int y, int scale, int color) {
        c.save();
        c.translate(x, y);
        c.scale(scale, scale);
        blitPaint.setColor(color);
        c.drawBitmap(mask, 0, 0, blitPaint);
        c.restore();
    }

    private void icon(Canvas c, String[] rows, int x, int y, int color, boolean flip) {
        fillPaint.setColor(color);
        for (int r = 0; r < rows.length; r++) {
            String row = rows[flip ? rows.length - 1 - r : r];
            for (int i = 0; i < row.length(); i++) {
                if (row.charAt(i) == '#') {
                    c.drawRect(x + i, y + r, x + i + 1, y + r + 1, fillPaint);
                }
            }
        }
    }
}

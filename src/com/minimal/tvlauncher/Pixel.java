package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Typeface;
import android.view.WindowManager;

/**
 * The whole launcher is drawn on one low-resolution grid, the same 320x180 grid as the
 * built-in background art. One grid pixel is {@link #unit} screen pixels (6 on a 1080p TV).
 */
final class Pixel {

    static final int GRID_WIDTH = 320;

    // Tiny5 is drawn on an 8px em: glyph outlines sit exactly on whole pixels at this size.
    static final int FONT_SIZE = 8;

    private static int unit;
    private static Typeface font;

    private Pixel() {
    }

    static int unit(Context context) {
        if (unit == 0) {
            Point size = new Point();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getRealSize(size);
            unit = Math.max(1, Math.round(Math.max(size.x, size.y) / (float) GRID_WIDTH));
        }
        return unit;
    }

    static Typeface font(Context context) {
        if (font == null) {
            font = Typeface.createFromAsset(context.getAssets(), "fonts/tiny5.ttf");
        }
        return font;
    }
}

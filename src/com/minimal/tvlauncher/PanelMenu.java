package com.minimal.tvlauncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The small settings panel the Apartment and Photos launchers open from their gear: a title,
 * a list of items (scrolling when long) and an optional footer line. While it's open, the
 * remote stays inside it. Also the shared look of their buttons ({@link #pill}).
 */
final class PanelMenu {

    static final int FG = 0xFFECEBE8;
    static final int MUTED = 0xFFA29F99;
    private static final int PANEL = 0xEB1E1F21;
    private static final int LINE = 0x24ECEBE8;

    /** Called when the panel closes, to put the focus back (e.g. on the gear). */
    interface OnClose {
        void onClose();
    }

    final LinearLayout view;
    private final Context context;
    private final Typeface face;
    private final ViewGroup blocked;
    private final OnClose onClose;
    private final TextView title;
    private final LinearLayout items;
    private final TextView footer;
    private int accent;
    private int ink;

    /** {@code blocked}: what the remote mustn't reach while the panel is open. */
    PanelMenu(Context context, Typeface face, ViewGroup blocked, OnClose onClose) {
        this.context = context;
        this.face = face;
        this.blocked = blocked;
        this.onClose = onClose;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(8), dp(14), dp(8), dp(8));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(PANEL);
        panel.setCornerRadius(dp(12));
        panel.setStroke(dp(1), LINE);
        view.setBackground(panel);
        view.setElevation(dp(8));
        title = text(context, face, 11, MUTED);
        title.setLetterSpacing(0.14f);
        title.setAllCaps(true);
        title.setPadding(dp(14), 0, dp(14), dp(8));
        view.addView(title);
        items = new LinearLayout(context);
        items.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(items);
        view.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1));
        footer = text(context, face, 12, MUTED);
        footer.setPadding(dp(14), dp(10), dp(14), dp(4));
        view.addView(footer);
        view.setVisibility(View.GONE);
    }

    /** The colour items take when focused, and their text then. */
    void setColors(int accent, int ink) {
        this.accent = accent;
        this.ink = ink;
    }

    boolean isOpen() {
        return view.getVisibility() == View.VISIBLE;
    }

    void open(String heading) {
        title.setText(heading);
        items.removeAllViews();
        footer.setVisibility(View.GONE);
        view.setVisibility(View.VISIBLE);
        blocked.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    void close() {
        if (!isOpen()) {
            return;
        }
        view.setVisibility(View.GONE);
        items.removeAllViews();
        blocked.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        onClose.onClose();
    }

    View add(CharSequence label, final Runnable action) {
        TextView item = text(context, face, 17, 0);
        item.setText(label);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setTextColor(focusColors(ink, FG));
        item.setBackground(pill(context, accent, dp(8)));
        item.setPadding(dp(14), dp(11), dp(14), dp(12));
        item.setFocusable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        items.addView(item, new LinearLayout.LayoutParams(-1, -2));
        return item;
    }

    void setFooter(String text) {
        footer.setText(text);
        footer.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    /** The phone page's address, if there is one, as the footer. */
    void phoneFooter() {
        String url = Remote.url();
        if (url != null) {
            setFooter("Phone: " + url.replace("http://", ""));
        }
    }

    void focus(int index) {
        if (index < items.getChildCount()) {
            items.getChildAt(index).requestFocus();
        }
    }

    /** Lists the launchers, the current one marked, and switches on a pick. */
    void showLaunchers(final MainActivity activity) {
        open("Launcher");
        View current = null;
        for (int i = 0; i < MainActivity.LAUNCHER_IDS.length; i++) {
            final String id = MainActivity.LAUNCHER_IDS[i];
            boolean isCurrent = id.equals(activity.currentLauncher());
            String name = MainActivity.LAUNCHER_NAMES[i];
            View item = add(isCurrent ? note(name, "current") : name, new Runnable() {
                @Override
                public void run() {
                    close();
                    activity.switchLauncher(id);
                }
            });
            if (isCurrent) {
                current = item;
            }
        }
        if (current != null) {
            current.requestFocus();
        } else {
            focus(0);
        }
    }

    private int dp(float v) {
        return dp(context, v);
    }

    // ---- Shared look ----

    static int dp(Context context, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics()));
    }

    static TextView text(Context context, Typeface face, float sp, int color) {
        TextView t = new TextView(context);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        return t;
    }

    static ColorStateList focusColors(int focused, int normal) {
        return new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {focused, normal});
    }

    /** Filled in the accent colour when focused, clear otherwise. Radius 0 means fully round. */
    static Drawable pill(Context context, int color, int radius) {
        GradientDrawable on = new GradientDrawable();
        on.setColor(color);
        on.setCornerRadius(radius > 0 ? radius : dp(context, 999));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, on);
        states.addState(new int[] {}, new GradientDrawable());
        return states;
    }

    /**
     * A soft dark shade spreading from the top-left corner, behind the clock, fading out
     * evenly in every direction. Lay it out at least {@code radiusDp} square.
     */
    static View cornerShade(Context context, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(0, 0);
        g.setGradientRadius(dp(context, radiusDp));
        int rgb = color & 0x00FFFFFF, alpha = color >>> 24;
        g.setColors(new int[] {color, ((alpha * 2 / 5) << 24) | rgb, rgb});   // eases out towards the edge
        View v = new View(context);
        v.setBackground(g);
        return v;
    }

    /** A label with a smaller, quieter note after it. */
    static CharSequence note(String label, String note) {
        SpannableString s = new SpannableString(label + "   " + note);
        s.setSpan(new RelativeSizeSpan(0.72f), label.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
}

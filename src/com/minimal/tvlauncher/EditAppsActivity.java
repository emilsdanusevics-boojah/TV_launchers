package com.minimal.tvlauncher;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Choose and order the home screen's apps with the remote. Shown apps are listed first,
 * in order; every other installed app follows.
 *
 * OK shows or hides an app. On a shown app, Right picks it up; Up/Down then move it and
 * OK (or Left/Right/Back) puts it down. Changes are saved straight away.
 */
public class EditAppsActivity extends Activity {

    private static final int BG = 0xFF141416;
    private static final int FG = 0xFFECEBE8;
    private static final int MUTED = 0xFFA29F99;
    private static final int ACCENT = 0xFFD4A464;
    private static final int INK = 0xFF1D1A15;

    private Typeface light;
    private Typeface medium;
    private LinearLayout list;
    private ScrollView scroll;
    private TextView hint;
    private final List<AppList.Item> shown = new ArrayList<AppList.Item>();
    private List<AppList.Installed> installed;
    private final Map<String, String> labels = new HashMap<String, String>();
    private final Map<String, View> rows = new HashMap<String, View>();
    private String moving;   // package being moved, or null

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        light = Typeface.createFromAsset(getAssets(), "fonts/HankenGrotesk-300.ttf");
        medium = Typeface.createFromAsset(getAssets(), "fonts/HankenGrotesk-500.ttf");

        installed = AppList.installed(this);
        for (AppList.Installed app : installed) {
            labels.put(app.pkg, app.label);
        }
        shown.addAll(AppList.get(this));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(56), dp(36), dp(56), 0);

        TextView title = text(light, 40, FG);
        title.setText("Apps");
        column.addView(title);
        hint = text(medium, 14, MUTED);
        hint.setPadding(0, dp(6), 0, dp(4));
        column.addView(hint);
        String url = Remote.url();
        if (url != null) {
            TextView phone = text(medium, 14, MUTED);
            phone.setText("Or use your phone: " + url);
            column.addView(phone);
        }

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(16), 0, dp(36));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        column.addView(scroll, new LinearLayout.LayoutParams(dp(520), 0, 1));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        rebuild(shown.isEmpty() ? null : shown.get(0).pkg);
    }

    private void rebuild(String focusPkg) {
        list.removeAllViews();
        rows.clear();
        List<String> shownPkgs = new ArrayList<String>();
        for (AppList.Item item : shown) {
            shownPkgs.add(item.pkg);
        }
        section("On the home screen");
        for (AppList.Item item : shown) {
            if (labels.containsKey(item.pkg)) {
                row(item.pkg, item.name, true);
            }
        }
        section("Hidden");
        for (AppList.Installed app : installed) {
            if (!shownPkgs.contains(app.pkg)) {
                row(app.pkg, app.label, false);
            }
        }
        hint.setText(moving != null
                ? "Up/Down to move, OK to put it down"
                : "OK shows or hides an app. Right on a shown app picks it up to move it.");
        View target = focusPkg != null ? rows.get(focusPkg) : null;
        if (target == null && list.getChildCount() > 1) {
            target = list.getChildAt(1);
        }
        if (target != null) {
            target.requestFocus();
        }
    }

    private void section(String name) {
        TextView t = text(medium, 12, MUTED);
        t.setText(name.toUpperCase());
        t.setLetterSpacing(0.14f);
        t.setPadding(dp(14), dp(14), 0, dp(6));
        list.addView(t);
    }

    private void row(final String pkg, String name, final boolean isShown) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(11));
        row.setFocusable(true);
        boolean isMoving = pkg.equals(moving);
        row.setBackground(rowBackground(isMoving));

        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (isShown) {
            d.setColor(ACCENT);
        } else {
            d.setStroke(dp(2), MUTED);
        }
        dot.setBackground(d);
        dot.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotParams.rightMargin = dp(14);
        row.addView(dot, dotParams);

        TextView label = text(medium, 18, 0);
        label.setText(name);
        label.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {INK, isShown ? FG : MUTED}));
        label.setDuplicateParentStateEnabled(true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        if (isMoving) {
            TextView tag = text(medium, 13, INK);
            tag.setText("MOVING");
            tag.setLetterSpacing(0.14f);
            row.addView(tag);
        }

        row.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                return onRowKey(pkg, isShown, keyCode, event);
            }
        });
        rows.put(pkg, row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(2);
        list.addView(row, lp);
    }

    private boolean onRowKey(String pkg, boolean isShown, int keyCode, KeyEvent event) {
        boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (moving != null) {
            int i = indexOf(moving);
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                int step = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                int j = i + step;
                // Skip saved apps that aren't installed any more (they aren't listed here).
                while (j >= 0 && j < shown.size() && !labels.containsKey(shown.get(j).pkg)) {
                    j += step;
                }
                if (i >= 0 && j >= 0 && j < shown.size()) {
                    shown.add(j, shown.remove(i));
                    save();
                    rebuild(moving);
                }
                return true;
            }
            if (ok || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                String done = moving;
                moving = null;
                rebuild(done);
                return true;
            }
            return false;
        }
        if (ok && event.getRepeatCount() == 0) {
            if (isShown) {
                shown.remove(indexOf(pkg));
            } else {
                shown.add(new AppList.Item(pkg, labels.get(pkg)));
            }
            save();
            rebuild(pkg);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isShown) {
            moving = pkg;
            rebuild(pkg);
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (moving != null) {
            String done = moving;
            moving = null;
            rebuild(done);
            return;
        }
        super.onBackPressed();
    }

    private int indexOf(String pkg) {
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).pkg.equals(pkg)) {
                return i;
            }
        }
        return -1;
    }

    private void save() {
        AppList.set(this, new ArrayList<AppList.Item>(shown));
    }

    private StateListDrawable rowBackground(boolean isMoving) {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(ACCENT);
        focused.setCornerRadius(dp(8));
        if (isMoving) {
            focused.setStroke(dp(3), FG);
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[] {}, new GradientDrawable());
        return states;
    }

    private TextView text(Typeface face, float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        return t;
    }
}

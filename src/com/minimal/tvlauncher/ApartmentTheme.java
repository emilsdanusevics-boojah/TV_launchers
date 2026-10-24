package com.minimal.tvlauncher;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A modern, minimal launcher: a slowly turning 3D model of the apartment fills the screen
 * (three.js in a WebView, bundled in assets/apartment), with the clock, the apps and a
 * settings button laid over it. Colours and type follow the original Apartment 028 page.
 */
class ApartmentTheme implements LauncherTheme {

    private static final int SCENE = 0xFF1D1E20;
    private static final int FG = 0xFFECEBE8;
    private static final int MUTED = 0xFFA29F99;
    private static final int ACCENT = 0xFFD4A464;
    private static final int ACCENT_INK = 0xFF1D1A15;
    private static final int PANEL = 0xEB1E1F21;
    private static final int LINE = 0x24ECEBE8;

    private final MainActivity activity;
    private Typeface light;
    private Typeface medium;
    private WebView web;
    private TextView time;
    private TextView date;
    private LinearLayout bar;
    private LinearLayout appRow;
    private ImageView gear;
    private LinearLayout menu;
    private TextView menuTitle;
    private LinearLayout menuItems;

    ApartmentTheme(MainActivity activity) {
        this.activity = activity;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                activity.getResources().getDisplayMetrics()));
    }

    @Override
    public View createView() {
        light = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-300.ttf");
        medium = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-500.ttf");

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(SCENE);

        // The 3D model. If the TV has no working WebView the plain background stays.
        try {
            web = new WebView(activity);
            web.setFocusable(false);
            web.setFocusableInTouchMode(false);
            web.setBackgroundColor(SCENE);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setAllowFileAccess(true);
            web.loadUrl("file:///android_asset/apartment/index.html");
            root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        } catch (Exception e) {
            web = null;
        }

        // Soft shades behind the text at the top and bottom.
        root.addView(shade(GradientDrawable.Orientation.TOP_BOTTOM, 0xB31D1E20),
                new FrameLayout.LayoutParams(-1, dp(200), Gravity.TOP));
        root.addView(shade(GradientDrawable.Orientation.BOTTOM_TOP, 0xE01D1E20),
                new FrameLayout.LayoutParams(-1, dp(190), Gravity.BOTTOM));

        // Clock.
        LinearLayout clock = new LinearLayout(activity);
        clock.setOrientation(LinearLayout.VERTICAL);
        time = text(light, 88, FG);
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        date = text(medium, 14, MUTED);
        date.setLetterSpacing(0.14f);
        date.setAllCaps(true);
        clock.addView(time);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
        dateParams.topMargin = dp(10);
        dateParams.leftMargin = dp(4);
        clock.addView(date, dateParams);
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        clockParams.setMargins(dp(32), dp(40), 0, 0);
        root.addView(clock, clockParams);

        // Apps along the bottom, settings at the far right.
        bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(44), 0, dp(44), dp(40));
        appRow = new LinearLayout(activity);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(appRow, new LinearLayout.LayoutParams(0, -2, 1));
        gear = new ImageView(activity);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setImageTintList(focusColors(ACCENT_INK, MUTED));
        gear.setBackground(pill(ACCENT, 0));
        gear.setPadding(dp(11), dp(11), dp(11), dp(11));
        gear.setFocusable(true);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu();
            }
        });
        bar.addView(gear, new LinearLayout.LayoutParams(dp(46), dp(46)));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // The settings menu: a small panel above the gear.
        menu = new LinearLayout(activity);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(14), dp(8), dp(8));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(PANEL);
        panel.setCornerRadius(dp(12));
        panel.setStroke(dp(1), LINE);
        menu.setBackground(panel);
        menu.setElevation(dp(8));
        menuTitle = text(medium, 11, MUTED);
        menuTitle.setLetterSpacing(0.14f);
        menuTitle.setAllCaps(true);
        menuTitle.setPadding(dp(14), 0, dp(14), dp(8));
        menu.addView(menuTitle);
        menuItems = new LinearLayout(activity);
        menuItems.setOrientation(LinearLayout.VERTICAL);
        menu.addView(menuItems);
        menu.setVisibility(View.GONE);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(280), -2, Gravity.BOTTOM | Gravity.END);
        menuParams.setMargins(0, 0, dp(44), dp(100));
        root.addView(menu, menuParams);

        return root;
    }

    @Override
    public void setApps(List<Apps.Entry> apps) {
        appRow.removeAllViews();
        for (final Apps.Entry app : apps) {
            TextView item = text(medium, 18, 0);
            item.setText(app.label);
            item.setTextColor(focusColors(ACCENT_INK, FG));
            item.setBackground(pill(ACCENT, 0));
            item.setPadding(dp(20), dp(10), dp(20), dp(11));
            item.setFocusable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Apps.launch(activity, app);
                }
            });
            // Long-press OK: the app's info page (uninstall, force stop, storage).
            item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return Apps.openAppInfo(activity, app);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(6);
            appRow.addView(item, lp);
        }
        if (!gear.hasFocus() && menu.getVisibility() != View.VISIBLE) {
            focusFirst();
        }
    }

    @Override
    public void onTimeChanged() {
        Date now = new Date();
        String fmt = DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm";
        time.setText(DateFormat.format(fmt, now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
    }

    @Override
    public void onResume() {
        if (web != null) {
            web.onResume();
            web.evaluateJavascript("window.launcherPause && launcherPause(false)", null);
        }
        if (menu.getVisibility() != View.VISIBLE && !gear.hasFocus()) {
            focusFirst();
        }
    }

    @Override
    public void onPause() {
        // Stop drawing the 3D model while an app is in front.
        if (web != null) {
            web.evaluateJavascript("window.launcherPause && launcherPause(true)", null);
            web.onPause();
        }
    }

    @Override
    public void onHome() {
        closeMenu();
        focusFirst();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettingsMenu();
            return true;
        }
        return false;
    }

    @Override
    public boolean onBack() {
        if (menu.getVisibility() == View.VISIBLE) {
            closeMenu();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (web != null) {
            ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
    }

    // ---- Settings menu ----

    private void showSettingsMenu() {
        openMenu("Settings");
        addMenuItem("TV settings", new Runnable() {
            @Override
            public void run() {
                closeMenu();
                Apps.openSettings(activity);
            }
        });
        addMenuItem("Change launcher", new Runnable() {
            @Override
            public void run() {
                showLauncherMenu();
            }
        });
        menuItems.getChildAt(0).requestFocus();
    }

    private void showLauncherMenu() {
        openMenu("Launcher");
        View current = null;
        for (int i = 0; i < MainActivity.LAUNCHER_IDS.length; i++) {
            final String id = MainActivity.LAUNCHER_IDS[i];
            String name = MainActivity.LAUNCHER_NAMES[i];
            boolean isCurrent = id.equals(activity.currentLauncher());
            CharSequence label = name;
            if (isCurrent) {
                SpannableString s = new SpannableString(name + "   current");
                s.setSpan(new RelativeSizeSpan(0.72f), name.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                label = s;
            }
            View item = addMenuItem(label, new Runnable() {
                @Override
                public void run() {
                    closeMenu();
                    activity.switchLauncher(id);
                }
            });
            if (isCurrent) {
                current = item;
            }
        }
        (current != null ? current : menuItems.getChildAt(0)).requestFocus();
    }

    private void openMenu(String title) {
        menuTitle.setText(title);
        menuItems.removeAllViews();
        menu.setVisibility(View.VISIBLE);
        // Keep the remote inside the menu while it is open.
        bar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private void closeMenu() {
        if (menu.getVisibility() != View.VISIBLE) {
            return;
        }
        menu.setVisibility(View.GONE);
        menuItems.removeAllViews();
        bar.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        gear.requestFocus();
    }

    private View addMenuItem(CharSequence label, final Runnable action) {
        TextView item = text(medium, 17, 0);
        item.setText(label);
        item.setTextColor(focusColors(ACCENT_INK, FG));
        item.setBackground(pill(ACCENT, dp(8)));
        item.setPadding(dp(14), dp(11), dp(14), dp(12));
        item.setFocusable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        menuItems.addView(item, new LinearLayout.LayoutParams(-1, -2));
        return item;
    }

    // ---- Helpers ----

    private void focusFirst() {
        View first = appRow.getChildCount() > 0 ? appRow.getChildAt(0) : gear;
        first.requestFocus();
    }

    private TextView text(Typeface face, float sp, int color) {
        TextView t = new TextView(activity);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        return t;
    }

    private static ColorStateList focusColors(int focused, int normal) {
        return new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {focused, normal});
    }

    /** Filled in the accent colour when focused, clear otherwise. Radius 0 means fully round. */
    private Drawable pill(int color, int radius) {
        GradientDrawable on = new GradientDrawable();
        on.setColor(color);
        on.setCornerRadius(radius > 0 ? radius : dp(999));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, on);
        states.addState(new int[] {}, new GradientDrawable());
        return states;
    }

    private View shade(GradientDrawable.Orientation orientation, int color) {
        View v = new View(activity);
        v.setBackground(new GradientDrawable(orientation, new int[] {color, 0x001D1E20}));
        return v;
    }
}

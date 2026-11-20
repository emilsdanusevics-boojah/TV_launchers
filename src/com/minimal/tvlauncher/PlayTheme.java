package com.minimal.tvlauncher;

import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Play: a practical launcher of big tiles that each start something in one press, such as
 * "Evening playlist" (a Spotify playlist), "News" (a channel) or "Continue watching" (an
 * app). The tiles are set up on the phone page; until then there's one per app on the home
 * screen. Calm, dark, quick: the clock in the corner, the tiles in the middle, the gear below.
 */
class PlayTheme implements LauncherTheme {

    // Muted tile colours, one after another.
    private static final int[] TILE_COLORS = {0xFF6B4E3D, 0xFF3F5A54, 0xFF4E4A6B, 0xFF6B5A35, 0xFF3D4E6B, 0xFF6B3D4E};
    private static final int BG = 0xFF141416;
    private static final int FG = PanelMenu.FG;
    private static final int MUTED = PanelMenu.MUTED;

    private final MainActivity activity;
    private Typeface light;
    private Typeface medium;
    private TextView time;
    private TextView date;
    private LinearLayout grid;
    private LinearLayout bottom;
    private ImageView gear;
    private PanelMenu menu;
    private AmbientClock ambientClock;
    private View curtain;    // a warm fade to dark as a tile starts
    private List<Apps.Entry> apps = new ArrayList<Apps.Entry>();

    PlayTheme(MainActivity activity) {
        this.activity = activity;
    }

    private int dp(float v) {
        return PanelMenu.dp(activity, v);
    }

    @Override
    public View createView() {
        light = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-300.ttf");
        medium = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-500.ttf");
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(BG);

        LinearLayout clock = new LinearLayout(activity);
        clock.setOrientation(LinearLayout.VERTICAL);
        time = PanelMenu.text(activity, light, 56, FG);
        time.setIncludeFontPadding(false);
        date = PanelMenu.text(activity, medium, 13, MUTED);
        date.setAllCaps(true);
        date.setLetterSpacing(0.14f);
        clock.addView(time);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
        dp.topMargin = dp(6);
        dp.leftMargin = dp(3);
        clock.addView(date, dp);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        cp.setMargins(dp(48), dp(34), 0, 0);
        root.addView(clock, cp);

        // Up to six tiles, three to a row, in the middle of the screen.
        grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        gp.setMargins(dp(48), dp(40), dp(48), 0);
        root.addView(grid, gp);

        bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = PanelMenu.text(activity, medium, 13, MUTED);
        hint.setText("Set up the tiles on the phone page");
        bottom.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
        gear = new ImageView(activity);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setImageTintList(PanelMenu.focusColors(BG, MUTED));
        gear.setBackground(PanelMenu.pill(activity, FG, 0));
        gear.setPadding(dp(11), dp(11), dp(11), dp(11));
        gear.setFocusable(true);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu();
            }
        });
        bottom.addView(gear, new LinearLayout.LayoutParams(dp(46), dp(46)));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bp.setMargins(dp(48), 0, dp(40), dp(26));
        root.addView(bottom, bp);

        menu = new PanelMenu(activity, medium, bottom, new PanelMenu.OnClose() {
            @Override
            public void onClose() {
                gear.requestFocus();
            }
        });
        menu.setColors(FG, BG);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(300), -2, Gravity.BOTTOM | Gravity.END);
        mp.setMargins(0, dp(24), dp(44), dp(90));
        root.addView(menu.view, mp);

        ambientClock = new AmbientClock(activity);
        root.addView(ambientClock, ambientClock.layout());
        curtain = new View(activity);
        curtain.setBackgroundColor(0xFF120B06);
        curtain.setAlpha(0f);
        curtain.setVisibility(View.GONE);
        root.addView(curtain, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    /** The tiles were changed on the phone. */
    void refreshTiles() {
        build();
    }

    private void build() {
        grid.removeAllViews();
        List<PlayTiles.Tile> tiles = PlayTiles.load(activity);
        if (tiles.isEmpty()) {
            // Not set up yet: one tile per home-screen app.
            for (Apps.Entry app : apps) {
                if (tiles.size() >= PlayTiles.MAX) {
                    break;
                }
                PlayTiles.Tile t = new PlayTiles.Tile();
                t.label = app.label;
                t.pkg = app.component.getPackageName();
                t.link = "";
                tiles.add(t);
            }
        }
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(150));
                rp.bottomMargin = dp(18);
                grid.addView(row, rp);
            }
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -1, 1);
            tp.rightMargin = i % 3 == 2 ? 0 : dp(18);
            row.addView(tile(tiles.get(i), TILE_COLORS[i % TILE_COLORS.length]), tp);
        }
        // Keep the last row's tiles the same width as the others.
        if (row != null) {
            for (int k = row.getChildCount(); k < 3 && tiles.size() > 3; k++) {
                View spacer = new View(activity);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -1, 1);
                sp.rightMargin = k == 2 ? 0 : dp(18);
                row.addView(spacer, sp);
            }
        }
        if (grid.getChildCount() > 0 && !menu.isOpen()) {
            ((LinearLayout) grid.getChildAt(0)).getChildAt(0).requestFocus();
        }
    }

    private View tile(final PlayTiles.Tile t, int color) {
        LinearLayout v = new LinearLayout(activity);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.BOTTOM);
        v.setPadding(dp(22), dp(18), dp(22), dp(18));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(lighten(color));
        focused.setCornerRadius(dp(16));
        focused.setStroke(dp(3), FG);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(color);
        normal.setCornerRadius(dp(16));
        bg.addState(new int[] {android.R.attr.state_focused}, focused);
        bg.addState(new int[0], normal);
        v.setBackground(bg);
        v.setFocusable(true);

        ImageView icon = new ImageView(activity);
        Drawable d = appIcon(t.pkg);
        if (d != null) {
            icon.setImageDrawable(d);
        }
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(36), dp(36));
        ip.bottomMargin = dp(10);
        v.addView(icon, ip);
        TextView label = PanelMenu.text(activity, medium, 24, FG);
        label.setText(t.label);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.addView(label);
        TextView sub = PanelMenu.text(activity, medium, 13, 0xCCECEBE8);
        sub.setText((t.link.isEmpty() ? "Opens " + appName(t.pkg) : "Plays in " + appName(t.pkg))
                + (t.minutes > 0 ? " · " + t.minutes + " min" : ""));
        sub.setSingleLine(true);
        v.addView(sub);

        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                view.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(150).start();
            }
        });
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Fade gently to a warm dark, then start it: movie night begins softly.
                curtain.animate().cancel();
                curtain.setVisibility(View.VISIBLE);
                curtain.animate().alpha(1f).setDuration(600).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!PlayTiles.start(activity, t, apps)) {
                            curtain.setAlpha(0f);
                            curtain.setVisibility(View.GONE);
                            Toast.makeText(activity, appName(t.pkg) + " isn't installed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).start();
            }
        });
        return v;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 28);
        int g = Math.min(255, ((c >> 8) & 255) + 28);
        int b = Math.min(255, (c & 255) + 28);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private Drawable appIcon(String pkg) {
        try {
            return activity.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private String appName(String pkg) {
        for (Apps.Entry a : apps) {
            if (a.component.getPackageName().equals(pkg)) {
                return a.label;
            }
        }
        try {
            PackageManager pm = activity.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    // ---- LauncherTheme ----

    @Override
    public void setApps(List<Apps.Entry> list) {
        apps = list;
        build();
    }

    @Override
    public void onTimeChanged() {
        Date now = new Date();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
        ambientClock.tick();
    }

    @Override
    public void onResume() {
        curtain.animate().cancel();
        curtain.setAlpha(0f);
        curtain.setVisibility(View.GONE);
        if (!menu.isOpen() && grid.findFocus() == null && grid.getChildCount() > 0) {
            ((LinearLayout) grid.getChildAt(0)).getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onHome() {
        menu.close();
        if (grid.getChildCount() > 0) {
            ((LinearLayout) grid.getChildAt(0)).getChildAt(0).requestFocus();
        }
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
        if (menu.isOpen()) {
            menu.close();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void setAmbient(boolean ambient) {
        if (ambient) {
            menu.close();
        }
        AmbientClock.fade(ambient, grid, bottom, time, date);
        ambientClock.show(ambient);
    }

    private void showSettingsMenu() {
        menu.open("Settings");
        menu.add("TV settings", new Runnable() {
            @Override
            public void run() {
                menu.close();
                Apps.openSettings(activity);
            }
        });
        menu.add("Edit apps", new Runnable() {
            @Override
            public void run() {
                menu.close();
                activity.editApps();
            }
        });
        menu.add("Change launcher", new Runnable() {
            @Override
            public void run() {
                menu.showLaunchers(activity);
            }
        });
        menu.phoneFooter();
        if (menu.view.findFocus() == null) {
            menu.focus(0);
        }
    }
}

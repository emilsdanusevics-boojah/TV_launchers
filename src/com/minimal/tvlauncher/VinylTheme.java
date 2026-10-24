package com.minimal.tvlauncher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * A launcher just for listening to Spotify. While music plays the screen shows only a
 * turning record with the album cover as its label, over the cover's colours.
 *
 * Remote: OK play/pause, Left/Right previous/next track, Down opens Spotify,
 * Up or Menu opens the menu. With nothing playing there is an "Open Spotify" button.
 */
class VinylTheme implements LauncherTheme, NowPlaying.Listener {

    private static final String[] SPOTIFY_PACKAGES = {"com.spotify.tv.android", "com.spotify.music"};
    private static final int[] IDLE_COLORS = {0xFF18181B, 0xFF26262B, 0xFF0E0E10};
    private static final int FG = 0xFFF2F1EE;
    private static final int MUTED = 0xB3F2F1EE;
    private static final int INK = 0xFF141416;
    private static final int PANEL = 0xE6141416;
    private static final int LINE = 0x24FFFFFF;

    private final MainActivity activity;
    private NowPlaying nowPlaying;
    private Typeface medium;
    private VinylView vinyl;
    private LinearLayout idle;
    private TextView idleTitle;
    private TextView idleText;
    private TextView openButton;
    private LinearLayout menu;
    private TextView menuTitle;
    private LinearLayout menuItems;
    private Apps.Entry spotify;

    VinylTheme(MainActivity activity) {
        this.activity = activity;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                activity.getResources().getDisplayMetrics()));
    }

    @Override
    public View createView() {
        medium = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-500.ttf");
        Typeface light = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-300.ttf");

        FrameLayout root = new FrameLayout(activity);
        vinyl = new VinylView(activity);
        root.addView(vinyl, new FrameLayout.LayoutParams(-1, -1));

        // Shown only when nothing is playing, to the left of the record.
        idle = new LinearLayout(activity);
        idle.setOrientation(LinearLayout.VERTICAL);
        idleTitle = text(light, 30, FG);
        idleText = text(medium, 14, MUTED);
        idleText.setSingleLine(false);
        idleText.setLineSpacing(dp(3), 1f);
        idle.addView(idleTitle);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2);
        textParams.topMargin = dp(8);
        textParams.bottomMargin = dp(20);
        idle.addView(idleText, textParams);
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        openButton = text(medium, 18, 0);
        openButton.setText("Open Spotify");
        openButton.setTextColor(focusColors(INK, FG));
        openButton.setBackground(pill(FG, 0, 0x26FFFFFF));
        openButton.setPadding(dp(20), dp(10), dp(20), dp(11));
        openButton.setFocusable(true);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSpotify();
            }
        });
        buttons.addView(openButton);
        ImageView gear = new ImageView(activity);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setImageTintList(focusColors(INK, MUTED));
        gear.setBackground(pill(FG, 0, 0));
        gear.setPadding(dp(11), dp(11), dp(11), dp(11));
        gear.setFocusable(true);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu();
            }
        });
        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        gearParams.leftMargin = dp(8);
        buttons.addView(gear, gearParams);
        idle.addView(buttons);
        FrameLayout.LayoutParams idleParams = new FrameLayout.LayoutParams(dp(250), -2, Gravity.BOTTOM | Gravity.START);
        idleParams.setMargins(dp(44), 0, 0, dp(44));
        root.addView(idle, idleParams);

        // Menu panel, same idea as the Apartment launcher's.
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
        menuParams.setMargins(0, 0, dp(44), dp(44));
        root.addView(menu, menuParams);

        nowPlaying = new NowPlaying(activity, this);
        showIdle(NowPlaying.NOTHING);
        return root;
    }

    @Override
    public void setApps(List<Apps.Entry> apps) {
        spotify = null;
        for (Apps.Entry app : apps) {
            if (app.component.getPackageName().contains("spotify")) {
                spotify = app;
            }
        }
    }

    @Override
    public void onTimeChanged() {
    }

    @Override
    public void onResume() {
        vinyl.setRunning(true);
        nowPlaying.start();
    }

    @Override
    public void onPause() {
        vinyl.setRunning(false);
        nowPlaying.stop();
    }

    @Override
    public void onHome() {
        closeMenu();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettingsMenu();
            return true;
        }
        if (menu.getVisibility() == View.VISIBLE || idle.getVisibility() == View.VISIBLE) {
            return false;   // the buttons handle the remote
        }
        // The record is on screen.
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() == 0) {
                    nowPlaying.playPause();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                nowPlaying.previous();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                nowPlaying.next();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                openSpotify();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                showSettingsMenu();
                return true;
            default:
                return false;
        }
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
        nowPlaying.stop();
    }

    // ---- Now playing ----

    @Override
    public void onStateChanged(int state) {
        vinyl.setPlaying(state == NowPlaying.PLAYING);
        if (state == NowPlaying.PLAYING || state == NowPlaying.PAUSED) {
            // Only the record: hide everything else.
            idle.setVisibility(View.GONE);
            if (menu.getVisibility() != View.VISIBLE) {
                vinyl.requestFocus();
            }
        } else {
            showIdle(state);
            vinyl.setCover(null);
            vinyl.setColors(IDLE_COLORS);
        }
    }

    @Override
    public void onArtChanged(Bitmap art, int[] colors) {
        vinyl.setCover(art);
        vinyl.setColors(colors != null ? colors : IDLE_COLORS);
    }

    private void showIdle(int state) {
        if (state == NowPlaying.NO_ACCESS) {
            idleTitle.setText("One more step");
            idleText.setText("To see what Spotify is playing, this launcher needs notification access. "
                    + "On your computer run:\n\nadb shell cmd notification allow_listener "
                    + "com.minimal.tvlauncher/com.minimal.tvlauncher.MediaListener");
        } else {
            idleTitle.setText("Nothing playing");
            idleText.setText("Start some music in Spotify and it will turn up here.");
        }
        idle.setVisibility(View.VISIBLE);
        if (menu.getVisibility() != View.VISIBLE) {
            openButton.requestFocus();
        }
    }

    private void openSpotify() {
        if (spotify != null && Apps.launch(activity, spotify)) {
            return;
        }
        PackageManager pm = activity.getPackageManager();
        for (String pkg : SPOTIFY_PACKAGES) {
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) {
                intent = pm.getLaunchIntentForPackage(pkg);
            }
            if (intent != null && Apps.start(activity, intent)) {
                return;
            }
        }
    }

    // ---- Menu ----

    private void showSettingsMenu() {
        openMenu("Settings");
        addMenuItem("Open Spotify", new Runnable() {
            @Override
            public void run() {
                closeMenu();
                openSpotify();
            }
        });
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
        idle.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private void closeMenu() {
        if (menu.getVisibility() != View.VISIBLE) {
            return;
        }
        menu.setVisibility(View.GONE);
        menuItems.removeAllViews();
        idle.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (idle.getVisibility() == View.VISIBLE) {
            openButton.requestFocus();
        } else {
            vinyl.requestFocus();
        }
    }

    private View addMenuItem(CharSequence label, final Runnable action) {
        TextView item = text(medium, 17, 0);
        item.setText(label);
        item.setTextColor(focusColors(INK, FG));
        item.setBackground(pill(FG, dp(8), 0));
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

    /** Filled when focused, {@code idleFill} otherwise. Radius 0 means fully round. */
    private Drawable pill(int color, int radius, int idleFill) {
        GradientDrawable on = new GradientDrawable();
        on.setColor(color);
        on.setCornerRadius(radius > 0 ? radius : dp(999));
        GradientDrawable off = new GradientDrawable();
        off.setColor(idleFill);
        off.setCornerRadius(radius > 0 ? radius : dp(999));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, on);
        states.addState(new int[] {}, off);
        return states;
    }
}

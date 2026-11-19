package com.minimal.tvlauncher;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The everyday launcher: a full-screen photo that slowly drifts and zooms, crossfading to
 * the next every 45 seconds, with the clock, the date, today's Latvian name days and the
 * weather in the corner, and the apps as large tiles (their TV banners) along the bottom.
 *
 * Photos come from the phone page (files/photos on the TV), or else a few built-in photos
 * of Riga (assets/photos, credited in the corner as their licence asks). On Latvia's red
 * days a greeting appears and the tiles take the holiday's colour.
 */
class PhotosTheme implements LauncherTheme {

    private static final long PHOTO_MS = 45000;
    private static final long FADE_MS = 2500;
    private static final float ZOOM = 1.12f;
    private static final long WEATHER_EVERY_MS = 15 * 60 * 1000;
    private static final String PHOTOS_DIR = "photos";
    private static final List<String> IMAGE_TYPES = Arrays.asList("jpg", "jpeg", "png", "webp");

    private static final int FG = PanelMenu.FG;
    private static final int MUTED = PanelMenu.MUTED;
    private static final int ACCENT = 0xFFD4A464;
    private static final int ACCENT_INK = 0xFF1D1A15;

    private final MainActivity activity;
    private final Handler handler = new Handler();
    private final Random random = new Random();
    private SharedPreferences prefs;
    private Typeface light;
    private Typeface medium;
    private ImageView back;      // the photo showing now
    private ImageView front;     // the next one, fading in over it
    private TextView time;
    private TextView date;
    private TextView names;
    private TextView weatherLine;
    private TextView holidayLine;
    private TextView credit;
    private LinearLayout tiles;
    private ImageView gear;
    private LinearLayout bottom;
    private PanelMenu menu;
    private View cornerShade;
    private View bottomShade;
    private LinearLayout corner;
    private AmbientClock ambientClock;
    private PhotoEffects effects;
    private List<Apps.Entry> apps;
    private int accent = ACCENT;
    private int ink = ACCENT_INK;
    private String shownHoliday = "";

    // Photos: phone uploads (files) or the built-in ones (asset names), with credits.
    private final List<String> photos = new ArrayList<String>();
    private final Map<String, String> credits = new HashMap<String, String>();
    private boolean builtIn;
    private int photoIndex = -1;
    private int screenW = 1920, screenH = 1080;
    private int request;

    private final Runnable nextPhoto = new Runnable() {
        @Override
        public void run() {
            showNext();
        }
    };

    private final Runnable weatherTimer = new Runnable() {
        @Override
        public void run() {
            Weather.refresh(new Weather.Callback() {
                @Override
                public void onWeather(Weather.Now now) {
                    showWeather(now);
                }
            });
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    PhotosTheme(MainActivity activity) {
        this.activity = activity;
    }

    private int dp(float v) {
        return PanelMenu.dp(activity, v);
    }

    @Override
    public View createView() {
        prefs = activity.getSharedPreferences("launcher", MainActivity.MODE_PRIVATE);
        light = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-300.ttf");
        medium = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-500.ttf");
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(size);
        screenW = Math.max(size.x, size.y);
        screenH = Math.min(size.x, size.y);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xFF101012);
        back = photoView();
        front = photoView();
        root.addView(back, new FrameLayout.LayoutParams(-1, -1));
        root.addView(front, new FrameLayout.LayoutParams(-1, -1));
        // Rain on the glass, snow, frost, holiday sparks: over the photos, under the text.
        effects = new PhotoEffects(activity);
        root.addView(effects, new FrameLayout.LayoutParams(-1, -1));

        // Shades so the text and tiles read on any photo.
        cornerShade = PanelMenu.cornerShade(activity, 0xB0000000, 560);
        root.addView(cornerShade, new FrameLayout.LayoutParams(dp(560), dp(560), Gravity.TOP | Gravity.START));
        bottomShade = shade(GradientDrawable.Orientation.BOTTOM_TOP, 0xC0000000);
        root.addView(bottomShade, new FrameLayout.LayoutParams(-1, dp(240), Gravity.BOTTOM));

        // Clock, date, name days, weather, holiday greeting.
        corner = new LinearLayout(activity);
        corner.setOrientation(LinearLayout.VERTICAL);
        time = PanelMenu.text(activity, light, 96, FG);
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        date = PanelMenu.text(activity, medium, 14, 0xE6ECEBE8);
        date.setLetterSpacing(0.14f);
        date.setAllCaps(true);
        names = PanelMenu.text(activity, medium, 15, 0xD9ECEBE8);
        weatherLine = PanelMenu.text(activity, medium, 16, FG);
        weatherLine.setCompoundDrawablePadding(dp(8));
        weatherLine.setGravity(Gravity.CENTER_VERTICAL);
        holidayLine = PanelMenu.text(activity, medium, 16, ACCENT);
        holidayLine.setVisibility(View.GONE);
        corner.addView(time);
        corner.addView(date, under(10));
        corner.addView(names, under(6));
        corner.addView(weatherLine, under(8));
        corner.addView(holidayLine, under(6));
        for (TextView t : new TextView[] {time, date, names, weatherLine, holidayLine}) {
            t.setShadowLayer(dp(8), 0, dp(1), 0xAA000000);
        }
        FrameLayout.LayoutParams cornerParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        cornerParams.setMargins(dp(40), dp(36), 0, 0);
        root.addView(corner, cornerParams);
        showWeather(Weather.cached());

        // The photo's credit, quietly in the top-right corner.
        credit = PanelMenu.text(activity, medium, 11, 0x99ECEBE8);
        credit.setShadowLayer(dp(4), 0, dp(1), 0xAA000000);
        FrameLayout.LayoutParams creditParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        creditParams.setMargins(0, dp(20), dp(28), 0);
        root.addView(credit, creditParams);

        // App tiles along the bottom, settings at the far right.
        bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(8), dp(14), dp(8), dp(14));   // room for a focused tile to grow
        tiles = new LinearLayout(activity);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setClipChildren(false);
        scroll.addView(tiles);
        bottom.addView(scroll, new LinearLayout.LayoutParams(0, -2, 1));
        gear = new ImageView(activity);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setPadding(dp(12), dp(12), dp(12), dp(12));
        gear.setFocusable(true);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu();
            }
        });
        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        gearParams.leftMargin = dp(12);
        bottom.addView(gear, gearParams);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bottomParams.setMargins(dp(32), 0, dp(36), dp(22));
        root.addView(bottom, bottomParams);

        menu = new PanelMenu(activity, medium, bottom, new PanelMenu.OnClose() {
            @Override
            public void onClose() {
                gear.requestFocus();
            }
        });
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(300), -2, Gravity.BOTTOM | Gravity.END);
        menuParams.setMargins(0, dp(24), dp(36), dp(140));
        root.addView(menu.view, menuParams);
        paintAccent();

        ambientClock = new AmbientClock(activity);
        root.addView(ambientClock, ambientClock.layout());

        loadPhotoList();
        return root;
    }

    /** The weather effects were turned on or off on the phone. */
    void refreshEffects() {
        effects.refresh();
    }

    @Override
    public void setAmbient(boolean ambient) {
        // Only the photos, drifting and changing as before.
        if (ambient) {
            menu.close();
        }
        AmbientClock.fade(ambient, cornerShade, bottomShade, corner, credit, bottom);
        ambientClock.show(ambient);
    }

    private LinearLayout.LayoutParams under(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(top);
        lp.leftMargin = dp(4);
        return lp;
    }

    @Override
    public void setApps(List<Apps.Entry> list) {
        apps = list;
        tiles.removeAllViews();
        PackageManager pm = activity.getPackageManager();
        for (final Apps.Entry app : list) {
            tiles.addView(tile(pm, app), tileParams());
        }
        if (!gear.hasFocus() && !menu.isOpen()) {
            focusFirst();
        }
    }

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(160), dp(90));
        lp.rightMargin = dp(16);
        return lp;
    }

    // One app: its TV banner, or its icon and name on a dark card. Grows and gets an outline
    // in the accent colour when focused.
    private View tile(PackageManager pm, final Apps.Entry app) {
        FrameLayout tile = new FrameLayout(activity);
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xD91C1C1F);
        card.setCornerRadius(dp(12));
        tile.setBackground(card);
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });
        tile.setClipToOutline(true);
        tile.setElevation(dp(4));
        Drawable banner = null;
        try {
            banner = pm.getActivityBanner(app.component);
            if (banner == null) {
                banner = pm.getApplicationBanner(app.component.getPackageName());
            }
        } catch (Exception ignored) {
            // No banner: icon and name instead.
        }
        if (banner != null) {
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageDrawable(banner);
            tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout inner = new LinearLayout(activity);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            try {
                ImageView icon = new ImageView(activity);
                icon.setImageDrawable(pm.getActivityIcon(app.component));
                inner.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            } catch (Exception ignored) {
                // Name only.
            }
            TextView label = PanelMenu.text(activity, medium, 15, FG);
            label.setText(app.label);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(6);
            inner.addView(label, lp);
            tile.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        tile.setForeground(focusOutline());
        tile.setFocusable(true);
        tile.setContentDescription(app.label);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.animate().scaleX(hasFocus ? 1.1f : 1f).scaleY(hasFocus ? 1.1f : 1f)
                        .translationZ(hasFocus ? dp(8) : 0).setDuration(150).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.launch(activity, app);
            }
        });
        // Long-press OK: the app's info page (uninstall, force stop, storage).
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return Apps.openAppInfo(activity, app);
            }
        });
        return tile;
    }

    private Drawable focusOutline() {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0);
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(3), accent);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, ring);
        states.addState(new int[] {}, new GradientDrawable());
        return states;
    }

    @Override
    public void onTimeChanged() {
        Date now = new Date();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
        String today = NameDays.today(activity);
        names.setText(today == null ? "" : "Vārda dienas · " + today);
        names.setVisibility(today == null ? View.GONE : View.VISIBLE);
        showHoliday();
        ambientClock.tick();
    }

    @Override
    public void onResume() {
        effects.start();
        handler.removeCallbacks(weatherTimer);
        weatherTimer.run();
        showHoliday();
        if (photoIndex < 0) {
            showNext();
        } else {
            drift(back, PHOTO_MS, false);   // carry on drifting, then move on
            handler.removeCallbacks(nextPhoto);
            handler.postDelayed(nextPhoto, PHOTO_MS);
        }
        if (!menu.isOpen() && !gear.hasFocus()) {
            focusFirst();
        }
    }

    @Override
    public void onPause() {
        effects.stop();
        handler.removeCallbacks(weatherTimer);
        handler.removeCallbacks(nextPhoto);
        back.animate().cancel();
        front.animate().cancel();
    }

    @Override
    public void onHome() {
        menu.close();
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
        if (menu.isOpen()) {
            menu.close();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(weatherTimer);
        handler.removeCallbacks(nextPhoto);
    }

    /** Photos were added or removed from the phone: start again with the new set. */
    void refreshPhotos() {
        loadPhotoList();
        photoIndex = -1;
        handler.removeCallbacks(nextPhoto);
        showNext();
    }

    // ---- Photos ----

    private ImageView photoView() {
        ImageView v = new ImageView(activity);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    private File photosDir() {
        File root = activity.getExternalFilesDir(null);
        return root == null ? null : new File(root, PHOTOS_DIR);
    }

    private void loadPhotoList() {
        photos.clear();
        builtIn = false;
        File dir = photosDir();
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                int dot = name.lastIndexOf('.');
                if (f.isFile() && dot > 0 && IMAGE_TYPES.contains(name.substring(dot + 1))) {
                    photos.add(f.getPath());
                }
            }
        }
        if (photos.isEmpty()) {
            builtIn = true;
            try {
                for (String name : activity.getAssets().list(PHOTOS_DIR)) {
                    if (name.endsWith(".jpg")) {
                        photos.add(name);
                    }
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(activity.getAssets().open(PHOTOS_DIR + "/credits.txt"), "UTF-8"));
                String line;
                while ((line = in.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab > 0 && !line.startsWith("#")) {
                        credits.put(line.substring(0, tab), line.substring(tab + 1));
                    }
                }
                in.close();
            } catch (Exception ignored) {
                // No built-in photos: the plain background stays.
            }
            java.util.Collections.sort(photos);
        }
    }

    // Loads the next photo off the main thread, then fades it in and sets it drifting.
    private void showNext() {
        handler.removeCallbacks(nextPhoto);
        if (photos.isEmpty()) {
            return;
        }
        photoIndex = (photoIndex + 1) % photos.size();
        final String photo = photos.get(photoIndex);
        final boolean asset = builtIn;
        final int req = ++request;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = decode(photo, asset);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (req != request || activity.isDestroyed()) {
                            return;
                        }
                        if (bitmap != null) {
                            crossfade(bitmap, asset ? credits.get(photo) : null);
                        }
                        handler.postDelayed(nextPhoto, PHOTO_MS);
                    }
                });
            }
        }).start();
    }

    // Decoded a little larger than the screen (for the zoom), no bigger.
    private Bitmap decode(String photo, boolean asset) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            read(photo, asset, bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (bounds.outWidth / (opts.inSampleSize * 2) >= screenW * ZOOM
                    && bounds.outHeight / (opts.inSampleSize * 2) >= screenH * ZOOM) {
                opts.inSampleSize *= 2;
            }
            return read(photo, asset, opts);
        } catch (Throwable e) {
            return null;
        }
    }

    private Bitmap read(String photo, boolean asset, BitmapFactory.Options opts) throws Exception {
        InputStream in = asset ? activity.getAssets().open(PHOTOS_DIR + "/" + photo) : new java.io.FileInputStream(photo);
        try {
            return BitmapFactory.decodeStream(in, null, opts);
        } finally {
            in.close();
        }
    }

    private void crossfade(Bitmap bitmap, String by) {
        final ImageView incoming = front;
        incoming.animate().cancel();
        incoming.setImageBitmap(bitmap);
        incoming.setAlpha(0f);
        // Just above the current photo, still below the text and tiles.
        FrameLayout parent = (FrameLayout) incoming.getParent();
        parent.removeView(incoming);
        parent.addView(incoming, parent.indexOfChild(back) + 1, new FrameLayout.LayoutParams(-1, -1));
        drift(incoming, PHOTO_MS + FADE_MS, true);
        incoming.animate().alpha(1f).setDuration(FADE_MS).withEndAction(new Runnable() {
            @Override
            public void run() {
                ImageView old = back;
                back = incoming;
                front = old;
                front.animate().cancel();
                front.setAlpha(0f);
                front.setImageDrawable(null);
            }
        }).start();
        credit.setText(by == null ? "" : by);
    }

    // A slow zoom and pan in a random direction (the "Ken Burns" effect): in or out, from
    // the start for a new photo, or on from where it is after a pause.
    private void drift(ImageView v, long ms, boolean fresh) {
        float room = (ZOOM - 1) / 2;
        float dx = (random.nextFloat() * 2 - 1) * room * screenW, dy = (random.nextFloat() * 2 - 1) * room * screenH * 0.6f;
        float toZoom;
        if (fresh) {
            float fromZoom = random.nextBoolean() ? 1f : ZOOM;
            toZoom = fromZoom == 1f ? ZOOM : 1f;
            v.setScaleX(fromZoom);
            v.setScaleY(fromZoom);
            v.setTranslationX(fromZoom == 1f ? 0 : -dx);
            v.setTranslationY(fromZoom == 1f ? 0 : -dy);
        } else {
            toZoom = v.getScaleX() > (1 + ZOOM) / 2 ? 1f : ZOOM;
        }
        v.animate().scaleX(toZoom).scaleY(toZoom).translationX(toZoom == 1f ? 0 : dx).translationY(toZoom == 1f ? 0 : dy)
                .setDuration(ms).setInterpolator(new LinearInterpolator()).withLayer().start();
    }

    // ---- Weather, holidays ----

    private void showWeather(Weather.Now now) {
        if (effects != null) {
            effects.setWeather(now);
        }
        weatherLine.setText(now == null ? "" : now.line());
        weatherLine.setVisibility(now == null ? View.GONE : View.VISIBLE);
        if (now != null) {
            WeatherIcon icon = new WeatherIcon(now.code, Sky.at(new Date(), now).lampsOn(), FG);
            icon.setBounds(0, 0, dp(22), dp(22));
            weatherLine.setCompoundDrawablesRelative(icon, null, null, null);
        }
    }

    private boolean holidaysOn() {
        return prefs.getBoolean("holidays", true);
    }

    private void showHoliday() {
        Holidays.Day day = holidaysOn() ? Holidays.today() : null;
        String key = day == null ? "" : day.id;
        effects.setHoliday(day == null ? null : day.id);
        if (key.equals(shownHoliday)) {
            return;
        }
        shownHoliday = key;
        holidayLine.setVisibility(day == null ? View.GONE : View.VISIBLE);
        if (day != null) {
            holidayLine.setText(day.greeting);
            holidayLine.setTextColor(day.textColor());
        }
        accent = day != null ? day.accent : ACCENT;
        ink = day != null ? day.ink : ACCENT_INK;
        paintAccent();
        if (apps != null) {
            int index = tiles.indexOfChild(tiles.getFocusedChild());
            setApps(apps);
            if (index >= 0 && index < tiles.getChildCount()) {
                tiles.getChildAt(index).requestFocus();
            }
        }
    }

    private void paintAccent() {
        gear.setImageTintList(PanelMenu.focusColors(ink, MUTED));
        gear.setBackground(PanelMenu.pill(activity, accent, 0));
        menu.setColors(accent, ink);
    }

    // ---- Settings ----

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
        final boolean on = holidaysOn();
        menu.add(PanelMenu.note("Holidays", on ? "on" : "off"), new Runnable() {
            @Override
            public void run() {
                prefs.edit().putBoolean("holidays", !on).apply();
                showHoliday();
                showSettingsMenu();
                menu.focus(2);
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

    private void focusFirst() {
        View first = tiles.getChildCount() > 0 ? tiles.getChildAt(0) : gear;
        first.requestFocus();
    }

    private View shade(GradientDrawable.Orientation orientation, int color) {
        View v = new View(activity);
        v.setBackground(new GradientDrawable(orientation, new int[] {color, 0x00000000}));
        return v;
    }
}

package com.minimal.tvlauncher;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The launcher dressed as the VGA Secret of Monkey Island interface. */
class MonkeyIslandTheme implements LauncherTheme, ScummView.Listener {

    private static final String[] BACKGROUND_FILES = {"background.jpg", "background.png", "background.webp"};
    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif"};
    private static final String BACKGROUNDS_DIR = "backgrounds";
    private static final long CROSSFADE_MS = 1500;

    // Verb ids. Verbs fill the 3x3 grid left to right, top to bottom, in the order added below.
    private static final int OPEN = 0;
    private static final int LOOK_AT = 1;
    private static final int CLOSE = 2;
    private static final int WIFI = 3;
    private static final int BLUETOOTH = 4;
    private static final int SOUND = 5;
    private static final int PICTURE = 6;
    private static final int APPS = 7;
    private static final int SETTINGS = 8;

    private final MainActivity activity;
    private final Handler handler = new Handler();
    private ScummView scumm;
    private ImageView background;
    private ImageView backgroundTop;
    private String backgroundKey;
    private int backgroundRequest;
    private long backgroundIntervalMs;
    private int screenWidth;
    private SharedPreferences prefs;

    private final Runnable rotateBackground = new Runnable() {
        @Override
        public void run() {
            updateBackground(true);
        }
    };

    MonkeyIslandTheme(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public View createView() {
        View root = activity.getLayoutInflater().inflate(R.layout.monkey_island, null);
        // Same preferences file as before, so the slideshow keeps its place.
        prefs = activity.getPreferences(MainActivity.MODE_PRIVATE);
        backgroundIntervalMs = activity.getResources().getInteger(R.integer.background_interval_minutes) * 60000L;
        background = (ImageView) root.findViewById(R.id.background);
        backgroundTop = (ImageView) root.findViewById(R.id.background_top);
        scumm = (ScummView) root.findViewById(R.id.scumm);

        // The background only fills the scene above the verb panel, like in the game.
        Point screen = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(screen);
        screenWidth = Math.max(screen.x, screen.y);
        int sceneHeight = ScummView.sceneHeight(activity, Math.min(screen.x, screen.y));
        background.getLayoutParams().height = sceneHeight;
        backgroundTop.getLayoutParams().height = sceneHeight;

        List<ScummView.Verb> verbs = new ArrayList<ScummView.Verb>();
        verbs.add(new ScummView.Verb(OPEN, "Open", null, true));
        verbs.add(new ScummView.Verb(WIFI, "Wi-Fi", "Change Wi-Fi network", false));
        verbs.add(new ScummView.Verb(PICTURE, "Picture", "Change picture settings", false));
        verbs.add(new ScummView.Verb(LOOK_AT, "Look at", null, true));
        verbs.add(new ScummView.Verb(BLUETOOTH, "Bluetooth", "Connect Bluetooth devices", false));
        verbs.add(new ScummView.Verb(APPS, "Apps", "See all installed apps", false));
        verbs.add(new ScummView.Verb(CLOSE, "Close", null, true));
        verbs.add(new ScummView.Verb(SOUND, "Sound", "Change sound settings", false));
        verbs.add(new ScummView.Verb(SETTINGS, "Settings", "TV settings or another launcher", false));
        scumm.setVerbs(verbs);
        scumm.setListener(this);
        return root;
    }

    @Override
    public void setApps(List<Apps.Entry> apps) {
        List<ScummView.Item> items = new ArrayList<ScummView.Item>();
        for (Apps.Entry app : apps) {
            items.add(new ScummView.Item(app.label, app));
        }
        scumm.setItems(items);
    }

    @Override
    public void onTimeChanged() {
        Date now = new Date();
        String time = DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm";
        String day = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        scumm.setTime(DateFormat.format(time, now).toString(), DateFormat.format(day, now).toString());
    }

    @Override
    public void onResume() {
        updateBackground(false);
        scumm.requestFocus();
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(rotateBackground);
    }

    /** Images were added or removed from the phone: show the current one again. */
    void refreshBackground() {
        backgroundKey = null;
        updateBackground(true);
    }

    @Override
    public void onHome() {
        scumm.reset();
        scumm.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettingsChoices();
            return true;
        }
        return false;
    }

    @Override
    public boolean onBack() {
        return false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(rotateBackground);
    }

    // ---- Verbs ----

    @Override
    public void onVerb(ScummView.Verb verb) {
        switch (verb.id) {
            case WIFI:
                settings(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS);
                break;
            case BLUETOOTH:
                // Android TV calls this page "Remotes & Accessories".
                settings(Settings.ACTION_BLUETOOTH_SETTINGS);
                break;
            case SOUND:
                settings(Settings.ACTION_SOUND_SETTINGS);
                break;
            case PICTURE:
                settings(Settings.ACTION_DISPLAY_SETTINGS);
                break;
            case APPS:
                settings(Settings.ACTION_APPLICATION_SETTINGS);
                break;
            default:
                showSettingsChoices();
                break;
        }
    }

    @Override
    public void onItem(ScummView.Verb verb, ScummView.Item item) {
        Apps.Entry app = (Apps.Entry) item.tag;
        switch (verb.id) {
            case LOOK_AT:
                if (!Apps.openAppInfo(activity, app)) {
                    scumm.showMessage("There's nothing to see.");
                }
                break;
            case CLOSE:
                Apps.close(activity, app);
                scumm.showMessage(app.label + " is closed.");
                break;
            default:
                if (!Apps.launch(activity, app)) {
                    scumm.showMessage("I can't open " + app.label + ".");
                }
                break;
        }
    }

    private void settings(String... actions) {
        if (!Apps.openSettings(activity, actions)) {
            scumm.showMessage("I can't find the settings.");
        }
    }

    // Settings asks what you want, like a conversation in the game.
    private void showSettingsChoices() {
        String url = Remote.url();
        scumm.showChoices(url == null ? "" : "Phone: " + url.replace("http://", ""),
                Arrays.asList("Open the TV settings.", "I'd like to change my apps.", "I'd like a different launcher."),
                new ScummView.ChoiceListener() {
                    @Override
                    public void onChoice(int index) {
                        if (index == 0) {
                            settings();
                        } else if (index == 1) {
                            activity.editApps();
                        } else {
                            showLauncherChoices();
                        }
                    }
                });
    }

    private void showLauncherChoices() {
        final String[] ids = MainActivity.LAUNCHER_IDS;
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < ids.length; i++) {
            String name = MainActivity.LAUNCHER_NAMES[i];
            lines.add(ids[i].equals(activity.currentLauncher()) ? name + " - this one." : name + ".");
        }
        scumm.showChoices("", lines, new ScummView.ChoiceListener() {
            @Override
            public void onChoice(int index) {
                activity.switchLauncher(ids[index]);
            }
        });
    }

    // ---- Background ----

    // Background images, in order of preference:
    //   1. every image in .../files/backgrounds/ — shown in name order, changing every few minutes
    //   2. a single .../files/background.jpg (or .png/.webp)
    //   3. the built-in pixel art
    // where ... is /sdcard/Android/data/com.minimal.tvlauncher. Files are re-read each time,
    // so images pushed with adb show up without restarting the launcher.
    private List<File> findBackgrounds() {
        List<File> result = new ArrayList<File>();
        File dir = activity.getExternalFilesDir(null);
        if (dir == null) {
            return result;
        }
        File[] files = new File(dir, BACKGROUNDS_DIR).listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                if (f.isFile() && f.canRead() && isImage(f.getName())) {
                    result.add(f);
                }
            }
        }
        if (result.isEmpty()) {
            for (String name : BACKGROUND_FILES) {
                File f = new File(dir, name);
                if (f.isFile() && f.canRead()) {
                    result.add(f);
                    break;
                }
            }
        }
        return result;
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    // Shows the current background, moving on to the next one if the interval has passed,
    // and schedules the next change.
    private void updateBackground(boolean animate) {
        handler.removeCallbacks(rotateBackground);
        List<File> images = findBackgrounds();
        if (images.isEmpty()) {
            showBackground(null, animate);
            return;
        }

        int index = prefs.getInt("bg_index", 0);
        long changedAt = prefs.getLong("bg_changed_at", 0);
        long now = System.currentTimeMillis();
        long elapsed = now - changedAt;
        if (images.size() > 1 && (elapsed >= backgroundIntervalMs || elapsed < 0)) {
            index++;
            changedAt = now;
            prefs.edit().putInt("bg_index", index).putLong("bg_changed_at", changedAt).apply();
        }
        showBackground(images.get(Math.abs(index % images.size())), animate);

        if (images.size() > 1) {
            long delay = Math.max(1000, backgroundIntervalMs - (now - changedAt));
            handler.postDelayed(rotateBackground, delay);
        }
    }

    private void showBackground(final File file, final boolean animate) {
        String key = file == null ? "default" : file.getPath() + ":" + file.lastModified() + ":" + file.length();
        if (key.equals(backgroundKey)) {
            return;
        }
        final String newKey = key;
        final int request = ++backgroundRequest;

        if (file == null) {
            applyBackground(defaultArt(), newKey, animate);
            return;
        }
        // Decode off the main thread so the remote stays responsive.
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = decodeScaled(file);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (request != backgroundRequest || activity.isDestroyed()) {
                            return;
                        }
                        if (bitmap != null) {
                            applyBackground(pixelArt(bitmap), newKey, animate);
                        } else if (backgroundKey == null) {
                            applyBackground(defaultArt(), "default", false);
                        }
                    }
                });
            }
        }).start();
    }

    // Backgrounds are pixel art already: scale them up without smoothing so pixels stay sharp.
    private BitmapDrawable pixelArt(Bitmap bitmap) {
        BitmapDrawable d = new BitmapDrawable(activity.getResources(), bitmap);
        d.setFilterBitmap(false);
        d.setAntiAlias(false);
        return d;
    }

    private Drawable defaultArt() {
        return pixelArt(BitmapFactory.decodeResource(activity.getResources(), R.drawable.bg_default));
    }

    // Crossfades using two stacked ImageViews: the new image goes into whichever layer is
    // hidden, then the top layer fades in (or out) to reveal it.
    private void applyBackground(Drawable drawable, String key, boolean animate) {
        backgroundKey = key;
        backgroundTop.animate().cancel();
        boolean topVisible = backgroundTop.getAlpha() > 0.5f;

        if (!animate) {
            background.setImageDrawable(drawable);
            backgroundTop.setImageDrawable(null);
            backgroundTop.setAlpha(0f);
            return;
        }
        if (topVisible) {
            backgroundTop.setAlpha(1f);
            background.setImageDrawable(drawable);
            backgroundTop.animate().alpha(0f).setDuration(CROSSFADE_MS).withEndAction(new Runnable() {
                @Override
                public void run() {
                    backgroundTop.setImageDrawable(null);
                }
            }).start();
        } else {
            backgroundTop.setAlpha(0f);
            backgroundTop.setImageDrawable(drawable);
            backgroundTop.animate().alpha(1f).setDuration(CROSSFADE_MS).withEndAction(new Runnable() {
                @Override
                public void run() {
                    background.setImageDrawable(null);
                }
            }).start();
        }
    }

    // Decodes at full size unless the image is much bigger than the screen; halving keeps
    // pixel art intact when it was already scaled up by an even amount.
    private Bitmap decodeScaled(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize * 2) >= screenWidth) {
            opts.inSampleSize *= 2;
        }
        try {
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}

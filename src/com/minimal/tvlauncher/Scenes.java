package com.minimal.tvlauncher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * The big videos (the Apartment room loops) aren't in the APK, which keeps it small enough to
 * update from a phone. The first time a launcher wants one, it is downloaded from the
 * project's GitHub repository (the scenes/ folder) into the app's storage, resuming where it
 * left off if the connection drops. Until then the launcher shows the video's still.
 *
 * Anything still bundled under assets/ is used as is.
 */
final class Scenes {

    private static final String TAG = "Scenes";
    static final String BASE_URL = "https://raw.githubusercontent.com/emilsdanusevics-boojah/TV_launchers/main/scenes/";
    private static final long RETRY_AFTER_MS = 5 * 60 * 1000;

    // Videos that can be downloaded, by their path (as under assets/).
    private static final String[] DOWNLOADABLE = {"apartment/room_day.mp4", "apartment/room_evening.mp4"};

    private static final Map<String, Integer> progress = new HashMap<String, Integer>();   // 0..100 while downloading
    private static final Map<String, Long> failedAt = new HashMap<String, Long>();

    private Scenes() {
    }

    /** Where a downloaded video lives. */
    static File file(Context context, String path) {
        return new File(new File(context.getFilesDir(), "scenes"), path);
    }

    /** True if the video can be played now: downloaded, or bundled in the APK. */
    static boolean available(Context context, String path) {
        if (file(context, path).isFile()) {
            return true;
        }
        try {
            context.getAssets().openFd(path).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean downloadable(String path) {
        for (String p : DOWNLOADABLE) {
            if (p.equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** Download progress 0..100, or -1 when not downloading. */
    static synchronized int progress(String path) {
        Integer p = progress.get(path);
        return p == null ? -1 : p;
    }

    /** The first video still downloading and its progress, e.g. "room_day 34%", or null. */
    static synchronized String anyProgress() {
        for (Map.Entry<String, Integer> e : progress.entrySet()) {
            return e.getValue() + "%";
        }
        return null;
    }

    /** Starts downloading the video in the background, unless it's here, on its way, or just failed. */
    static synchronized void fetch(Context context, final String path) {
        if (!downloadable(path) || progress.containsKey(path) || file(context, path).isFile()) {
            return;
        }
        Long failed = failedAt.get(path);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) {
            return;
        }
        progress.put(path, 0);
        final File target = file(context, path);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = download(path, target);
                synchronized (Scenes.class) {
                    progress.remove(path);
                    if (ok) {
                        failedAt.remove(path);
                    } else {
                        failedAt.put(path, System.currentTimeMillis());
                    }
                }
            }
        }, "Scenes").start();
    }

    private static boolean download(String path, File target) {
        File part = new File(target.getPath() + ".part");
        target.getParentFile().mkdirs();
        HttpURLConnection c = null;
        try {
            long have = part.isFile() ? part.length() : 0;
            c = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "MinimalTVLauncher");
            if (have > 0) {
                c.setRequestProperty("Range", "bytes=" + have + "-");
            }
            int code = c.getResponseCode();
            if (code == 200) {
                have = 0;   // the server sent it all again
            } else if (code != 206) {
                throw new Exception("HTTP " + code);
            }
            long total = have + Math.max(0, c.getContentLength());
            if (total > 0 && part.getParentFile().getUsableSpace() < (total - have) + 50L * 1024 * 1024) {
                throw new Exception("not enough storage");
            }
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(part, have > 0);
            try {
                byte[] buf = new byte[64 * 1024];
                long done = have;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        synchronized (Scenes.class) {
                            progress.put(path, (int) (done * 100 / total));
                        }
                    }
                }
            } finally {
                out.close();
                in.close();
            }
            if (total > 0 && part.length() != total) {
                throw new Exception("incomplete");
            }
            if (!part.renameTo(target)) {
                throw new Exception("rename failed");
            }
            Log.i(TAG, "downloaded " + path);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "couldn't download " + path, e);
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}

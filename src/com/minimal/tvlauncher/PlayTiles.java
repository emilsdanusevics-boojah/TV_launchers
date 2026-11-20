package com.minimal.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The Play launcher's tiles, set up on the phone page: each has a name, an app, and optionally
 * a link that app opens straight to (a Spotify playlist, a YouTube channel, a radio stream...),
 * so one press starts what you want instead of opening the app and finding it. Saved as
 * play.json in the app's storage.
 */
final class PlayTiles {

    static final int MAX = 6;

    static final class Tile {
        String label;
        String pkg;     // the app's package
        String link;    // "" to just open the app
        int minutes;    // a time limit: starts a kitchen timer when pressed (0 = none)
    }

    private PlayTiles() {
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), "play.json");
    }

    static JSONArray json(Context context) {
        File f = file(context);
        if (f.isFile()) {
            try {
                byte[] b = new byte[(int) f.length()];
                FileInputStream in = new FileInputStream(f);
                int n = in.read(b);
                in.close();
                return new JSONArray(new String(b, 0, Math.max(0, n), "UTF-8"));
            } catch (Exception ignored) {
                // fall through to none
            }
        }
        return new JSONArray();
    }

    static List<Tile> load(Context context) {
        List<Tile> out = new ArrayList<Tile>();
        JSONArray a = json(context);
        for (int i = 0; i < a.length() && out.size() < MAX; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) {
                continue;
            }
            Tile t = new Tile();
            t.label = o.optString("label");
            t.pkg = o.optString("pkg");
            t.link = o.optString("link");
            t.minutes = o.optInt("minutes", 0);
            if (!t.label.isEmpty() && !t.pkg.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** Checks and saves what the phone page sent. Throws with a reason if it doesn't fit. */
    static void save(Context context, JSONArray in) throws Exception {
        if (in.length() > MAX) {
            throw new IllegalArgumentException("Up to " + MAX + " tiles");
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < in.length(); i++) {
            JSONObject o = in.getJSONObject(i);
            String label = o.optString("label").replaceAll("\\s+", " ").trim();
            String pkg = o.optString("pkg").trim();
            String link = o.optString("link").trim();
            if (label.isEmpty() || pkg.isEmpty()) {
                throw new IllegalArgumentException("Each tile needs a name and an app");
            }
            if (label.length() > 40 || link.length() > 500 || !pkg.matches("[A-Za-z0-9_.]+")) {
                throw new IllegalArgumentException("That tile doesn't fit: " + label);
            }
            if (!link.isEmpty() && !link.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.+")) {
                throw new IllegalArgumentException("The link should look like spotify:playlist:… or https://…");
            }
            int minutes = o.optInt("minutes", 0);
            if (minutes < 0 || minutes > 240) {
                throw new IllegalArgumentException("A time limit is up to 240 minutes");
            }
            out.put(new JSONObject().put("label", label).put("pkg", pkg).put("link", link).put("minutes", minutes));
        }
        FileOutputStream f = new FileOutputStream(file(context));
        try {
            f.write(out.toString().getBytes("UTF-8"));
        } finally {
            f.close();
        }
    }

    /**
     * Starts a tile: its link in its app if it has one, else the app itself. Returns false if
     * neither works (the app isn't installed any more).
     */
    static boolean start(Context context, Tile t, List<Apps.Entry> apps) {
        if (!launch(context, t, apps)) {
            return false;
        }
        if (t.minutes > 0) {
            try {
                Timers.add(t.label, t.minutes * 60);   // shows over the launcher when back home
            } catch (IllegalArgumentException ignored) {
                // too many timers already
            }
        }
        return true;
    }

    private static boolean launch(Context context, Tile t, List<Apps.Entry> apps) {
        if (!t.link.isEmpty()) {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(t.link)).setPackage(t.pkg);
            if (Apps.start(context, view)) {
                return true;
            }
        }
        for (Apps.Entry app : apps) {
            if (app.component.getPackageName().equals(t.pkg)) {
                return Apps.launch(context, app);
            }
        }
        Intent launch = context.getPackageManager().getLeanbackLaunchIntentForPackage(t.pkg);
        if (launch == null) {
            launch = context.getPackageManager().getLaunchIntentForPackage(t.pkg);
        }
        return launch != null && Apps.start(context, launch);
    }
}

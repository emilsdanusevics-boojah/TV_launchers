package com.minimal.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * What the Photos launcher knows about an uploaded photo, kept next to it as a small hidden
 * file (.name.jpg.json): who sent it, when it was uploaded and taken, and a few numbers about
 * how it looks (bright or dark, warm or cool, snowy white). Worked out once, at upload.
 */
final class PhotoInfo {

    String from = "";
    long uploaded;
    long taken;       // 0 = unknown
    float bright;     // 0..1 average brightness
    float warm;       // -1..1, red over blue
    float white;      // 0..1 share of bright, colourless pixels (snow, fog)

    static File sidecar(File photo) {
        return new File(photo.getParentFile(), "." + photo.getName() + ".json");
    }

    /** The saved info, or null for a photo without any (uploaded before 9.1). */
    static PhotoInfo read(File photo) {
        File f = sidecar(photo);
        if (!f.isFile()) {
            return null;
        }
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 4096)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(b);
            in.close();
            JSONObject o = new JSONObject(new String(b, 0, Math.max(0, n), "UTF-8"));
            PhotoInfo i = new PhotoInfo();
            i.from = o.optString("from", "");
            i.uploaded = o.optLong("uploaded");
            i.taken = o.optLong("taken");
            i.bright = (float) o.optDouble("bright", 0.5);
            i.warm = (float) o.optDouble("warm", 0);
            i.white = (float) o.optDouble("white", 0);
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Works out and saves the info for a just-uploaded photo. {@code phoneTime} is the file's
     * date on the phone (ms), used when the photo has no camera date; 0 if unknown.
     */
    static void analyse(File photo, String from, long phoneTime) {
        PhotoInfo i = new PhotoInfo();
        i.from = from == null ? "" : from.trim();
        if (i.from.length() > 40) {
            i.from = i.from.substring(0, 40);
        }
        i.uploaded = System.currentTimeMillis();
        try {
            String d = new ExifInterface(photo.getPath()).getAttribute(ExifInterface.TAG_DATETIME);
            String original = new ExifInterface(photo.getPath()).getAttribute("DateTimeOriginal");
            if (original != null) {
                d = original;
            }
            if (d != null) {
                i.taken = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).parse(d).getTime();
            }
        } catch (Exception ignored) {
            // no camera date
        }
        if (i.taken <= 0 && phoneTime > 0 && phoneTime < i.uploaded + 86400000L) {
            i.taken = phoneTime;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photo.getPath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize * 2) >= 64) {
                opts.inSampleSize *= 2;
            }
            Bitmap b = BitmapFactory.decodeFile(photo.getPath(), opts);
            if (b != null) {
                int w = b.getWidth(), h = b.getHeight();
                int[] px = new int[w * h];
                b.getPixels(px, 0, w, 0, 0, w, h);
                b.recycle();
                double lum = 0, warmth = 0;
                int whites = 0;
                for (int c : px) {
                    int r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255;
                    double l = (0.2126 * r + 0.7152 * g + 0.0722 * bl) / 255;
                    lum += l;
                    warmth += (r - bl) / 255.0;
                    int spread = Math.max(r, Math.max(g, bl)) - Math.min(r, Math.min(g, bl));
                    if (l > 0.72 && spread < 34) {
                        whites++;
                    }
                }
                i.bright = (float) (lum / px.length);
                i.warm = (float) (warmth / px.length);
                i.white = whites / (float) px.length;
            }
        } catch (Throwable ignored) {
            i.bright = 0.5f;
        }
        try {
            JSONObject o = new JSONObject().put("from", i.from).put("uploaded", i.uploaded).put("taken", i.taken)
                    .put("bright", i.bright).put("warm", i.warm).put("white", i.white);
            FileOutputStream out = new FileOutputStream(sidecar(photo));
            out.write(o.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
            // the photo still shows, just without its info
        }
    }
}

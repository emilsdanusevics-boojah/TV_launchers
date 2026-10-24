package com.minimal.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Finds the main colours of an album cover, for the Vinyl launcher's background. */
final class AlbumColors {

    private static final int SIDE = 32;
    private static final int CLUSTERS = 5;
    private static final int MIN_DISTANCE = 48;   // colours closer than this count as one

    private AlbumColors() {
    }

    /** Three colours, most prominent first. */
    static int[] extract(Bitmap art) {
        Bitmap small = Bitmap.createScaledBitmap(art, SIDE, SIDE, true);
        int[] px = new int[SIDE * SIDE];
        small.getPixels(px, 0, SIDE, 0, 0, SIDE, SIDE);

        // k-means in RGB. Start from colours spread far apart (farthest-point seeding).
        float[][] centers = new float[CLUSTERS][3];
        centers[0] = rgb(px[px.length / 2]);
        for (int k = 1; k < CLUSTERS; k++) {
            int best = 0;
            float bestDist = -1;
            for (int i = 0; i < px.length; i++) {
                float d = Float.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    d = Math.min(d, dist(rgb(px[i]), centers[j]));
                }
                if (d > bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
            centers[k] = rgb(px[best]);
        }
        int[] counts = new int[CLUSTERS];
        for (int iter = 0; iter < 10; iter++) {
            float[][] sums = new float[CLUSTERS][3];
            counts = new int[CLUSTERS];
            for (int p : px) {
                float[] c = rgb(p);
                int nearest = 0;
                for (int k = 1; k < CLUSTERS; k++) {
                    if (dist(c, centers[k]) < dist(c, centers[nearest])) {
                        nearest = k;
                    }
                }
                counts[nearest]++;
                for (int ch = 0; ch < 3; ch++) {
                    sums[nearest][ch] += c[ch];
                }
            }
            for (int k = 0; k < CLUSTERS; k++) {
                if (counts[k] > 0) {
                    for (int ch = 0; ch < 3; ch++) {
                        centers[k][ch] = sums[k][ch] / counts[k];
                    }
                }
            }
        }

        // Most common first, skipping near-duplicates.
        List<Integer> order = new ArrayList<Integer>();
        for (int k = 0; k < CLUSTERS; k++) {
            order.add(k);
        }
        final int[] n = counts;
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return n[b] - n[a];
            }
        });
        List<float[]> picked = new ArrayList<float[]>();
        for (int k : order) {
            if (counts[k] == 0) {
                continue;
            }
            boolean distinct = true;
            for (float[] other : picked) {
                if (Math.sqrt(dist(centers[k], other)) < MIN_DISTANCE) {
                    distinct = false;
                }
            }
            if (distinct) {
                picked.add(centers[k]);
            }
        }

        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            if (i < picked.size()) {
                result[i] = tame(picked.get(i));
            } else {
                // Single-colour cover: use darker and lighter shades of it.
                result[i] = shade(result[0], i == 1 ? 0.7f : 1.25f);
            }
        }
        return result;
    }

    private static float[] rgb(int c) {
        return new float[] {Color.red(c), Color.green(c), Color.blue(c)};
    }

    private static float dist(float[] a, float[] b) {
        float dr = a[0] - b[0], dg = a[1] - b[1], db = a[2] - b[2];
        return dr * dr + dg * dg + db * db;
    }

    // Keeps the cover's colour but takes the edge off near-white, so the screen doesn't glare.
    private static int tame(float[] c) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Math.round(c[0]), Math.round(c[1]), Math.round(c[2]), hsv);
        hsv[2] = Math.min(hsv[2], 0.9f);
        return Color.HSVToColor(hsv);
    }

    private static int shade(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0.06f, Math.min(0.9f, hsv[2] * factor));
        return Color.HSVToColor(hsv);
    }
}

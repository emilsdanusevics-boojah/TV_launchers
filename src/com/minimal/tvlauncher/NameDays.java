package com.minimal.tvlauncher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Latvian name days (vārda dienas): whose name day it is today, from the traditional
 * calendar list in assets/namedays.txt.
 */
final class NameDays {

    private static Map<String, String> days;

    private NameDays() {
    }

    /** e.g. "Mārtiņš, Mārcis, Markuss, Marks" on 10 November; null if nobody's. */
    static String today(Context context) {
        Calendar now = Calendar.getInstance();   // the TV's own time zone, like the date shown above it
        String key = String.format(java.util.Locale.ROOT, "%02d-%02d", now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        return load(context).get(key);
    }

    /** Whose name day it is on a given day; null if nobody's. */
    static String on(Context context, Calendar day) {
        String key = String.format(java.util.Locale.ROOT, "%02d-%02d", day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
        return load(context).get(key);
    }

    private static synchronized Map<String, String> load(Context context) {
        if (days == null) {
            days = new HashMap<String, String>();
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(context.getAssets().open("namedays.txt"), "UTF-8"));
                String line;
                while ((line = in.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (!line.startsWith("#") && tab > 0) {
                        days.put(line.substring(0, tab), line.substring(tab + 1));
                    }
                }
                in.close();
            } catch (Exception ignored) {
                // No list: no name days shown.
            }
        }
        return days;
    }
}

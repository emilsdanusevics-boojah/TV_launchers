package com.minimal.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Picks the launcher by itself: one by day, another after sunset, and another on Latvia's red
 * days (all three set on the phone page). It only changes launcher when the home screen comes
 * back or ambient mode starts, never while someone is using it.
 *
 * Choosing a launcher by hand (from a menu or the phone) wins until the next change of period,
 * e.g. pick Photos in the evening and it stays until tomorrow morning. Choose the same one in
 * the same part of the day twice (on different days) and Autopilot learns it: from then on
 * that's the launcher for that part of the day.
 */
final class Autopilot {

    static final String NONE = "none";

    private Autopilot() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean("autopilot", true);
    }

    static String day(Context context) {
        return prefs(context).getString("autopilot_day", "photos");
    }

    static String evening(Context context) {
        return prefs(context).getString("autopilot_evening", "fireplace");
    }

    /** The launcher for mornings (05:00–10:00), or NONE to keep to the time of day. */
    static String morning(Context context) {
        return prefs(context).getString("autopilot_morning", "rits");
    }

    /** The launcher for rainy or snowy evenings, or NONE to keep to the evening one. */
    static String weather(Context context) {
        return prefs(context).getString("autopilot_weather", "window");
    }

    /** The red-day launcher, or NONE to keep to the time of day. */
    static String holiday(Context context) {
        return prefs(context).getString("autopilot_holiday", "monkey_island");
    }

    // Which part of the day it is, e.g. "2026-11-18 holiday", "2026-11-19 evening". Evenings
    // that run past midnight count as the day they started.
    private static String period(Context context, Date now, StringBuilder launcher) {
        boolean dark = DayArc.dark(now);
        Date day = now;
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(now);
        if (dark && c.get(java.util.Calendar.HOUR_OF_DAY) < 12) {
            day = new Date(now.getTime() - 12L * 60 * 60 * 1000);   // after midnight: yesterday's evening
        }
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(day);
        boolean holidays = prefs(context).getBoolean("holidays", true);
        String red = holiday(context);
        if (holidays && !NONE.equals(red) && Holidays.today() != null) {
            launcher.append(red);
            return date + " holiday";
        }
        int hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        String morning = morning(context);
        if (hour >= 5 && hour < 10 && !NONE.equals(morning)) {
            launcher.append(morning);
            return date + " morning";
        }
        Weather.Now w = Weather.cached();
        String code = w == null ? "" : w.code;
        boolean wet = code.equals("rain") || code.equals("drizzle") || code.equals("heavy_rain")
                || code.equals("thunder") || code.equals("sleet") || code.equals("snow");
        String rainy = weather(context);
        if (dark && wet && !NONE.equals(rainy)) {
            launcher.append(rainy);
            return date + " weather";
        }
        launcher.append(dark ? evening(context) : day(context));
        return date + (dark ? " evening" : " day");
    }

    /** The launcher to show now, or null to keep the current one. */
    static String choose(Context context, String current) {
        if (!enabled(context)) {
            return null;
        }
        StringBuilder want = new StringBuilder();
        String period = period(context, new Date(), want);
        if (period.equals(prefs(context).getString("autopilot_manual", ""))) {
            return null;   // picked by hand earlier in this period
        }
        String id = want.toString();
        if (id.equals(current) || !java.util.Arrays.asList(MainActivity.LAUNCHER_IDS).contains(id)) {
            return null;
        }
        return id;
    }

    /**
     * A launcher was chosen by hand: leave it alone until the next period. Returns a message
     * if this taught Autopilot a new launcher for this part of the day, otherwise null.
     */
    static String choseByHand(Context context, String id) {
        StringBuilder want = new StringBuilder();
        String period = period(context, new Date(), want);
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor e = p.edit().putString("autopilot_manual", period);
        String learned = null;
        if (enabled(context) && !id.equals(want.toString())) {
            String date = period.substring(0, period.indexOf(' '));
            String slot = period.substring(period.indexOf(' ') + 1);   // day, evening or holiday
            String last = p.getString("autopilot_learn_" + slot, "");
            if (last.endsWith("|" + id) && !last.startsWith(date + "|")) {
                e.putString("autopilot_" + slot, id).remove("autopilot_learn_" + slot);
                learned = "Autopilot will use " + name(id) + (slot.equals("holiday") ? " on red days"
                        : slot.equals("morning") ? " in the mornings"
                        : slot.equals("weather") ? " on rainy evenings" : slot.equals("evening") ? " after sunset" : " by day")
                        + " from now on";
            } else {
                e.putString("autopilot_learn_" + slot, date + "|" + id);
            }
        }
        e.apply();
        return learned;
    }

    private static String name(String id) {
        for (int i = 0; i < MainActivity.LAUNCHER_IDS.length; i++) {
            if (MainActivity.LAUNCHER_IDS[i].equals(id)) {
                return MainActivity.LAUNCHER_NAMES[i];
            }
        }
        return id;
    }
}

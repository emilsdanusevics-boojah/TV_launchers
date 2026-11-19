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
 * e.g. pick Photos in the evening and it stays until tomorrow morning.
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

    /** A launcher was chosen by hand: leave it alone until the next period. */
    static void choseByHand(Context context) {
        StringBuilder ignored = new StringBuilder();
        prefs(context).edit().putString("autopilot_manual", period(context, new Date(), ignored)).apply();
    }
}

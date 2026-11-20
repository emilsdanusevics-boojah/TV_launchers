package com.minimal.tvlauncher;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The Photos launcher's glance card, from the hour-by-hour forecast:
 *
 *   mornings (05:00–11:00)   Today: the temperature and how cold it feels, what to wear, when
 *                            rain or snow starts or stops, and a warning when pavements are
 *                            likely icy (wet, then freezing)
 *   evenings (18:00–02:00)   Tomorrow: the morning's weather, the day's high, sunrise, and
 *                            whether it's a red day, and whose name day it is
 *
 * Nothing the rest of the day, and nothing without a recent forecast.
 */
final class Glance {

    private static final TimeZone RIGA = TimeZone.getTimeZone("Europe/Riga");
    private static final long STALE_MS = 3 * 60 * 60 * 1000;

    private Glance() {
    }

    /** The card's title, or null when there's no card now. */
    static String title(Date now) {
        int h = hour(now);
        if (h >= 5 && h < 11) {
            return "Today";
        }
        if (h >= 18 || h < 2) {
            return "Tomorrow";
        }
        return null;
    }

    /** The card's lines, or an empty list. */
    static List<String> lines(Context context, Weather.Now w, Date now) {
        String title = title(now);
        if (title == null) {
            return new ArrayList<String>();
        }
        return title.equals("Today") ? today(w, now) : tomorrow(context, w, now);
    }

    /** Now and the rest of today: temperature, what to wear, rain or snow coming or going, ice. */
    static List<String> today(Weather.Now w, Date now) {
        List<String> out = new ArrayList<String>();
        int at = usable(w) ? index(w, now) : -1;
        if (at < 0) {
            return out;
        }
        double feels = w.hourFeels[at];
        out.add(Math.round(w.tempC) + "°, feels like " + Math.round(feels) + "° · " + wear(feels));
        String soon = soon(w, now, at);
        String change = soon != null ? soon : rainChange(w, at);
        if (change != null) {
            out.add(change);
        }
        if (icy(w, at)) {
            out.add("Icy pavements likely");
        }
        return out;
    }

    /** Tomorrow (or, after midnight, today): the morning's weather, the high, sunrise, red day, name days. */
    static List<String> tomorrow(Context context, Weather.Now w, Date now) {
        List<String> out = new ArrayList<String>();
        if (!usable(w)) {
            return out;
        }
        Calendar tomorrow = Calendar.getInstance(RIGA);
        tomorrow.setTime(now);
        if (hour(now) >= 2) {
            tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        }
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) {{ setTimeZone(RIGA); }}.format(tomorrow.getTime());
        int morning = find(w, day + "T08:00");
        if (morning >= 0) {
            double high = -99;
            for (int i = morning; i < Math.min(w.hourTime.length, morning + 12); i++) {
                high = Math.max(high, w.hourTemp[i]);
            }
            out.add("08:00 " + Math.round(w.hourTemp[morning]) + "°, " + word(w.hourCode[morning])
                    + " · up to " + Math.round(high) + "°");
        }
        String sunrise = null;
        for (String s : w.sunrise) {
            if (s != null && s.startsWith(day) && s.length() >= 16) {
                sunrise = s.substring(11, 16);
            }
        }
        if (sunrise != null) {
            out.add("Sunrise " + sunrise);
        }
        Holidays.Day red = Holidays.on(tomorrow);
        if (red != null) {
            out.add("Red day: " + red.name);
        }
        String names = NameDays.on(context, tomorrow);
        if (names != null) {
            out.add("Name days: " + names);
        }
        return out;
    }

    private static boolean usable(Weather.Now w) {
        return w != null && w.hourTime.length > 0 && System.currentTimeMillis() - w.fetchedAt <= STALE_MS;
    }

    // ---- Reading the forecast ----

    private static int hour(Date now) {
        Calendar c = Calendar.getInstance(RIGA);
        c.setTime(now);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    private static int index(Weather.Now w, Date now) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.ROOT);
        f.setTimeZone(RIGA);
        return find(w, f.format(now));
    }

    private static int find(Weather.Now w, String time) {
        for (int i = 0; i < w.hourTime.length; i++) {
            if (time.equals(w.hourTime[i])) {
                return i;
            }
        }
        return -1;
    }

    private static boolean wet(Weather.Now w, int i) {
        return w.hourRain[i] >= 0.1;
    }

    private static boolean snowy(String code) {
        return "snow".equals(code) || "sleet".equals(code);
    }

    // "Rain from 16:00", "Snow until 10:00", or null if it stays dry (or stays wet) till evening.
    private static String rainChange(Weather.Now w, int at) {
        int end = Math.min(w.hourTime.length, at + 14);
        boolean now = wet(w, at);
        for (int i = at + 1; i < end; i++) {
            if (wet(w, i) != now) {
                String what = snowy(w.hourCode[now ? at : i]) ? "Snow" : "Rain";
                String time = w.hourTime[i].substring(11, 16);
                return now ? what + " until " + time : what + " from " + time + (what.equals("Rain") ? " · take an umbrella" : "");
            }
        }
        return now ? (snowy(w.hourCode[at]) ? "Snow all day" : "Rain all day · take an umbrella") : null;
    }

    // From the quarter-hour forecast: "Rain in about 20 min" or "Dry in about 45 min", within
    // the next two hours; null if nothing changes that soon (or there's no such forecast).
    private static String soon(Weather.Now w, Date now, int at) {
        if (w.quarterTime.length == 0) {
            return null;
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT);
        f.setTimeZone(RIGA);
        String nowText = f.format(now);
        int first = -1;
        for (int i = 0; i < w.quarterTime.length; i++) {
            if (w.quarterTime[i].compareTo(nowText) >= 0) {
                first = i;
                break;
            }
        }
        if (first < 0) {
            return null;
        }
        boolean wetNow = (first > 0 ? w.quarterRain[first - 1] : w.quarterRain[first]) >= 0.05;
        for (int i = first; i < w.quarterTime.length; i++) {
            boolean wet = w.quarterRain[i] >= 0.05;
            if (wet != wetNow) {
                try {
                    long minutes = Math.max(5, (f.parse(w.quarterTime[i]).getTime() - now.getTime()) / 60000);
                    if (minutes > 120) {
                        return null;
                    }
                    long rounded = Math.max(5, Math.round(minutes / 5.0) * 5);
                    String what = snowy(w.hourCode[at]) ? "Snow" : "Rain";
                    return wetNow ? "Dry in about " + rounded + " min" : what + " in about " + rounded + " min"
                            + (what.equals("Rain") ? " · take an umbrella" : "");
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    // Wet in the last twelve hours and at or below freezing now or soon.
    private static boolean icy(Weather.Now w, int at) {
        if ("sleet".equals(w.hourCode[at])) {
            return true;
        }
        boolean wasWet = false;
        for (int i = Math.max(0, at - 12); i <= at; i++) {
            if (wet(w, i) && w.hourTemp[i] > -1) {
                wasWet = true;
            }
        }
        if (!wasWet) {
            return false;
        }
        for (int i = at; i < Math.min(w.hourTime.length, at + 4); i++) {
            if (w.hourTemp[i] <= 0) {
                return true;
            }
        }
        return false;
    }

    private static String wear(double feels) {
        if (feels < -10) {
            return "warm coat, hat and gloves";
        } else if (feels < 0) {
            return "winter coat and hat";
        } else if (feels < 8) {
            return "warm jacket";
        } else if (feels < 15) {
            return "light jacket";
        }
        return "no jacket needed";
    }

    static String word(String code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case "clear": return "clear";
            case "partly": return "partly cloudy";
            case "overcast": case "cloudy": return "cloudy";
            case "fog": return "fog";
            case "drizzle": return "drizzle";
            case "rain": return "rain";
            case "heavy_rain": return "heavy rain";
            case "sleet": return "sleet";
            case "snow": return "snow";
            case "thunder": return "thunder";
            default: return code;
        }
    }
}

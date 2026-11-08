package com.minimal.tvlauncher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Latvia's public holidays (the red days in the calendar, "svētku dienas" in the law on
 * holidays), each with its own decoration for the Apartment launcher.
 *
 * When 4 May or 18 November falls on a weekend, the following Monday is a day off too; it
 * gets the same decoration.
 */
final class Holidays {

    // Decorations, drawn by OrbitView's shader.
    static final int NONE = 0;
    static final int FIREWORKS = 1;   // New Year's Eve and Day
    static final int CATKINS = 2;     // Good Friday: pussy-willow catkins
    static final int EGGS = 3;        // Easter: onion-skin dyed eggs
    static final int BLOSSOM = 4;     // 1 May: apple blossom
    static final int FLAG = 5;        // 4 May: the flag
    static final int TULIPS = 6;      // Mother's Day
    static final int BIRCH = 7;       // Pentecost: birch leaves
    static final int MIDSUMMER = 8;   // Līgo and Jāņi: bonfire and embers
    static final int CANDLES = 9;     // 18 November: the flag and candles
    static final int CHRISTMAS = 10;  // Christmas: lights and snow

    private static final TimeZone RIGA = TimeZone.getTimeZone("Europe/Riga");
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    static final class Day {
        final String id;
        final String name;       // Latvian name, as in the calendar (shortened for the long ones)
        final String greeting;   // shown under the weather on the day
        final int effect;
        final int accent;        // focus colour for buttons
        final int ink;           // text on the accent colour
        final float[] palette;   // three sRGB colours for the decoration
        int month;               // 0-11, in the year asked for
        int dayOfMonth;

        Day(String id, String name, String greeting, int effect, int accent, int ink, int a, int b, int c) {
            this.id = id;
            this.name = name;
            this.greeting = greeting;
            this.effect = effect;
            this.accent = accent;
            this.ink = ink;
            this.palette = new float[] {r(a), g(a), b(a), r(b), g(b), b(b), r(c), g(c), b(c)};
        }

        Day on(int month, int dayOfMonth) {
            Day d = new Day(id, name, greeting, effect, accent, ink, 0, 0, 0);
            System.arraycopy(palette, 0, d.palette, 0, 9);
            d.month = month;
            d.dayOfMonth = dayOfMonth;
            return d;
        }

        /** e.g. "24 Jun". */
        String date() {
            return dayOfMonth + " " + MONTHS[month];
        }

        private static float r(int c) {
            return ((c >> 16) & 255) / 255f;
        }

        private static float g(int c) {
            return ((c >> 8) & 255) / 255f;
        }

        private static float b(int c) {
            return (c & 255) / 255f;
        }
    }

    private static final int DARK = 0xFF1D1A15, WHITE = 0xFFFFFFFF;
    private static final Day NEW_YEAR = new Day("new_year", "Jaungada diena", "Laimīgu Jauno gadu! · Happy New Year",
            FIREWORKS, 0xFFE6B85C, DARK, 0xFFD27D, 0xFF6B6B, 0x8FD3FF);
    private static final Day GOOD_FRIDAY = new Day("good_friday", "Lielā Piektdiena", "Lielā Piektdiena · Good Friday",
            CATKINS, 0xFFBBADC9, DARK, 0xE9E6E1, 0xC9C3BC, 0xF4EFE6);
    private static final Day EASTER = new Day("easter", "Lieldienas", "Priecīgas Lieldienas! · Happy Easter",
            EGGS, 0xFFD9893F, DARK, 0x9C3F1D, 0xC8692A, 0x7A2A1E);
    private static final Day EASTER_MONDAY = new Day("easter_monday", "Otrās Lieldienas", "Otrās Lieldienas · Easter Monday",
            EGGS, 0xFFD9893F, DARK, 0x9C3F1D, 0xC8692A, 0x7A2A1E);
    private static final Day MAY_DAY = new Day("may_day", "Darba svētki", "Darba svētki · Labour Day",
            BLOSSOM, 0xFFE8A6B6, DARK, 0xFFF3F4, 0xF7C9D4, 0xFFE3EA);
    private static final Day INDEPENDENCE = new Day("independence", "Neatkarības atjaunošana",
            "Neatkarības atjaunošanas diena · Restoration of Independence",
            FLAG, 0xFFA4343A, WHITE, 0x9E3039, 0xFFFFFF, 0x9E3039);
    private static final Day MOTHERS_DAY = new Day("mothers_day", "Mātes diena", "Mātes diena · Mother's Day",
            TULIPS, 0xFFE57A8A, DARK, 0xE0384B, 0xF28CA0, 0xF5C842);
    private static final Day PENTECOST = new Day("pentecost", "Vasarsvētki", "Vasarsvētki · Pentecost",
            BIRCH, 0xFF9CC56B, DARK, 0x8CC63F, 0x6FA82E, 0xB4D96A);
    private static final Day LIGO = new Day("ligo", "Līgo diena", "Līgo, līgo! · Midsummer Eve",
            MIDSUMMER, 0xFFE59A4F, DARK, 0xFFB347, 0xFF7A1F, 0xFFE08A);
    private static final Day JANI = new Day("jani", "Jāņu diena", "Priecīgus Jāņus! · Midsummer",
            MIDSUMMER, 0xFFE59A4F, DARK, 0xFFB347, 0xFF7A1F, 0xFFE08A);
    private static final Day PROCLAMATION = new Day("proclamation", "Proklamēšanas diena",
            "Daudz laimes, Latvija! · Proclamation Day",
            CANDLES, 0xFFA4343A, WHITE, 0x9E3039, 0xFFFFFF, 0xFFC46B);
    private static final Day CHRISTMAS_EVE = new Day("christmas_eve", "Ziemassvētku vakars", "Priecīgus Ziemassvētkus! · Christmas Eve",
            CHRISTMAS, 0xFFC94B3F, WHITE, 0xFF5A4A, 0xFFD36B, 0x7FD67A);
    private static final Day CHRISTMAS_DAY = new Day("christmas", "Pirmie Ziemassvētki", "Priecīgus Ziemassvētkus! · Christmas Day",
            CHRISTMAS, 0xFFC94B3F, WHITE, 0xFF5A4A, 0xFFD36B, 0x7FD67A);
    private static final Day SECOND_CHRISTMAS = new Day("second_christmas", "Otrie Ziemassvētki", "Otrie Ziemassvētki · Second Christmas Day",
            CHRISTMAS, 0xFFC94B3F, WHITE, 0xFF5A4A, 0xFFD36B, 0x7FD67A);
    private static final Day OLD_YEAR = new Day("old_year", "Vecgada diena", "Vecgada vakars · New Year's Eve",
            FIREWORKS, 0xFFE6B85C, DARK, 0xFFD27D, 0xFF6B6B, 0x8FD3FF);

    private Holidays() {
    }

    /** All of the year's red days, in date order. */
    static List<Day> year(int year) {
        Calendar easter = easter(year);
        List<Day> days = new ArrayList<Day>();
        days.add(NEW_YEAR.on(Calendar.JANUARY, 1));
        days.add(shifted(GOOD_FRIDAY, easter, -2));
        days.add(shifted(EASTER, easter, 0));
        days.add(shifted(EASTER_MONDAY, easter, 1));
        days.add(MAY_DAY.on(Calendar.MAY, 1));
        days.add(INDEPENDENCE.on(Calendar.MAY, 4));
        days.add(MOTHERS_DAY.on(Calendar.MAY, secondSundayOfMay(year)));
        Day pentecost = shifted(PENTECOST, easter, 49);
        days.add(pentecost);
        days.add(LIGO.on(Calendar.JUNE, 23));
        days.add(JANI.on(Calendar.JUNE, 24));
        days.add(PROCLAMATION.on(Calendar.NOVEMBER, 18));
        days.add(CHRISTMAS_EVE.on(Calendar.DECEMBER, 24));
        days.add(CHRISTMAS_DAY.on(Calendar.DECEMBER, 25));
        days.add(SECOND_CHRISTMAS.on(Calendar.DECEMBER, 26));
        days.add(OLD_YEAR.on(Calendar.DECEMBER, 31));
        // Pentecost (10 May to 13 June) can come before Mother's Day (8 to 14 May).
        if (pentecost.month == Calendar.MAY && pentecost.dayOfMonth < days.get(6).dayOfMonth) {
            days.set(7, days.get(6));
            days.set(6, pentecost);
        }
        return days;
    }

    /** Today's red day in Riga, or null. */
    static Day today() {
        return on(Calendar.getInstance(RIGA));
    }

    /** The red day on this date, or null. */
    static Day on(Calendar now) {
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH), d = now.get(Calendar.DAY_OF_MONTH);
        for (Day day : year(y)) {
            if (day.month == m && day.dayOfMonth == d) {
                return day;
            }
        }
        // The Monday after a 4 May or 18 November that fell on a weekend.
        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            for (Day day : new Day[] {INDEPENDENCE.on(Calendar.MAY, 4), PROCLAMATION.on(Calendar.NOVEMBER, 18)}) {
                Calendar c = Calendar.getInstance(RIGA);
                c.clear();
                c.set(y, day.month, day.dayOfMonth);
                int dow = c.get(Calendar.DAY_OF_WEEK);
                int gap = dow == Calendar.SATURDAY ? 2 : dow == Calendar.SUNDAY ? 1 : -1;
                if (gap > 0) {
                    c.add(Calendar.DAY_OF_MONTH, gap);
                    if (c.get(Calendar.MONTH) == m && c.get(Calendar.DAY_OF_MONTH) == d) {
                        return day;
                    }
                }
            }
        }
        return null;
    }

    static Day byId(String id) {
        if (id == null) {
            return null;
        }
        for (Day day : year(Calendar.getInstance(RIGA).get(Calendar.YEAR))) {
            if (day.id.equals(id)) {
                return day;
            }
        }
        return null;
    }

    private static Day shifted(Day day, Calendar easter, int days) {
        Calendar c = (Calendar) easter.clone();
        c.add(Calendar.DAY_OF_MONTH, days);
        return day.on(c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    private static int secondSundayOfMay(int year) {
        Calendar c = Calendar.getInstance(RIGA);
        c.clear();
        c.set(year, Calendar.MAY, 1);
        int toSunday = (Calendar.SUNDAY - c.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        return 1 + toSunday + 7;
    }

    // Western Easter Sunday (the Gregorian "anonymous" algorithm).
    static Calendar easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        Calendar cal = Calendar.getInstance(RIGA);
        cal.clear();
        cal.set(year, month - 1, day);
        return cal;
    }
}

package com.minimal.tvlauncher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The household's people and notes, typed on the phone page and shown on the Photos launcher
 * (and said by the pirate in Monkey Island's ambient mode):
 *
 *   people  a name, who they are and an optional birthday. When the name is in the day's
 *           name-day list, "Dad's name day (Mārtiņš)" shows on the day, and "Tomorrow: …" the
 *           day before; the same for birthdays.
 *   notes   a line of text, shown always ("Buy rye bread"), every week on a weekday, or every
 *           month on a date ("Water meter readings"), then on the day and the day before.
 *
 * Saved in the app's storage as board.json.
 */
final class Board {

    static final int MAX_PEOPLE = 50;
    static final int MAX_NOTES = 30;
    static final int MAX_TEXT = 80;
    private static final int MAX_LINES = 4;

    private static JSONObject cached;
    private static long cachedAt = -1;

    private Board() {
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), "board.json");
    }

    static synchronized JSONObject load(Context context) {
        File f = file(context);
        long modified = f.isFile() ? f.lastModified() : 0;
        if (cached != null && modified == cachedAt) {
            return cached;
        }
        JSONObject o = null;
        if (modified > 0) {
            try {
                byte[] b = new byte[(int) f.length()];
                FileInputStream in = new FileInputStream(f);
                int n = in.read(b);
                in.close();
                o = new JSONObject(new String(b, 0, Math.max(0, n), "UTF-8"));
            } catch (Exception ignored) {
                o = null;
            }
        }
        if (o == null) {
            o = new JSONObject();
        }
        try {
            if (!o.has("people")) {
                o.put("people", new JSONArray());
            }
            if (!o.has("notes")) {
                o.put("notes", new JSONArray());
            }
        } catch (Exception ignored) {
            // can't happen
        }
        cached = o;
        cachedAt = modified;
        return o;
    }

    /** Checks and saves what the phone page sent. Throws with a reason if it doesn't fit. */
    static synchronized void save(Context context, JSONObject in) throws Exception {
        JSONArray people = in.optJSONArray("people");
        JSONArray notes = in.optJSONArray("notes");
        if (people == null || notes == null || people.length() > MAX_PEOPLE || notes.length() > MAX_NOTES) {
            throw new IllegalArgumentException("Up to " + MAX_PEOPLE + " people and " + MAX_NOTES + " notes");
        }
        JSONArray cleanPeople = new JSONArray();
        for (int i = 0; i < people.length(); i++) {
            JSONObject p = people.getJSONObject(i);
            String name = text(p.optString("name"));
            if (name.isEmpty()) {
                continue;
            }
            String birthday = p.optString("birthday", "").trim();
            if (!birthday.isEmpty() && !birthday.matches("(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])")) {
                throw new IllegalArgumentException("Birthday must be MM-DD, e.g. 03-14");
            }
            cleanPeople.put(new JSONObject().put("name", name).put("who", text(p.optString("who"))).put("birthday", birthday));
        }
        JSONArray cleanNotes = new JSONArray();
        for (int i = 0; i < notes.length(); i++) {
            JSONObject n = notes.getJSONObject(i);
            String t = text(n.optString("text"));
            if (t.isEmpty()) {
                continue;
            }
            String repeat = n.optString("repeat", "once");
            int day = n.optInt("day", 1);
            if (repeat.equals("weekly") ? day < 1 || day > 7 : repeat.equals("monthly") ? day < 1 || day > 31 : !repeat.equals("once")) {
                throw new IllegalArgumentException("Bad repeat for note: " + t);
            }
            cleanNotes.put(new JSONObject().put("text", t).put("repeat", repeat).put("day", day));
        }
        JSONObject out = new JSONObject().put("people", cleanPeople).put("notes", cleanNotes);
        FileOutputStream f = new FileOutputStream(file(context));
        try {
            f.write(out.toString().getBytes("UTF-8"));
        } finally {
            f.close();
        }
        cached = null;
    }

    private static String text(String s) {
        s = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    // ---- What to show ----

    /** Up to four lines for now, most pressing first; empty if there's nothing. */
    static List<String> lines(Context context, Calendar now) {
        List<String> today = new ArrayList<String>();
        List<String> tomorrow = new ArrayList<String>();
        List<String> always = new ArrayList<String>();
        JSONObject o = load(context);
        Calendar next = (Calendar) now.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        try {
            JSONArray people = o.getJSONArray("people");
            for (int i = 0; i < people.length(); i++) {
                JSONObject p = people.getJSONObject(i);
                String name = p.optString("name");
                String who = p.optString("who");
                String label = who.isEmpty() ? name + "'s" : who + "'s";
                String suffix = who.isEmpty() ? "" : " (" + name + ")";
                if (hasNameDay(context, name, now)) {
                    today.add(label + " name day" + suffix);
                } else if (hasNameDay(context, name, next)) {
                    tomorrow.add("Tomorrow: " + label + " name day" + suffix);
                }
                String birthday = p.optString("birthday");
                if (birthday.equals(monthDay(now))) {
                    today.add(label + " birthday" + suffix);
                } else if (birthday.equals(monthDay(next))) {
                    tomorrow.add("Tomorrow: " + label + " birthday" + suffix);
                }
            }
            JSONArray notes = o.getJSONArray("notes");
            for (int i = 0; i < notes.length(); i++) {
                JSONObject n = notes.getJSONObject(i);
                String t = n.optString("text");
                String repeat = n.optString("repeat");
                int day = n.optInt("day");
                if (repeat.equals("once")) {
                    always.add(t);
                } else if (due(repeat, day, now)) {
                    today.add(t);
                } else if (due(repeat, day, next)) {
                    tomorrow.add("Tomorrow: " + t);
                }
            }
        } catch (Exception ignored) {
            // show what we have
        }
        List<String> all = new ArrayList<String>(today);
        all.addAll(tomorrow);
        all.addAll(always);
        return all.size() > MAX_LINES ? new ArrayList<String>(all.subList(0, MAX_LINES)) : all;
    }

    /** Only today's lines (for the pirate to mention). */
    static List<String> todayLines(Context context, Calendar now) {
        List<String> out = new ArrayList<String>();
        for (String l : lines(context, now)) {
            if (!l.startsWith("Tomorrow: ")) {
                out.add(l);
            }
        }
        return out;
    }

    private static boolean due(String repeat, int day, Calendar c) {
        if (repeat.equals("weekly")) {
            int dow = c.get(Calendar.DAY_OF_WEEK);           // Sunday = 1
            int mondayFirst = dow == Calendar.SUNDAY ? 7 : dow - 1;
            return mondayFirst == day;
        }
        if (repeat.equals("monthly")) {
            int last = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            return c.get(Calendar.DAY_OF_MONTH) == Math.min(day, last);   // the 31st falls on the last day
        }
        return false;
    }

    private static String monthDay(Calendar c) {
        return String.format(Locale.ROOT, "%02d-%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static boolean hasNameDay(Context context, String name, Calendar day) {
        String list = NameDays.on(context, day);
        if (list == null) {
            return false;
        }
        String want = fold(name);
        for (String n : list.split(",")) {
            if (fold(n).equals(want)) {
                return true;
            }
        }
        return false;
    }

    // Case- and accent-insensitive, so "martins" matches "Mārtiņš".
    private static String fold(String s) {
        String d = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return d.replaceAll("\\p{M}", "");
    }
}

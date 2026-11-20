package com.minimal.tvlauncher;

import java.util.ArrayList;
import java.util.List;

/**
 * Kitchen timers started from the phone page ("Tea 4:00", "Pasta 9:00"). They count down at
 * the top of whichever launcher is on screen (see TimerBar) and chime when done. Kept in
 * memory: a timer is for the next few minutes, not for after a restart.
 */
final class Timers {

    static final int MAX = 4;
    static final int MAX_SECONDS = 4 * 60 * 60;

    static final class Timer {
        final int id;
        final String label;
        final long endAt;       // System.currentTimeMillis() when it's done

        Timer(int id, String label, long endAt) {
            this.id = id;
            this.label = label;
            this.endAt = endAt;
        }

        long leftMs() {
            return Math.max(0, endAt - System.currentTimeMillis());
        }

        boolean done() {
            return leftMs() == 0;
        }
    }

    private static final List<Timer> timers = new ArrayList<Timer>();
    private static int nextId = 1;

    private Timers() {
    }

    static synchronized List<Timer> all() {
        return new ArrayList<Timer>(timers);
    }

    static synchronized Timer add(String label, int seconds) {
        if (timers.size() >= MAX) {
            throw new IllegalArgumentException("Up to " + MAX + " timers at once");
        }
        if (seconds < 1 || seconds > MAX_SECONDS) {
            throw new IllegalArgumentException("A timer can run from 1 second to 4 hours");
        }
        String l = label == null ? "" : label.replaceAll("\\s+", " ").trim();
        if (l.isEmpty()) {
            l = "Timer";
        }
        if (l.length() > 24) {
            l = l.substring(0, 24);
        }
        Timer t = new Timer(nextId++, l, System.currentTimeMillis() + seconds * 1000L);
        timers.add(t);
        return t;
    }

    static synchronized void remove(int id) {
        for (int i = timers.size() - 1; i >= 0; i--) {
            if (timers.get(i).id == id) {
                timers.remove(i);
            }
        }
    }

    /** Clears the timers that have finished (someone pressed a button: heard it). */
    static synchronized boolean dismissDone() {
        boolean any = false;
        for (int i = timers.size() - 1; i >= 0; i--) {
            if (timers.get(i).done()) {
                timers.remove(i);
                any = true;
            }
        }
        return any;
    }

    static synchronized boolean anyDone() {
        for (Timer t : timers) {
            if (t.done()) {
                return true;
            }
        }
        return false;
    }

    static String format(long ms) {
        long s = (ms + 999) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
    }
}

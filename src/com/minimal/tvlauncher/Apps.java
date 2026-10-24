package com.minimal.tvlauncher;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The apps shown on the home screen, and the actions both launchers share. */
final class Apps {

    static class Entry {
        ComponentName component;
        String category;
        String label;   // the name from apps.xml
    }

    private Apps() {
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace("+", "plus").replaceAll("[^a-z0-9]", "");
    }

    /** The installed apps from res/values/apps.xml, in that order. */
    static List<Entry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        String[] wanted = context.getResources().getStringArray(R.array.visible_apps);

        // Map every package name and normalised label in the list to its slot.
        Map<String, Integer> byPackage = new HashMap<String, Integer>();
        Map<String, Integer> byLabel = new HashMap<String, Integer>();
        String[] names = new String[wanted.length];
        for (int i = 0; i < wanted.length; i++) {
            String[] parts = wanted[i].split("\\|", -1);
            names[i] = parts[0].trim();
            byLabel.put(normalize(names[i]), i);
            if (parts.length > 1) {
                for (String pkg : parts[1].split(",")) {
                    if (pkg.trim().length() > 0) {
                        byPackage.put(pkg.trim(), i);
                    }
                }
            }
        }

        Entry[] slots = new Entry[wanted.length];
        // TV versions of apps first, then regular launcher apps.
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, byPackage, byLabel, slots);
        collect(pm, Intent.CATEGORY_LAUNCHER, byPackage, byLabel, slots);

        List<Entry> result = new ArrayList<Entry>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                slots[i].label = names[i];
                result.add(slots[i]);
            }
        }
        return result;
    }

    private static void collect(PackageManager pm, String category, Map<String, Integer> byPackage,
                                Map<String, Integer> byLabel, Entry[] slots) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> infos = pm.queryIntentActivities(query, 0);
        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null) {
                continue;
            }
            CharSequence rawLabel = ri.loadLabel(pm);
            String label = rawLabel != null ? rawLabel.toString() : ai.packageName;

            Integer slot = byPackage.get(ai.packageName);
            if (slot == null) {
                slot = byLabel.get(normalize(label));
            }
            if (slot == null || slots[slot] != null) {
                continue;
            }

            Entry entry = new Entry();
            entry.component = new ComponentName(ai.packageName, ai.name);
            entry.category = category;
            slots[slot] = entry;
        }
    }

    static boolean start(Context context, Intent intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean launch(Context context, Entry app) {
        return start(context, new Intent(Intent.ACTION_MAIN)
                .addCategory(app.category)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    static boolean openAppInfo(Context context, Entry app) {
        return start(context, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.component.getPackageName(), null)));
    }

    /** Stops the app if it is running in the background. */
    static void close(Context context, Entry app) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(app.component.getPackageName());
    }

    /** Opens the first settings page the TV supports, falling back to the main Settings screen. */
    static boolean openSettings(Context context, String... actions) {
        for (String action : actions) {
            if (start(context, new Intent(action))) {
                return true;
            }
        }
        return start(context, new Intent(Settings.ACTION_SETTINGS));
    }
}

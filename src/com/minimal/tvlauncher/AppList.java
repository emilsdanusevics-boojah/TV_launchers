package com.minimal.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which apps the launchers show, in what order and under what name. Edited on the TV
 * (EditAppsActivity) or from a phone (RemoteServer). Until it has been edited, the list
 * comes from res/values/apps.xml.
 */
final class AppList {

    static class Item {
        final String pkg;
        final String name;

        Item(String pkg, String name) {
            this.pkg = pkg;
            this.name = name;
        }
    }

    /** An installed app that can be opened. */
    static class Installed {
        final String pkg;
        final String label;
        final ComponentName component;
        final String category;

        Installed(String pkg, String label, ComponentName component, String category) {
            this.pkg = pkg;
            this.label = label;
            this.component = component;
            this.category = category;
        }
    }

    private static final String KEY = "apps";

    private AppList() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** The shown apps, in order. Apps that are no longer installed are kept but skipped by the launchers. */
    static List<Item> get(Context context) {
        String saved = prefs(context).getString(KEY, null);
        if (saved != null) {
            List<Item> items = new ArrayList<Item>();
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    items.add(new Item(o.getString("pkg"), o.getString("name")));
                }
                return items;
            } catch (Exception e) {
                // Unreadable: fall back to the defaults below.
            }
        }
        return defaults(context);
    }

    static void set(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                array.put(new JSONObject().put("pkg", item.pkg).put("name", item.name));
            }
        } catch (Exception e) {
            return;
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
        Remote.appsChanged();
    }

    /** Every installed app with a TV or phone launcher entry, by name. TV versions win. */
    static List<Installed> installed(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, Installed> byPackage = new LinkedHashMap<String, Installed>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> infos = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
            for (ResolveInfo ri : infos) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || byPackage.containsKey(ai.packageName)
                        || ai.packageName.equals(context.getPackageName())) {
                    continue;
                }
                CharSequence label = ri.loadLabel(pm);
                byPackage.put(ai.packageName, new Installed(ai.packageName,
                        label != null ? label.toString() : ai.packageName,
                        new ComponentName(ai.packageName, ai.name), category));
            }
        }
        List<Installed> result = new ArrayList<Installed>(byPackage.values());
        final Collator collator = Collator.getInstance();
        Collections.sort(result, new Comparator<Installed>() {
            @Override
            public int compare(Installed a, Installed b) {
                return collator.compare(a.label, b.label);
            }
        });
        return result;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace("+", "plus").replaceAll("[^a-z0-9]", "");
    }

    // The starting list from apps.xml ("Name|package,package"), matched to what is installed.
    private static List<Item> defaults(Context context) {
        String[] wanted = context.getResources().getStringArray(R.array.visible_apps);
        List<Installed> installed = installed(context);
        Map<String, Installed> byPackage = new HashMap<String, Installed>();
        Map<String, Installed> byLabel = new HashMap<String, Installed>();
        for (Installed app : installed) {
            byPackage.put(app.pkg, app);
            byLabel.put(normalize(app.label), app);
        }
        List<Item> items = new ArrayList<Item>();
        for (String line : wanted) {
            String[] parts = line.split("\\|", -1);
            String name = parts[0].trim();
            Installed match = null;
            if (parts.length > 1) {
                for (String pkg : parts[1].split(",")) {
                    if (match == null && byPackage.containsKey(pkg.trim())) {
                        match = byPackage.get(pkg.trim());
                    }
                }
            }
            if (match == null) {
                match = byLabel.get(normalize(name));
            }
            if (match != null) {
                items.add(new Item(match.pkg, name));
            }
        }
        return items;
    }
}

package com.minimal.tvlauncher;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The apps shown on the home screen, and the actions both launchers share. */
final class Apps {

    static class Entry {
        ComponentName component;
        String category;
        String label;   // the name chosen in AppList
    }

    private Apps() {
    }

    /** The apps to show, in order, as chosen in AppList. Apps that aren't installed are skipped. */
    static List<Entry> load(Context context) {
        Map<String, AppList.Installed> installed = new HashMap<String, AppList.Installed>();
        for (AppList.Installed app : AppList.installed(context)) {
            installed.put(app.pkg, app);
        }
        List<Entry> result = new ArrayList<Entry>();
        for (AppList.Item item : AppList.get(context)) {
            AppList.Installed app = installed.get(item.pkg);
            if (app != null) {
                Entry entry = new Entry();
                entry.component = app.component;
                entry.category = app.category;
                entry.label = item.name;
                result.add(entry);
            }
        }
        return result;
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

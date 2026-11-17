package com.minimal.tvlauncher;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Updates the launcher from an APK uploaded on the phone page, without adb: the upload is
 * streamed straight into Android's package installer, and the TV shows its usual "Do you
 * want to install an update?" screen, answered with the remote. Android only accepts an
 * update signed with the same key as the installed launcher, and only for this package.
 *
 * Android 8 and later also need "Install unknown apps" allowed for the launcher, once; the
 * phone page can open that setting on the TV ({@link #openPermission}).
 */
final class Updater {

    // Changes when the launcher restarts, which is how the phone page knows an update went in.
    static final long STARTED = System.currentTimeMillis();
    // What the phone page shows while it waits: idle, installing, confirm, failed.
    private static volatile String state = "idle";
    private static volatile String message = "";

    private Updater() {
    }

    static String state() {
        return state;
    }

    static String message() {
        return message;
    }

    /** Whether Android lets the launcher install updates. */
    static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {   // PackageManager.canRequestPackageInstalls(), newer than this app's build tools
                return (Boolean) context.getPackageManager().getClass()
                        .getMethod("canRequestPackageInstalls").invoke(context.getPackageManager());
            } catch (Exception e) {
                return false;
            }
        }
        return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1;
    }

    /** Opens the TV's setting that allows the launcher to install updates. */
    static void openPermission(Context context) {
        Intent intent = Build.VERSION.SDK_INT >= 26
                ? new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + context.getPackageName()))
                : new Intent(Settings.ACTION_SECURITY_SETTINGS);
        if (!Apps.start(context, intent)) {
            Apps.start(context, new Intent(Settings.ACTION_SECURITY_SETTINGS));
        }
    }

    /** Reads {@code length} bytes of APK from {@code in} into a new install, then asks the TV to confirm. */
    static void install(Context context, InputStream in, long length) throws IOException {
        state = "installing";
        message = "";
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());   // refuse any other app
        params.setSize(length);
        int id = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(id);
        try {
            OutputStream out = session.openWrite("launcher.apk", 0, length);
            try {
                byte[] buf = new byte[65536];
                long left = length;
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) {
                        throw new IOException("The upload was cut short");
                    }
                    out.write(buf, 0, n);
                    left -= n;
                }
                session.fsync(out);
            } finally {
                out.close();
            }
            Intent result = new Intent(context, Result.class);
            PendingIntent pending = PendingIntent.getBroadcast(context, id, result, PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pending.getIntentSender());
        } catch (IOException e) {
            session.abandon();
            state = "failed";
            message = e.getMessage();
            throw e;
        } finally {
            session.close();
        }
    }

    /** Hears back from the installer: shows the TV's confirmation screen, or notes a failure. */
    public static class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    state = "confirm";
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                state = "idle";   // normally never seen: the update replaces this process
            } else {
                state = "failed";
                String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                message = status == PackageInstaller.STATUS_FAILURE_ABORTED ? "Cancelled on the TV"
                        : status == PackageInstaller.STATUS_FAILURE_CONFLICT ? "Doesn't fit the installed launcher (another signing key, or an older version)"
                        : status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ? "Not compatible with this TV"
                        : status == PackageInstaller.STATUS_FAILURE_INVALID ? "Not a valid launcher APK"
                        : detail != null ? detail : "The TV refused the update";
            }
        }
    }
}

package com.minimal.tvlauncher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Settings changed from the phone page (RemoteServer) or the TV's own editor reach the
 * home screen through here, always on the main thread.
 */
final class Remote {

    static final int PORT = 8765;

    interface Listener {
        void onAppsChanged();

        void onLauncherChanged(String id);

        void onBackgroundsChanged();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Listener listener;
    private static RemoteServer server;

    private Remote() {
    }

    /** Starts the phone page server once per process. */
    static synchronized void start(Context context) {
        if (server == null) {
            server = new RemoteServer(context.getApplicationContext(), PORT);
            server.start();
        }
    }

    static void setListener(Listener l) {
        listener = l;
    }

    /** Stops notifying {@code l}, unless a newer home screen has already taken over. */
    static void clearListener(Listener l) {
        if (listener == l) {
            listener = null;
        }
    }

    static void appsChanged() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onAppsChanged();
                }
            }
        });
    }

    static void backgroundsChanged() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onBackgroundsChanged();
                }
            }
        });
    }

    /** Saves the chosen launcher and tells the home screen to switch. */
    static void setLauncher(Context context, final String id) {
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putString("launcher", id).apply();
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onLauncherChanged(id);
                }
            }
        });
    }

    /** The phone page's address, e.g. "http://192.168.1.106:8765", or null when offline. */
    static String url() {
        String ip = address();
        return ip == null ? null : "http://" + ip + ":" + PORT;
    }

    private static String address() {
        try {
            String fallback = null;
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String name = nif.getName();
                        if (name.startsWith("wlan") || name.startsWith("eth")) {
                            return a.getHostAddress();
                        }
                        fallback = a.getHostAddress();
                    }
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }
}

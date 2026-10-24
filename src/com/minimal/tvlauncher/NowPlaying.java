package com.minimal.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Follows Spotify's media session: whether it is playing, and the current album cover. */
class NowPlaying {

    static final int NO_ACCESS = 0;   // notification access not granted yet
    static final int NOTHING = 1;     // Spotify isn't playing anything
    static final int PAUSED = 2;
    static final int PLAYING = 3;

    interface Listener {
        void onStateChanged(int state);

        /** A new track's cover (null if it has none) and its colours (null with no cover). */
        void onArtChanged(Bitmap art, int[] colors);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final ComponentName access;
    private MediaController controller;
    private String trackKey;
    private int state = -1;
    private int artRequest;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    follow(controllers);
                }
            };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            update();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            update();
        }

        @Override
        public void onSessionDestroyed() {
            setController(null);
        }
    };

    NowPlaying(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        sessions = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        access = new ComponentName(context, MediaListener.class);
    }

    void start() {
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, access, main);
            follow(sessions.getActiveSessions(access));
        } catch (SecurityException e) {
            setState(NO_ACCESS);
        }
    }

    void stop() {
        try {
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        } catch (Exception ignored) {
            // Was never added.
        }
        setController(null);
        state = -1;
    }

    int state() {
        return state;
    }

    void playPause() {
        if (controller != null) {
            if (state == PLAYING) {
                controller.getTransportControls().pause();
            } else {
                controller.getTransportControls().play();
            }
        }
    }

    void next() {
        if (controller != null) {
            controller.getTransportControls().skipToNext();
        }
    }

    void previous() {
        if (controller != null) {
            controller.getTransportControls().skipToPrevious();
        }
    }

    private void follow(List<MediaController> controllers) {
        MediaController spotify = null;
        if (controllers != null) {
            for (MediaController c : controllers) {
                if (c.getPackageName().contains("spotify")) {
                    spotify = c;
                    break;
                }
            }
        }
        if (spotify == null || controller == null
                || !spotify.getSessionToken().equals(controller.getSessionToken())) {
            setController(spotify);
        }
    }

    private void setController(MediaController next) {
        if (controller != null) {
            controller.unregisterCallback(callback);
        }
        controller = next;
        if (controller != null) {
            controller.registerCallback(callback, main);
        }
        update();
    }

    private void update() {
        MediaMetadata metadata = controller == null ? null : controller.getMetadata();
        PlaybackState playback = controller == null ? null : controller.getPlaybackState();
        int s = playback == null ? PlaybackState.STATE_NONE : playback.getState();

        if (metadata == null) {
            setState(NOTHING);
        } else if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
                || s == PlaybackState.STATE_CONNECTING || s == PlaybackState.STATE_FAST_FORWARDING
                || s == PlaybackState.STATE_REWINDING || s == PlaybackState.STATE_SKIPPING_TO_NEXT
                || s == PlaybackState.STATE_SKIPPING_TO_PREVIOUS
                || s == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM) {
            setState(PLAYING);
        } else {
            setState(PAUSED);
        }

        String key = metadata == null ? null
                : metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) + "\n"
                + metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (key == null ? trackKey != null : !key.equals(trackKey)) {
            trackKey = key;
            loadArt(metadata);
        }
    }

    private void setState(int s) {
        if (s != state) {
            state = s;
            listener.onStateChanged(s);
        }
    }

    // The cover may come as a bitmap or as a link to one; links are fetched off the main thread.
    private void loadArt(final MediaMetadata metadata) {
        final int request = ++artRequest;
        if (metadata == null) {
            listener.onArtChanged(null, null);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap art = null;
                for (String k : new String[] {MediaMetadata.METADATA_KEY_ALBUM_ART,
                        MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
                    if (art == null) {
                        art = metadata.getBitmap(k);
                    }
                }
                for (String k : new String[] {MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                        MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
                    if (art == null) {
                        art = fetch(metadata.getString(k));
                    }
                }
                final Bitmap cover = art;
                final int[] colors = art == null ? null : AlbumColors.extract(art);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (request == artRequest) {
                            listener.onArtChanged(cover, colors);
                        }
                    }
                });
            }
        }).start();
    }

    private Bitmap fetch(String link) {
        if (link == null || link.isEmpty()) {
            return null;
        }
        InputStream in = null;
        try {
            Uri uri = Uri.parse(link);
            String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                in = conn.getInputStream();
            } else {
                in = context.getContentResolver().openInputStream(uri);
            }
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // Nothing to do.
                }
            }
        }
    }
}

package com.minimal.tvlauncher;

import android.content.Context;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

/**
 * Plays a looping video from the app's assets full-screen (the Fireplace launcher's fire),
 * through {@link VideoLoop}: a frame is asked for on every screen refresh, so each one shows
 * for the same time. Tells {@code onStarted} when the first frame is up.
 */
final class FireView extends SurfaceView implements SurfaceHolder.Callback, Choreographer.FrameCallback {

    private String path;
    private final Runnable onStarted;
    private final long vsyncUs;
    private Surface surface;
    private VideoLoop loop;
    private boolean resumed;
    private boolean failed;
    private long clockUs;
    private long lastNs;

    FireView(Context context, String path, Runnable onStarted) {
        super(context);
        this.path = path;
        this.onStarted = onStarted;
        float hz = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRefreshRate();
        vsyncUs = Math.round(1e6 / (hz >= 23 && hz <= 240 ? hz : 60));
        getHolder().addCallback(this);
    }

    /** Plays another video instead (from its start). */
    void setPath(String next) {
        if (next.equals(path) && !failed) {
            return;
        }
        stop();
        path = next;
        failed = false;
        clockUs = 0;
        start();
    }

    void onResume() {
        resumed = true;
        lastNs = 0;
        start();
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
    }

    void onPause() {
        resumed = false;
        Choreographer.getInstance().removeFrameCallback(this);
        stop();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stop();
        surface = null;
    }

    // The clock moves in whole screen refreshes, so the frames change on an even beat.
    @Override
    public void doFrame(long frameTimeNanos) {
        if (!resumed) {
            return;
        }
        if (lastNs != 0) {
            long steps = Math.round((frameTimeNanos - lastNs) / 1000.0 / vsyncUs);
            clockUs += vsyncUs * Math.max(1, Math.min(steps, 6));
        }
        lastNs = frameTimeNanos;
        if (loop != null) {
            if (!loop.alive()) {
                stop();
                failed = true;
            } else {
                loop.showUpTo(clockUs + vsyncUs);
            }
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void start() {
        if (loop != null || !resumed || surface == null || failed) {
            return;
        }
        try {
            loop = new VideoLoop(getContext(), path, surface, clockUs);
            loop.setOnFirstFrame(new Runnable() {
                @Override
                public void run() {
                    post(onStarted);
                }
            });
        } catch (Exception e) {
            failed = true;   // the still stays up
        }
    }

    private void stop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
    }
}

package com.minimal.tvlauncher;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Plays a video from the app's assets onto a Surface in an endless, seamless loop, one frame
 * at a time on request: the drawing thread says which moment it is about to show
 * ({@link #showUpTo}) and the frame for that moment is handed over. That keeps every frame on
 * screen for the same number of refreshes, so slow motion stays perfectly even.
 *
 * Times are in microseconds and keep counting up across loops (the second time round, the
 * first frame is at one duration, and so on).
 */
final class VideoLoop implements Runnable {

    private static final String TAG = "VideoLoop";

    final long durationUs;
    final long frameUs;

    private final MediaExtractor extractor = new MediaExtractor();
    private final MediaFormat format;
    private final Surface surface;
    private final Thread thread;
    private long loopBaseUs;
    private volatile boolean running = true;
    private final Object lock = new Object();
    private long wantedUs = Long.MIN_VALUE;

    /**
     * Opens the video at the keyframe at or before {@code atUs} (0 or more). Frames before
     * the first requested time are decoded but skipped.
     */
    VideoLoop(AssetManager assets, String path, Surface surface, long atUs) throws Exception {
        this.surface = surface;
        AssetFileDescriptor fd = assets.openFd(path);   // the video is stored uncompressed in the APK
        try {
            extractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        } finally {
            fd.close();
        }
        MediaFormat f = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat t = extractor.getTrackFormat(i);
            if (t.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                extractor.selectTrack(i);
                f = t;
                break;
            }
        }
        if (f == null) {
            throw new IllegalArgumentException("no video in " + path);
        }
        format = f;
        int fps = f.containsKey(MediaFormat.KEY_FRAME_RATE) ? f.getInteger(MediaFormat.KEY_FRAME_RATE) : 30;
        frameUs = 1000000L / fps;
        // The whole number of frames that fit the track's length.
        long frames = Math.round(f.getLong(MediaFormat.KEY_DURATION) * fps / 1e6);
        durationUs = frames * 1000000L / fps;

        atUs = Math.max(0, atUs);
        loopBaseUs = atUs / durationUs * durationUs;
        extractor.seekTo(atUs - loopBaseUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        thread = new Thread(this, TAG);
        thread.start();
    }

    /** Hands over the latest frame due at or before this time. */
    void showUpTo(long us) {
        synchronized (lock) {
            wantedUs = us;
            lock.notifyAll();
        }
    }

    boolean alive() {
        return thread.isAlive();
    }

    void stop() {
        running = false;
        synchronized (lock) {
            lock.notifyAll();
        }
        try {
            thread.join(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, surface, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (running) {
                int in = codec.dequeueInputBuffer(2000);
                if (in >= 0) {
                    feed(codec, in);
                }
                int out = codec.dequeueOutputBuffer(info, 2000);
                if (out < 0) {
                    continue;
                }
                long pts = info.presentationTimeUs;
                boolean show = true;
                synchronized (lock) {
                    // Wait until this frame's moment comes; the decoder has the next ones ready.
                    while (running && wantedUs < pts) {
                        lock.wait(100);
                    }
                    // Too late already (the next frame is due too): skip it to catch up.
                    if (wantedUs >= pts + frameUs) {
                        show = false;
                    }
                }
                codec.releaseOutputBuffer(out, show && running);
            }
        } catch (Exception e) {
            Log.w(TAG, "video stopped", e);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                codec.release();
            }
            extractor.release();
        }
    }

    // Next sample into the decoder; at the end, round to the start with the times running on.
    private void feed(MediaCodec codec, int in) {
        ByteBuffer buf = codec.getInputBuffer(in);
        int size = extractor.readSampleData(buf, 0);
        if (size < 0) {
            loopBaseUs += durationUs;
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            size = extractor.readSampleData(buf, 0);
        }
        long time = loopBaseUs + extractor.getSampleTime();
        int flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
        codec.queueInputBuffer(in, 0, Math.max(size, 0), time, flags);
        extractor.advance();
    }
}

package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Date;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The Apartment launcher's picture: a slow loop at eye level through the living room, with
 * the live sky over Riga in the windows.
 *
 * The room is a pre-rendered video (assets/apartment, made by web/render_video.js), looped:
 * the room on the left of each frame and, top right at half size, how solid each point is,
 * so the sky shows only through the window glass. One for daytime and one for the evening,
 * lit by the flat's orange lamps, and both again with snow on the terrace for winter (when
 * it's snowing, or freezing from November to March). The TV's video decoder plays it
 * ({@link VideoLoop}), so the motion costs almost nothing. One shader draws the sky (time of
 * day, the sun, stars, the city's warm glow at night), the room over it, and rain, snow and
 * lightning outside the windows.
 *
 * It starts at once: until the decoder's first frame arrives, a still of the video's first
 * frame is shown. It draws at 1280×720 (the TV scales it up), and asks the TV for a refresh
 * rate the video's 30 frames per second divide evenly.
 *
 * On Latvia's red days it also draws that holiday's decoration ({@link Holidays}).
 */
final class OrbitView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "OrbitView";
    private static final String DAY = "apartment/room_day.mp4";
    private static final String EVENING = "apartment/room_evening.mp4";
    private static final String WINTER_DAY = "apartment/room_winter_day.mp4";        // snow on the terrace
    private static final String WINTER_EVENING = "apartment/room_winter_evening.mp4";
    // The camera looks along the plan's -x (towards the terrace door): where the sun and
    // stars are, seen through the windows.
    private static final float VIEW_ANGLE = (float) (Math.PI / 2);
    private static final int DRAW_WIDTH = 1280, DRAW_HEIGHT = 720;

    private final long vsyncUs;
    private volatile Weather.Now weather;
    private volatile boolean weatherChanged = true;
    private volatile int holiday = Holidays.NONE;
    private volatile float[] holidayColors = new float[9];

    // Video. The loop is shared with the main thread (which stops it on pause), so it's guarded.
    private final Object videoLock = new Object();
    private boolean resumed = true;
    private VideoLoop loop;
    private String loopPath;
    private boolean videoFailed;
    private SurfaceTexture frames;
    private Surface videoSurface;
    private int pendingFrames;
    private boolean hasFrame;
    private final float[] texMatrix = new float[16];

    // Drawing state, on the drawing thread only.
    private int program;
    private int videoTex;
    private int posterTex;
    private boolean hasPoster;
    private FloatBuffer quad;
    private int width = 1, height = 1;
    private long clockUs;
    private volatile long lastDrawNs;   // 0: start timing afresh (set again on resume)
    private Sky sky;
    private long skyAtMs;
    private float time;
    private float flash, nextFlash = 5;

    OrbitView(Context context) {
        super(context);
        float hz = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRefreshRate();
        vsyncUs = Math.round(1e6 / (hz >= 23 && hz <= 240 ? hz : 60));
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        getHolder().setFixedSize(DRAW_WIDTH, DRAW_HEIGHT);   // the TV's scaler does the rest
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    /**
     * Asks for a display mode whose refresh rate 30 divides evenly (60 Hz rather than 50 Hz),
     * so each video frame shows for the same time. Only if the TV isn't at such a rate already.
     */
    static void preferEvenRefresh(android.view.Window window, android.view.Display display) {
        if (android.os.Build.VERSION.SDK_INT < 23 || window == null || display == null) {
            return;
        }
        android.view.Display.Mode current = display.getMode();
        if (Math.abs(current.getRefreshRate() / 30 - Math.round(current.getRefreshRate() / 30)) < 0.02) {
            return;
        }
        android.view.Display.Mode best = null;
        for (android.view.Display.Mode m : display.getSupportedModes()) {
            float hz = m.getRefreshRate();
            boolean even = Math.abs(hz / 30 - Math.round(hz / 30)) < 0.02 && hz >= 59;
            if (even && m.getPhysicalWidth() == current.getPhysicalWidth() && m.getPhysicalHeight() == current.getPhysicalHeight()
                    && (best == null || Math.abs(hz - 60) < Math.abs(best.getRefreshRate() - 60))) {
                best = m;
            }
        }
        if (best != null) {
            android.view.WindowManager.LayoutParams lp = window.getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            window.setAttributes(lp);
        }
    }

    void setWeather(Weather.Now now) {
        if (now != null) {
            weather = now;
            weatherChanged = true;
        }
    }

    /** Today's holiday decoration, or null for none. */
    void setHoliday(Holidays.Day day) {
        if (day != null) {
            holidayColors = day.palette;
        }
        holiday = day != null ? day.effect : Holidays.NONE;
    }

    @Override
    public void onResume() {
        synchronized (videoLock) {
            resumed = true;
        }
        lastDrawNs = 0;
        super.onResume();
    }

    @Override
    public void onPause() {
        synchronized (videoLock) {
            resumed = false;
            stopVideo();
        }
        super.onPause();
    }

    void release() {
        synchronized (videoLock) {
            resumed = false;
            stopVideo();
        }
    }

    private void stopVideo() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (videoLock) {
            if (surfaceTexture == frames) {
                pendingFrames++;
            }
        }
    }

    // ---- Renderer (the drawing thread) ----

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        program = buildProgram(VERTEX, FRAGMENT);
        int[] tex = new int[2];
        GLES20.glGenTextures(2, tex, 0);
        videoTex = tex[0];
        posterTex = tex[1];

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTex);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // The still shown until the video's first frame: that frame, packed the same way.
        sky = Sky.at(new Date(), weather);
        skyAtMs = System.currentTimeMillis();
        hasPoster = false;
        try {
            java.io.InputStream in = getContext().getAssets().open(video(sky).replace(".mp4", ".jpg"));
            android.graphics.Bitmap still = android.graphics.BitmapFactory.decodeStream(in);
            in.close();
            if (still != null) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, posterTex);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, still, 0);
                still.recycle();
                hasPoster = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "no still for the start", e);
        }

        quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(new float[] {-1, -1, 1, -1, -1, 1, 1, 1}).position(0);

        // A new context (first start, or the old one was lost): the video goes to a new surface.
        synchronized (videoLock) {
            stopVideo();
            if (videoSurface != null) {
                videoSurface.release();
                frames.release();
            }
            frames = new SurfaceTexture(videoTex);
            frames.setOnFrameAvailableListener(this);
            videoSurface = new Surface(frames);
            pendingFrames = 0;
            hasFrame = false;
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // The clock moves in whole screen refreshes, so the video's frames (two refreshes
        // each at 30 fps on a 60 Hz screen) always change on the same beat.
        long now = System.nanoTime();
        if (lastDrawNs != 0) {
            long steps = Math.round((now - lastDrawNs) / 1000.0 / vsyncUs);
            clockUs += vsyncUs * Math.max(1, Math.min(steps, 6));
        }
        float dt = lastDrawNs == 0 ? 0 : Math.min((now - lastDrawNs) / 1e9f, 0.1f);
        lastDrawNs = now;
        time += dt;

        long nowMs = System.currentTimeMillis();
        if (sky == null || weatherChanged || nowMs - skyAtMs > 60000) {
            weatherChanged = false;
            sky = Sky.at(new Date(nowMs), weather);
            skyAtMs = nowMs;
        }

        synchronized (videoLock) {
            String want = video(sky);
            if (loop != null && !want.equals(loopPath)) {
                stopVideo();   // evening or winter came or went: carry on with the other video
            }
            if (loop != null && !loop.alive()) {
                stopVideo();
                videoFailed = true;   // the decoder gave up; the sky carries on alone
            }
            // A new loop starts from the keyframe before the clock and skips ahead to it,
            // so after a pause (or at sunset) the turn carries on where it was.
            if (loop == null && resumed && !videoFailed && videoSurface != null && waitedForVideo(want, nowMs)) {
                try {
                    loop = new VideoLoop(getContext(), want, videoSurface, clockUs);
                    loopPath = want;
                } catch (Exception e) {
                    Log.w(TAG, "no video, showing the sky only", e);
                    videoFailed = true;
                }
            }
            if (loop != null) {
                // Ask for the frame due at the next refresh; it's on hand by then.
                loop.showUpTo(clockUs + vsyncUs);
            }
            int n = pendingFrames;
            pendingFrames = 0;
            for (int i = 0; i < n; i++) {
                frames.updateTexImage();
            }
            if (n > 0) {
                frames.getTransformMatrix(texMatrix);
                hasFrame = true;
            }
        }

        if (sky.thunder > 0) {
            nextFlash -= dt;
            if (nextFlash <= 0) {
                flash = 0.18f;
                nextFlash = 6 + (float) Math.random() * 14;
            }
            flash -= dt;
        }
        draw(VIEW_ANGLE);
    }

    private void draw(float angle) {
        GLES20.glUseProgram(program);
        int pos = GLES20.glGetAttribLocation(program, "aPos");
        GLES20.glEnableVertexAttribArray(pos);
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTex);
        GLES20.glUniform1i(u("uVideo"), 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, posterTex);
        GLES20.glUniform1i(u("uPoster"), 1);
        GLES20.glUniformMatrix4fv(u("uTexM"), 1, false, texMatrix, 0);
        GLES20.glUniform1f(u("uSource"), hasFrame ? 1 : hasPoster ? 2 : 0);

        float aspect = (float) width / height;
        GLES20.glUniform1f(u("uAspect"), aspect);
        GLES20.glUniform1f(u("uAngle"), angle);
        GLES20.glUniform1f(u("uTime"), time);

        Sky s = sky;
        GLES20.glUniform3fv(u("uTop"), 1, s.top, 0);
        GLES20.glUniform3fv(u("uHorizon"), 1, s.horizon, 0);
        GLES20.glUniform3fv(u("uSunColor"), 1, s.sunColor, 0);
        GLES20.glUniform1f(u("uSunVis"), s.sunVis);
        GLES20.glUniform1f(u("uDusk"), s.dusk);
        GLES20.glUniform1f(u("uDay"), s.day);
        int fx = holiday;
        GLES20.glUniform1f(u("uSnow"), fx == Holidays.CHRISTMAS ? Math.max(s.snow, 0.35f) : s.snow);
        GLES20.glUniform1f(u("uFog"), s.fog);
        GLES20.glUniform1f(u("uStars"), (1 - s.day) * (1 - s.cloud) * 0.9f);
        GLES20.glUniform1f(u("uRain"), s.rain * 0.7f);   // calmer than before
        GLES20.glUniform1f(u("uFlash"), flash > 0 && s.thunder > 0 ? 0.6f : 0);

        // The sun's place on screen, from its real direction relative to where the camera
        // faces: ahead of the camera is the top of the screen, behind it the bottom; the
        // higher the sun, the further in from the edge.
        double hl = Math.hypot(s.sunX, s.sunZ);
        double hx = hl > 0 ? s.sunX / hl : 0, hz = hl > 0 ? s.sunZ / hl : 1;
        double ahead = -Math.sin(angle) * hx - Math.cos(angle) * hz;
        double right = Math.cos(angle) * hx - Math.sin(angle) * hz;
        double r = 0.62 - 0.35 * Math.min(1, Math.max(0, s.sunAlt / 0.9));
        GLES20.glUniform2f(u("uSun"), (float) (0.5 + right * r / aspect * 1.4), (float) (0.5 + ahead * r));

        // The flat in the daytime video was lit by a partly cloudy sun: duller under heavy
        // cloud, warmer around sunrise and sunset.
        boolean evening = s.lampsOn();
        float dull = evening ? 1 : 1 - 0.15f * Math.max(0, s.cloud - 0.4f) / 0.6f;
        float warm = evening ? 0 : s.dusk * 0.6f;
        GLES20.glUniform3f(u("uTint"), dull, dull * (1 - 0.12f * warm), dull * (1 - 0.22f * warm));

        // The city's warm light low in the night sky.
        GLES20.glUniform1f(u("uCity"), 1 - s.day);

        float[] hc = holidayColors;
        GLES20.glUniform1f(u("uHoliday"), fx);
        GLES20.glUniform3f(u("uHolA"), hc[0], hc[1], hc[2]);
        GLES20.glUniform3f(u("uHolB"), hc[3], hc[4], hc[5]);
        GLES20.glUniform3f(u("uHolC"), hc[6], hc[7], hc[8]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    // Which video suits the time of day and the weather.
    private String video(Sky s) {
        String want = s.lampsOn() ? EVENING : DAY;
        if (s.wintry) {
            String winter = s.lampsOn() ? WINTER_EVENING : WINTER_DAY;
            if (hasAsset(winter)) {
                want = winter;
            }
        }
        return want;
    }

    private final java.util.Map<String, Boolean> assets = new java.util.HashMap<String, Boolean>();

    // The winter videos are optional: used only if bundled or downloaded.
    private boolean hasAsset(String path) {
        Boolean known = assets.get(path);
        if (known == null) {
            known = Scenes.available(getContext(), path);
            assets.put(path, known);
        }
        return known;
    }

    private long videoCheckedAt;

    // True once the video is on the TV. If it isn't yet, starts downloading it (see Scenes) and
    // looks again every couple of seconds; meanwhile the still stays up.
    private boolean waitedForVideo(String path, long nowMs) {
        if (nowMs - videoCheckedAt < 2000) {
            return false;
        }
        videoCheckedAt = nowMs;
        if (Scenes.available(getContext(), path)) {
            return true;
        }
        Scenes.fetch(getContext(), path);
        return false;
    }

    private int u(String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    private static int buildProgram(String vertex, String fragment) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vertex));
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fragment));
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "link: " + GLES20.glGetProgramInfoLog(p));
        }
        return p;
    }

    private static int shader(int type, String source) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, source);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "compile: " + GLES20.glGetShaderInfoLog(s));
        }
        return s;
    }

    private static final String VERTEX =
            "attribute vec2 aPos;\n"
            + "varying vec2 vUv;\n"
            + "void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n";

    // The sky follows the original page's colours (mixed in linear light, then shown as
    // sRGB); the video is already sRGB, premultiplied by its transparency.
    private static final String FRAGMENT =
            "#extension GL_OES_EGL_image_external : require\n"
            + "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
            + "precision highp float;\n"
            + "#else\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "uniform samplerExternalOES uVideo;\n"
            + "uniform sampler2D uPoster;\n"
            + "uniform mat4 uTexM;\n"
            + "uniform vec3 uTop, uHorizon, uSunColor, uTint;\n"
            + "uniform vec2 uSun;\n"
            + "uniform float uSource, uSunVis, uDusk, uDay, uRain, uSnow, uFog, uFlash, uTime, uAngle, uAspect, uStars;\n"
            + "uniform float uCity, uHoliday;\n"
            + "uniform vec3 uHolA, uHolB, uHolC;\n"
            + "varying vec2 vUv;\n"
            + "float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }\n"
            + "vec3 toSRGB(vec3 c) { return mix(c * 12.92, 1.055 * pow(c, vec3(0.41666)) - 0.055, step(0.0031308, c)); }\n"
            + "bool fx(float id) { return abs(uHoliday - id) < 0.5; }\n"
            + "vec3 pick(float k) { return k < 0.333 ? uHolA : (k < 0.667 ? uHolB : uHolC); }\n"
            // Holiday things drifting down (speed > 0) or up (speed < 0), at most one per grid cell,
            // swaying together in the breeze and turning: xy = the pixel in the thing's own frame
            // (outline at length 1), z = which of the three colours (0-1). Far away (9) if none.
            + "vec3 drifter(vec2 p, float scale, float speed, float density, vec2 size, float spin, float seed) {\n"
            + "  vec2 g = p * scale + vec2(sin(uTime * 0.4 + seed) * 0.6, uTime * speed);\n"
            + "  vec2 cell = floor(g);\n"
            + "  float h = hash(cell + seed);\n"
            + "  if (h > density) return vec3(9.0, 9.0, 0.0);\n"
            + "  vec2 f = fract(g) - 0.5 - (vec2(hash(cell + seed + 3.1), hash(cell + seed + 5.7)) - 0.5) * 0.5;\n"
            + "  float a = h * 40.0 + uTime * spin * (h / density - 0.5) * 2.0;\n"
            + "  float ca = cos(a), sa = sin(a);\n"
            + "  return vec3(mat2(ca, -sa, sa, ca) * f / size, h / density);\n"
            + "}\n"
            + "void main() {\n"
            + "  vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);\n"
            // Sky: zenith colour at the top of the screen, the horizon's towards the bottom.
            + "  vec3 col = mix(uHorizon, uTop, smoothstep(-0.15, 1.05, vUv.y));\n"
            // The sun's glow, and the warm side of the sky around sunrise and sunset.
            + "  vec2 sp = (uSun - 0.5) * vec2(uAspect, 1.0);\n"
            + "  float d = length(p - sp);\n"
            + "  col += uSunColor * (uSunVis * (0.55 * exp(-d * d * 60.0) + 0.18 * exp(-d * d * 4.0)) + uDusk * 0.5 * exp(-d * d * 1.1));\n"
            // World-fixed coordinates, for the stars.
            + "  float c = cos(uAngle), s = sin(uAngle);\n"
            + "  vec2 w = mat2(c, -s, s, c) * p;\n"
            // Stars on clear nights.
            + "  if (uStars > 0.01) {\n"
            + "    vec2 g = w * 85.0; float h = hash(floor(g));\n"
            + "    float star = step(0.986, h) * smoothstep(0.32, 0.0, length(fract(g) - 0.5)) * (0.65 + 0.35 * sin(uTime * 1.7 + h * 40.0));\n"
            + "    col += vec3(star) * uStars;\n"
            + "  }\n"
            // Fog: everything fades into the haze.
            + "  col = mix(col, uHorizon, uFog * 0.6);\n"
            // At night the city's warm light along the horizon.
            + "  col += vec3(0.045, 0.02, 0.007) * uCity * smoothstep(0.6, 0.0, vUv.y);\n"
            + "  col = toSRGB(max(col * 0.9, 0.0));\n"   // a touch calmer, so the clock and apps stand out
            // Holiday decorations. New Year: fireworks outside the windows.
            + "  if (fx(1.0)) {\n"
            + "    float bright = 1.0 - 0.45 * uDay;\n"
            + "    for (int i = 0; i < 4; i++) {\n"
            + "      float fi = float(i);\n"
            + "      float t = uTime / (2.6 + fi * 0.55) + fi * 0.31;\n"
            + "      float n = floor(t), ft = fract(t);\n"
            + "      vec2 c = vec2((hash(vec2(n, fi)) - 0.5) * uAspect * 0.85, 0.1 + 0.28 * hash(vec2(fi, n + 7.0)));\n"
            + "      c.y -= ft * ft * 0.05;\n"
            + "      vec2 dv = p - c;\n"
            + "      float r = length(dv);\n"
            + "      float ray = abs(fract(atan(dv.y, dv.x) / 6.2832 * 28.0 + hash(vec2(n, fi + 3.0))) - 0.5);\n"
            + "      float R = 0.2 * (1.0 - (1.0 - ft) * (1.0 - ft) * (1.0 - ft));\n"
            + "      float spark = smoothstep(0.2, 0.0, ray) * smoothstep(0.026, 0.0, abs(r - R));\n"
            + "      float trail = smoothstep(0.1, 0.0, ray) * smoothstep(0.0, R, r) * step(r, R) * 0.5;\n"
            + "      float fade = (1.0 - ft) * (1.0 - ft) * (0.75 + 0.25 * sin(uTime * 25.0 + r * 80.0));\n"
            + "      col += pick(fract(n * 0.37 + fi * 0.29)) * ((spark + trail) * fade * 2.2 + exp(-r * r * 30.0) * fade * 0.25) * bright;\n"
            + "    }\n"
            + "  }\n"
            // The room: the left two thirds of the video; how solid it is, top right at half
            // size (or the same from the still, until the video starts).
            + "  float a = 0.0;\n"
            + "  if (uSource > 0.5) {\n"
            + "    float cu = min(vUv.x * 0.6667, 0.6662), au = 0.6667 + vUv.x * 0.3333, av = clamp(vUv.y, 0.004, 1.0);\n"
            + "    vec3 m;\n"
            + "    if (uSource < 1.5) {\n"
            + "      m = texture2D(uVideo, (uTexM * vec4(cu, vUv.y, 0.0, 1.0)).xy).rgb;\n"
            + "      a = texture2D(uVideo, (uTexM * vec4(au, 0.5 + av * 0.5, 0.0, 1.0)).xy).g;\n"
            + "    } else {\n"
            + "      m = texture2D(uPoster, vec2(cu, 1.0 - vUv.y)).rgb;\n"
            + "      a = texture2D(uPoster, vec2(au, (1.0 - av) * 0.5)).g;\n"
            + "    }\n"
            + "    a = clamp((a - 0.02) / 0.96, 0.0, 1.0);\n"   // video levels aren't exact: keep the sky clear
            + "    col = min(m * uTint, vec3(a)) + col * (1.0 - a);\n"
            + "  }\n"
            + "  float outside = 1.0 - a;\n"   // where the windows show the sky

            // 4 May and 18 November: the flag (carmine, white, carmine) waving at the top right, in front.
            + "  if (fx(5.0) || fx(9.0)) {\n"
            + "    float fw = 0.15, fh = fw * uAspect * 0.5, x0 = 0.8, top = 0.93;\n"
            + "    float u = (vUv.x - x0) / fw;\n"
            + "    float wave = u * 7.0 - uTime * 2.4;\n"
            + "    float v = (vUv.y - (top - fh) - sin(wave) * 0.012 * u) / fh;\n"
            + "    float lit = mix(0.55, 1.0, uDay);\n"
            + "    if (u > 0.0 && u < 1.0 && v > 0.0 && v < 1.0) {\n"
            + "      col = ((v > 0.4 && v < 0.6) ? uHolB : uHolA) * (0.92 + 0.12 * cos(wave) * u) * lit;\n"
            + "    }\n"
            + "    float pole = step(abs(vUv.x - x0 + 0.002) * uAspect, 0.003) * step(top - 0.32, vUv.y) * step(vUv.y, top + 0.012);\n"
            + "    col = mix(col, vec3(0.75, 0.74, 0.7) * lit, pole);\n"
            + "  }\n"
            // Midsummer: a bonfire's glow from below, in the room.
            + "  if (fx(8.0)) {\n"
            + "    vec2 bp = p - vec2(-0.25 * uAspect, -0.72);\n"
            + "    col += vec3(1.0, 0.45, 0.12) * exp(-dot(bp, bp) * 9.0) * 0.2 * (0.85 + 0.15 * sin(uTime * 7.0) * sin(uTime * 3.1 + 1.0));\n"
            + "  }\n"
            // Good Friday: pussy-willow catkins.
            + "  if (fx(2.0)) {\n"
            + "    for (int k = 0; k < 2; k++) {\n"
            + "      float fk = float(k);\n"
            + "      vec3 d = drifter(p, 7.0 + fk * 4.0, 0.22 + fk * 0.1, 0.18, vec2(0.11, 0.19), 0.6, 11.0 + fk * 7.0);\n"
            + "      col = mix(col, pick(d.z) * mix(0.55, 1.0, uDay), smoothstep(1.0, 0.3, length(d.xy)) * (0.8 - fk * 0.2));\n"
            + "    }\n"
            + "  }\n"
            // Easter: onion-skin dyed eggs floating up, with the leaf and band prints of the dyeing.
            + "  if (fx(3.0)) {\n"
            + "    vec3 d = drifter(p, 4.5, -0.12, 0.28, vec2(0.17, 0.23), 0.25, 21.0);\n"
            + "    float e = length(d.xy);\n"
            + "    vec3 ec = pick(d.z) * (0.8 + 0.25 * step(0.55, sin(d.y * 18.0 + sin(d.x * 9.0) * 1.5)));\n"
            + "    ec = ec * (1.05 - 0.45 * e * e) + vec3(0.22) * smoothstep(0.35, 0.0, length(d.xy - vec2(-0.35, 0.4)));\n"
            + "    col = mix(col, ec * mix(0.6, 1.0, uDay), smoothstep(1.0, 0.93, e));\n"
            + "  }\n"
            // 1 May: apple blossom petals. Mother's Day: tulip petals.
            + "  if (fx(4.0) || fx(6.0)) {\n"
            + "    for (int k = 0; k < 2; k++) {\n"
            + "      float fk = float(k);\n"
            + "      vec3 d = drifter(p, 8.0 + fk * 5.0, 0.35 + fk * 0.15, 0.17, vec2(0.2, 0.12), 1.5, 31.0 + fk * 5.0);\n"
            + "      vec3 pc = pick(d.z) * (0.85 + 0.15 * clamp(d.x, -1.0, 1.0));\n"
            + "      col = mix(col, pc * mix(0.6, 1.0, uDay), smoothstep(1.0, 0.85, length(d.xy)) * (0.95 - fk * 0.2));\n"
            + "    }\n"
            + "  }\n"
            // Pentecost: young birch leaves.
            + "  if (fx(7.0)) {\n"
            + "    for (int k = 0; k < 2; k++) {\n"
            + "      float fk = float(k);\n"
            + "      vec3 d = drifter(p, 8.0 + fk * 5.0, 0.3 + fk * 0.15, 0.17, vec2(0.22), 1.2, 41.0 + fk * 5.0);\n"
            + "      float leaf = smoothstep(0.0, 0.06, (1.0 - d.x * d.x) * 0.5 - abs(d.y)) * step(abs(d.x), 1.0);\n"
            + "      vec3 lc = pick(d.z) * (1.0 - 0.3 * smoothstep(0.05, 0.0, abs(d.y)));\n"
            + "      col = mix(col, lc * mix(0.6, 1.0, uDay), leaf * (0.95 - fk * 0.2));\n"
            + "    }\n"
            + "  }\n"
            // Midsummer: embers rising from the bonfire.
            + "  if (fx(8.0)) {\n"
            + "    for (int k = 0; k < 2; k++) {\n"
            + "      float fk = float(k);\n"
            + "      vec3 d = drifter(p, 14.0 + fk * 8.0, -0.5 - fk * 0.3, 0.35, vec2(0.08), 0.0, 51.0 + fk * 9.0);\n"
            + "      float glow = smoothstep(1.0, 0.0, length(d.xy)) * (0.5 + 0.5 * sin(uTime * 9.0 + d.z * 40.0));\n"
            + "      col += pick(d.z) * glow * smoothstep(0.95, 0.15, vUv.y) * (1.2 - fk * 0.4);\n"
            + "    }\n"
            + "  }\n"
            // 18 November: a row of candles along the bottom.
            + "  if (fx(9.0)) {\n"
            + "    float i = floor(vUv.x * 14.0);\n"
            + "    vec2 dc = vec2((vUv.x - (i + 0.5) / 14.0) * uAspect, vUv.y);\n"
            + "    col = mix(col, vec3(0.75, 0.72, 0.67), step(abs(dc.x), 0.006) * step(dc.y, 0.045) * step(0.01, dc.y));\n"
            + "    float fl = 0.85 + 0.15 * sin(uTime * 11.0 + i * 2.3) * sin(uTime * 7.0 + i);\n"
            + "    vec2 fc = dc - vec2(sin(uTime * 5.0 + i) * 0.001, 0.058);\n"
            + "    col = mix(col, uHolC, smoothstep(1.0, 0.3, length(fc / vec2(0.0065, 0.013 * fl))));\n"
            + "    col += uHolC * exp(-dot(fc, fc) * 900.0) * 0.35 * fl;\n"
            + "  }\n"
            // Christmas: strings of twinkling lights across the top (snow falls too).
            + "  if (fx(10.0)) {\n"
            + "    float span = 0.5, n = 11.0;\n"
            + "    float sx = mod(vUv.x, span) / span;\n"
            + "    col = mix(col, vec3(0.08, 0.1, 0.08), smoothstep(0.0025, 0.0008, abs(vUv.y - (0.985 - 0.14 * sx * (1.0 - sx)))) * 0.8);\n"
            + "    float k = floor(sx * n), bx = (k + 0.5) / n;\n"
            + "    vec2 bd = vec2((sx - bx) * span * uAspect, vUv.y - (0.985 - 0.14 * bx * (1.0 - bx) - 0.009));\n"
            + "    float idx = k + floor(vUv.x / span) * n;\n"
            + "    vec3 bc = pick(fract(idx * 0.34));\n"
            + "    float tw = 0.65 + 0.35 * sin(uTime * 2.0 + idx * 1.7);\n"
            + "    col = mix(col, bc * (0.6 + 0.4 * tw), smoothstep(0.0075, 0.005, length(bd / vec2(1.0, 1.3))));\n"
            + "    col += bc * exp(-dot(bd, bd) * 2200.0) * 0.45 * tw;\n"
            + "  }\n"
            // Rain: slanted streaks, seen through the windows.
            + "  if (uRain > 0.0) {\n"
            + "    vec2 r = vec2(p.x + p.y * 0.18, p.y);\n"
            + "    vec2 rg = vec2(r.x * 150.0, r.y * 5.0 + uTime * 7.0);\n"
            + "    float column = floor(rg.x), y = rg.y + hash(vec2(column, 3.0)) * 9.0;\n"
            + "    float seg = floor(y), f = fract(y);\n"
            + "    float on = step(1.0 - uRain * 0.4, hash(vec2(column, seg)));\n"
            + "    float streak = on * smoothstep(0.0, 0.15, f) * smoothstep(0.55, 0.25, f) * smoothstep(0.5, 0.05, abs(fract(rg.x) - 0.5));\n"
            + "    col = mix(col, vec3(0.9, 0.93, 0.95), streak * 0.35 * outside);\n"
            + "  }\n"
            // Snow: three layers of drifting flakes, through the windows.
            + "  if (uSnow > 0.0) {\n"
            + "    for (int k = 0; k < 3; k++) {\n"
            + "      float fk = float(k);\n"
            + "      vec2 sg = p * (12.0 + fk * 7.0) + vec2(sin(uTime * 0.4 + fk * 2.0) * 0.7, uTime * (0.45 + fk * 0.2));\n"
            + "      vec2 sc = floor(sg);\n"
            + "      vec2 o = vec2(hash(sc + 1.3), hash(sc + 7.7)) - 0.5;\n"
            + "      float flake = step(1.0 - uSnow * 0.5, hash(sc + fk * 13.0)) * smoothstep(0.13 - fk * 0.03, 0.0, length(fract(sg) - 0.5 - o * 0.6));\n"
            + "      col += vec3(flake) * (0.75 - fk * 0.18) * outside;\n"
            + "    }\n"
            + "  }\n"
            + "  col += uFlash * vec3(0.55, 0.6, 0.75) * (0.25 + 0.75 * outside);\n"
            + "  gl_FragColor = vec4(col, 1.0);\n"
            + "}\n";
}

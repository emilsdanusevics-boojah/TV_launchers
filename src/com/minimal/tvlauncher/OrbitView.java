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
 * The Apartment launcher's picture: the flat turning slowly, over a live sky.
 *
 * The flat is a pre-rendered video (assets/apartment, made by web/render_video.js): one full
 * turn, looped, with the flat's colour in the left half of each frame and its transparency in
 * the right half. One for daytime and one for the evening, lit by its orange lamps. The TV's
 * video decoder plays it ({@link VideoLoop}), so the motion costs almost nothing, and one
 * shader draws the sky around it (time of day, the sun from its real direction, clouds
 * drifting with the wind, stars, fog, the city's warm glow at night), then the flat, with a
 * soft flickering glow around its lamps in the evening, then rain, snow and lightning in
 * front. The clouds and stars turn with the flat, as if the camera were going round.
 *
 * On Latvia's red days it also draws that holiday's decoration ({@link Holidays}).
 */
final class OrbitView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "OrbitView";
    private static final String DAY = "apartment/orbit_day.mp4";
    private static final String EVENING = "apartment/orbit_night.mp4";
    // How the video was rendered (see web/render_video.js and web/apartment.js).
    private static final double ANGLE0 = 0.6;              // the camera's angle at the first frame
    private static final long FALLBACK_TURN_US = 150000000L;
    private static final float TILE_W = 1.04f;             // the video's picture, in screen heights
    private static final float TILE_H = 1.04f * 880 / 960;
    private static final float CENTRE_X = 0.56f, CENTRE_Y = 0.56f;   // nudged up and right, between the clock and the apps
    // The video's camera, to find where the lamps are on screen: it circles TARGET at
    // RADIUS, HEIGHT metres up, with a 42° tall view of the whole screen.
    private static final double TARGET_X = 4.34, TARGET_Z = 2.6, RADIUS = 12.2, HEIGHT = 21.2;
    private static final double TAN_HALF_FOV = Math.tan(Math.toRadians(21));
    // The flat's three orange lamps (x, height, z in metres, from web/apartment.js).
    private static final double[][] LAMPS = {{1.66, 1.0, 0.36}, {3.9, 1.28, 0.25}, {4.2, 0.8, 3.15}};

    private final long vsyncUs;
    private volatile Weather.Now weather;
    private volatile boolean weatherChanged = true;
    private volatile int holiday = Holidays.NONE;
    private volatile float[] holidayColors = new float[9];
    private final float[] lamps = new float[9];

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
    private int noiseTex;
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
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
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
        noiseTex = tex[1];

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTex);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTex);
        int size = 512;
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, size, size, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, cloudNoise(size));
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

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

        long turnUs = FALLBACK_TURN_US;
        synchronized (videoLock) {
            String want = sky.lampsOn() ? EVENING : DAY;
            if (loop != null && !want.equals(loopPath)) {
                stopVideo();   // evening came or went: carry on with the other video
            }
            if (loop != null && !loop.alive()) {
                stopVideo();
                videoFailed = true;   // the decoder gave up; the sky carries on alone
            }
            // A new loop starts from the keyframe before the clock and skips ahead to it,
            // so after a pause (or at sunset) the turn carries on where it was.
            if (loop == null && resumed && !videoFailed && videoSurface != null) {
                try {
                    loop = new VideoLoop(getContext().getAssets(), want, videoSurface, clockUs);
                    loopPath = want;
                } catch (Exception e) {
                    Log.w(TAG, "no video, showing the sky only", e);
                    videoFailed = true;
                }
            }
            if (loop != null) {
                turnUs = loop.durationUs;
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

        double angle = ANGLE0 + 2 * Math.PI * (clockUs % turnUs) / turnUs;
        if (sky.thunder > 0) {
            nextFlash -= dt;
            if (nextFlash <= 0) {
                flash = 0.18f;
                nextFlash = 6 + (float) Math.random() * 14;
            }
            flash -= dt;
        }
        draw((float) angle);
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
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTex);
        GLES20.glUniform1i(u("uNoise"), 1);
        GLES20.glUniformMatrix4fv(u("uTexM"), 1, false, texMatrix, 0);
        GLES20.glUniform1f(u("uHasVideo"), hasFrame ? 1 : 0);

        float aspect = (float) width / height;
        float tw = TILE_W / aspect, th = TILE_H;
        GLES20.glUniform4f(u("uTile"), CENTRE_X - tw / 2, CENTRE_Y - th / 2, tw, th);
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
        GLES20.glUniform1f(u("uCloud"), s.cloud);
        GLES20.glUniform1f(u("uRain"), s.rain);
        int fx = holiday;
        GLES20.glUniform1f(u("uSnow"), fx == Holidays.CHRISTMAS ? Math.max(s.snow, 0.35f) : s.snow);
        GLES20.glUniform1f(u("uFog"), s.fog);
        GLES20.glUniform1f(u("uStars"), (1 - s.day) * (1 - s.cloud) * 0.9f);
        GLES20.glUniform1f(u("uFlash"), flash > 0 && s.thunder > 0 ? 0.6f : 0);
        GLES20.glUniform2f(u("uWind"), s.windX, s.windZ);

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

        // The lamps' soft glow in the evening, and the city's warm light low in the night sky.
        placeLamps(angle, tw, th);
        GLES20.glUniform3fv(u("uLamps"), 3, lamps, 0);
        GLES20.glUniform1f(u("uLampGlow"), evening && hasFrame ? 1 : 0);
        GLES20.glUniform1f(u("uCity"), 1 - s.day);

        float[] hc = holidayColors;
        GLES20.glUniform1f(u("uHoliday"), fx);
        GLES20.glUniform3f(u("uHolA"), hc[0], hc[1], hc[2]);
        GLES20.glUniform3f(u("uHolB"), hc[3], hc[4], hc[5]);
        GLES20.glUniform3f(u("uHolC"), hc[6], hc[7], hc[8]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    // Where each lamp is on screen (x, y as fractions of it, and 1 if in front of the
    // camera), seen from the video's camera at this angle.
    private void placeLamps(double angle, float tw, float th) {
        double cx = TARGET_X + Math.sin(angle) * RADIUS, cy = HEIGHT, cz = TARGET_Z + Math.cos(angle) * RADIUS;
        double fx = TARGET_X - cx, fy = -cy, fz = TARGET_Z - cz;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        double rx = -fz, rz = fx;                          // right = forward × up
        double rl = Math.sqrt(rx * rx + rz * rz);
        rx /= rl;
        rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;   // up = right × forward
        for (int i = 0; i < 3; i++) {
            double dx = LAMPS[i][0] - cx, dy = LAMPS[i][1] - cy, dz = LAMPS[i][2] - cz;
            double x = dx * rx + dz * rz, y = dx * ux + dy * uy + dz * uz, z = dx * fx + dy * fy + dz * fz;
            double tileX = 0.5 + 0.5 * x / (z * TAN_HALF_FOV * TILE_W);
            double tileY = 0.5 + 0.5 * y / (z * TAN_HALF_FOV * TILE_H);
            lamps[i * 3] = (float) (CENTRE_X - tw / 2 + tileX * tw);
            lamps[i * 3 + 1] = (float) (CENTRE_Y - th / 2 + tileY * th);
            lamps[i * 3 + 2] = z > 0 ? 1 : 0;
        }
    }

    private int u(String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    // Cloud noise: a tileable pattern of five octaves of smooth noise, made once. Reading it
    // is far cheaper than working noise out per pixel.
    private static ByteBuffer cloudNoise(int size) {
        float[] sum = new float[size * size];
        long seed = 7;
        float amp = 0.5f;
        for (int cells = 8, o = 0; o < 5; o++, cells *= 2, amp *= 0.5f) {
            float[] lattice = new float[cells * cells];
            for (int i = 0; i < lattice.length; i++) {
                seed = seed * 16807 % 2147483647;
                lattice[i] = seed / 2147483647f;
            }
            for (int y = 0; y < size; y++) {
                float fy = (float) y / size * cells;
                int iy = (int) fy;
                float ty = fy - iy, sy = ty * ty * (3 - 2 * ty);
                int r0 = iy * cells, r1 = ((iy + 1) % cells) * cells;
                for (int x = 0; x < size; x++) {
                    float fx = (float) x / size * cells;
                    int ix = (int) fx, ix1 = (ix + 1) % cells;
                    float tx = fx - ix, sx = tx * tx * (3 - 2 * tx);
                    float top = lattice[r0 + ix] + (lattice[r0 + ix1] - lattice[r0 + ix]) * sx;
                    float bottom = lattice[r1 + ix] + (lattice[r1 + ix1] - lattice[r1 + ix]) * sx;
                    sum[y * size + x] += amp * (top + (bottom - top) * sy);
                }
            }
        }
        ByteBuffer out = ByteBuffer.allocateDirect(size * size);
        for (float v : sum) {
            out.put((byte) Math.round(Math.min(1, v) * 255));
        }
        out.position(0);
        return out;
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
            + "uniform sampler2D uNoise;\n"
            + "uniform mat4 uTexM;\n"
            + "uniform vec4 uTile;\n"
            + "uniform vec3 uTop, uHorizon, uSunColor, uTint;\n"
            + "uniform vec2 uSun, uWind;\n"
            + "uniform float uHasVideo, uSunVis, uDusk, uDay, uCloud, uRain, uSnow, uFog, uFlash, uTime, uAngle, uAspect, uStars;\n"
            + "uniform float uLampGlow, uCity, uHoliday;\n"
            + "uniform vec3 uLamps[3];\n"
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
            // World-fixed coordinates, turning with the orbit.
            + "  float c = cos(uAngle), s = sin(uAngle);\n"
            + "  vec2 w = mat2(c, -s, s, c) * p;\n"
            // Stars on clear nights.
            + "  if (uStars > 0.01) {\n"
            + "    vec2 g = w * 85.0; float h = hash(floor(g));\n"
            + "    float star = step(0.986, h) * smoothstep(0.32, 0.0, length(fract(g) - 0.5)) * (0.65 + 0.35 * sin(uTime * 1.7 + h * 40.0));\n"
            + "    col += vec3(star) * uStars;\n"
            + "  }\n"
            // Clouds, as much as the real cover, drifting with the wind.
            + "  vec2 q = (w * 2.0 - uWind * uTime * 0.01) / 8.0;\n"
            + "  float n = texture2D(uNoise, q).r * 0.65 + texture2D(uNoise, q * 2.6 + 0.3875).r * 0.35;\n"
            + "  float edge = 1.0 - uCloud * 0.8;\n"
            + "  float cover = smoothstep(edge - 0.14, edge + 0.26, n);\n"
            + "  vec3 cloudCol = mix(vec3(0.14, 0.16, 0.21), mix(vec3(0.96), vec3(0.58, 0.6, 0.64), uCloud), uDay);\n"
            + "  cloudCol = mix(cloudCol, vec3(1.0, 0.7, 0.55), uDusk * 0.45 * (1.0 - uCloud * 0.6));\n"
            + "  col = mix(col, cloudCol, cover * (0.5 + 0.45 * uCloud));\n"
            // Fog: everything fades into the haze.
            + "  col = mix(col, uHorizon, uFog * 0.6);\n"
            // At night the city's warm light along the horizon.
            + "  col += vec3(0.045, 0.02, 0.007) * uCity * smoothstep(0.6, 0.0, vUv.y);\n"
            + "  col = toSRGB(max(col * 0.9, 0.0));\n"   // a touch calmer, so the clock and apps stand out
            // Holiday decorations behind the flat. New Year: fireworks.
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
            // 4 May and 18 November: the flag (carmine, white, carmine) waving at the top right.
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
            // Midsummer: a bonfire's glow from below.
            + "  if (fx(8.0)) {\n"
            + "    vec2 bp = p - vec2(-0.25 * uAspect, -0.72);\n"
            + "    col += vec3(1.0, 0.45, 0.12) * exp(-dot(bp, bp) * 9.0) * 0.32 * (0.85 + 0.15 * sin(uTime * 7.0) * sin(uTime * 3.1 + 1.0));\n"
            + "  }\n"
            // The flat: colour from the left half of the video, transparency from the right.
            + "  vec2 t = (vUv - uTile.xy) / uTile.zw;\n"
            + "  if (uHasVideo > 0.5 && t.x > 0.0 && t.x < 1.0 && t.y > 0.0 && t.y < 1.0) {\n"
            + "    vec3 m = texture2D(uVideo, (uTexM * vec4(t.x * 0.5, t.y, 0.0, 1.0)).xy).rgb;\n"
            + "    float a = texture2D(uVideo, (uTexM * vec4(0.5 + t.x * 0.5, t.y, 0.0, 1.0)).xy).g;\n"
            + "    a = clamp((a - 0.02) / 0.96, 0.0, 1.0);\n"   // video levels aren't exact: keep the empty parts empty
            + "    m = min(m * uTint, vec3(a));\n"
            + "    m = mix(m, toSRGB(uHorizon) * a, uFog * 0.35);\n"
            + "    col = m + col * (1.0 - a);\n"
            + "  }\n"
            // In the evening, a soft, slightly flickering glow around the flat's orange lamps.
            + "  if (uLampGlow > 0.0) {\n"
            + "    for (int i = 0; i < 3; i++) {\n"
            + "      vec2 ld = (vUv - uLamps[i].xy) * vec2(uAspect, 1.0);\n"
            + "      float r2 = dot(ld, ld);\n"
            + "      float fl = 0.9 + 0.1 * sin(uTime * 2.1 + float(i) * 1.9) * sin(uTime * 3.3 + float(i));\n"
            + "      col += vec3(1.0, 0.55, 0.22) * (exp(-r2 * 2500.0) * 0.22 + exp(-r2 * 120.0) * 0.07) * fl * uLampGlow * uLamps[i].z;\n"
            + "    }\n"
            + "  }\n"
            // Holiday decorations in front. Good Friday: pussy-willow catkins.
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
            // Rain: slanted streaks falling across the whole picture.
            + "  if (uRain > 0.0) {\n"
            + "    vec2 r = vec2(p.x + p.y * 0.18, p.y);\n"
            + "    vec2 rg = vec2(r.x * 150.0, r.y * 5.0 + uTime * 7.0);\n"
            + "    float column = floor(rg.x), y = rg.y + hash(vec2(column, 3.0)) * 9.0;\n"
            + "    float seg = floor(y), f = fract(y);\n"
            + "    float on = step(1.0 - uRain * 0.4, hash(vec2(column, seg)));\n"
            + "    float streak = on * smoothstep(0.0, 0.15, f) * smoothstep(0.55, 0.25, f) * smoothstep(0.5, 0.05, abs(fract(rg.x) - 0.5));\n"
            + "    col = mix(col, vec3(0.9, 0.93, 0.95), streak * 0.4);\n"
            + "  }\n"
            // Snow: three layers of drifting flakes.
            + "  if (uSnow > 0.0) {\n"
            + "    for (int k = 0; k < 3; k++) {\n"
            + "      float fk = float(k);\n"
            + "      vec2 sg = p * (12.0 + fk * 7.0) + vec2(sin(uTime * 0.4 + fk * 2.0) * 0.7, uTime * (0.45 + fk * 0.2));\n"
            + "      vec2 sc = floor(sg);\n"
            + "      vec2 o = vec2(hash(sc + 1.3), hash(sc + 7.7)) - 0.5;\n"
            + "      float flake = step(1.0 - uSnow * 0.5, hash(sc + fk * 13.0)) * smoothstep(0.13 - fk * 0.03, 0.0, length(fract(sg) - 0.5 - o * 0.6));\n"
            + "      col += vec3(flake) * (0.75 - fk * 0.18);\n"
            + "    }\n"
            + "  }\n"
            + "  col += uFlash * vec3(0.55, 0.6, 0.75);\n"
            + "  gl_FragColor = vec4(col, 1.0);\n"
            + "}\n";
}

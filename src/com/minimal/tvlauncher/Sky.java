package com.minimal.tvlauncher;

import java.util.Date;

/**
 * The sky over Brīvības iela 95 for the Apartment launcher: where the sun is, how light it is,
 * and the colours and weather the sky shader draws. A port of the sky code from the original
 * Apartment page (web/apartment.js), so the picture matches it.
 */
final class Sky {

    private static final double LAT = 56.958, LON = 24.124;

    // Cloud cover, rain, snow, fog and thunder for each of Weather's scene codes.
    private static float[] effects(String code) {
        //                                 cloud  rain   snow  fog  thunder
        if ("clear".equals(code)) return new float[] {0.05f, 0, 0, 0, 0};
        if ("partly".equals(code)) return new float[] {0.4f, 0, 0, 0, 0};
        if ("overcast".equals(code)) return new float[] {1, 0, 0, 0, 0};
        if ("fog".equals(code)) return new float[] {0.9f, 0, 0, 1, 0};
        if ("drizzle".equals(code)) return new float[] {0.95f, 0.25f, 0, 0, 0};
        if ("rain".equals(code)) return new float[] {0.95f, 0.6f, 0, 0, 0};
        if ("heavy_rain".equals(code)) return new float[] {1, 1, 0, 0, 0};
        if ("snow".equals(code)) return new float[] {0.95f, 0, 0.7f, 0, 0};
        if ("sleet".equals(code)) return new float[] {0.95f, 0.35f, 0.35f, 0, 0};
        if ("thunder".equals(code)) return new float[] {1, 1, 0, 0, 1};
        return new float[] {0.75f, 0, 0, 0, 0};   // cloudy, and before the first forecast
    }

    private static final String[] COMPASS = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    // Sky colours are mixed in linear light, like the original (three.js) did.
    final float[] top = new float[3];
    final float[] horizon = new float[3];
    final float[] sunColor = new float[3];
    float day;        // 0 at night, 1 by day
    float dusk;       // glow around sunrise and sunset
    float sunVis;     // how much of the sun's disc shows through the clouds
    float sunAlt;     // radians above the horizon
    float sunX, sunZ; // direction of the sun on the plan (x right, z down = south)
    float cloud, rain, snow, fog, thunder;
    float windX, windZ;   // metres per second, on the plan

    static Sky at(Date when, Weather.Now weather) {
        Sky s = new Sky();
        float[] fx = effects(weather != null ? weather.code : null);
        s.cloud = fx[0];
        s.rain = fx[1];
        s.snow = fx[2];
        s.fog = fx[3];
        s.thunder = fx[4];

        double from = 180;
        double speed = 0;
        if (weather != null) {
            for (int i = 0; i < COMPASS.length; i++) {
                if (COMPASS[i].equals(weather.windFrom)) {
                    from = i * 22.5;
                }
            }
            speed = Math.max(0, weather.windKmh) / 3.6;
        }
        from = Math.toRadians(from);
        // It blows away from where it comes from (north is up the plan, -z).
        s.windX = (float) (-Math.sin(from) * speed);
        s.windZ = (float) (Math.cos(from) * speed);

        // The sun's position for Riga (a standard low-precision formula).
        double rad = Math.PI / 180, d = when.getTime() / 86400000.0 - 10957.5;
        double g = (357.529 + 0.98560028 * d) * rad, q = 280.459 + 0.98564736 * d;
        double l = (q + 1.915 * Math.sin(g) + 0.02 * Math.sin(2 * g)) * rad;
        double e = (23.439 - 0.00000036 * d) * rad;
        double ra = Math.atan2(Math.cos(e) * Math.sin(l), Math.cos(l));
        double dec = Math.asin(Math.sin(e) * Math.sin(l));
        double gmst = ((18.697374558 + 24.06570982441908 * d) % 24 + 24) % 24;
        double h = (gmst * 15 + LON) * rad - ra, la = LAT * rad;
        double alt = Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(h));
        double az = Math.atan2(-Math.sin(h), Math.tan(dec) * Math.cos(la) - Math.sin(la) * Math.cos(h));
        s.sunAlt = (float) alt;
        s.sunX = (float) (Math.cos(alt) * Math.sin(az));
        s.sunZ = (float) (-Math.cos(alt) * Math.cos(az));

        float c = s.cloud;
        s.day = smoothstep(-0.12f, 0.12f, s.sunAlt);
        float duskHorizon = Math.max(0, 1 - Math.abs(s.sunAlt) / 0.2f) * (1 - c * 0.7f);
        float[] topDay = mix(linear(0x3f7fc6), linear(0x8d949b), c);
        float[] horDay = mix(linear(0xcfe2f1), linear(0xc4c8cb), c);
        float[] topNight = mix(linear(0x070b17), linear(0x15181d), c);
        float[] horNight = mix(linear(0x18203a), linear(0x23262b), c);
        System.arraycopy(mix(topNight, topDay, s.day), 0, s.top, 0, 3);
        System.arraycopy(mix(mix(horNight, horDay, s.day), linear(0xf0a46a), duskHorizon * 0.55f), 0, s.horizon, 0, 3);
        s.sunVis = Math.max(0, 1 - c * 1.1f) * smoothstep(-0.03f, 0.05f, s.sunAlt);
        s.dusk = Math.max(0, 1 - Math.abs(s.sunAlt) / 0.25f) * (1 - c * 0.6f);
        System.arraycopy(mix(new float[] {1, 0.86f, 0.62f}, new float[] {1, 0.55f, 0.32f}, s.dusk), 0, s.sunColor, 0, 3);
        return s;
    }

    /** Night: the video of the flat with its lamps on. */
    boolean night() {
        return day < 0.5f;
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    // An sRGB colour as linear light.
    private static float[] linear(int rgb) {
        return new float[] {toLinear((rgb >> 16) & 255), toLinear((rgb >> 8) & 255), toLinear(rgb & 255)};
    }

    private static float toLinear(int v) {
        double c = v / 255.0;
        return (float) (c < 0.04045 ? c * 0.0773993808 : Math.pow(c * 0.9478672986 + 0.0521327014, 2.4));
    }
}

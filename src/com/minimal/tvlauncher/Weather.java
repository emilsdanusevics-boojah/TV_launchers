package com.minimal.tvlauncher;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Current weather and today's sunrise/sunset for Brīvības iela 95, Riga, from Open-Meteo
 * (free, no account or key). Fetched at most every 15 minutes and shared by the launchers.
 */
final class Weather {

    private static final String URL_STRING = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=56.9606&longitude=24.1300"
            + "&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m"
            + "&daily=sunrise,sunset&timezone=Europe%2FRiga&forecast_days=2";
    private static final long FRESH_MS = 15 * 60 * 1000;
    private static final TimeZone RIGA = TimeZone.getTimeZone("Europe/Riga");
    private static final String[] COMPASS = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    static class Now {
        String code;        // the Apartment sky's weather: clear, partly, overcast, rain, snow, ...
        String summary;     // e.g. "Light rain"
        double tempC;
        double windKmh;
        String windFrom;    // compass point the wind comes from
        String[] sunrise = new String[0];
        String[] sunset = new String[0];
        long fetchedAt;

        /** e.g. "11° · Mainly clear · Sunset 17:58". */
        String line() {
            StringBuilder s = new StringBuilder();
            s.append(Math.round(tempC)).append("° · ").append(summary);
            String sun = nextSunEvent();
            if (sun != null) {
                s.append(" · ").append(sun);
            }
            return s.toString();
        }

        // The next of today's sunrise, today's sunset or tomorrow's sunrise.
        private String nextSunEvent() {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT);
            f.setTimeZone(RIGA);
            String now = f.format(new Date());
            String[][] events = {
                    {sunrise.length > 0 ? sunrise[0] : null, "Sunrise"},
                    {sunset.length > 0 ? sunset[0] : null, "Sunset"},
                    {sunrise.length > 1 ? sunrise[1] : null, "Sunrise"},
            };
            for (String[] e : events) {
                if (e[0] != null && e[0].length() >= 16 && e[0].compareTo(now) > 0) {
                    return e[1] + " " + e[0].substring(11, 16);
                }
            }
            return null;
        }
    }

    interface Callback {
        void onWeather(Now now);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Now cached;
    private static boolean fetching;

    private Weather() {
    }

    static Now cached() {
        return cached;
    }

    /** Calls back on the main thread with fresh weather; does nothing if offline. */
    static synchronized void refresh(final Callback callback) {
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < FRESH_MS) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWeather(cached);
                }
            });
            return;
        }
        if (fetching) {
            return;
        }
        fetching = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Now now = fetch();
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        fetching = false;
                        if (now != null) {
                            cached = now;
                            callback.onWeather(now);
                        }
                    }
                });
            }
        }).start();
    }

    private static Now fetch() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(URL_STRING).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            in.close();
            JSONObject root = new JSONObject(buf.toString("UTF-8"));
            JSONObject current = root.getJSONObject("current");
            Now now = new Now();
            describe(now, current.getInt("weather_code"));
            now.tempC = current.getDouble("temperature_2m");
            now.windKmh = current.getDouble("wind_speed_10m");
            now.windFrom = COMPASS[(int) Math.round(current.getDouble("wind_direction_10m") / 22.5) % 16];
            JSONObject daily = root.optJSONObject("daily");
            if (daily != null) {
                now.sunrise = strings(daily.optJSONArray("sunrise"));
                now.sunset = strings(daily.optJSONArray("sunset"));
            }
            now.fetchedAt = System.currentTimeMillis();
            return now;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String[] strings(JSONArray array) {
        if (array == null) {
            return new String[0];
        }
        String[] out = new String[array.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.optString(i);
        }
        return out;
    }

    // WMO weather codes (as used by Open-Meteo) to the scene's weather and a short description.
    private static void describe(Now now, int wmo) {
        String code;
        String summary;
        switch (wmo) {
            case 0: code = "clear"; summary = "Clear"; break;
            case 1: code = "clear"; summary = "Mainly clear"; break;
            case 2: code = "partly"; summary = "Partly cloudy"; break;
            case 3: code = "overcast"; summary = "Overcast"; break;
            case 45: case 48: code = "fog"; summary = "Fog"; break;
            case 51: case 53: case 55: code = "drizzle"; summary = "Drizzle"; break;
            case 56: case 57: code = "sleet"; summary = "Freezing drizzle"; break;
            case 61: code = "rain"; summary = "Light rain"; break;
            case 63: code = "rain"; summary = "Rain"; break;
            case 65: code = "heavy_rain"; summary = "Heavy rain"; break;
            case 66: case 67: code = "sleet"; summary = "Freezing rain"; break;
            case 71: code = "snow"; summary = "Light snow"; break;
            case 73: code = "snow"; summary = "Snow"; break;
            case 75: code = "snow"; summary = "Heavy snow"; break;
            case 77: code = "snow"; summary = "Snow grains"; break;
            case 80: case 81: code = "rain"; summary = "Rain showers"; break;
            case 82: code = "heavy_rain"; summary = "Heavy showers"; break;
            case 85: case 86: code = "snow"; summary = "Snow showers"; break;
            case 95: code = "thunder"; summary = "Thunderstorm"; break;
            case 96: case 99: code = "thunder"; summary = "Thunderstorm, hail"; break;
            default: code = "cloudy"; summary = "Cloudy"; break;
        }
        now.code = code;
        now.summary = summary;
    }
}

package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Rīts ("morning"): a practical launcher set like the front page of a paper about your own
 * day. The date and name days across the top; the weather as the headline, with what to wear,
 * rain coming or going, ice, and the next twelve hours drawn as a strip; columns for today
 * (red day, people's days, notes), tomorrow, and the sun and moon (day length, how much light
 * tomorrow gains or loses, the moon's phase); the week ahead along the bottom; the apps as a
 * quiet row of links. Warm paper by day, dark paper after sunset. Autopilot can show it in the
 * mornings.
 *
 * Everything comes from what the launcher already has: the Open-Meteo forecast, Holidays,
 * NameDays and the People & notes board. It's redrawn once a minute and never animates.
 */
class RitsTheme implements LauncherTheme {

    private static final TimeZone RIGA = TimeZone.getTimeZone("Europe/Riga");
    private static final long WEATHER_EVERY_MS = 15 * 60 * 1000;
    private static final String[] DAYS_LV = {"Svētdiena", "Pirmdiena", "Otrdiena", "Trešdiena", "Ceturtdiena", "Piektdiena", "Sestdiena"};
    private static final String[] DAYS_SHORT = {"Sv", "P", "O", "T", "C", "Pk", "S"};
    private static final String[] MONTHS_LV = {"janvāris", "februāris", "marts", "aprīlis", "maijs", "jūnijs",
            "jūlijs", "augusts", "septembris", "oktobris", "novembris", "decembris"};

    // Paper by day, dark paper at night.
    private static final int DAY_PAPER = 0xFFF1EADB, DAY_INK = 0xFF23201B, DAY_MUTED = 0xFF7A7164, DAY_RULE = 0x33231F1A;
    private static final int NIGHT_PAPER = 0xFF1A1815, NIGHT_INK = 0xFFE9E2D3, NIGHT_MUTED = 0xFF9C9485, NIGHT_RULE = 0x33E9E2D3;
    private static final int RED_DAY = 0xFF9E3039;    // Latvian carmine
    private static final int RAIN = 0xFF4F7BB0;

    private final MainActivity activity;
    private final Handler handler = new Handler();
    private Typeface light;
    private Typeface medium;
    private FrameLayout root;
    private TextView title;
    private TextView time;
    private TextView dateLine;
    private TextView headline;
    private TextView deck;
    private HourStrip hours;
    private TextView todayText;
    private TextView tomorrowText;
    private TextView todayHead;
    private TextView tomorrowHead;
    private View mastRule;
    private TextView sunText;
    private WeekStrip week;
    private LinearLayout appRow;
    private LinearLayout footer;
    private ImageView gear;
    private PanelMenu menu;
    private final List<View> rules = new ArrayList<View>();
    private final List<TextView> heads = new ArrayList<TextView>();
    private final List<TextView> links = new ArrayList<TextView>();
    private boolean night;
    private AmbientClock ambientClock;

    private final Runnable weatherTimer = new Runnable() {
        @Override
        public void run() {
            Weather.refresh(new Weather.Callback() {
                @Override
                public void onWeather(Weather.Now now) {
                    refresh();
                }
            });
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    RitsTheme(MainActivity activity) {
        this.activity = activity;
    }

    private int dp(float v) {
        return PanelMenu.dp(activity, v);
    }

    @Override
    public View createView() {
        light = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-300.ttf");
        medium = Typeface.createFromAsset(activity.getAssets(), "fonts/HankenGrotesk-500.ttf");
        root = new FrameLayout(activity);

        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(48), dp(26), dp(48), dp(18));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // Masthead: the paper's name, the date and name days, the time.
        LinearLayout mast = new LinearLayout(activity);
        mast.setOrientation(LinearLayout.HORIZONTAL);
        mast.setGravity(Gravity.BOTTOM);
        title = PanelMenu.text(activity, medium, 15, 0);
        title.setText("RĪTS");
        title.setLetterSpacing(0.5f);
        mast.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        time = PanelMenu.text(activity, light, 40, 0);
        time.setIncludeFontPadding(false);
        mast.addView(time);
        page.addView(mast);
        dateLine = PanelMenu.text(activity, medium, 18, 0);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(2);
        page.addView(dateLine, dl);
        mastRule = rule(true);
        page.addView(mastRule, ruleParams(true, 10));

        // Body: the weather on the left, three short columns on the right.
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout lead = new LinearLayout(activity);
        lead.setOrientation(LinearLayout.VERTICAL);
        headline = PanelMenu.text(activity, light, 40, 0);
        headline.setIncludeFontPadding(false);
        lead.addView(headline);
        deck = PanelMenu.text(activity, medium, 17, 0);
        deck.setSingleLine(false);
        deck.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams dk = new LinearLayout.LayoutParams(-1, -2);
        dk.topMargin = dp(10);
        lead.addView(deck, dk);
        hours = new HourStrip(activity);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, 0, 1);
        hp.topMargin = dp(14);
        lead.addView(hours, hp);
        body.addView(lead, new LinearLayout.LayoutParams(0, -1, 1.55f));

        body.addView(rule(false), ruleParams(false, 22));

        LinearLayout side = new LinearLayout(activity);
        side.setOrientation(LinearLayout.VERTICAL);
        todayText = section(side, "Today", 0);
        todayHead = heads.get(heads.size() - 1);
        tomorrowText = section(side, "Tomorrow", 10);
        tomorrowHead = heads.get(heads.size() - 1);
        sunText = section(side, "Sun & moon", 10);
        body.addView(side, new LinearLayout.LayoutParams(0, -1, 1f));

        page.addView(rule(true), ruleParams(true, 10));
        TextView weekHead = head("The week");
        page.addView(weekHead);
        week = new WeekStrip(activity);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, dp(92));
        wp.topMargin = dp(4);
        page.addView(week, wp);

        // The apps: a quiet row of links, and the gear.
        footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        appRow = new LinearLayout(activity);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        footer.addView(appRow, new LinearLayout.LayoutParams(0, -2, 1));
        gear = new ImageView(activity);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setPadding(dp(10), dp(10), dp(10), dp(10));
        gear.setFocusable(true);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu();
            }
        });
        footer.addView(gear, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(6);
        page.addView(footer, fp);

        menu = new PanelMenu(activity, medium, footer, new PanelMenu.OnClose() {
            @Override
            public void onClose() {
                gear.requestFocus();
            }
        });
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(300), -2, Gravity.BOTTOM | Gravity.END);
        mp.setMargins(0, dp(24), dp(44), dp(70));
        root.addView(menu.view, mp);

        ambientClock = new AmbientClock(activity);
        root.addView(ambientClock, ambientClock.layout());

        night = DayArc.dark(new Date());
        paint();
        refresh();
        return root;
    }

    private TextView head(String text) {
        TextView t = PanelMenu.text(activity, medium, 11, 0);
        t.setText(text);
        t.setAllCaps(true);
        t.setLetterSpacing(0.16f);
        heads.add(t);
        return t;
    }

    private TextView section(LinearLayout parent, String name, int top) {
        TextView h = head(name);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(top);
        parent.addView(h, hp);
        TextView body = PanelMenu.text(activity, medium, 14, 0);
        body.setSingleLine(false);
        body.setLineSpacing(dp(3), 1f);
        body.setMaxLines(5);
        body.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(4);
        parent.addView(body, bp);
        return body;
    }

    private View rule(boolean horizontal) {
        View v = new View(activity);
        rules.add(v);
        return v;
    }

    private LinearLayout.LayoutParams ruleParams(boolean horizontal, int margin) {
        LinearLayout.LayoutParams lp = horizontal ? new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)))
                : new LinearLayout.LayoutParams(Math.max(1, dp(1)), -1);
        if (horizontal) {
            lp.topMargin = dp(margin);
            lp.bottomMargin = dp(margin);
        } else {
            lp.leftMargin = dp(margin);
            lp.rightMargin = dp(margin);
        }
        return lp;
    }

    // Day or night paper.
    private void paint() {
        int paper = night ? NIGHT_PAPER : DAY_PAPER;
        int ink = night ? NIGHT_INK : DAY_INK;
        int muted = night ? NIGHT_MUTED : DAY_MUTED;
        int ruleColor = night ? NIGHT_RULE : DAY_RULE;
        root.setBackgroundColor(paper);
        for (TextView t : new TextView[] {title, time, dateLine, headline, deck, todayText, tomorrowText, sunText}) {
            t.setTextColor(ink);
        }
        title.setTextColor(muted);
        for (TextView h : heads) {
            h.setTextColor(muted);
        }
        for (View r : rules) {
            r.setBackgroundColor(ruleColor);
        }
        for (TextView l : links) {
            l.setTextColor(PanelMenu.focusColors(paper, ink));
            l.setBackground(PanelMenu.pill(activity, ink, 0));
        }
        gear.setImageTintList(PanelMenu.focusColors(paper, muted));
        gear.setBackground(PanelMenu.pill(activity, ink, 0));
        menu.setColors(ink, paper);
        hours.setColors(ink, muted, ruleColor);
        week.setColors(ink, muted, ruleColor);
    }

    // ---- Content ----

    private void refresh() {
        Date now = new Date();
        boolean dark = DayArc.dark(now);
        if (dark != night) {
            night = dark;
            paint();
        }
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        time.setText(DateFormat.format(DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm", now));
        String names = NameDays.today(activity);
        dateLine.setText(DAYS_LV[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + ". "
                + MONTHS_LV[c.get(Calendar.MONTH)] + (names == null ? "" : "   ·   Vārda dienas: " + names));

        Weather.Now w = Weather.cached();
        headline.setText(w == null ? "No forecast yet" : Math.round(w.tempC) + "°  " + w.summary);
        List<String> today = new ArrayList<String>(Glance.today(w, now));
        // An old forecast (the TV was offline): say how old, and dim it.
        boolean stale = w != null && System.currentTimeMillis() - w.fetchedAt > 60 * 60 * 1000;
        if (stale) {
            today.add("Forecast from " + DateFormat.format("HH:mm", new Date(w.fetchedAt)) + ", not updated since");
        }
        headline.setAlpha(stale ? 0.55f : 1f);
        deck.setAlpha(stale ? 0.7f : 1f);
        deck.setText(today.isEmpty() ? (w == null ? "The weather appears once the TV is online." : "") : TextUtils.join("\n", today));
        hours.setWeather(w, now);
        week.setWeather(w);

        List<String> todayLines = new ArrayList<String>();
        Holidays.Day red = activity.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean("holidays", true)
                ? Holidays.today() : null;
        if (red != null) {
            todayLines.add(red.greeting);
        }
        for (String l : Board.lines(activity, c)) {
            if (!l.startsWith("Tomorrow: ")) {
                todayLines.add(l);
            }
        }
        todayText.setText(bullets(todayLines));
        show(todayLines.isEmpty() ? View.GONE : View.VISIBLE, todayHead, todayText);   // empty sections step aside
        // On a red day, the masthead takes the holiday's colour.
        int ruleColor = night ? NIGHT_RULE : DAY_RULE;
        title.setTextColor(red != null ? red.textColor() : (night ? NIGHT_MUTED : DAY_MUTED));
        mastRule.setBackgroundColor(red != null ? red.accent : ruleColor);

        List<String> tomorrowLines = new ArrayList<String>();
        for (String l : Glance.tomorrow(activity, w, now)) {
            if (!l.startsWith("Sunrise")) {
                tomorrowLines.add(l);   // the sun has its own column here
            }
        }
        for (String l : Board.lines(activity, c)) {
            if (l.startsWith("Tomorrow: ")) {
                tomorrowLines.add(l.substring("Tomorrow: ".length()));
            }
        }
        tomorrowText.setText(bullets(tomorrowLines));
        show(tomorrowLines.isEmpty() ? View.GONE : View.VISIBLE, tomorrowHead, tomorrowText);

        sunText.setText(sunAndMoon(w, now));
        ambientClock.tick();
    }

    private static void show(int visibility, View... views) {
        for (View v : views) {
            v.setVisibility(visibility);
        }
    }

    private static String bullets(List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (String l : lines) {
            b.append(b.length() > 0 ? "\n" : "").append("·  ").append(l);
        }
        return b.toString();
    }

    // Sunrise and sunset, the day's length and tomorrow's change, and the moon's phase.
    private static String sunAndMoon(Weather.Now w, Date now) {
        StringBuilder b = new StringBuilder();
        if (w != null && w.sunrise.length > 0 && w.sunset.length > 0 && w.sunrise[0].length() >= 16 && w.sunset[0].length() >= 16) {
            b.append("Sunrise ").append(w.sunrise[0].substring(11, 16)).append(" · sunset ").append(w.sunset[0].substring(11, 16));
            int today = minutes(w.sunset[0]) - minutes(w.sunrise[0]);
            b.append("\nDay ").append(today / 60).append(" h ").append(today % 60).append(" min");
            if (w.sunrise.length > 1 && w.sunset.length > 1) {
                int change = (minutes(w.sunset[1]) - minutes(w.sunrise[1])) - today;
                b.append(change == 0 ? " · the same tomorrow" : " · " + (change > 0 ? "+" : "−") + Math.abs(change) + " min tomorrow");
            }
        }
        String gained = sinceMidwinter(now);
        if (gained != null) {
            b.append(b.length() > 0 ? "\n" : "").append(gained);
        }
        b.append(b.length() > 0 ? "\n" : "").append("Moon: ").append(moon(now));
        return b.toString();
    }

    // From midwinter to midsummer, how much more daylight Riga has than on the shortest day:
    // a small comfort on dark mornings. Worked out from the sun's declination.
    static String sinceMidwinter(Date now) {
        Calendar c = Calendar.getInstance(RIGA);
        c.setTime(now);
        int doy = c.get(Calendar.DAY_OF_YEAR);
        int midwinter = 355;   // about 21 December
        int since = doy >= midwinter ? doy - midwinter : doy + (c.getActualMaximum(Calendar.DAY_OF_YEAR) - midwinter);
        if (since < 1 || since > 182) {
            return null;   // after midsummer the days shorten; no need to say
        }
        int extra = (int) Math.round((dayLength(doy) - dayLength(midwinter)) * 60);
        return extra < 1 ? null : "+" + (extra / 60 > 0 ? extra / 60 + " h " : "") + extra % 60 + " min of light since midwinter";
    }

    // Hours of daylight in Riga on a day of the year (sunrise to sunset, with refraction).
    private static double dayLength(int doy) {
        double lat = Math.toRadians(56.95);
        double decl = Math.toRadians(-23.44) * Math.cos(2 * Math.PI * (doy + 10) / 365.0);
        double cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(lat) * Math.sin(decl)) / (Math.cos(lat) * Math.cos(decl));
        return 2 * Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosW)))) / 15.0;
    }

    private static int minutes(String isoLocal) {
        return Integer.parseInt(isoLocal.substring(11, 13)) * 60 + Integer.parseInt(isoLocal.substring(14, 16));
    }

    // The moon's phase from the mean synodic month, counted from a known new moon (6 Jan 2000).
    static String moon(Date now) {
        double days = (now.getTime() - 947182440000L) / 86400000.0;
        double age = ((days % 29.530588853) + 29.530588853) % 29.530588853;
        String[] names = {"new moon", "waxing crescent", "first quarter", "waxing gibbous",
                "full moon", "waning gibbous", "last quarter", "waning crescent"};
        return names[(int) Math.floor(age / 29.530588853 * 8 + 0.5) % 8];
    }

    // ---- LauncherTheme ----

    @Override
    public void setApps(List<Apps.Entry> apps) {
        appRow.removeAllViews();
        links.clear();
        for (final Apps.Entry app : apps) {
            TextView item = PanelMenu.text(activity, medium, 16, 0);
            item.setText(app.label);
            item.setPadding(dp(16), dp(8), dp(16), dp(9));
            item.setFocusable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Apps.launch(activity, app);
                }
            });
            item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return Apps.openAppInfo(activity, app);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(4);
            appRow.addView(item, lp);
            links.add(item);
        }
        paint();
        focusFirst();
    }

    private void focusFirst() {
        if (!menu.isOpen()) {
            (appRow.getChildCount() > 0 ? appRow.getChildAt(0) : gear).requestFocus();
        }
    }

    @Override
    public void onTimeChanged() {
        refresh();
    }

    @Override
    public void onResume() {
        handler.removeCallbacks(weatherTimer);
        weatherTimer.run();
        refresh();
        focusFirst();
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(weatherTimer);
    }

    @Override
    public void onHome() {
        menu.close();
        focusFirst();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettingsMenu();
            return true;
        }
        return false;
    }

    @Override
    public boolean onBack() {
        if (menu.isOpen()) {
            menu.close();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(weatherTimer);
    }

    @Override
    public void setAmbient(boolean ambient) {
        // The page stays; the app links go, and a small clock drifts in the corner.
        if (ambient) {
            menu.close();
        }
        AmbientClock.fade(ambient, footer, time);
        ambientClock.setTextColor(night ? NIGHT_INK : DAY_INK);
        ambientClock.setShadowLayer(0, 0, 0, 0);
        ambientClock.show(ambient);
    }

    private void showSettingsMenu() {
        menu.open("Settings");
        menu.add("TV settings", new Runnable() {
            @Override
            public void run() {
                menu.close();
                Apps.openSettings(activity);
            }
        });
        menu.add("Edit apps", new Runnable() {
            @Override
            public void run() {
                menu.close();
                activity.editApps();
            }
        });
        menu.add("Change launcher", new Runnable() {
            @Override
            public void run() {
                menu.showLaunchers(activity);
            }
        });
        menu.phoneFooter();
        if (menu.view.findFocus() == null) {
            menu.focus(0);
        }
    }

    // ---- The next twelve hours, drawn ----

    /** Temperatures as a line with figures, rain and snow as bars, the hours underneath. */
    static final class HourStrip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final Typeface face;
        private int ink, muted, rule;
        private final List<String> labels = new ArrayList<String>();
        private final List<Double> temps = new ArrayList<Double>();
        private final List<Double> rain = new ArrayList<Double>();
        private final List<Boolean> snow = new ArrayList<Boolean>();

        HourStrip(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            face = Typeface.createFromAsset(context.getAssets(), "fonts/HankenGrotesk-500.ttf");
        }

        void setColors(int ink, int muted, int rule) {
            this.ink = ink;
            this.muted = muted;
            this.rule = rule;
            invalidate();
        }

        void setWeather(Weather.Now w, Date now) {
            labels.clear();
            temps.clear();
            rain.clear();
            snow.clear();
            if (w != null && w.hourTime.length > 0) {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.ROOT);
                f.setTimeZone(RIGA);
                String hour = f.format(now);
                int at = -1;
                for (int i = 0; i < w.hourTime.length; i++) {
                    if (hour.equals(w.hourTime[i])) {
                        at = i;
                    }
                }
                for (int i = Math.max(0, at); at >= 0 && i < Math.min(w.hourTime.length, at + 13); i++) {
                    labels.add(w.hourTime[i].substring(11, 13));
                    temps.add(w.hourTemp[i]);
                    rain.add(w.hourRain[i]);
                    snow.add("snow".equals(w.hourCode[i]) || "sleet".equals(w.hourCode[i]));
                }
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = temps.size();
            if (n < 2) {
                return;
            }
            float w = getWidth(), h = getHeight();
            float labelH = 18 * density, figH = 18 * density;
            float top = figH + 4 * density, bottom = h - labelH - 6 * density;
            double lo = 99, hi = -99;
            for (double t : temps) {
                lo = Math.min(lo, t);
                hi = Math.max(hi, t);
            }
            if (hi - lo < 4) {
                double mid = (hi + lo) / 2;
                lo = mid - 2;
                hi = mid + 2;
            }
            float step = w / n;
            paint.setTypeface(face);
            paint.setTextAlign(Paint.Align.CENTER);
            // Rain and snow bars from the bottom.
            for (int i = 0; i < n; i++) {
                double mm = rain.get(i);
                if (mm >= 0.1) {
                    float bh = (float) Math.min(1, mm / 3) * (bottom - top) * 0.6f + 3 * density;
                    paint.setColor(snow.get(i) ? (ink & 0x00FFFFFF) | 0x55000000 : RAIN);
                    c.drawRoundRect(new RectF(i * step + step * 0.28f, bottom - bh, i * step + step * 0.72f, bottom), 3 * density, 3 * density, paint);
                }
            }
            // The baseline and hour labels.
            paint.setColor(rule);
            c.drawRect(0, bottom, w, bottom + Math.max(1, density), paint);
            paint.setTextSize(12 * density);
            paint.setColor(muted);
            for (int i = 0; i < n; i++) {
                c.drawText(i == 0 ? "now" : labels.get(i), i * step + step / 2, h - 4 * density, paint);
            }
            // The temperature line with figures every other hour.
            paint.setColor(ink);
            paint.setStrokeWidth(2 * density);
            paint.setStyle(Paint.Style.STROKE);
            float px = 0, py = 0;
            for (int i = 0; i < n; i++) {
                float x = i * step + step / 2;
                float y = (float) (bottom - 8 * density - (temps.get(i) - lo) / (hi - lo) * (bottom - top - 16 * density));
                if (i > 0) {
                    c.drawLine(px, py, x, y, paint);
                }
                px = x;
                py = y;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(14 * density);
            for (int i = 0; i < n; i += 2) {
                float x = i * step + step / 2;
                float y = (float) (bottom - 8 * density - (temps.get(i) - lo) / (hi - lo) * (bottom - top - 16 * density));
                c.drawCircle(x, y, 3 * density, paint);
                c.drawText(Math.round(temps.get(i)) + "°", x, y - 8 * density, paint);
            }
        }
    }

    // ---- The week ahead ----

    /** Seven columns: the day, its high and low, rain, red days in carmine. */
    static final class WeekStrip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final Typeface face;
        private final Typeface thin;
        private int ink, muted, rule;
        private Weather.Now w;

        WeekStrip(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            face = Typeface.createFromAsset(context.getAssets(), "fonts/HankenGrotesk-500.ttf");
            thin = Typeface.createFromAsset(context.getAssets(), "fonts/HankenGrotesk-300.ttf");
        }

        void setColors(int ink, int muted, int rule) {
            this.ink = ink;
            this.muted = muted;
            this.rule = rule;
            invalidate();
        }

        void setWeather(Weather.Now w) {
            this.w = w;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = w == null ? 0 : Math.min(7, w.dayDate.length);
            float width = getWidth(), h = getHeight();
            paint.setTextAlign(Paint.Align.LEFT);
            if (n == 0) {
                paint.setTypeface(face);
                paint.setTextSize(14 * density);
                paint.setColor(muted);
                c.drawText("The week's forecast appears once the TV is online.", 0, h / 2, paint);
                return;
            }
            float col = width / 7;
            Calendar cal = Calendar.getInstance(RIGA);
            for (int i = 0; i < n; i++) {
                float x = i * col;
                try {
                    cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(w.dayDate[i]));
                } catch (Exception e) {
                    continue;
                }
                Holidays.Day red = Holidays.on(cal);
                boolean weekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
                if (i > 0) {
                    paint.setColor(rule);
                    c.drawRect(x, 6 * density, x + Math.max(1, density), h - 6 * density, paint);
                }
                float left = x + 14 * density;
                paint.setTypeface(face);
                paint.setTextSize(13 * density);
                paint.setColor(red != null ? RED_DAY : (weekend ? ink : muted));
                String day = (i == 0 ? "Šodien" : DAYS_SHORT[cal.get(Calendar.DAY_OF_WEEK) - 1] + " " + cal.get(Calendar.DAY_OF_MONTH) + ".");
                c.drawText(day, left, 18 * density, paint);
                paint.setTypeface(thin);
                paint.setTextSize(26 * density);
                paint.setColor(ink);
                String hi = Math.round(w.dayHigh[i]) + "°";
                c.drawText(hi, left, 50 * density, paint);
                float hiW = paint.measureText(hi);
                paint.setTypeface(face);
                paint.setTextSize(14 * density);
                paint.setColor(muted);
                c.drawText(Math.round(w.dayLow[i]) + "°", left + hiW + 6 * density, 50 * density, paint);
                String what = Glance.word(w.dayCode[i]);
                double mm = w.dayRain[i];
                paint.setTextSize(12.5f * density);
                paint.setColor(mm >= 1 ? RAIN : muted);
                c.drawText(mm >= 1 ? what + " · " + Math.round(mm) + " mm" : what, left, 70 * density, paint);
                if (red != null) {
                    paint.setColor(RED_DAY);
                    paint.setTextSize(11.5f * density);
                    String name = TextUtils.ellipsize(red.name, new android.text.TextPaint(paint), col - 20 * density, TextUtils.TruncateAt.END).toString();
                    c.drawText(name, left, 87 * density, paint);
                }
            }
        }
    }
}

# Minimal TV Launcher

A home screen for Android TV / Xiaomi TV with five looks. You can switch between them from the settings menu in any of them, or from your phone; the choice is remembered.

## Photos (default)

The everyday launcher: quick to open, easy to use.

- A full-screen photo that slowly drifts and zooms, crossfading to the next every 45 seconds. Upload your own photos from the phone page; until you do, it shows built-in pictures of the apartment.
- A large clock, the date, today's **Latvian name days** (vārda dienas, e.g. "Vārda dienas · Mārtiņš, Mārcis, Markuss, Marks") and the weather with a small icon, in the top-left.
- Your apps as large tiles along the bottom, showing each app's TV banner (or its icon and name). The focused tile grows and gets an outline.
- On red days (see Holidays below) a greeting appears and the tiles' outline takes the holiday's colour.

The gear (or **Menu** on the remote) opens **TV settings**, **Edit apps**, **Holidays** (on or off) and **Change launcher**.

The name days are the traditional calendar list of the State Language Centre's calendar names commission ([vvc.gov.lv](https://www.vvc.gov.lv/lv/kalendarvardu-ekspertu-komisija)), taken from the [lv-namedays](https://pypi.org/project/lv-namedays/) package (MIT licence); see `assets/namedays.txt`.

## Apartment

A cozy launcher: slow drifts at eye level through the apartment's living room, alternating between two views: from the bed corner past the desk and the lamps towards the terrace door, and from beside the terrace door towards the desk, its lamp and the slatted wall. They dissolve into each other every 75 seconds. The windows show a live picture of the sky over Brīvības iela 95, Riga:

- **Sky colour** follows the real time of day and the cloud cover: blue by day, grey when overcast, orange and pink around the real sunrise and sunset, deep navy at night with the city's warm glow low down.
- **The sun** glows from its real direction when it's in view, and **stars** come out on clear nights.
- **Rain, snow, fog and lightning** show outside the windows when they're happening.

The room is shown in daylight by day. From just before sunset until sunrise it switches to a cozy evening look: the ceiling lights are dimmed low and the flat's orange lamps light the room in amber, with a soft glow around them. Under heavy cloud the daytime room looks a little duller, and around sunrise and sunset a little warmer. When it snows, the snow falls outside the windows. The app can also show winter versions of the videos with snow lying on the terrace (`web/build.sh` renders them as `room_winter_day` and `room_winter_evening`), used while it's snowing or at 0 °C or below from November to March. They aren't included at the moment, to save rendering time and space.

### Holidays

On Latvia's public holidays (the red days in the calendar) the picture gets a decoration for the day, a greeting appears under the weather, and the buttons take the holiday's colour:

| Day | Date | Decoration |
|---|---|---|
| Jaungada diena (New Year's Day) | 1 Jan | fireworks |
| Lielā Piektdiena (Good Friday) | 2 days before Easter | pussy-willow catkins |
| Lieldienas, Otrās Lieldienas (Easter Sunday and Monday) | Easter | onion-skin dyed eggs floating up |
| Darba svētki (Labour Day; Convocation of the Constituent Assembly) | 1 May | apple blossom petals |
| Neatkarības atjaunošanas diena (Restoration of Independence) | 4 May | the Latvian flag |
| Mātes diena (Mother's Day) | 2nd Sunday of May | tulip petals |
| Vasarsvētki (Pentecost) | 7 weeks after Easter | young birch leaves |
| Līgo diena, Jāņu diena (Midsummer) | 23 and 24 Jun | a bonfire's glow and rising embers |
| Proklamēšanas diena (Proclamation Day) | 18 Nov | the flag and a row of candles |
| Ziemassvētku vakars, Pirmie and Otrie Ziemassvētki (Christmas) | 24–26 Dec | strings of lights and snow |
| Vecgada diena (New Year's Eve) | 31 Dec | fireworks |

When 4 May or 18 November falls on a weekend, the Monday after (a day off) gets the decoration too. One-off holidays the government adds in some years aren't included.

**Settings → Holidays** turns the decorations on or off, and lists every red day of this year with its date: pick one to preview it, and press **Back** (or Home) to end the preview.

On top of it:

- a large clock and the date in the top-left
- today's weather with a small icon, e.g. "11° · Mainly clear · Sunset 17:58"
- your apps as a row of names on a dark, see-through strip along the bottom
- a settings button (gear) at the right end of the strip

The room is a pre-rendered video, so the motion is perfectly smooth: the TV's video decoder plays it, with each frame shown for exactly two screen refreshes, and the loop (2½ minutes at 30 frames per second: one view, a dissolve, the other view, a dissolve) has no seam. It's rendered with soft shading where surfaces meet and a glow around the lamps. Each frame holds the room on the left and, at half size top right, a mask of the solid parts, so the app can show its live sky through the windows; it draws the sky itself with one small shader. It starts at once by showing a still of the first frame until the video is running, draws at 1280×720 (the TV scales it up), and if the TV runs at 50 Hz it asks for 60 Hz so the 30 frames per second play evenly. Everything pauses while another app is open.

The videos live in `assets/apartment` (`room_day`, `room_evening`, and `room_winter_day` / `room_winter_evening` when present), each with a `.jpg` still of its first frame. The model comes from the apartment page (three.js). To update it from a new version of that page, run `python3 web/import_model.py <Apartment.html>` and then `web/build.sh`, which renders the videos again in a headless browser (about an hour each; needs Node.js and ffmpeg). Each camera loop is rendered separately (`day`/`evening` for the first, `day2`/`evening2` for the second) and `web/join_loops.sh` dissolves them into one video. The lighting and camera code in `web/apartment.js` are kept. Text uses Hanken Grotesk (SIL Open Font License; see `assets/fonts/HankenGrotesk-OFL.txt`).

- **Left/Right** moves along the apps. **OK** opens one; **long-press OK** opens its info page.
- **Gear** (or **Menu** on the remote) opens a menu:
  - **TV settings**
  - **Edit apps**
  - **Holidays**, see above
  - **Screensaver**, opens the TV's screen saver settings (see below)
  - **Change launcher**, where you can pick Photos, Apartment, Fireplace, Monkey Island or Vinyl
- **Back** closes the menu.

### Screensaver

The launcher also gives the TV a screensaver called **Apartment**: the room loop with the live sky in the windows (with the evening lamps and holiday decorations) and a large, dim clock that drifts a little every minute so nothing burns into the screen. Choose it in the TV's settings under **Device Preferences → Screen saver** (the gear menu's **Screensaver** item goes there). If your TV's menu doesn't list it, set it once with adb:

```sh
adb shell settings put secure screensaver_components com.minimal.tvlauncher/.ApartmentDream
```

## Live weather

The current conditions and today's sunrise and sunset for Brīvības iela 95 come from [Open-Meteo](https://open-meteo.com/). It's free and needs no account or key. The launcher fetches them when it appears and every 15 minutes while it's on screen. Without internet, the sky keeps its last conditions and the weather line is hidden.

## Fireplace

A crackling fire fills the screen: a brick fireplace with logs, glowing embers, flames and sparks drifting up, the bricks lit by the fire's flicker. The clock sits dim in the top-left corner. The apps stay out of the way: press any button on the remote and the app strip appears along the bottom; it fades away again after 10 seconds without a press (Back hides it at once).

**Sound** (in the gear menu, or Menu on the remote) plays a quiet crackling fire or gentle rain, or nothing (the default). It stops whenever another app is open.

The fire is a pre-rendered, seamless 20-second loop played by the TV's video decoder (`web/fireplace.html` draws it; `node web/render_fireplace.js assets/fireplace/fireplace.mp4 assets/fireplace/fireplace.jpg` renders it). The sounds are synthesised, not recorded: `python3 web/make_sounds.py assets/fireplace` makes them as seamless 60-second loops.

## Monkey Island

Looks like the interface of *The Secret of Monkey Island* (VGA). The background scene fills the top of the screen, with the clock in the corner. Below it is a black panel with:

- a **sentence line** that says what OK will do, e.g. "Open Spotify"
- **verbs** on the left
- an **inventory** on the right with the names of your apps

On Latvia's red days (see Holidays above) someone in the scene says something about the day in the game's style, e.g. *"It's Jāņi. I should find some cheese and a wreath."* or *"Look behind you, a three-headed Christmas tree!"*: when the home screen appears and every few minutes after. The Apartment launcher's Holidays setting turns this off too.

Everything except the background is drawn on a 320×180 pixel grid. The verbs and app names use a hand-drawn recreation of the game's bold verb font (`make_verb_font.py`). Everything else uses the [Tiny5](https://github.com/Gissio/font_tiny5) pixel font (SIL Open Font License; see `assets/fonts/Tiny5-OFL.txt`).

- **D-pad** moves between verbs and inventory items.
- **OK on an app** uses the highlighted verb on it. The verb is **Open** unless you picked another one, so one press launches the app.
- **OK on Look at or Close** picks that verb and jumps to the inventory. Then press **OK** on an app:
  - **Look at** opens the app's info page (version, storage, uninstall).
  - **Close** stops the app if it's running in the background.
- **Back** drops a picked verb.
- **Wi-Fi**, **Bluetooth**, **Sound**, **Picture** and **Apps** (every installed app, including hidden ones) open those TV settings straight away.
- **Settings** (or **Menu** on the remote) asks, as in a game conversation:
  - *Open the TV settings.*
  - *I'd like a different launcher.* This then lists the launchers to pick from.

  **Up/Down** and **OK** choose; **Back** cancels.
- **Home** goes back to the first app.

## Vinyl

A launcher just for listening to Spotify. While music plays, the screen shows nothing but a record turning at 33⅓ rpm, with the album cover as its label. The background slowly fades between the cover's main colours and crossfades when the song changes. When you pause, the record slows to a stop like a turntable. A tonearm behaves like a real one. When music starts, it swings over from its rest and lowers onto the record. Pause lifts it in place, like a cue lever, and play lowers it again. When the music stops, it returns to its rest. While a song plays, the needle moves from the edge towards the label. When nothing is playing, there's an **Open Spotify** button and a gear.

- **OK** plays or pauses. **Left/Right** skips to the previous or next track.
- **Down** opens Spotify.
- **Up** (or **Menu**) opens a menu with **Open Spotify**, **TV settings** and **Change launcher**.

**Automatic switching.** Whichever launcher you've picked, the record appears over it whenever the home screen is showing and Spotify is playing. That includes going Home while music plays, and starting music from your phone with Spotify Connect. When the music stops, or stays paused for 30 seconds, your launcher fades back. Press **Back** on the record to hide it while the music keeps playing; it comes back the next time music starts. Picking your own launcher under **Change launcher** on the record also hides it.

**One-time setup.** Android only shows other apps what's playing if you give them notification access, and Android TV usually hides that setting. Grant it with adb:

```sh
adb shell cmd notification allow_listener com.minimal.tvlauncher/com.minimal.tvlauncher.MediaListener
```

Until you do, the launcher shows this command on screen.

## Choosing which apps are shown

**On the TV:** open the settings menu in any launcher and choose **Edit apps**. In Monkey Island, choose *"I'd like to change my apps."* The screen lists the apps on the home screen first, in order, then every other installed app.

- **OK** shows or hides an app.
- **Right** on a shown app picks it up. **Up/Down** then moves it, and **OK** puts it down.
- Changes are saved straight away. **Back** returns to the home screen.

**From your phone:** see below. The phone page can also rename apps.

All the launchers use the same list. In Monkey Island there are 6 apps per inventory page, and the arrows scroll if there are more. The list starts from `res/values/apps.xml` (`Name|package,package`) until you first edit it.

## Phone page

While the launcher is running, it serves a small page on your home Wi-Fi at **http://&lt;TV's IP address&gt;:8765**. Every settings menu shows the address. On the page you can:

- switch launcher
- show, hide, reorder and rename apps
- upload and delete photos for the Photos launcher
- upload and delete Monkey Island background images
- **update the launcher** (see below)

Changes appear on the TV straight away. The page is only reachable on your local network, and there's no password: anyone on your Wi-Fi who knows the address can use it.

### Updating from your phone

From version 7.2, updates need no computer:

1. Download the new `MinimalTVLauncher.apk` to your phone.
2. Open the phone page and, under **Update the launcher**, choose the file. The first time, the page asks you to allow updates: press **Open the setting on the TV**, turn on *Allow from this source* for the launcher on the TV, and press Back.
3. The TV asks *"Do you want to install an update to this existing application?"*: press **Install** with the remote. The launcher restarts with the new version and the page says so.

Keep the TV on the home screen while you do this. The TV only accepts an update signed with the same key as the installed launcher, so someone else on your Wi-Fi can't install a different app this way, and nothing installs without pressing Install on the TV.

## Monkey Island backgrounds

Upload images from the phone page, or put them in a folder called `backgrounds` and push it to the TV with adb. The launcher shows them in name order and crossfades to the next one every 5 minutes:

```sh
adb push backgrounds /sdcard/Android/data/com.minimal.tvlauncher/files/
```

- `.jpg`, `.png` and `.webp` all work. 1920×1080 images look best.
- To add more images later, push them the same way. New images are picked up on the next change, with no restart needed.
- To remove one image: `adb shell rm /sdcard/Android/data/com.minimal.tvlauncher/files/backgrounds/NAME.jpg`
- To remove all of them: `adb shell rm -r /sdcard/Android/data/com.minimal.tvlauncher/files/backgrounds`
- The timer pauses while you're in another app. When you come back, you'll see a new image if 5 minutes have passed.
- To change the interval, edit `res/values/config.xml` and rebuild.

If the folder is empty or missing, the launcher shows a single `files/background.jpg` if there is one. Otherwise it shows the built-in pixel art.

Backgrounds fill the scene area above the panel (about 320×132 grid pixels, cropped from the centre) and are scaled up with no smoothing, so pixel art stays sharp. Screenshots work best cropped to just the scene, without the game's own verb panel. A 320×144 Monkey Island scene, or a whole-number enlargement of one, fits almost exactly.

## Install (from a computer on the same Wi-Fi as the TV)

This is needed for the first install (and for updating to 7.2, the first version that can update itself). Later updates can come from your phone; see above.

1. On the TV, go to **Settings → Device Preferences → About** and click **Build** 7 times. Then, under **Developer options**, turn on **USB debugging** (on some models it's also called *Network/ADB debugging*). Write down the TV's IP address from **Network & Internet**.
2. On your computer, install `adb` (Android platform-tools) and run:

```sh
adb connect <TV_IP>:5555          # accept the prompt that appears on the TV
adb install -r MinimalTVLauncher.apk
```

3. Make it the default home screen:

```sh
# See which launcher handles Home right now
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME

# Try to set ours as the default (works on many Android 9+ TVs)
adb shell cmd package set-home-activity com.minimal.tvlauncher/.MainActivity
```

Press **Home** on the remote. If the TV shows a chooser, pick **Minimal Launcher → Always**.

### If the stock launcher still takes over

On Xiaomi and Google TV the stock launcher is usually given priority. You can disable it for the current user. This doesn't uninstall anything and can be undone:

| TV software | Launcher package |
|---|---|
| Xiaomi PatchWall | `com.mitv.tvhome` |
| Android TV (stock) | `com.google.android.tvlauncher` |
| Google TV | `com.google.android.apps.tv.launcherx` (and `com.google.android.tungsten.setupwraith`) |

```sh
adb shell pm disable-user --user 0 com.mitv.tvhome   # replace with your package from the table
```

**To undo this:** `adb shell pm enable com.mitv.tvhome`. You can also uninstall this launcher: `adb uninstall com.minimal.tvlauncher`. Re-enable the stock launcher *before* you uninstall this one, otherwise the TV has no home screen.

## Building from source

The APK is built without Gradle, using the Debian/Ubuntu Android tools:

```sh
sudo apt install aapt dalvik-exchange apksigner zipalign android-sdk-platform-23 openjdk-17-jdk python3-pil
python3 make_icons.py res/drawable-xhdpi   # only needed if you change the icons
python3 make_background.py res/drawable-nodpi/bg_default.png   # built-in background art
python3 make_verb_font.py src/com/minimal/tvlauncher/VerbFont.java   # verb font
web/build.sh                               # 3D apartment -> assets/apartment/room_*.mp4 (needs Node.js, ffmpeg)
./build.sh                                 # -> MinimalTVLauncher.apk
```

The first build creates `release.keystore`. Keep it: updates installed with `adb install -r` have to be signed with the same key.

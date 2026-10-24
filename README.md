# Minimal TV Launcher

A home screen for Android TV / Xiaomi TV with two looks. You can switch between them from the settings button in either one; the choice is remembered.

## Apartment (default)

A modern, minimal launcher. A 3D model of Apartment 028 slowly turns in the background, one full turn every 2½ minutes. On top of it:

- a large clock and the date in the top-left
- your apps as a row of names along the bottom
- a settings button (gear) at the bottom right

The model is the three.js scene from *Apartment 028*, unchanged except that the controls and panel are replaced by an automatic orbit (`web/apartment.js`). It runs in a WebView from files inside the app, so it needs no internet. It pauses while another app is open. Text uses Hanken Grotesk (SIL Open Font License; see `assets/fonts/HankenGrotesk-OFL.txt`).

- **Left/Right** moves along the apps. **OK** opens one; **long-press OK** opens its info page.
- **Gear** (or **Menu** on the remote) opens a menu:
  - **TV settings**
  - **Change launcher**, where you can pick Apartment or Monkey Island
- **Back** closes the menu.

## Monkey Island

Looks like the interface of *The Secret of Monkey Island* (VGA). The background scene fills the top of the screen, with the clock in the corner. Below it is a black panel with:

- a **sentence line** that says what OK will do, e.g. "Open Spotify"
- **verbs** on the left
- an **inventory** on the right with the names of your apps

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

## Choosing which apps are shown

Edit `res/values/apps.xml` and rebuild. Each line is `Name|package,package`. The name is what appears in the inventory, so keep it to letters, digits and `+ - ! : . '`, the characters the verb font has. An app is shown if its package is listed or its installed name matches (case, spaces and symbols are ignored). Both launchers show them in the order listed. In Monkey Island there are 6 per inventory page, and the arrows scroll if there are more. To list the packages installed on the TV, run `adb shell pm list packages`.

## Monkey Island backgrounds

Put your images in a folder called `backgrounds` and push it to the TV. The launcher shows them in name order and crossfades to the next one every 5 minutes:

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
web/build.sh                               # 3D apartment -> assets/apartment/apartment.js (needs Node.js)
./build.sh                                 # -> MinimalTVLauncher.apk
```

The first build creates `release.keystore`. Keep it: updates installed with `adb install -r` have to be signed with the same key.

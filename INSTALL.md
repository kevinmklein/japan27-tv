# Japan27 on the Fire TV: install guide

A small Fire TV app that shows https://jp27.netlify.app full screen and sends every **▶️ Videos** button to the real YouTube app, where you're signed in. It's free, with no subscriptions and no app store.

## 1. One-time setup on the Fire TV (5 min)

1. **Install Downloader.** On the home screen go to **Find → Search**, type `Downloader` and install the orange **Downloader** app (by AFTVnews).
2. **Turn on Developer Options.** Go to **Settings → My Fire TV → About**. Highlight the first line (the device name, e.g. "Fire TV Stick 4K Max") and press **OK 7 times** until it says you're a developer.
3. **Allow Downloader to install apps.** Go back one screen, open **Developer Options → Install unknown apps**, and switch **Downloader** to **ON**.
   (Older menus show **Apps from Unknown Sources** instead. Turn that ON.)

## 2. Install Japan27

1. Open **Downloader**. If it asks for access to files, allow it.
2. In the URL box type **`jp27.netlify.app/tv`** and press **Go**.
   If that short link isn't set up yet, use the full link:
   `github.com/kevinmklein/japan27-tv/releases/latest/download/japan27-tv.apk`
3. When the download finishes, choose **Install**, then **Open**.
4. Downloader offers to delete the APK file. Choose **Delete**, since it's no longer needed.

Japan27 now sits at the bottom of **Your Apps & Channels** (the three-squares tile on the home row). To pin it to the front, highlight it, press **☰** and choose **Move to front**.

## 3. Using it with the remote

| Button | What it does |
|---|---|
| ◀ ▶ ▲ ▼ | Move between cards and buttons (a pink ring shows where you are) |
| **OK** | Press the highlighted button. **▶️ Videos** opens in YouTube |
| **Play/Pause** | On an activity card: play its video |
| **Back** | Close the in-app video, close a popup, back a page, then exit |
| **☰** | Japan27 menu: Reload, Start page, where videos play, YouTube link test, version |

## 4. Videos: in the YouTube app or right here

**▶️ Videos** finds the top YouTube video for that place and plays it. The Fire TV YouTube app can't open search results, so Japan27 does the search itself. Under **☰** you can choose where videos play:

- **The YouTube app** (default): signed in, with YouTube Premium (no ads). YouTube's Back button never returns to Japan27; it goes to YouTube's own screens. To come back, press **Home → ▶ → ▶ → OK**. Japan27 is pinned first on the home row, and it reopens on the same card.
- **Here in Japan27**: plays full screen inside the app. **Back** returns straight to the same card. It isn't signed in to YouTube, so expect ads.

The **☰ → YouTube link test** screen is for troubleshooting. On our stick, V1 and V3 play a video; V2 and every search format (S1–S5) only open YouTube's home screen.

## 5. The tile on the home screen

Our stick is the original **Fire TV Stick 4K (2018, model AFTMM, Fire OS 6)**. Its home screen shows every sideloaded app as its square icon on a grey tile; only Appstore apps get full-width artwork, which Amazon's servers supply. A landscape icon just gets squashed into the square, so the square Mt. Fuji icon is the best this TV can show.

## 6. Updating (uninstall first)

Fire OS 6 loses a sideloaded app's tile image when the app is updated in place: you get a broken-picture tile, and the app vanishes from **Your Apps**. So update like this:

1. **Settings → Applications → Manage Installed Applications → Japan27 → Uninstall.**
2. Open **Downloader**, go to `jp27.netlify.app/tv` (Downloader remembers it), and choose **Install**.
3. Open Japan27 once, then in **Your Apps** highlight it, press **☰** and choose **Move to front**.

Uninstalling only resets the ☰ "where videos play" choice. The itinerary itself is the website, so it updates without reinstalling anything. An APK update is only needed when the TV app itself changes.

---

## For Claude / troubleshooting: the same test over adb (optional)

Turn on **Developer Options → ADB debugging**, and find the IP under **Settings → My Fire TV → About → Network**. On the Mac (`brew install --cask android-platform-tools` if `adb` is missing):

```bash
adb connect 192.168.1.50:5555
```

Accept the "Allow USB debugging?" prompt on the TV. Then:

```bash
adb shell pm list packages | grep -i youtube
```

```bash
adb shell dumpsys package com.amazon.firetv.youtube | grep -iE "scheme|authority|path|action" | sort -u
```

```bash
adb shell am start -a android.intent.action.VIEW -d "https://www.youtube.com/watch?v=jNQXAC9IVRw" com.amazon.firetv.youtube
```

```bash
adb shell am start -a android.intent.action.VIEW -d "https://www.youtube.com/results?search_query=Fushimi%20Inari" com.amazon.firetv.youtube
```

```bash
adb shell am start -a android.intent.action.SEARCH --es query "Fushimi Inari" com.amazon.firetv.youtube
```

You can also install a local build with `adb install -r app-release.apk`.

**If an update won't install** ("App not installed"): the new APK was signed with a different key. Uninstall Japan27 first (**Settings → Applications → Manage Installed Applications → Japan27 → Uninstall**), then install again. The signing key is at `~/.japan27-tv-signing/` on Kevin's Mac, so back it up (e.g. in 1Password).

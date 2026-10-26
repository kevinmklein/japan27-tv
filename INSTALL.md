# Japan27 on the Fire TV: install guide

A small Fire TV app that shows https://jp27.netlify.app full screen and sends every **▶️ Videos** button to the real YouTube app, where you're signed in. It's free, with no subscriptions and no app store.

## 1. One-time setup on the Fire TV Stick 4K Max (5 min)

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

Japan27 now sits in **Apps** (the three-square tile on the home row). To pin it to the front, highlight it, press **☰** and choose **Move to front**.

## 3. Using it with the remote

| Button | What it does |
|---|---|
| ◀ ▶ ▲ ▼ | Move between cards and buttons (a pink ring shows where you are) |
| **OK** | Press the highlighted button. **▶️ Videos** opens in YouTube |
| **Back** | In YouTube: back to Japan27. In Japan27: close a popup, then back a page, then exit |
| **☰** | Japan27 menu: Reload, Start page, YouTube link test, version |

## 4. First-time check: the YouTube link test (2 min)

Fire TV's YouTube app doesn't document which links it accepts, so the app includes a test screen. Press **☰ → YouTube link test**, then try each button. After each one, press **Back** to return.

- **V1, V2, V3** should open a short video ("Me at the zoo").
- **S1, S2, S3** should open YouTube *search results* for Fushimi Inari. **S4** just opens YouTube's home screen.

Note which ones open the right thing. Then, under **Itinerary search links use**, press **Use S…** for the best search format that worked. Send the results to Claude so the app's defaults can be set. If no S button shows search results, the itinerary will switch to one chosen video per place.

## 5. Updating

When there's a new version, open **Downloader**, go to the same address (`jp27.netlify.app/tv`), and choose **Install**. It installs over the old version and keeps your settings. Downloader remembers recent addresses, so there's nothing to retype.

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

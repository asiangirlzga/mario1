# Block Hero 3D

A tiny 3D platformer for Android TV (also runs on phones/tablets). Controlled with a TV remote,
gamepad or keyboard. No libraries, no image/sound files: everything is generated in code,
so the APK is only a few dozen KB and the game needs very little RAM.

## Controls
| TV remote / keyboard | Action |
|---|---|
| D-pad (W A S D) | Move (up = forward, left/right = strafe, down = back) |
| OK / Enter / Space / A | Jump (hold = higher) and menu confirm |
| Back | Pause, press Back again to quit |
| Menu / Play-Pause | Pause / resume |

Gamepads: left stick or D-pad to move, A/B/X/Y to jump, Start to pause.
Touch: tap = jump / OK (for testing on a phone).

## Rules
Stomp mushrooms (+100), collect coins (+10, 100 coins = extra life), touch a green post to set a
checkpoint, reach the flag to finish the level. 3 hearts per life, 3 lives, 3 levels.

## Get the APK from GitHub (no Android Studio needed)
1. Create a new GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab -> **Build APK** -> wait ~3 minutes for the green tick.
3. Open the finished run -> **Artifacts** -> download `BlockHero3D-apk` (zip containing `BlockHero3D.apk`).
   Tip: push a tag like `v1.0` and the APK is also attached to a GitHub **Release** page.

## Install on the TV
* Put `BlockHero3D.apk` on a USB stick and open it with a file manager app, or
* `adb connect <tv-ip>:5555` then `adb install BlockHero3D.apk`, or
* send it with the "Send Files to TV" app.
Enable "Install unknown apps" for the app you use. The game shows up in the TV launcher.

## Build locally
Android Studio (open this folder) or `gradle assembleRelease` with JDK 17 + Android SDK 34.
The release is signed with the bundled hobby key `app/blockhero.jks` so new builds install over old ones.
For Play Store publishing create your own key and change `signingConfigs` in `app/build.gradle`.

## Downloadable sounds and title picture
On first launch the game downloads optional files and caches them in its private storage
(only missing files are fetched, so later launches work offline). If a file is missing the built-in
procedural sound/graphics are used. Edit `BASE` in `app/src/main/java/com/blockhero/tv/Assets.java`
to the https folder where you host these files (6 MB max each):

| File | Used for |
|---|---|
| `title.jpg` | title-screen picture |
| `music.mp3` | looping background music |
| `jump.ogg` `coin.ogg` `stomp.ogg` `hurt.ogg` `win.ogg` | sound effects |

Until `BASE` is changed from `YOUR-HOST`, nothing is downloaded.

## Resource use
* 720p fixed render size (TV upscales), one 864-byte cube mesh, no textures
* RAM ~40-60 MB, APK ~50 KB

# TV Caster

TV Caster is a minimal Google TV / Android TV receiver app for Chromecast with Google TV and other Google TV devices. It starts a small HTTP receiver on the TV and lets an iPhone send a direct video URL to the TV. Once playback starts, the TV streams the media URL itself through Android's built-in media player, so the iPhone does not need to stay on the original video page.

## How to use

1. Install and open the app on Google TV.
2. Keep the iPhone and TV on the same local network.
3. On the iPhone, open the address shown on the TV, or create a Shortcut that requests:

   `http://TV_IP_ADDRESS:8090/play?url=VIDEO_URL`

4. Replace `VIDEO_URL` with a direct media URL such as an MP4, HLS `.m3u8`, or DASH stream URL.

The app cannot extract protected streams from arbitrary iPhone apps. The iPhone must provide a direct URL that the TV can access.

## Building an APK on a computer

Run `gradle assembleDebug` from the repository root. The debug APK will be written to `app/build/outputs/apk/debug/app-debug.apk` when the Android Gradle Plugin and Android SDK are available.

## If you do not have a computer

You still need someone or something to build the APK before it can be installed on Google TV. The easiest no-computer path is to use the included GitHub Actions workflow from your phone:

1. Push this repository to GitHub.
2. Open the repository in your iPhone browser.
3. Go to **Actions** > **Build APK** > **Run workflow**.
4. When the run finishes, download the `tv-caster-debug-apk` artifact.
5. Put the APK somewhere your Google TV can download it, such as a private cloud storage link.
6. On Google TV, use a sideloading app such as Downloader or a file manager to download and install the APK.

If you cannot use GitHub Actions or another cloud build service, you will need access to a computer, Android phone with build tools, or someone else who can build and send you the APK.

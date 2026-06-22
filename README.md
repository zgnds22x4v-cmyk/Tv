# TV Caster

TV Caster is a minimal Google TV / Android TV receiver app for Chromecast with Google TV and other Google TV devices. It starts a small HTTP receiver on the TV and lets an iPhone send a direct video URL to the TV. Once playback starts, the TV streams the media URL itself through Android's built-in media player, so the iPhone does not need to stay on the original video page.

## How to use

1. Install and open the app on Google TV.
2. Keep the iPhone and TV on the same local network.
3. Scan the QR code shown on the TV with your iPhone camera.
4. The QR code opens the TV Caster Remote web page hosted by the TV.
5. Paste a direct video URL such as an MP4 or HLS `.m3u8` link and tap **Play on TV**.

The app cannot extract protected streams from arbitrary iPhone apps. The iPhone must provide a direct URL that the TV can access. iPhone screen mirroring uses AirPlay; this app is not an AirPlay receiver, so it can play shared video links but cannot mirror your whole iPhone screen.

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

## If the APK build fails

The GitHub Actions workflow installs Gradle 8.7, Android SDK platform 35, and Android build tools 35.0.0 before running `gradle assembleDebug`. If the build still fails, open the failed Actions run and check the first red step. Common causes are temporary Maven download failures or GitHub Actions not being enabled for the repository.

If the error mentions `androidx.media3.common.MediaItem`, `androidx.media3.exoplayer.ExoPlayer`, or `androidx.media3.ui.PlayerView`, the workflow is building an older commit. Pull or push the latest code and rerun the workflow. The current app uses Android `VideoView` plus ZXing for the QR code, not Media3/ExoPlayer.


## If the app will not open on Google TV

Make sure you are using Chromecast with Google TV or another Android TV / Google TV device. Older cast-only Chromecast devices cannot install or open Android APKs.

If the APK installs but immediately closes, rebuild and reinstall the latest version. The app now discovers the TV IP address from Android network interfaces instead of using Wi-Fi-only APIs, which avoids launch crashes on Ethernet or restricted Wi-Fi devices.

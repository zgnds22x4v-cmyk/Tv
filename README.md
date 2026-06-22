# TV Caster

TV Caster is a minimal Google TV / Android TV receiver app. It starts a small HTTP receiver on the TV and lets an iPhone send a direct video URL to the TV. Once playback starts, the TV streams the media URL itself through Media3/ExoPlayer, so the iPhone does not need to stay on the original video page.

## How to use

1. Install and open the app on Google TV.
2. Keep the iPhone and TV on the same local network.
3. On the iPhone, open the address shown on the TV, or create a Shortcut that requests:

   `http://TV_IP_ADDRESS:8090/play?url=VIDEO_URL`

4. Replace `VIDEO_URL` with a direct media URL such as an MP4, HLS `.m3u8`, or DASH stream URL.

The app cannot extract protected streams from arbitrary iPhone apps. The iPhone must provide a direct URL that the TV can access.

package com.example.tvcaster;

import android.app.Activity;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends Activity {
    private static final int PORT = 8090;
    private ExoPlayer player;
    private TextView instructions;
    private CastServer server;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        player = new ExoPlayer.Builder(this).build();
        PlayerView playerView = new PlayerView(this);
        playerView.setPlayer(player);

        instructions = new TextView(this);
        instructions.setTextColor(0xffffffff);
        instructions.setTextSize(24);
        instructions.setGravity(Gravity.CENTER);
        instructions.setBackgroundColor(0xaa000000);
        instructions.setPadding(32, 32, 32, 32);
        instructions.setText(helpText());

        FrameLayout root = new FrameLayout(this);
        root.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(instructions, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        server = new CastServer();
        try { server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); }
        catch (IOException e) { Toast.makeText(this, "Could not start receiver: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private String helpText() {
        return "TV Caster is ready\n\n" +
                "From your iPhone, send a direct video URL to:\n" +
                "http://" + localIpAddress() + ":" + PORT + "/play?url=VIDEO_URL\n\n" +
                "The TV streams the video itself, so your iPhone can leave the page after playback starts.";
    }

    private String localIpAddress() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi == null || wifi.getConnectionInfo() == null) return "TV_IP_ADDRESS";
        return Formatter.formatIpAddress(wifi.getConnectionInfo().getIpAddress());
    }

    private void play(String url) {
        runOnUiThread(() -> {
            instructions.setVisibility(TextView.GONE);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
            player.prepare();
            player.play();
        });
    }

    @Override protected void onDestroy() {
        if (server != null) server.stop();
        player.release();
        super.onDestroy();
    }

    private class CastServer extends NanoHTTPD {
        CastServer() { super(PORT); }
        @Override public Response serve(IHTTPSession session) {
            if ("/play".equals(session.getUri())) {
                Map<String, String> params = new HashMap<>(session.getParms());
                if (Method.POST.equals(session.getMethod())) {
                    try { session.parseBody(new HashMap<>()); params.putAll(session.getParms()); }
                    catch (Exception ignored) { }
                }
                String url = params.get("url");
                if (url == null || url.trim().isEmpty()) return text("Missing url parameter", Response.Status.BAD_REQUEST);
                play(url.trim());
                return text("Playing on TV. You can now leave the video on your iPhone.", Response.Status.OK);
            }
            return html("<html><body><h1>TV Caster</h1><form action='/play' method='get'><input name='url' style='width:80%' placeholder='Direct video URL'><button>Play</button></form></body></html>");
        }
        private Response text(String body, Response.Status status) { return newFixedLengthResponse(status, "text/plain", body); }
        private Response html(String body) { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); return newFixedLengthResponse(Response.Status.OK, "text/html", new java.io.ByteArrayInputStream(bytes), bytes.length); }
    }
}

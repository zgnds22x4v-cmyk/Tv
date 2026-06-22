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
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PORT = 8090;
    private VideoView videoView;
    private TextView instructions;
    private ReceiverServer server;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        videoView = new VideoView(this);
        videoView.setOnPreparedListener(player -> player.setOnInfoListener((mp, what, extra) -> false));
        videoView.setOnErrorListener((mp, what, extra) -> {
            instructions.setVisibility(TextView.VISIBLE);
            instructions.setText(helpText() + "\n\nCould not play the supplied video URL.");
            return true;
        });

        instructions = new TextView(this);
        instructions.setTextColor(0xffffffff);
        instructions.setTextSize(24);
        instructions.setGravity(Gravity.CENTER);
        instructions.setBackgroundColor(0xaa000000);
        instructions.setPadding(32, 32, 32, 32);
        instructions.setText(helpText());

        FrameLayout root = new FrameLayout(this);
        root.addView(videoView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(instructions, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        server = new ReceiverServer();
        server.start();
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
            videoView.stopPlayback();
            videoView.setVideoURI(Uri.parse(url));
            videoView.start();
        });
    }

    @Override protected void onDestroy() {
        if (server != null) server.shutdown();
        videoView.stopPlayback();
        super.onDestroy();
    }

    private class ReceiverServer extends Thread {
        private volatile boolean running = true;
        private ServerSocket serverSocket;

        ReceiverServer() { super("tv-caster-receiver"); }

        @Override public void run() {
            try {
                serverSocket = new ServerSocket(PORT);
                while (running) handle(serverSocket.accept());
            } catch (IOException e) {
                if (running) runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not start receiver: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        void shutdown() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
        }

        private void handle(Socket socket) {
            try (Socket client = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
                String requestLine = in.readLine();
                if (requestLine == null) return;
                Map<String, String> params = parseParams(requestLine);
                String url = params.get("url");
                if (requestLine.startsWith("GET /play") && url != null && !url.trim().isEmpty()) {
                    play(url.trim());
                    respond(out, "200 OK", "text/plain", "Playing on TV. You can now leave the video on your iPhone.");
                } else if (requestLine.startsWith("GET / ") || requestLine.startsWith("GET /?")) {
                    respond(out, "200 OK", "text/html", "<html><body><h1>TV Caster</h1><form action='/play' method='get'><input name='url' style='width:80%' placeholder='Direct video URL'><button>Play</button></form></body></html>");
                } else {
                    respond(out, "400 Bad Request", "text/plain", "Send /play?url=VIDEO_URL");
                }
            } catch (IOException ignored) { }
        }

        private Map<String, String> parseParams(String requestLine) {
            Map<String, String> params = new HashMap<>();
            int queryStart = requestLine.indexOf('?');
            int queryEnd = requestLine.indexOf(' ', queryStart);
            if (queryStart < 0 || queryEnd < 0) return params;
            String query = requestLine.substring(queryStart + 1, queryEnd);
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0) params.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
            }
            return params;
        }

        private String decode(String value) {
            try { return URLDecoder.decode(value, "UTF-8"); }
            catch (Exception e) { return value; }
        }

        private void respond(BufferedWriter out, String status, String contentType, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            out.write("HTTP/1.1 " + status + "\r\n");
            out.write("Content-Type: " + contentType + "; charset=utf-8\r\n");
            out.write("Content-Length: " + bytes.length + "\r\n");
            out.write("Connection: close\r\n\r\n");
            out.write(body);
            out.flush();
        }
    }
}

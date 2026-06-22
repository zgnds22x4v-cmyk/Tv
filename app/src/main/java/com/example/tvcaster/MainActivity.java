package com.example.tvcaster;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PORT = 8090;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private LinearLayout instructionsCard;
    private TextView titleText;
    private TextView urlText;
    private TextView bodyText;
    private ReceiverServer server;
    private String lastUrl;
    private String pendingUrl;
    private int retryAttempts;
    private boolean surfaceReady;
    private boolean released;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        hideSystemUi();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);

        surfaceView = new SurfaceView(this);
        surfaceView.setBackgroundColor(Color.BLACK);
        surfaceView.setKeepScreenOn(true);
        surfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceHolder = holder;
                surfaceReady = true;
                if (pendingUrl != null) startPlayback(pendingUrl);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                surfaceHolder = holder;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                surfaceHolder = null;
            }
        });
        root.addView(surfaceView);

        instructionsCard = new LinearLayout(this);
        instructionsCard.setOrientation(LinearLayout.VERTICAL);
        instructionsCard.setGravity(Gravity.CENTER);
        instructionsCard.setBackgroundColor(0xcc000000);
        instructionsCard.setPadding(48, 40, 48, 40);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.gravity = Gravity.CENTER;
        cardParams.leftMargin = 64;
        cardParams.rightMargin = 64;

        titleText = new TextView(this);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(30);
        titleText.setGravity(Gravity.CENTER);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        instructionsCard.addView(titleText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        urlText = new TextView(this);
        urlText.setTextColor(0xff8fffe0);
        urlText.setTextSize(24);
        urlText.setGravity(Gravity.CENTER);
        urlText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        urlText.setPadding(0, 28, 0, 28);
        instructionsCard.addView(urlText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        bodyText = new TextView(this);
        bodyText.setTextColor(0xffeeeeee);
        bodyText.setTextSize(20);
        bodyText.setGravity(Gravity.CENTER);
        bodyText.setLineSpacing(4, 1.05f);
        instructionsCard.addView(bodyText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(instructionsCard, cardParams);
        setContentView(root);
        showReadyState();

        server = new ReceiverServer();
        server.start();
    }

    private void showReadyState() {
        String receiverUrl = "http://" + localIpAddress() + ":" + PORT;
        titleText.setText("TV Caster is ready");
        urlText.setText(receiverUrl);
        bodyText.setText("Open this address from your phone on the same Wi-Fi, paste a direct video link, and press Play.\n\n" +
                "The TV handles playback directly, so your phone can leave the page after the video starts.");
        instructionsCard.setVisibility(View.VISIBLE);
        hideSystemUi();
    }

    private void showMessage(String title, String message) {
        runOnUiThread(() -> {
            titleText.setText(title);
            urlText.setText("http://" + localIpAddress() + ":" + PORT);
            bodyText.setText(message);
            instructionsCard.setVisibility(View.VISIBLE);
            hideSystemUi();
        });
    }

    private String localIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return "TV_IP_ADDRESS";
    }

    private void play(String url) {
        runOnUiThread(() -> {
            lastUrl = url;
            pendingUrl = url;
            retryAttempts = 0;
            instructionsCard.setVisibility(View.GONE);
            if (surfaceReady) startPlayback(url);
            hideSystemUi();
        });
    }

    private void startPlayback(String url) {
        if (released || surfaceHolder == null || surfaceHolder.getSurface() == null) return;

        releasePlayerOnly();

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setSurface(surfaceHolder.getSurface());
            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build());
            mediaPlayer.setDataSource(this, Uri.parse(url));
            mediaPlayer.setOnPreparedListener(player -> {
                retryAttempts = 0;
                instructionsCard.setVisibility(View.GONE);
                player.start();
                hideSystemUi();
            });
            mediaPlayer.setOnCompletionListener(player -> {
                pendingUrl = null;
                showMessage("Playback finished", "Send another video URL to start a new video.");
            });
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                retryPlaybackOrShowError(what, extra);
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception error) {
            showMessage("Could not play the video", "The TV could not open that direct video URL. Send a fresh direct MP4 or supported stream URL and try again.\n\n" +
                    "Details: " + error.getMessage());
        }
    }

    private void retryPlaybackOrShowError(int what, int extra) {
        if (lastUrl != null && retryAttempts < MAX_RETRY_ATTEMPTS) {
            retryAttempts++;
            mainHandler.postDelayed(() -> {
                if (!released && surfaceReady && lastUrl != null) {
                    startPlayback(lastUrl);
                }
            }, 2_500L);
            return;
        }

        showMessage("Could not keep playing the video", "The stream stopped or the direct video URL expired. Send a fresh direct video URL and try again.\n\n" +
                "Details: media error " + what + ", extra " + extra);
    }

    private void releasePlayerOnly() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setOnPreparedListener(null);
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.setOnErrorListener(null);
                mediaPlayer.stop();
            } catch (Exception ignored) { }
            try {
                mediaPlayer.release();
            } catch (Exception ignored) { }
            mediaPlayer = null;
        }
    }

    private void hideSystemUi() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    protected void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        released = true;
        if (server != null) server.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
        releasePlayerOnly();
        super.onDestroy();
    }

    private class ReceiverServer extends Thread {
        private volatile boolean running = true;
        private ServerSocket serverSocket;

        ReceiverServer() {
            super("tv-caster-receiver");
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT);
                while (running) handle(serverSocket.accept());
            } catch (IOException e) {
                if (running) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            "Could not start receiver: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show());
                }
            }
        }

        void shutdown() {
            running = false;
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) { }
        }

        private void handle(Socket socket) {
            try (Socket client = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
                String requestLine = in.readLine();
                if (requestLine == null) return;

                if (requestLine.startsWith("OPTIONS ")) {
                    respond(out, "204 No Content", "text/plain", "");
                    return;
                }

                Map<String, String> params = parseParams(requestLine);
                String url = params.get("url");
                if (requestLine.startsWith("GET /play") && url != null && !url.trim().isEmpty()) {
                    play(url.trim());
                    respond(out, "200 OK", "text/plain", "Playing on TV. You can now leave the video on your phone.");
                } else if (requestLine.startsWith("GET / ") || requestLine.startsWith("GET /?")) {
                    respond(out, "200 OK", "text/html", formHtml());
                } else {
                    respond(out, "400 Bad Request", "text/plain", "Send /play?url=VIDEO_URL");
                }
            } catch (IOException ignored) { }
        }

        private String formHtml() {
            return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<title>TV Caster</title>" +
                    "<style>body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;background:#050505;color:#fff;padding:24px;}" +
                    "input{width:100%;box-sizing:border-box;font-size:16px;padding:14px;border-radius:12px;border:1px solid #444;background:#111;color:#fff;}" +
                    "button{margin-top:14px;width:100%;font-size:18px;padding:14px;border:0;border-radius:12px;background:#18e0b7;color:#001b16;font-weight:800;}" +
                    "p{color:#ccc;line-height:1.4}</style></head><body>" +
                    "<h1>TV Caster</h1><p>Paste a direct MP4, HLS .m3u8, or another Android-supported video stream URL.</p>" +
                    "<form action='/play' method='get'><input name='url' placeholder='Direct video URL' autocomplete='off' autocapitalize='off'><button>Play on TV</button></form>" +
                    "</body></html>";
        }

        private Map<String, String> parseParams(String requestLine) {
            Map<String, String> params = new HashMap<>();
            int queryStart = requestLine.indexOf('?');
            int queryEnd = requestLine.indexOf(' ', queryStart);
            if (queryStart < 0 || queryEnd < 0) return params;
            String query = requestLine.substring(queryStart + 1, queryEnd);
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0) {
                    params.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
                }
            }
            return params;
        }

        private String decode(String value) {
            try {
                return URLDecoder.decode(value, "UTF-8");
            } catch (Exception e) {
                return value;
            }
        }

        private void respond(BufferedWriter out, String status, String contentType, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            out.write("HTTP/1.1 " + status + "\r\n");
            out.write("Content-Type: " + contentType + "; charset=utf-8\r\n");
            out.write("Content-Length: " + bytes.length + "\r\n");
            out.write("Access-Control-Allow-Origin: *\r\n");
            out.write("Access-Control-Allow-Methods: GET, OPTIONS\r\n");
            out.write("Access-Control-Allow-Headers: Content-Type\r\n");
            out.write("Connection: close\r\n\r\n");
            out.write(body);
            out.flush();
        }
    }
}

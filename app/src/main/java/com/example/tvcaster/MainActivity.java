package com.example.tvcaster;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class MainActivity extends Activity {
    private static final int PORT = 8090;
    private VideoView videoView;
    private TextView instructions;
    private ImageView qrCode;
    private ReceiverServer server;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        videoView = new VideoView(this);
        videoView.setOnPreparedListener(player -> player.setOnInfoListener((mp, what, extra) -> false));
        videoView.setOnErrorListener((mp, what, extra) -> {
            qrCode.setVisibility(ImageView.VISIBLE);
            instructions.setVisibility(TextView.VISIBLE);
            instructions.setText(helpText() + "\n\nCould not play the supplied video URL.");
            return true;
        });

        qrCode = new ImageView(this);
        qrCode.setBackgroundColor(Color.WHITE);
        qrCode.setPadding(24, 24, 24, 24);
        qrCode.setImageBitmap(makeQrBitmap(remoteUrl(), 360));

        instructions = new TextView(this);
        instructions.setTextColor(0xffffffff);
        instructions.setTextSize(24);
        instructions.setGravity(Gravity.CENTER);
        instructions.setBackgroundColor(0xaa000000);
        instructions.setPadding(32, 32, 32, 32);
        instructions.setText(helpText());

        FrameLayout root = new FrameLayout(this);
        root.addView(videoView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams qrParams = new FrameLayout.LayoutParams(420, 420, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        qrParams.topMargin = 72;
        root.addView(qrCode, qrParams);
        root.addView(instructions, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        server = new ReceiverServer();
        server.start();
    }

    private String helpText() {
        return "TV Caster is ready\n\n" +
                "Scan the QR code with your iPhone camera to open the remote.\n\n" +
                remoteUrl() + "\n\n" +
                "Paste or share a direct video URL, then the TV streams it itself.";
    }

    private String remoteUrl() {
        return "http://" + localIpAddress() + ":" + PORT + "/";
    }

    private Bitmap makeQrBitmap(String value, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            return bitmap;
        }
    }

    private String localIpAddress() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                for (java.net.InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return "TV_IP_ADDRESS";
    }

    private void play(String url) {
        runOnUiThread(() -> {
            qrCode.setVisibility(ImageView.GONE);
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
                    respond(out, "200 OK", "text/html", remoteHtml());
                } else if (requestLine.startsWith("GET /manifest.webmanifest")) {
                    respond(out, "200 OK", "application/manifest+json", "{\"name\":\"TV Caster Remote\",\"short_name\":\"TV Caster\",\"start_url\":\"/\",\"display\":\"standalone\",\"share_target\":{\"action\":\"/play\",\"method\":\"GET\",\"params\":{\"url\":\"url\"}}}");
                } else {
                    respond(out, "400 Bad Request", "text/plain", "Send /play?url=VIDEO_URL");
                }
            } catch (IOException ignored) { }
        }

        private String remoteHtml() {
            return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<link rel='manifest' href='/manifest.webmanifest'><title>TV Caster Remote</title>" +
                    "<style>body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;margin:24px;background:#101820;color:white}input,button{font-size:20px;padding:14px;border-radius:10px}input{width:100%;box-sizing:border-box}button{margin-top:12px;width:100%;background:#64B5F6;border:0}p{line-height:1.4;color:#ddd}</style></head>" +
                    "<body><h1>TV Caster Remote</h1><p>Paste a direct video URL, like an MP4 or M3U8 link. The TV will play it directly, so your phone can leave this page.</p>" +
                    "<form action='/play' method='get'><input name='url' inputmode='url' autocomplete='url' placeholder='https://example.com/video.mp4'><button>Play on TV</button></form>" +
                    "<p><strong>Phone screen casting:</strong> iPhone system screen mirroring uses AirPlay. This app is not an AirPlay receiver, so it can play shared video links but cannot mirror your whole iPhone screen.</p></body></html>";
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

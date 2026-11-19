package com.minimal.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A tiny web server for managing the launcher from a phone on the same Wi-Fi: pick the
 * launcher, choose and order the apps, upload photos and Monkey Island backgrounds, and
 * install an update of the launcher itself. The page is assets/remote/index.html; it talks to the JSON
 * endpoints below.
 *
 *   GET    /                         the page
 *   GET    /api/state                launchers, apps, installed apps, backgrounds, photos
 *   POST   /api/launcher             {"id": "..."}
 *   POST   /api/apps                 [{"pkg": "...", "name": "..."}, ...]
 *   POST   /api/ambient              {"minutes": n}   minutes without the remote before ambient mode; 0 = off
 *   GET    /api/background?name=x    one background image
 *   POST   /api/background?name=x    upload (the request body is the image)
 *   DELETE /api/background?name=x
 *   GET, POST, DELETE /api/photo?name=x   the same, for the Photos launcher's photos
 *   GET    /api/pirate               the uploaded walk sprite sheet for Monkey Island's ambient mode
 *   POST   /api/pirate?frames=n&name=x   upload one (replaces any earlier one)
 *   DELETE /api/pirate               back to the built-in pirate
 *   GET    /api/update               version, whether installs are allowed, install progress
 *   POST   /api/update               install an update (the request body is the APK)
 *   POST   /api/update/permission    open the TV's "Install unknown apps" setting
 */
class RemoteServer extends Thread {

    private static final int MAX_UPLOAD = 25 * 1024 * 1024;
    private static final long MAX_APK = 400L * 1024 * 1024;
    private static final List<String> IMAGE_TYPES = Arrays.asList("jpg", "jpeg", "png", "webp", "gif");

    private final Context context;
    private final int port;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);

    RemoteServer(Context context, int port) {
        super("RemoteServer");
        this.context = context;
        this.port = port;
        setDaemon(true);
    }

    @Override
    public void run() {
        // Keep trying: the port may be briefly busy, e.g. right after an update.
        while (true) {
            ServerSocket server = null;
            try {
                server = new ServerSocket(port);
                while (true) {
                    final Socket socket = server.accept();
                    workers.execute(new Runnable() {
                        @Override
                        public void run() {
                            handle(socket);
                        }
                    });
                }
            } catch (IOException e) {
                try {
                    if (server != null) {
                        server.close();
                    }
                    Thread.sleep(5000);
                } catch (Exception ignored) {
                    return;
                }
            }
        }
    }

    // ---- HTTP ----

    private static class Request {
        String method;
        String path;
        Map<String, String> query = new HashMap<String, String>();
        Map<String, String> headers = new HashMap<String, String>();
        byte[] body = new byte[0];
        // An APK upload isn't read into memory: the installer reads it from the connection.
        InputStream stream;
        long streamLength = -1;
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            Request req = read(in);
            if (req == null) {
                return;
            }
            try {
                route(req, out);
            } catch (Exception e) {
                send(out, 500, "text/plain", ("Error: " + e.getMessage()).getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
            // Client went away.
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (c != '\r') {
                line.write(c);
            }
            if (line.size() > 8192) {
                throw new IOException("Line too long");
            }
        }
        return c == -1 && line.size() == 0 ? null : line.toString("UTF-8");
    }

    private static Request read(InputStream in) throws IOException {
        String first = readLine(in);
        if (first == null) {
            return null;
        }
        String[] parts = first.split(" ");
        if (parts.length < 2) {
            return null;
        }
        Request req = new Request();
        req.method = parts[0];
        String target = parts[1];
        int q = target.indexOf('?');
        req.path = q < 0 ? target : target.substring(0, q);
        if (q >= 0) {
            for (String pair : target.substring(q + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    req.query.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                            URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                }
            }
        }
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                req.headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
            }
        }
        String length = req.headers.get("content-length");
        if (length != null && req.method.equals("POST") && req.path.equals("/api/update")) {
            req.streamLength = Long.parseLong(length);
            if (req.streamLength <= 0 || req.streamLength > MAX_APK) {
                throw new IOException("Too large");
            }
            req.stream = new Bounded(in, req.streamLength);
        } else if (length != null) {
            int n = Integer.parseInt(length);
            if (n < 0 || n > MAX_UPLOAD) {
                throw new IOException("Too large");
            }
            req.body = new byte[n];
            int off = 0;
            while (off < n) {
                int r = in.read(req.body, off, n - off);
                if (r < 0) {
                    throw new IOException("Body cut short");
                }
                off += r;
            }
        }
        return req;
    }

    private static void drain(InputStream in) {
        byte[] buf = new byte[65536];
        try {
            while (in.read(buf) >= 0) {
                // discard
            }
        } catch (IOException ignored) {
            // The phone gave up; nothing more to read.
        }
    }

    /** The request body only: reads end where the upload does. */
    private static final class Bounded extends InputStream {
        private final InputStream in;
        private long left;

        Bounded(InputStream in, long length) {
            this.in = in;
            this.left = length;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                left--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) {
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n > 0) {
                left -= n;
            }
            return n;
        }
    }

    private static void send(OutputStream out, int status, String type, byte[] body) throws IOException {
        String reason = status == 200 ? "OK" : status == 404 ? "Not Found" : status == 400 ? "Bad Request"
                : status == 409 ? "Conflict" : "Error";
        String head = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    private static void sendJson(OutputStream out, Object json) throws IOException {
        send(out, 200, "application/json; charset=utf-8", json.toString().getBytes("UTF-8"));
    }

    // ---- Routes ----

    private void route(Request req, OutputStream out) throws Exception {
        String key = req.method + " " + req.path;
        if (key.equals("GET /") || key.equals("GET /index.html")) {
            send(out, 200, "text/html; charset=utf-8", asset("remote/index.html"));
        } else if (key.equals("GET /api/state")) {
            sendJson(out, state());
        } else if (key.equals("POST /api/launcher")) {
            String id = new JSONObject(new String(req.body, "UTF-8")).getString("id");
            if (!Arrays.asList(MainActivity.LAUNCHER_IDS).contains(id)) {
                send(out, 400, "text/plain", "Unknown launcher".getBytes("UTF-8"));
                return;
            }
            Remote.setLauncher(context, id);
            sendJson(out, state());
        } else if (key.equals("POST /api/ambient")) {
            int minutes = new JSONObject(new String(req.body, "UTF-8")).getInt("minutes");
            if (minutes < 0 || minutes > 120) {
                send(out, 400, "text/plain", "Minutes must be 0 to 120".getBytes("UTF-8"));
                return;
            }
            context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putInt("ambient_minutes", minutes).apply();
            sendJson(out, state());
        } else if (key.equals("POST /api/apps")) {
            JSONArray array = new JSONArray(new String(req.body, "UTF-8"));
            List<AppList.Item> items = new ArrayList<AppList.Item>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String name = o.optString("name", "").trim();
                items.add(new AppList.Item(o.getString("pkg"), name.isEmpty() ? o.getString("pkg") : name));
            }
            AppList.set(context, items);
            sendJson(out, state());
        } else if (req.path.equals("/api/background")) {
            images(req, out, backgroundsDir());
        } else if (req.path.equals("/api/photo")) {
            images(req, out, photosDir());
        } else if (req.path.equals("/api/pirate")) {
            pirate(req, out);
        } else if (key.equals("GET /api/update")) {
            sendJson(out, updateState());
        } else if (key.equals("POST /api/update/permission")) {
            Updater.openPermission(context);
            sendJson(out, updateState());
        } else if (key.equals("POST /api/update")) {
            int status = 200;
            String refusal = null;
            try {
                if (req.stream == null) {
                    status = 400;
                    refusal = "No file";
                } else if (!Updater.allowed(context)) {
                    status = 409;
                    refusal = "Installing updates isn't allowed yet";
                } else {
                    Updater.install(context, req.stream, req.streamLength);
                }
            } catch (Exception e) {
                status = 500;
                refusal = "The TV couldn't take the file: " + e.getMessage();
            }
            if (refusal != null) {
                // Take in the rest of the upload first: closing on the phone mid-send would
                // only show it "connection dropped" instead of this answer.
                if (req.stream != null) {
                    drain(req.stream);
                }
                send(out, status, "text/plain", refusal.getBytes("UTF-8"));
            } else {
                sendJson(out, updateState());
            }
        } else {
            send(out, 404, "text/plain", "Not found".getBytes("UTF-8"));
        }
    }

    private JSONObject updateState() throws Exception {
        return new JSONObject().put("version", version()).put("started", Updater.STARTED).put("allowed", Updater.allowed(context))
                .put("state", Updater.state()).put("message", Updater.message());
    }

    private String version() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private JSONObject state() throws Exception {
        JSONObject state = new JSONObject();
        state.put("version", version());
        SharedPreferences prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        state.put("launcher", prefs.getString("launcher", MainActivity.DEFAULT_LAUNCHER));
        state.put("ambientMinutes", MainActivity.ambientMinutes(context));
        JSONArray launchers = new JSONArray();
        for (int i = 0; i < MainActivity.LAUNCHER_IDS.length; i++) {
            launchers.put(new JSONObject().put("id", MainActivity.LAUNCHER_IDS[i]).put("name", MainActivity.LAUNCHER_NAMES[i]));
        }
        state.put("launchers", launchers);
        JSONArray apps = new JSONArray();
        for (AppList.Item item : AppList.get(context)) {
            apps.put(new JSONObject().put("pkg", item.pkg).put("name", item.name));
        }
        state.put("apps", apps);
        JSONArray installed = new JSONArray();
        for (AppList.Installed app : AppList.installed(context)) {
            installed.put(new JSONObject().put("pkg", app.pkg).put("label", app.label));
        }
        state.put("installed", installed);
        state.put("backgrounds", imageList(backgroundsDir()));
        state.put("photos", imageList(photosDir()));
        File sheet = PirateView.customSheet(context);
        state.put("pirate", sheet == null ? JSONObject.NULL : sheet.getName() + ":" + sheet.lastModified());
        return state;
    }

    private static JSONArray imageList(File dir) throws Exception {
        JSONArray list = new JSONArray();
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                if (f.isFile() && isImage(f.getName())) {
                    list.put(new JSONObject().put("name", f.getName()).put("size", f.length()));
                }
            }
        }
        return list;
    }

    // The Monkey Island slideshow folder: .../Android/data/com.minimal.tvlauncher/files/backgrounds
    private File backgroundsDir() {
        File root = context.getExternalFilesDir(null);
        return root == null ? null : new File(root, "backgrounds");
    }

    // The Photos launcher's photos: .../Android/data/com.minimal.tvlauncher/files/photos
    private File photosDir() {
        File root = context.getExternalFilesDir(null);
        return root == null ? null : new File(root, "photos");
    }

    // Monkey Island's ambient-mode walker: files/pirate/walk_N.png, N = frames in the strip.
    private void pirate(Request req, OutputStream out) throws Exception {
        File root = context.getExternalFilesDir(null);
        if (root == null) {
            send(out, 500, "text/plain", "No storage".getBytes("UTF-8"));
            return;
        }
        File dir = new File(root, PirateView.CUSTOM_DIR);
        File current = PirateView.customSheet(context);
        if (req.method.equals("GET")) {
            if (current == null) {
                send(out, 404, "text/plain", "Not found".getBytes("UTF-8"));
                return;
            }
            String n = current.getName();
            send(out, 200, "image/" + n.substring(n.lastIndexOf('.') + 1), readFile(current));
            return;
        }
        if (req.method.equals("POST")) {
            int frames;
            try {
                frames = Integer.parseInt(req.query.get("frames"));
            } catch (Exception e) {
                frames = 0;
            }
            String name = safeName(req.query.get("name"));
            String ext = name == null ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (frames < 1 || frames > 99 || !(ext.equals("png") || ext.equals("gif") || ext.equals("webp"))) {
                send(out, 400, "text/plain", "Needs a PNG, GIF or WebP and 1 to 99 frames".getBytes("UTF-8"));
                return;
            }
            if (req.body.length == 0) {
                send(out, 400, "text/plain", "Empty upload".getBytes("UTF-8"));
                return;
            }
            clearDir(dir);
            dir.mkdirs();
            FileOutputStream fos = new FileOutputStream(new File(dir, "walk_" + frames + "." + ext));
            try {
                fos.write(req.body);
            } finally {
                fos.close();
            }
        } else if (req.method.equals("DELETE")) {
            clearDir(dir);
        } else {
            send(out, 405, "text/plain", "Method not allowed".getBytes("UTF-8"));
            return;
        }
        sendJson(out, state());
    }

    private static void clearDir(File dir) {
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                f.delete();
            }
        }
    }

    private static boolean isImage(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && IMAGE_TYPES.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Keeps letters, digits, dots, dashes and underscores; must end in an image extension. */
    private static String safeName(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        return clean.isEmpty() || clean.length() > 120 || !isImage(clean) ? null : clean;
    }

    // One image in a folder (backgrounds or photos): get, upload or delete it.
    private void images(Request req, OutputStream out, File dir) throws Exception {
        String name = safeName(req.query.get("name"));
        if (name == null || dir == null) {
            send(out, 400, "text/plain", "Bad file name".getBytes("UTF-8"));
            return;
        }
        File file = new File(dir, name);
        if (req.method.equals("GET")) {
            if (!file.isFile()) {
                send(out, 404, "text/plain", "Not found".getBytes("UTF-8"));
                return;
            }
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            send(out, 200, "image/" + (ext.equals("jpg") ? "jpeg" : ext), readFile(file));
        } else if (req.method.equals("POST")) {
            if (req.body.length == 0) {
                send(out, 400, "text/plain", "Empty upload".getBytes("UTF-8"));
                return;
            }
            dir.mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(req.body);
            } finally {
                fos.close();
            }
            Remote.backgroundsChanged();
            sendJson(out, state());
        } else if (req.method.equals("DELETE")) {
            file.delete();
            Remote.backgroundsChanged();
            sendJson(out, state());
        } else {
            send(out, 400, "text/plain", "Unsupported".getBytes("UTF-8"));
        }
    }

    private byte[] asset(String path) throws IOException {
        return readAll(context.getAssets().open(path));
    }

    private static byte[] readFile(File file) throws IOException {
        return readAll(new FileInputStream(file));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        } finally {
            in.close();
        }
    }
}

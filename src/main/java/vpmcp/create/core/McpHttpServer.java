package vpmcp.create.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP "Streamable HTTP" transport on a plain {@link ServerSocket} (the JRE bundled with Visual Paradigm has no
 * jdk.httpserver module). POST /mcp carries JSON-RPC and is answered with application/json; GET is refused with
 * 405, which the specification allows for servers that never push messages. Bound to the loopback address only.
 */
public final class McpHttpServer {

    private static final int MAX_BODY = 16 * 1024 * 1024;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final int port;
    private final McpProtocol protocol;
    private final Gson gson = new Gson();
    private final String sessionId = UUID.randomUUID().toString();
    private ServerSocket socket;
    private ExecutorService pool;
    private volatile boolean running;
    private volatile String lastError;

    public McpHttpServer(int port, McpProtocol protocol) {
        this.port = port;
        this.protocol = protocol;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        try {
            s.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 50);
        } catch (IOException e) {
            s.close();
            lastError = "Cannot bind 127.0.0.1:" + port + " (" + e.getMessage() + ")";
            throw e;
        }
        socket = s;
        AtomicInteger n = new AtomicInteger();
        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "vpmcp-create-http-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        running = true;
        lastError = null;
        Thread acceptor = new Thread(this::acceptLoop, "vpmcp-create-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        Log.info("MCP server listening on " + url());
    }

    public synchronized void stop() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        if (pool != null) {
            pool.shutdownNow();
        }
        socket = null;
        pool = null;
        Log.info("MCP server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public String getLastError() {
        return lastError;
    }

    public int getPort() {
        return port;
    }

    public String url() {
        return "http://127.0.0.1:" + port + "/mcp";
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket c = socket.accept();
                pool.execute(() -> serve(c));
            } catch (SocketException e) {
                if (running) {
                    Log.error("accept failed", e);
                }
                return;
            } catch (Exception e) {
                Log.error("accept failed", e);
            }
        }
    }

    private void serve(Socket c) {
        try (Socket client = c) {
            client.setSoTimeout(READ_TIMEOUT_MS);
            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            Request req = Request.read(in);
            if (req == null) {
                return;
            }
            handle(req, out);
        } catch (Exception e) {
            Log.error("connection handler failed", e);
        }
    }

    private void handle(Request req, OutputStream out) throws IOException {
        String origin = req.header("origin");
        if (origin != null && !isLocalOrigin(origin)) {
            send(out, 403, "Forbidden", "text/plain", "Forbidden: non-local Origin.", null);
            return;
        }
        String path = req.path;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        if (!path.equals("/mcp") && !path.equals("/mcp/")) {
            send(out, 404, "Not Found", "text/plain", "Not found. The MCP endpoint is /mcp.", null);
            return;
        }
        switch (req.method) {
            case "POST":
                handlePost(req, out);
                return;
            case "DELETE":
                send(out, 200, "OK", "text/plain", "", null);
                return;
            case "OPTIONS":
                send(out, 204, "No Content", "text/plain", "", "Allow: POST, DELETE, OPTIONS\r\n");
                return;
            default:
                send(out, 405, "Method Not Allowed", "text/plain", "Method not allowed.", "Allow: POST, DELETE\r\n");
        }
    }

    private void handlePost(Request req, OutputStream out) throws IOException {
        JsonElement body;
        try {
            body = JsonParser.parseString(new String(req.body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            String err = gson.toJson(McpProtocol.error(null, -32700, "Parse error: " + e.getMessage()));
            send(out, 400, "Bad Request", "application/json", err, null);
            return;
        }
        String extra = "Mcp-Session-Id: " + sessionId + "\r\n";
        if (body.isJsonArray()) {
            JsonArray replies = new JsonArray();
            for (JsonElement e : body.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    JsonObject r = protocol.handle(e.getAsJsonObject());
                    if (r != null) {
                        replies.add(r);
                    }
                }
            }
            if (replies.size() == 0) {
                send(out, 202, "Accepted", "text/plain", "", extra);
            } else {
                send(out, 200, "OK", "application/json", gson.toJson(replies), extra);
            }
            return;
        }
        if (!body.isJsonObject()) {
            String err = gson.toJson(McpProtocol.error(null, -32600, "Expected a JSON-RPC object."));
            send(out, 400, "Bad Request", "application/json", err, null);
            return;
        }
        JsonObject reply = protocol.handle(body.getAsJsonObject());
        if (reply == null) {
            send(out, 202, "Accepted", "text/plain", "", extra);
        } else {
            send(out, 200, "OK", "application/json", gson.toJson(reply), extra);
        }
    }

    static boolean isLocalOrigin(String origin) {
        try {
            String host = URI.create(origin.trim()).getHost();
            if (host == null) {
                return "null".equals(origin.trim());
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
        } catch (Exception e) {
            return false;
        }
    }

    private static void send(OutputStream out, int code, String reason, String type, String body, String extraHeaders)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        h.append("Content-Type: ").append(type).append("; charset=utf-8\r\n");
        h.append("Content-Length: ").append(bytes.length).append("\r\n");
        if (extraHeaders != null) {
            h.append(extraHeaders);
        }
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    /** A parsed HTTP/1.1 request (headers lower-cased; body read by Content-Length or chunked encoding). */
    static final class Request {
        String method;
        String path;
        final Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];

        String header(String name) {
            return headers.get(name);
        }

        static Request read(InputStream in) throws IOException {
            String start = readLine(in);
            if (start == null || start.isEmpty()) {
                return null;
            }
            String[] parts = start.split(" ");
            if (parts.length < 2) {
                throw new IOException("Malformed request line: " + start);
            }
            Request r = new Request();
            r.method = parts[0].toUpperCase(Locale.ROOT);
            r.path = parts[1];
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    r.headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }
            }
            String te = r.header("transfer-encoding");
            if (te != null && te.toLowerCase(Locale.ROOT).contains("chunked")) {
                r.body = readChunked(in);
            } else if (r.header("content-length") != null) {
                int len = Integer.parseInt(r.header("content-length"));
                if (len < 0 || len > MAX_BODY) {
                    throw new IOException("Body too large: " + len);
                }
                r.body = readFully(in, len);
            }
            return r;
        }

        private static byte[] readChunked(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            while (true) {
                String sizeLine = readLine(in);
                if (sizeLine == null) {
                    break;
                }
                int semi = sizeLine.indexOf(';');
                int size = Integer.parseInt((semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim(), 16);
                if (size == 0) {
                    String l;
                    while ((l = readLine(in)) != null && !l.isEmpty()) {
                        // trailers ignored
                    }
                    break;
                }
                if (buf.size() + size > MAX_BODY) {
                    throw new IOException("Body too large");
                }
                buf.write(readFully(in, size));
                readLine(in);
            }
            return buf.toByteArray();
        }

        private static byte[] readFully(InputStream in, int len) throws IOException {
            byte[] b = new byte[len];
            int off = 0;
            while (off < len) {
                int n = in.read(b, off, len - off);
                if (n < 0) {
                    throw new IOException("Unexpected end of body");
                }
                off += n;
            }
            return b;
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) >= 0) {
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    buf.write(b);
                }
                if (buf.size() > 64 * 1024) {
                    throw new IOException("Header line too long");
                }
            }
            if (b < 0 && buf.size() == 0) {
                return null;
            }
            return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}

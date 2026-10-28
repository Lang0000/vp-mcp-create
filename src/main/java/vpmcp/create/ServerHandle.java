package vpmcp.create;

import vpmcp.create.core.Log;
import vpmcp.create.core.McpHttpServer;
import vpmcp.create.core.McpProtocol;
import vpmcp.create.core.Tool;
import vpmcp.create.tools.AddElementsTool;
import vpmcp.create.tools.CreateDiagramTool;
import vpmcp.create.tools.DeleteElementTool;
import vpmcp.create.tools.ExportDiagramTool;
import vpmcp.create.tools.LayoutDiagramTool;
import vpmcp.create.tools.ListDiagramsTool;
import vpmcp.create.tools.ReloadPluginTool;
import vpmcp.create.tools.SaveProjectTool;
import vpmcp.create.tools.UpdateElementTool;
import vpmcp.create.vp.EdtInvoker;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** The one running server of this plugin, shared between the plugin class and the status dialog. */
public final class ServerHandle {

    public static final String VERSION = "1.0.0";
    public static final int DEFAULT_PORT = 8900;

    private static McpHttpServer server;
    private static File pluginDir;
    private static int port = DEFAULT_PORT;
    private static String startError;
    private static List<String> toolNames = new ArrayList<>();

    private ServerHandle() {
    }

    public static synchronized void start(File dir) {
        pluginDir = dir;
        Properties cfg = loadConfig(dir);
        port = parsePort(System.getProperty("vpcreate.port", cfg.getProperty("port", String.valueOf(DEFAULT_PORT))));
        boolean dev = Boolean.parseBoolean(cfg.getProperty("dev", "false"));
        List<Tool> tools = new ArrayList<>();
        tools.add(new CreateDiagramTool());
        tools.add(new AddElementsTool());
        tools.add(new UpdateElementTool());
        tools.add(new DeleteElementTool());
        tools.add(new LayoutDiagramTool());
        tools.add(new ExportDiagramTool());
        tools.add(new ListDiagramsTool());
        tools.add(new SaveProjectTool());
        if (dev) {
            tools.add(new ReloadPluginTool());
        }
        McpProtocol protocol = new McpProtocol(tools, new EdtInvoker(), VERSION);
        toolNames = protocol.toolNames();
        server = new McpHttpServer(port, protocol);
        try {
            server.start();
            startError = null;
        } catch (Exception e) {
            startError = e.getMessage();
            Log.error("MCP server failed to start on port " + port, e);
        }
    }

    /** Entry point for {@link ReloadPluginTool}: this class was loaded from a fresh jar in a new class loader. */
    public static void devStart(File dir) {
        Log.init(new File(dir, "vpcreate.log"));
        Log.info("--- dev reload: " + VERSION + " ---");
        start(dir);
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop();
        }
    }

    public static synchronized void restart() {
        stop();
        start(pluginDir);
    }

    public static synchronized boolean isRunning() {
        return server != null && server.isRunning();
    }

    public static synchronized String status() {
        return isRunning() ? "RUNNING" : "STOPPED";
    }

    public static synchronized String error() {
        return startError;
    }

    public static synchronized int port() {
        return port;
    }

    public static String url() {
        return "http://127.0.0.1:" + port() + "/mcp";
    }

    public static synchronized List<String> tools() {
        return new ArrayList<>(toolNames);
    }

    public static synchronized File pluginDir() {
        return pluginDir;
    }

    private static Properties loadConfig(File dir) {
        Properties p = new Properties();
        if (dir == null) {
            return p;
        }
        File f = new File(dir, "vpcreate.properties");
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
                Log.info("Config           : " + f + " " + p);
            } catch (Exception e) {
                Log.error("cannot read " + f, e);
            }
        }
        return p;
    }

    private static int parsePort(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            if (v > 0 && v < 65536) {
                return v;
            }
        } catch (Exception ignored) {
            // fall back to default
        }
        Log.warn("Bad port \"" + s + "\", using " + DEFAULT_PORT);
        return DEFAULT_PORT;
    }
}

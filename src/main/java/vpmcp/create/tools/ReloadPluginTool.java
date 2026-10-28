package vpmcp.create.tools;

import com.google.gson.JsonObject;
import com.vp.plugin.ApplicationManager;
import vpmcp.create.ServerHandle;
import vpmcp.create.core.Log;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Development helper, listed only with dev=true in vpcreate.properties. Visual Paradigm's reloadPluginClasses does
 * not re-run the plugin's loaded() method, so this tool stops the server and starts a fresh copy of it from the
 * rebuilt jar in a new class loader. The menu action keeps the classes loaded at start-up.
 */
public final class ReloadPluginTool implements Tool {

    @Override
    public String name() {
        return "reload_plugin";
    }

    @Override
    public String description() {
        return "Development only: restart the MCP Create server from the rebuilt lib/vpmcp.create.jar without "
                + "restarting Visual Paradigm. Reconnect after a second.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.parse("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");
    }

    @Override
    public boolean mutates() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args) throws Exception {
        File dir = ServerHandle.pluginDir();
        File jar = dir == null ? null : new File(dir, "lib/vpmcp.create.jar");
        if (jar == null || !jar.isFile()) {
            throw new ToolException("Plugin jar not found: " + jar);
        }
        File copy = File.createTempFile("vpmcp.create-", ".jar");
        copy.deleteOnExit();
        Files.copy(jar.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
                Log.info("dev reload from " + copy);
                ServerHandle.stop();
                URLClassLoader cl = new URLClassLoader(new URL[] {copy.toURI().toURL()},
                        ApplicationManager.class.getClassLoader());
                cl.loadClass("vpmcp.create.ServerHandle").getMethod("devStart", File.class).invoke(null, dir);
            } catch (Throwable e) {
                Log.error("dev reload failed; restarting the old server", e);
                ServerHandle.start(dir);
            }
        }, "vpmcp-create-reload");
        t.setDaemon(true);
        t.start();
        JsonObject out = new JsonObject();
        out.addProperty("reloading", true);
        out.addProperty("jar", copy.getAbsolutePath());
        return out;
    }
}

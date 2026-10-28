package vpmcp.create;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;
import vpmcp.create.core.Log;

import java.io.File;

/** Visual Paradigm entry point: starts the MCP Create server when the plugin loads, stops it on unload. */
public final class VpCreatePlugin implements VPPlugin {

    @Override
    public void loaded(VPPluginInfo info) {
        File dir = info == null ? null : info.getPluginDir();
        if (dir != null) {
            Log.init(new File(dir, "vpcreate.log"));
        }
        Log.info("--- Visual Paradigm MCP Create Server " + ServerHandle.VERSION + " loading ---");
        Log.info("Plugin directory : " + dir);
        Log.info("Java runtime     : " + System.getProperty("java.version") + " at " + System.getProperty("java.home"));
        try {
            ApplicationManager am = ApplicationManager.instance();
            if (am != null && am.getProductInfo() != null) {
                Log.info("Visual Paradigm  : " + am.getProductInfo().getName() + " " + am.getProductInfo().getVersion()
                        + " build " + am.getBuildNo());
            }
        } catch (Throwable t) {
            Log.warn("Cannot read product info: " + t);
        }
        ServerHandle.start(dir);
    }

    @Override
    public void unloaded() {
        Log.info("--- unloading ---");
        ServerHandle.stop();
    }
}

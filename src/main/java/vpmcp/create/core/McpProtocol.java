package vpmcp.create.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON-RPC 2.0 / MCP method dispatch: initialize, ping, tools/list, tools/call. */
public final class McpProtocol {

    public static final String SERVER_NAME = "visual-paradigm-mcp-create";
    private static final List<String> VERSIONS = Arrays.asList("2025-06-18", "2025-03-26", "2024-11-05");

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final ToolInvoker invoker;
    private final String serverVersion;
    private final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create();

    public McpProtocol(List<Tool> toolList, ToolInvoker invoker, String serverVersion) {
        for (Tool t : toolList) {
            tools.put(t.name(), t);
        }
        this.invoker = invoker;
        this.serverVersion = serverVersion;
    }

    public List<String> toolNames() {
        return new java.util.ArrayList<>(tools.keySet());
    }

    /** Handles one request object; returns null for notifications. */
    public JsonObject handle(JsonObject req) {
        JsonElement id = req.has("id") ? req.get("id") : null;
        if (!req.has("method") || !req.get("method").isJsonPrimitive()) {
            return error(id, -32600, "Missing \"method\".");
        }
        String method = req.get("method").getAsString();
        if (id == null || method.startsWith("notifications/")) {
            return null;
        }
        JsonObject params = req.has("params") && req.get("params").isJsonObject()
                ? req.getAsJsonObject("params") : new JsonObject();
        switch (method) {
            case "initialize":
                return result(id, initialize(params));
            case "ping":
                return result(id, new JsonObject());
            case "tools/list":
                return result(id, toolsList());
            case "tools/call":
                return toolsCall(id, params);
            default:
                return error(id, -32601, "Method not found: " + method);
        }
    }

    private JsonObject initialize(JsonObject params) {
        String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : null;
        JsonObject caps = new JsonObject();
        JsonObject toolCaps = new JsonObject();
        toolCaps.addProperty("listChanged", false);
        caps.add("tools", toolCaps);
        JsonObject info = new JsonObject();
        info.addProperty("name", SERVER_NAME);
        info.addProperty("version", serverVersion);
        JsonObject r = new JsonObject();
        r.addProperty("protocolVersion", VERSIONS.contains(requested) ? requested : VERSIONS.get(0));
        r.add("capabilities", caps);
        r.add("serverInfo", info);
        r.addProperty("instructions",
                "Creates and edits diagrams in the project currently open in Visual Paradigm. "
                        + "Start with list_diagrams or create_diagram; created diagrams can be read back with "
                        + "the get_diagram_by_url tool of the visual-paradigm server.");
        return r;
    }

    private JsonObject toolsList() {
        JsonArray arr = new JsonArray();
        for (Tool t : tools.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", t.name());
            o.addProperty("description", t.description());
            o.add("inputSchema", t.inputSchema());
            arr.add(o);
        }
        JsonObject r = new JsonObject();
        r.add("tools", arr);
        return r;
    }

    private JsonObject toolsCall(JsonElement id, JsonObject params) {
        if (!params.has("name") || !params.get("name").isJsonPrimitive()) {
            return error(id, -32602, "\"params.name\" is required.");
        }
        String name = params.get("name").getAsString();
        Tool tool = tools.get(name);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name + ". Available: " + tools.keySet());
        }
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        long t0 = System.currentTimeMillis();
        try {
            JsonObject out = invoker.invoke(tool, args);
            Log.info("tools/call " + name + " ok (" + (System.currentTimeMillis() - t0) + " ms)");
            return result(id, toolResult(gson.toJson(out), false));
        } catch (ToolException e) {
            Log.warn("tools/call " + name + " failed: " + e.getMessage());
            return result(id, toolResult("Error: " + e.getMessage(), true));
        } catch (Throwable e) {
            Log.error("tools/call " + name + " crashed", e);
            return result(id, toolResult("Error: " + describe(e), true));
        }
    }

    private static JsonObject toolResult(String text, boolean isError) {
        JsonArray content = new JsonArray();
        JsonObject c = new JsonObject();
        c.addProperty("type", "text");
        c.addProperty("text", text);
        content.add(c);
        JsonObject r = new JsonObject();
        r.add("content", content);
        r.addProperty("isError", isError);
        return r;
    }

    static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 4) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(cur.getClass().getSimpleName());
            if (cur.getMessage() != null) {
                sb.append(": ").append(cur.getMessage());
            }
            cur = cur.getCause();
        }
        return sb.toString();
    }

    public static JsonObject result(JsonElement id, JsonObject result) {
        JsonObject r = new JsonObject();
        r.addProperty("jsonrpc", "2.0");
        r.add("id", id == null ? JsonNull.INSTANCE : id);
        r.add("result", result);
        return r;
    }

    public static JsonObject error(JsonElement id, int code, String message) {
        JsonObject e = new JsonObject();
        e.addProperty("code", code);
        e.addProperty("message", message);
        JsonObject r = new JsonObject();
        r.addProperty("jsonrpc", "2.0");
        r.add("id", id == null ? JsonNull.INSTANCE : id);
        r.add("error", e);
        return r;
    }
}

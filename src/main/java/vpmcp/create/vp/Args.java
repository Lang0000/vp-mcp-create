package vpmcp.create.vp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import vpmcp.create.core.ToolException;

import java.util.ArrayList;
import java.util.List;

/** Small helpers for reading tool arguments with clear error messages. */
public final class Args {

    private Args() {
    }

    public static String str(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return def;
        }
        JsonElement e = o.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    public static String reqStr(JsonObject o, String key) throws ToolException {
        String s = str(o, key, null);
        if (s == null || s.trim().isEmpty()) {
            throw new ToolException("\"" + key + "\" is required.");
        }
        return s;
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return def;
        }
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        String s = e.getAsString().trim().toLowerCase();
        return s.equals("true") || s.equals("yes") || s.equals("1");
    }

    public static Integer integer(JsonObject o, String key) throws ToolException {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        try {
            return (int) Math.round(o.get(key).getAsDouble());
        } catch (Exception e) {
            throw new ToolException("\"" + key + "\" must be a number.");
        }
    }

    public static Double number(JsonObject o, String key) throws ToolException {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        try {
            return o.get(key).getAsDouble();
        } catch (Exception e) {
            throw new ToolException("\"" + key + "\" must be a number.");
        }
    }

    public static JsonArray array(JsonObject o, String key) throws ToolException {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return new JsonArray();
        }
        if (!o.get(key).isJsonArray()) {
            throw new ToolException("\"" + key + "\" must be an array.");
        }
        return o.getAsJsonArray(key);
    }

    public static JsonObject obj(JsonObject o, String key) throws ToolException {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        if (!o.get(key).isJsonObject()) {
            throw new ToolException("\"" + key + "\" must be an object.");
        }
        return o.getAsJsonObject(key);
    }

    /** A string or an array of strings. */
    public static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return out;
        }
        JsonElement e = o.get(key);
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (!x.isJsonNull()) {
                    out.add(x.getAsString());
                }
            }
        } else {
            out.add(e.getAsString());
        }
        return out;
    }

    /** Normalises kind and type names: lower case, spaces and dashes to underscores. */
    public static String norm(String s) {
        return s == null ? null : s.trim().toLowerCase().replace('-', '_').replace(' ', '_');
    }
}

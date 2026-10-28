package vpmcp.create.core;

import com.google.gson.JsonObject;

/** One MCP tool. {@link #execute} runs on the Visual Paradigm event dispatch thread. */
public interface Tool {

    String name();

    String description();

    /** JSON Schema of the arguments object. */
    JsonObject inputSchema();

    /**
     * True when the tool changes the model, so it runs inside a project transaction. That transaction is VP's
     * storage transaction; it does not merge the changes into one Undo step (the Open API has no way to do that).
     */
    boolean mutates();

    JsonObject execute(JsonObject args) throws Exception;
}

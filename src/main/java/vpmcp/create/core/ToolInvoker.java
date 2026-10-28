package vpmcp.create.core;

import com.google.gson.JsonObject;

/** Decides where and how a tool runs (directly in tests, on the EDT inside Visual Paradigm). */
public interface ToolInvoker {

    JsonObject invoke(Tool tool, JsonObject args) throws Exception;

    ToolInvoker DIRECT = Tool::execute;
}

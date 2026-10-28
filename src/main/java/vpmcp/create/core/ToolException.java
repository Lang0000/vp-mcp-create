package vpmcp.create.core;

/** A user-facing tool failure: its message is returned to the MCP client as an error result. */
public class ToolException extends Exception {

    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}

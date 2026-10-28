package vpmcp.create.vp;

import com.google.gson.JsonObject;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IProjectTransaction;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;
import vpmcp.create.core.ToolInvoker;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs every tool on the Swing event dispatch thread, as the Open API requires. Mutating tools are wrapped in one
 * project transaction (VP's storage transaction; Undo still steps through the single changes). Calls are
 * serialised; a call that cannot get the
 * EDT in time (for example because a modal dialog is open) fails instead of hanging the client.
 */
public final class EdtInvoker implements ToolInvoker {

    private static final long TIMEOUT_SECONDS = 180;
    private final Object lock = new Object();

    @Override
    public JsonObject invoke(Tool tool, JsonObject args) throws Exception {
        synchronized (lock) {
            if (SwingUtilities.isEventDispatchThread()) {
                return run(tool, args);
            }
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
            SwingUtilities.invokeLater(() -> {
                // a request that timed out before it got the EDT must not run later behind the client's back
                if (f.isDone() || !started.compareAndSet(false, true)) {
                    return;
                }
                try {
                    f.complete(run(tool, args));
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            });
            try {
                return f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                boolean running = !started.compareAndSet(false, true);
                f.cancel(false);
                if (running) {
                    throw new ToolException("Visual Paradigm is still busy with this request after " + TIMEOUT_SECONDS
                            + " s, probably waiting for a dialog. Answer the dialog in Visual Paradigm; the "
                            + "request may still complete.");
                }
                throw new ToolException("Visual Paradigm did not run the request within " + TIMEOUT_SECONDS
                        + " s and it was cancelled. Close any open dialog in Visual Paradigm and retry.");
            } catch (ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof Exception) {
                    throw (Exception) c;
                }
                throw new ToolException("Internal error: " + c, c);
            }
        }
    }

    private static JsonObject run(Tool tool, JsonObject args) throws Exception {
        if (!tool.mutates()) {
            return tool.execute(args);
        }
        IProject project = Vp.requireProject();
        IProjectTransaction tx = project.startProjectTransaction();
        try {
            return tool.execute(args);
        } finally {
            if (tx != null) {
                tx.endTransaction();
            }
        }
    }
}

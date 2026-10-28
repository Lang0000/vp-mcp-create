package vpmcp.create.ui;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import vpmcp.create.ServerHandle;
import vpmcp.create.core.Log;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Font;
import java.io.File;

/** Tools → MCP Create Server: shows the server status and lets the user restart it. */
public final class StatusActionController implements VPActionController {

    @Override
    public void performAction(VPAction action) {
        Component parent = null;
        try {
            parent = ApplicationManager.instance().getViewManager().getRootFrame();
        } catch (Throwable ignored) {
            // show without parent
        }
        while (true) {
            JTextArea text = new JTextArea(statusText(), 14, 62);
            text.setEditable(false);
            text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            Object[] options = ServerHandle.isRunning()
                    ? new Object[] {"OK", "Copy URL", "Restart"}
                    : new Object[] {"OK", "Start"};
            int choice = JOptionPane.showOptionDialog(parent, new JScrollPane(text), "MCP Create Server",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
            if (choice == 1 && ServerHandle.isRunning()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(ServerHandle.url()), null);
                return;
            }
            if (choice == 1 || choice == 2) {
                ServerHandle.restart();
                continue;
            }
            return;
        }
    }

    private static String statusText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Status   : ").append(ServerHandle.status()).append('\n');
        sb.append("Address  : ").append(ServerHandle.url()).append('\n');
        sb.append("Host     : 127.0.0.1 (local connections only)\n");
        sb.append("Port     : ").append(ServerHandle.port()).append('\n');
        File log = Log.getFile();
        sb.append("Log file : ").append(log == null ? "-" : log.getAbsolutePath()).append('\n');
        sb.append("Version  : ").append(ServerHandle.VERSION).append('\n');
        if (ServerHandle.error() != null) {
            sb.append("Error    : ").append(ServerHandle.error()).append('\n');
        }
        sb.append("Tools    : ").append(String.join(", ", ServerHandle.tools())).append("\n\n");
        sb.append("Connect Claude Code with:\n");
        sb.append("  claude mcp add --transport http vp-create ").append(ServerHandle.url()).append('\n');
        return sb.toString();
    }

    @Override
    public void update(VPAction action) {
        // always enabled
    }
}

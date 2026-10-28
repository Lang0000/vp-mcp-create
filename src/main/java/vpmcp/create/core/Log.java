package vpmcp.create.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Minimal append-only file logger (vpcreate.log in the plugin directory). Thread-safe. */
public final class Log {

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static File file;

    private Log() {
    }

    public static synchronized void init(File logFile) {
        file = logFile;
        if (file != null && file.length() > MAX_BYTES) {
            File old = new File(file.getParentFile(), file.getName() + ".1");
            old.delete();
            file.renameTo(old);
        }
    }

    public static synchronized File getFile() {
        return file;
    }

    public static void info(String msg) {
        write("INFO ", msg, null);
    }

    public static void warn(String msg) {
        write("WARN ", msg, null);
    }

    public static void error(String msg, Throwable t) {
        write("ERROR", msg, t);
    }

    private static synchronized void write(String level, String msg, Throwable t) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "  " + level + "  " + msg;
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            line += "\n" + sw;
        }
        if (file == null) {
            System.err.println("[vpmcp.create] " + line);
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(line);
            w.write('\n');
        } catch (Exception e) {
            System.err.println("[vpmcp.create] " + line);
        }
    }
}

package vpmcp.create.tools;

import com.google.gson.JsonObject;
import com.vp.plugin.ProjectManager;
import com.vp.plugin.model.IProject;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.Vp;

import java.io.File;

public final class SaveProjectTool implements Tool {

    @Override
    public String name() {
        return "save_project";
    }

    @Override
    public String description() {
        return "Save the open project. Without 'path' it is saved to its current .vpp file (a project that was "
                + "never saved needs a path). With 'path' it is saved as that .vpp file; an existing file is only "
                + "replaced with overwrite=true.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.parse("{\"type\":\"object\",\"properties\":{"
                + "\"path\":{\"type\":\"string\",\"description\":\"Absolute path of the .vpp file.\"},"
                + "\"overwrite\":{\"type\":\"boolean\",\"default\":false}"
                + "},\"additionalProperties\":false}");
    }

    @Override
    public boolean mutates() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args) throws Exception {
        IProject p = Vp.requireProject();
        ProjectManager pm = Vp.app().getProjectManager();
        String path = Args.str(args, "path", null);
        boolean ok;
        File target;
        if (path == null) {
            target = p.getProjectFile();
            if (target == null) {
                throw new ToolException("The project has never been saved; give \"path\" (absolute, ending in .vpp).");
            }
            ok = pm.saveProject();
        } else {
            target = new File(path);
            if (!target.isAbsolute()) {
                throw new ToolException("\"path\" must be absolute.");
            }
            if (!target.getName().toLowerCase().endsWith(".vpp")) {
                target = new File(target.getParentFile(), target.getName() + ".vpp");
            }
            if (target.exists() && !Args.bool(args, "overwrite", false)) {
                throw new ToolException(target + " exists; pass overwrite=true to replace it.");
            }
            File dir = target.getAbsoluteFile().getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                throw new ToolException("Cannot create directory " + dir);
            }
            ok = pm.saveProjectAs(target);
        }
        if (!ok) {
            throw new ToolException("Visual Paradigm did not save the project to " + target + ".");
        }
        JsonObject out = new JsonObject();
        out.addProperty("saved", true);
        out.addProperty("path", target.getAbsolutePath());
        out.addProperty("bytes", target.length());
        out.addProperty("project", p.getName());
        return out;
    }
}

package vpmcp.create.tools;

import com.google.gson.JsonObject;
import com.vp.plugin.ExportDiagramAsImageOption;
import com.vp.plugin.diagram.IDiagramUIModel;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.Vp;

import java.io.File;

public final class ExportDiagramTool implements Tool {

    @Override
    public String name() {
        return "export_diagram";
    }

    @Override
    public String description() {
        return "Export a diagram as PNG, SVG, PDF or JPG into a directory on this computer. Returns the file path "
                + "and size. 'scale' > 1 gives a sharper PNG (2 is good for documents).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject s = Schemas.parse("{\"type\":\"object\",\"properties\":{"
                + "\"diagram\":{\"type\":\"string\",\"description\":\"Diagram URL, id or name.\"},"
                + "\"format\":{\"type\":\"string\",\"enum\":[\"png\",\"svg\",\"pdf\",\"jpg\"],\"default\":\"png\"},"
                + "\"directory\":{\"type\":\"string\",\"description\":\"Absolute output directory; created if "
                + "missing.\"},"
                + "\"file_name\":{\"type\":\"string\",\"description\":\"File name without extension; defaults "
                + "to the diagram name.\"},"
                + "\"scale\":{\"type\":\"number\",\"default\":1},"
                + "\"transparent\":{\"type\":\"boolean\",\"default\":false,\"description\":\"PNG without "
                + "background.\"}"
                + "},\"additionalProperties\":false}");
        s.add("required", Schemas.required("diagram", "directory"));
        return s;
    }

    @Override
    public boolean mutates() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args) throws Exception {
        IDiagramUIModel d = Vp.findDiagram(Args.str(args, "diagram", null));
        String format = Args.norm(Args.str(args, "format", "png"));
        boolean transparent = Args.bool(args, "transparent", false);
        int type;
        String ext;
        switch (format) {
            case "png":
                type = transparent ? ExportDiagramAsImageOption.IMAGE_TYPE_PNG_WITHOUT_BACKGROUND
                        : ExportDiagramAsImageOption.IMAGE_TYPE_PNG;
                ext = "png";
                break;
            case "svg":
                type = ExportDiagramAsImageOption.IMAGE_TYPE_SVG;
                ext = "svg";
                break;
            case "pdf":
                type = ExportDiagramAsImageOption.IMAGE_TYPE_PDF;
                ext = "pdf";
                break;
            case "jpg":
            case "jpeg":
                type = ExportDiagramAsImageOption.IMAGE_TYPE_JPG;
                ext = "jpg";
                break;
            default:
                throw new ToolException("\"format\" must be png, svg, pdf or jpg.");
        }
        File dir = new File(Args.reqStr(args, "directory"));
        if (!dir.isAbsolute()) {
            throw new ToolException("\"directory\" must be an absolute path.");
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new ToolException("Cannot create directory " + dir);
        }
        String base = Args.str(args, "file_name", null);
        if (base == null || base.trim().isEmpty()) {
            base = d.getName() == null || d.getName().isEmpty() ? d.getId() : d.getName();
        }
        base = base.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
        File file = new File(dir, base + "." + ext);
        ExportDiagramAsImageOption opt = new ExportDiagramAsImageOption(type);
        Double scale = Args.number(args, "scale");
        if (scale != null && scale > 0) {
            opt.setScale(scale.floatValue());
        }
        opt.setTextAntiAliasing(true);
        opt.setGraphicAntiAliasing(true);
        if (file.exists() && !file.delete()) {
            throw new ToolException("Cannot overwrite " + file);
        }
        try {
            Vp.app().getModelConvertionManager().exportDiagramAsImage(d, file, opt);
        } catch (Throwable t) {
            throw new ToolException("Export failed: " + t, t);
        }
        if (!file.isFile() || file.length() == 0) {
            throw new ToolException("Visual Paradigm reported no error but did not write " + file
                    + " (is the diagram empty?).");
        }
        JsonObject out = new JsonObject();
        out.addProperty("path", file.getAbsolutePath());
        out.addProperty("format", ext);
        out.addProperty("bytes", file.length());
        out.add("diagram", Common.diagramJson(d));
        return out;
    }
}

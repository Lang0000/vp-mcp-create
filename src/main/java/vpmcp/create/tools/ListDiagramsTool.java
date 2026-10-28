package vpmcp.create.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IProject;
import vpmcp.create.core.Tool;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.Kinds;
import vpmcp.create.vp.Vp;

public final class ListDiagramsTool implements Tool {

    @Override
    public String name() {
        return "list_diagrams";
    }

    @Override
    public String description() {
        return "List the diagrams of the open Visual Paradigm project: id, name, type, URL (for get_diagram_by_url "
                + "and the other tools) and the number of shapes and connectors. Also reports the project name "
                + "and file, and the diagram types, node kinds and edge kinds create_diagram understands.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.parse("{\"type\":\"object\",\"properties\":{"
                + "\"type\":{\"type\":\"string\",\"description\":\"Only diagrams of this type (e.g. class or "
                + "ClassDiagram).\"},"
                + "\"name_contains\":{\"type\":\"string\",\"description\":\"Case-insensitive name filter.\"},"
                + "\"include_vocabulary\":{\"type\":\"boolean\",\"default\":false,\"description\":\"Also list "
                + "supported diagram types, node kinds and edge kinds.\"}"
                + "},\"additionalProperties\":false}");
    }

    @Override
    public boolean mutates() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args) throws Exception {
        IProject p = Vp.requireProject();
        String type = Args.str(args, "type", null);
        String vpType = null;
        if (type != null) {
            vpType = Kinds.diagramType(type).vpType;
        }
        String filter = Args.str(args, "name_contains", null);
        JsonArray arr = new JsonArray();
        for (IDiagramUIModel d : p.toDiagramArray()) {
            if (vpType != null && !vpType.equals(d.getType())) {
                continue;
            }
            String name = d.getName() == null ? "" : d.getName();
            if (filter != null && !name.toLowerCase().contains(filter.toLowerCase())) {
                continue;
            }
            JsonObject o = Common.diagramJson(d);
            o.addProperty("shapes", d.toShapeUIModelArray().length);
            o.addProperty("connectors", d.toConnectorUIModelArray().length);
            o.addProperty("opened", d.isOpened());
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("project", p.getName());
        out.addProperty("project_file", p.getProjectFile() == null ? null : p.getProjectFile().getAbsolutePath());
        out.addProperty("count", arr.size());
        out.add("diagrams", arr);
        if (Args.bool(args, "include_vocabulary", false)) {
            out.add("diagram_types", Common.toArray(new java.util.ArrayList<>(Kinds.diagramTypeNames())));
            out.add("node_kinds", Common.toArray(new java.util.ArrayList<>(Kinds.nodeKindNames())));
            out.add("edge_kinds", Common.toArray(new java.util.ArrayList<>(Kinds.edgeKindNames())));
        }
        return out;
    }
}

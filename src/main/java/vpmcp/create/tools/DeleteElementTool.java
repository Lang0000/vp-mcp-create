package vpmcp.create.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.Vp;

import java.util.ArrayList;
import java.util.List;

public final class DeleteElementTool implements Tool {

    /** Model types that exist only as part of one diagram. */
    private static final java.util.Set<String> DIAGRAM_ONLY = new java.util.HashSet<>(java.util.Arrays.asList(
            "NOTE", "Anchor", "BPTextAnnotation", "Comment"));

    @Override
    public String name() {
        return "delete_element";
    }

    @Override
    public String description() {
        return "Remove elements from a diagram. By default only the view (shape or connector) is removed and the "
                + "model element stays in the project (notes and note links are always deleted completely); with "
                + "from_model=true the model element is deleted too, which removes it from every diagram. Also deletes a whole diagram when 'ids' is omitted and "
                + "delete_diagram=true.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject s = Schemas.parse("{\"type\":\"object\",\"properties\":{"
                + "\"diagram\":{\"type\":\"string\",\"description\":\"Diagram URL, id or name.\"},"
                + "\"ids\":{\"description\":\"One id or a list: shape/connector ids or model ids.\","
                + "\"anyOf\":[{\"type\":\"string\"},{\"type\":\"array\",\"items\":{\"type\":\"string\"}}]},"
                + "\"from_model\":{\"type\":\"boolean\",\"default\":false},"
                + "\"delete_diagram\":{\"type\":\"boolean\",\"default\":false,\"description\":\"Delete the "
                + "diagram itself (its model elements stay unless from_model is true).\"}"
                + "},\"additionalProperties\":false}");
        s.add("required", Schemas.required("diagram"));
        return s;
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public JsonObject execute(JsonObject args) throws Exception {
        IDiagramUIModel d = Vp.findDiagram(Args.str(args, "diagram", null));
        boolean fromModel = Args.bool(args, "from_model", false);
        List<String> ids = Args.strings(args, "ids");
        JsonObject out = new JsonObject();
        if (ids.isEmpty()) {
            if (!Args.bool(args, "delete_diagram", false)) {
                throw new ToolException("Give \"ids\" to delete, or delete_diagram=true to delete the diagram.");
            }
            String name = d.getName();
            if (fromModel) {
                for (IDiagramElement e : d.toDiagramElementArray()) {
                    IModelElement m = e.getModelElement();
                    if (m != null && Vp.requireProject().getModelElementById(m.getId()) != null) {
                        m.delete();
                    }
                }
            }
            d.delete();
            out.addProperty("deleted_diagram", name);
            return out;
        }
        List<IDiagramElement> targets = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            IDiagramElement e = Vp.findOnDiagram(d, id);
            if (e == null) {
                missing.add(id);
            } else {
                targets.add(e);
            }
        }
        if (!missing.isEmpty()) {
            throw new ToolException("Not on diagram \"" + d.getName() + "\": " + missing + ". Nothing was deleted.");
        }
        JsonArray done = new JsonArray();
        for (IDiagramElement e : targets) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.getId());
            IModelElement m = e.getModelElement();
            o.addProperty("model_id", m == null ? null : m.getId());
            o.addProperty("name", m == null ? null : m.getName());
            if (m == null) {
                d.removeDiagramElement(e);
                o.addProperty("deleted", "view");
            } else if (fromModel || DIAGRAM_ONLY.contains(m.getModelType())) {
                // notes live only on their diagram; deleteViewOnly() on them opens a modal confirmation in VP
                m.delete();
                o.addProperty("deleted", "view and model");
            } else {
                e.deleteViewOnly();
                o.addProperty("deleted", "view");
            }
            done.add(o);
        }
        out.add("deleted", done);
        return out;
    }
}

package vpmcp.create.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramUIModel;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;
import vpmcp.create.layout.LayeredLayout;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.DiagramBuilder;
import vpmcp.create.vp.DiagramLayouter;
import vpmcp.create.vp.Kinds;
import vpmcp.create.vp.Vp;

public final class AddElementsTool implements Tool {

    @Override
    public String name() {
        return "add_elements";
    }

    @Override
    public String description() {
        return "Add nodes and edges to an existing diagram. Edges may connect new nodes (by their request id) and "
                + "elements already on the diagram (by VP shape id or model id, as returned by create_diagram or "
                + "get_diagram_by_url). A node's parent may be an existing container. Layout 'append' (default) "
                + "places only the new nodes next to the existing content; 'auto' re-lays out the whole diagram.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject s = Schemas.parse("{\"type\":\"object\",\"properties\":{"
                + "\"diagram\":{\"type\":\"string\",\"description\":\"Diagram URL, id or exact name.\"},"
                + "\"nodes\":{\"type\":\"array\",\"items\":" + Schemas.node(Schemas.NODE_KINDS) + "},"
                + "\"edges\":{\"type\":\"array\",\"items\":" + Schemas.edge(Schemas.EDGE_KINDS) + "},"
                + "\"layout\":{\"type\":\"string\",\"description\":\"append (default), auto/layered (whole "
                + "diagram), none, grid, or a VP layout name (see layout_diagram).\"},"
                + "\"direction\":" + Schemas.DIRECTION
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
        Kinds.Family fam = Kinds.familyOf(d.getType());
        String layout = Args.str(args, "layout", "append");
        Common.validateLayout(layout);
        JsonArray nodes = Args.array(args, "nodes");
        JsonArray edges = Args.array(args, "edges");
        if (nodes.size() == 0 && edges.size() == 0) {
            throw new ToolException("Nothing to add: give \"nodes\" and/or \"edges\".");
        }
        DiagramBuilder b = new DiagramBuilder(d, fam);
        b.validate(nodes, edges);
        LayeredLayout.Direction dir = Common.direction(Args.str(args, "direction", null), b.direction());
        try {
            b.build();
            Common.open(d, b.warnings());
            b.fitShapes();
        } catch (Exception e) {
            b.rollback();
            throw e;
        }
        String l = Args.norm(layout);
        String used;
        if (l.equals("auto") || l.equals("layered")) {
            b.layoutNew(dir, true);
            DiagramLayouter.layered(d, dir, b.warnings());
            used = "layered (whole diagram)";
        } else {
            used = Common.applyLayout(d, b, layout, dir, b.warnings());
        }
        DiagramBuilder.finish(d, dir, b.warnings());
        JsonObject out = new JsonObject();
        out.add("diagram", Common.diagramJson(d));
        out.add("nodes", b.nodesJson());
        out.add("edges", b.edgesJson());
        out.addProperty("layout", used);
        out.add("warnings", Common.toArray(b.warnings()));
        return out;
    }
}

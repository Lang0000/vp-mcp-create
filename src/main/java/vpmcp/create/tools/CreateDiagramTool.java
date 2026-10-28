package vpmcp.create.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramUIModel;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;
import vpmcp.create.layout.LayeredLayout;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.DiagramBuilder;
import vpmcp.create.vp.Kinds;
import vpmcp.create.vp.Vp;

public final class CreateDiagramTool implements Tool {

    @Override
    public String name() {
        return "create_diagram";
    }

    @Override
    public String description() {
        return "Create a new diagram in the open Visual Paradigm project from a description of its nodes and edges, "
                + "lay it out and open it. Returns the diagram id and URL (readable with get_diagram_by_url) and, "
                + "for every node and edge id of the request, the Visual Paradigm model and shape ids. Diagram "
                + "types: class, usecase, activity, deployment, sequence, component, bpmn, flowchart (block "
                + "scheme), dfd/context, state, and others (package, object, communication, er, ...). The request "
                + "is validated first; on any error nothing is created. To undo a whole call, delete the diagram with "
                + "delete_element (delete_diagram=true, from_model=true): VP's Undo goes back one element at a time.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject s = Schemas.parse("{\"type\":\"object\",\"properties\":{"
                + "\"type\":{\"type\":\"string\",\"description\":\"Diagram type, e.g. class, usecase, activity, "
                + "deployment, sequence, component, bpmn, flowchart, dfd, state.\"},"
                + "\"name\":{\"type\":\"string\"},"
                + "\"documentation\":{\"type\":\"string\"},"
                + "\"nodes\":{\"type\":\"array\",\"items\":" + Schemas.node(Schemas.NODE_KINDS) + "},"
                + "\"edges\":{\"type\":\"array\",\"items\":" + Schemas.edge(Schemas.EDGE_KINDS) + "},"
                + "\"layout\":" + Schemas.LAYOUT + ","
                + "\"direction\":" + Schemas.DIRECTION + ","
                + "\"connector_style\":" + Schemas.CONNECTOR_STYLE
                + "},\"additionalProperties\":false}");
        s.add("required", Schemas.required("type", "name"));
        return s;
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public JsonObject execute(JsonObject args) throws Exception {
        Vp.requireProject();
        Kinds.DiagramType type = Kinds.diagramType(Args.str(args, "type", null));
        String name = Args.reqStr(args, "name");
        String layout = Args.str(args, "layout", "auto");
        Common.validateLayout(layout);
        String cs = Args.str(args, "connector_style", Common.defaultConnectorStyle(type.family));
        int csCode = Common.connectorStyle(cs);
        JsonArray nodes = Args.array(args, "nodes");
        JsonArray edges = Args.array(args, "edges");

        DiagramBuilder b = new DiagramBuilder(null, type.family);
        b.validate(nodes, edges);
        LayeredLayout.Direction dir = Common.direction(Args.str(args, "direction", null), b.direction());

        IDiagramUIModel d;
        try {
            d = Vp.dm().createDiagram(type.vpType);
        } catch (UnsupportedOperationException e) {
            throw new ToolException(type.vpType + " is not available in this Visual Paradigm edition ("
                    + Vp.app().getProductInfo().getName() + "). Community Edition has no BPMN; use activity or "
                    + "flowchart instead.");
        }
        if (d == null) {
            throw new ToolException("Visual Paradigm refused to create a " + type.vpType
                    + " (not available in this edition?).");
        }
        d.setName(name);
        String doc = Args.str(args, "documentation", null);
        if (doc != null) {
            d.setDocumentation(doc);
        }
        try {
            d.setConnectorStyle(csCode);
        } catch (Throwable t) {
            b.warnings().add("connector style not applied: " + t);
        }
        b.setDiagram(d);
        String used;
        try {
            b.build();
            Common.open(d, b.warnings()); // VP fits shapes to their text only on an opened diagram
            b.fitShapes();
            used = Common.applyLayout(d, b, layout, dir, b.warnings());
        } catch (Exception e) {
            b.rollback();
            try {
                d.delete();
            } catch (Throwable ignored) {
                // nothing more to undo
            }
            throw e;
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

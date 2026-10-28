package vpmcp.create.tools;

import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramUIModel;
import vpmcp.create.core.Tool;
import vpmcp.create.layout.LayeredLayout;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.DiagramBuilder;
import vpmcp.create.vp.DiagramLayouter;
import vpmcp.create.vp.Kinds;
import vpmcp.create.vp.Vp;

import java.util.ArrayList;
import java.util.List;

public final class LayoutDiagramTool implements Tool {

    @Override
    public String name() {
        return "layout_diagram";
    }

    @Override
    public String description() {
        return "Automatically lay out a whole diagram. 'layered' (default) is the plugin's own layered layout: it "
                + "keeps nodes from overlapping, ranks them along the flow (parents above children for "
                + "generalization), sizes containers to their content and arranges activity partitions as columns. "
                + "The other algorithms are Visual Paradigm's own. Optionally changes the connector style.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject s = Schemas.parse("{\"type\":\"object\",\"properties\":{"
                + "\"diagram\":{\"type\":\"string\",\"description\":\"Diagram URL, id or name.\"},"
                + "\"algorithm\":{\"type\":\"string\",\"enum\":" + enumOf(DiagramLayouter.algorithms()) + "},"
                + "\"direction\":" + Schemas.DIRECTION + ","
                + "\"connector_style\":" + Schemas.CONNECTOR_STYLE
                + "},\"additionalProperties\":false}");
        s.add("required", Schemas.required("diagram"));
        return s;
    }

    private static String enumOf(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(l.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public JsonObject execute(JsonObject args) throws Exception {
        IDiagramUIModel d = Vp.findDiagram(Args.str(args, "diagram", null));
        Kinds.Family fam = Kinds.familyOf(d.getType());
        String algo = Args.norm(Args.str(args, "algorithm", "layered"));
        List<String> warnings = new ArrayList<>();
        LayeredLayout.Direction def = fam == Kinds.Family.USECASE || fam == Kinds.Family.DEPLOYMENT
                || fam == Kinds.Family.BPMN || fam == Kinds.Family.DFD || fam == Kinds.Family.COMPONENT
                ? LayeredLayout.Direction.LR : LayeredLayout.Direction.TB;
        LayeredLayout.Direction dir = Common.direction(Args.str(args, "direction", null), def);
        String cs = Args.str(args, "connector_style", null);
        if (cs != null) {
            d.setConnectorStyle(Common.connectorStyle(cs));
        }
        switch (algo) {
            case "layered":
            case "auto":
                DiagramLayouter.layered(d, dir, warnings);
                algo = "layered";
                break;
            case "grid":
                DiagramLayouter.grid(d);
                break;
            default:
                DiagramLayouter.vpLayout(d, algo);
        }
        DiagramBuilder.finish(d, dir, warnings);
        JsonObject out = new JsonObject();
        out.add("diagram", Common.diagramJson(d));
        out.addProperty("algorithm", algo);
        out.addProperty("direction", dir.name());
        out.addProperty("shapes", d.toShapeUIModelArray().length);
        out.add("warnings", Common.toArray(warnings));
        return out;
    }
}

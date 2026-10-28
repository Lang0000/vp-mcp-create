package vpmcp.create.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import vpmcp.create.core.ToolException;
import vpmcp.create.layout.LayeredLayout;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.DiagramBuilder;
import vpmcp.create.vp.DiagramLayouter;
import vpmcp.create.vp.Kinds;
import vpmcp.create.vp.Vp;

import java.util.List;

/** Steps shared by create_diagram, add_elements and layout_diagram. */
final class Common {

    private Common() {
    }

    static LayeredLayout.Direction direction(String s, LayeredLayout.Direction def) throws ToolException {
        if (s == null) {
            return def;
        }
        switch (s.trim().toUpperCase()) {
            case "TB":
            case "TD":
            case "DOWN":
            case "VERTICAL":
                return LayeredLayout.Direction.TB;
            case "LR":
            case "RIGHT":
            case "HORIZONTAL":
                return LayeredLayout.Direction.LR;
            default:
                throw new ToolException("\"direction\" must be TB or LR.");
        }
    }

    static int connectorStyle(String s) throws ToolException {
        switch (Args.norm(s)) {
            case "straight":
            case "oblique":
                return IConnectorUIModel.CS_OBLIQUE;
            case "rectilinear":
            case "orthogonal":
                return IConnectorUIModel.CS_RECTI_LINEAR;
            case "curved":
            case "curve":
                return IConnectorUIModel.CS_CURVE;
            case "round_straight":
            case "round_oblique":
                return IConnectorUIModel.CS_ROUND_OBLIQUE;
            case "round_rectilinear":
                return IConnectorUIModel.CS_ROUND_RECTILINEAR;
            default:
                throw new ToolException("Unknown connector_style \"" + s + "\".");
        }
    }

    static String defaultConnectorStyle(Kinds.Family f) {
        switch (f) {
            case ACTIVITY:
            case FLOWCHART:
            case BPMN:
                return "rectilinear";
            default:
                return "straight";
        }
    }

    static void validateLayout(String layout) throws ToolException {
        String l = Args.norm(layout);
        if (l.equals("layered") || l.equals("auto") || l.equals("none") || l.equals("grid") || l.equals("append")
                || DiagramLayouter.isVpLayout(l)) {
            return;
        }
        throw new ToolException("Unknown layout \"" + layout + "\". Known: auto, append, none, "
                + DiagramLayouter.algorithms());
    }

    /**
     * Applies the requested layout after the builder created its elements. Returns the layout actually used.
     * "append" lays out only the new nodes and puts them next to the existing content.
     */
    static String applyLayout(IDiagramUIModel d, DiagramBuilder b, String layout, LayeredLayout.Direction dir,
                              List<String> warnings) throws ToolException {
        String l = Args.norm(layout);
        switch (l) {
            case "none":
                return "none";
            case "append":
                b.layoutNew(dir, true);
                return "append";
            case "auto":
            case "layered":
                b.layoutNew(dir, false);
                return "layered";
            case "grid":
                DiagramLayouter.grid(d);
                return "grid";
            default:
                b.layoutNew(dir, false); // sensible positions first, in case the VP layout does nothing
                try {
                    DiagramLayouter.vpLayout(d, l);
                    return l;
                } catch (ToolException e) {
                    warnings.add(e.getMessage() + " Kept the layered layout.");
                    return "layered";
                }
        }
    }

    static JsonObject diagramJson(IDiagramUIModel d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.getId());
        o.addProperty("name", d.getName());
        o.addProperty("type", d.getType());
        o.addProperty("url", Vp.diagramUrl(d));
        return o;
    }

    static JsonArray toArray(List<String> l) {
        JsonArray a = new JsonArray();
        for (String s : l) {
            a.add(s);
        }
        return a;
    }

    static void open(IDiagramUIModel d, List<String> warnings) {
        try {
            Vp.dm().openDiagram(d);
        } catch (Throwable t) {
            warnings.add("diagram not opened: " + t);
        }
    }
}

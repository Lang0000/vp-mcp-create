package vpmcp.create.vp;

import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IActivityPartition;
import com.vp.plugin.model.IActivitySwimlane2;
import com.vp.plugin.model.IModelElement;
import vpmcp.create.core.ToolException;
import vpmcp.create.layout.LayeredLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lays out a whole existing diagram, with the plugin's layered algorithm or one of Visual Paradigm's own. */
public final class DiagramLayouter {

    private static final Map<String, Integer> VP_LAYOUTS = new LinkedHashMap<>();

    static {
        VP_LAYOUTS.put("vp_auto", DiagramManager.LAYOUT_AUTO);
        VP_LAYOUTS.put("hierarchic", DiagramManager.LAYOUT_HIERARCHIC);
        VP_LAYOUTS.put("orthogonal", DiagramManager.LAYOUT_ORTHOGONAL);
        VP_LAYOUTS.put("organic", DiagramManager.LAYOUT_ORGANIC);
        VP_LAYOUTS.put("smart_organic", DiagramManager.LAYOUT_SMART_ORGANIC_LAYOUT);
        VP_LAYOUTS.put("tree", DiagramManager.LAYOUT_TREE_STYLE);
        VP_LAYOUTS.put("directed_tree", DiagramManager.LAYOUT_DIRECTED_TREE);
        VP_LAYOUTS.put("balloon_tree", DiagramManager.LAYOUT_BALLOON_TREE);
        VP_LAYOUTS.put("compact_tree", DiagramManager.LAYOUT_COMPACT_TREE);
        VP_LAYOUTS.put("circular", DiagramManager.LAYOUT_CIRCULAR);
        VP_LAYOUTS.put("compact", DiagramManager.LAYOUT_COMPACT);
        VP_LAYOUTS.put("route_orthogonal", DiagramManager.LAYOUT_ROUTE_CONNECTORS_ORTHOGONAL);
        VP_LAYOUTS.put("route_organic", DiagramManager.LAYOUT_ROUTE_CONNECTORS_ORGANIC);
    }

    private static final Set<String> SKIP_SHAPES = new HashSet<>(Arrays.asList(
            "ActivitySwimlane2Compartment", "ActivityPartitionHeader"));

    private DiagramLayouter() {
    }

    public static List<String> algorithms() {
        List<String> out = new ArrayList<>(Arrays.asList("layered", "grid"));
        out.addAll(VP_LAYOUTS.keySet());
        return out;
    }

    public static boolean isVpLayout(String algo) {
        return VP_LAYOUTS.containsKey(Args.norm(algo).replaceFirst("^vp_(?!auto)", ""));
    }

    /** Runs one of Visual Paradigm's layout algorithms. */
    public static void vpLayout(IDiagramUIModel d, String algo) throws ToolException {
        String key = Args.norm(algo).replaceFirst("^vp_(?!auto)", "");
        Integer code = VP_LAYOUTS.get(key);
        if (code == null) {
            throw new ToolException("Unknown layout \"" + algo + "\". Known: " + algorithms());
        }
        try {
            Vp.dm().layout(d, code);
        } catch (Throwable t) {
            throw new ToolException("Visual Paradigm layout \"" + key + "\" failed (it may be unavailable in "
                    + "Community Edition): " + t, t);
        }
    }

    /** Lays out every shape of the diagram with the plugin's layered algorithm. */
    public static void layered(IDiagramUIModel d, LayeredLayout.Direction dir, List<String> warnings) {
        Map<IShapeUIModel, LayeredLayout.Node> map = new LinkedHashMap<>();
        IShapeUIModel[] shapes = d.toShapeUIModelArray();
        Map<IModelElement, IShapeUIModel> byModel = new HashMap<>();
        for (IShapeUIModel s : shapes) {
            if (SKIP_SHAPES.contains(s.getShapeType())) {
                continue;
            }
            LayeredLayout.Node n = new LayeredLayout.Node(s.getId(), Math.max(10, s.getWidth()), Math.max(10, s.getHeight()));
            if (s.childrenCount() > 0) {
                n.header = 34;
                n.padding = 20;
                n.w = 0; // containers are resized to fit their children
                n.h = 0;
            }
            map.put(s, n);
            if (s.getModelElement() != null) {
                byModel.put(s.getModelElement(), s);
            }
        }
        List<LayeredLayout.Node> roots = new ArrayList<>();
        Map<IShapeUIModel, Map<IActivityPartition, LayeredLayout.Node>> swimlanes = new HashMap<>();
        for (IShapeUIModel s : map.keySet()) {
            IModelElement m = s.getModelElement();
            if (m instanceof IActivitySwimlane2) {
                LayeredLayout.Node owner = map.get(s);
                owner.header = 0;
                owner.padding = 0;
                Map<IActivityPartition, LayeredLayout.Node> lanes = new LinkedHashMap<>();
                for (IActivityPartition p : ((IActivitySwimlane2) m).toVerticalPartitionArray()) {
                    LayeredLayout.Node lane = new LayeredLayout.Node(p.getId(), 0, 0);
                    lane.lane = true;
                    lane.header = 28;
                    lane.padding = 0;
                    owner.add(lane);
                    lanes.put(p, lane);
                }
                swimlanes.put(s, lanes);
            }
        }
        Map<String, LayeredLayout.Node> shapeById = new HashMap<>();
        for (Map.Entry<IShapeUIModel, LayeredLayout.Node> en : map.entrySet()) {
            shapeById.put(en.getKey().getId(), en.getValue());
        }
        Set<String> swimlaneIds = new HashSet<>();
        for (IShapeUIModel sw : swimlanes.keySet()) {
            swimlaneIds.add(sw.getId());
        }
        for (Map.Entry<IShapeUIModel, LayeredLayout.Node> en : map.entrySet()) {
            IShapeUIModel s = en.getKey();
            LayeredLayout.Node n = en.getValue();
            LayeredLayout.Node lane = laneOf(s, swimlanes);
            IDiagramElement p = s.getParent();
            if (p instanceof IShapeUIModel && SKIP_SHAPES.contains(((IShapeUIModel) p).getShapeType())) {
                p = ((IShapeUIModel) p).getParent();
            }
            if (lane != null) {
                lane.add(n);
            } else if (p instanceof IShapeUIModel && shapeById.containsKey(p.getId())
                    && !swimlaneIds.contains(p.getId())) {
                shapeById.get(p.getId()).add(n);
            } else {
                roots.add(n);
            }
        }
        // a swimlane whose partitions are all empty is laid out like a plain box
        for (Map.Entry<IShapeUIModel, Map<IActivityPartition, LayeredLayout.Node>> en : swimlanes.entrySet()) {
            boolean any = false;
            for (LayeredLayout.Node l : en.getValue().values()) {
                any |= !l.children.isEmpty();
            }
            if (!any) {
                LayeredLayout.Node owner = map.get(en.getKey());
                owner.children.clear();
                owner.w = Math.max(200, en.getKey().getWidth());
                owner.h = Math.max(200, en.getKey().getHeight());
            }
        }
        // connectors hand out other wrapper objects for the same shapes, so match by id
        Map<String, LayeredLayout.Node> byId = new HashMap<>();
        for (Map.Entry<IShapeUIModel, LayeredLayout.Node> en : map.entrySet()) {
            byId.put(en.getKey().getId(), en.getValue());
        }
        List<LayeredLayout.Edge> edges = new ArrayList<>();
        for (IConnectorUIModel c : d.toConnectorUIModelArray()) {
            LayeredLayout.Node a = c.getFromShape() == null ? null : byId.get(c.getFromShape().getId());
            LayeredLayout.Node b = c.getToShape() == null ? null : byId.get(c.getToShape().getId());
            if (a == null || b == null) {
                continue;
            }
            // generalizations run from the general element in VP's model, so "from" ranks above "to" for all kinds
            edges.add(new LayeredLayout.Edge(a, b));
        }
        new LayeredLayout().run(roots, edges, dir);
        for (Map.Entry<IShapeUIModel, LayeredLayout.Node> en : map.entrySet()) {
            LayeredLayout.Node n = en.getValue();
            en.getKey().setBounds(n.x, n.y, n.w, n.h);
        }
        for (Map.Entry<IShapeUIModel, Map<IActivityPartition, LayeredLayout.Node>> en : swimlanes.entrySet()) {
            Map<String, java.awt.Rectangle> lanes = new HashMap<>();
            for (Map.Entry<IActivityPartition, LayeredLayout.Node> l : en.getValue().entrySet()) {
                LayeredLayout.Node n = l.getValue();
                lanes.put(l.getKey().getId(), new java.awt.Rectangle(n.x, n.y, n.w, n.h));
            }
            Swimlanes.arrange(en.getKey(), lanes);
        }
    }

    private static LayeredLayout.Node laneOf(IShapeUIModel s,
                                             Map<IShapeUIModel, Map<IActivityPartition, LayeredLayout.Node>> swimlanes) {
        IModelElement m = s.getModelElement();
        if (m == null) {
            return null;
        }
        for (Map<IActivityPartition, LayeredLayout.Node> lanes : swimlanes.values()) {
            for (Map.Entry<IActivityPartition, LayeredLayout.Node> l : lanes.entrySet()) {
                for (IModelElement c : l.getKey().toContainedElementArray()) {
                    if (c == m || c.getId().equals(m.getId())) {
                        return l.getValue();
                    }
                }
            }
        }
        return null;
    }

    /** Simple grid: top-level shapes in rows, in diagram order. */
    public static void grid(IDiagramUIModel d) {
        IShapeUIModel[] shapes = d.toShapeUIModelArray();
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(shapes.length)));
        int x = 40;
        int y = 40;
        int rowH = 0;
        int col = 0;
        for (IShapeUIModel s : shapes) {
            if (s.getParent() instanceof IShapeUIModel || SKIP_SHAPES.contains(s.getShapeType())) {
                continue;
            }
            int dx = s.getX();
            int dy = s.getY();
            s.setLocation(x, y);
            shiftChildren(s, x - dx, y - dy);
            rowH = Math.max(rowH, s.getHeight());
            x += s.getWidth() + 50;
            if (++col == cols) {
                col = 0;
                x = 40;
                y += rowH + 50;
                rowH = 0;
            }
        }
    }

    private static void shiftChildren(IShapeUIModel s, int dx, int dy) {
        for (IShapeUIModel c : s.toChildArray()) {
            c.setLocation(c.getX() + dx, c.getY() + dy);
            shiftChildren(c, dx, dy);
        }
    }
}

package vpmcp.create.vp;

import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import vpmcp.create.layout.LayeredLayout;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Orthogonal routes for rectilinear diagrams (activity, flowchart). Visual Paradigm's own route for a connector
 * created through the API starts at the left end of the overlap of the two shapes, which misses diamonds and
 * parallelograms; these routes run between the middles of the facing sides instead. Edges against the flow go
 * around the right of both shapes.
 */
public final class Router {

    private static final int AROUND = 30;

    private Router() {
    }

    public static boolean isRectilinear(IDiagramUIModel d) {
        int s = d.getConnectorStyle();
        return s == IConnectorUIModel.CS_RECTI_LINEAR || s == IConnectorUIModel.CS_ROUND_RECTILINEAR;
    }

    /** Shapes whose bounding box corners are empty: connectors must meet them at the middle of a side. */
    private static final java.util.Set<String> POINTY = new java.util.HashSet<>(java.util.Arrays.asList(
            "DecisionNode", "MergeNode", "InitialNode", "ActivityFinalNode", "FlowFinalNode", "FlowchartDecision",
            "FlowchartMerge", "FlowchartData", "FlowchartTerminator", "FlowchartPreparation", "FlowchartDisplay",
            "FlowchartManualInput", "FlowchartOr", "FlowchartSummingJunction", "FlowchartOnPageReference",
            "ObjectNode", "InitialPseudoState", "FinalState"));

    private static final java.util.Set<String> DIAMONDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "DecisionNode", "MergeNode", "FlowchartDecision", "FlowchartMerge"));

    public static void route(IDiagramUIModel d, LayeredLayout.Direction dir, List<String> warnings) {
        boolean tb = dir == LayeredLayout.Direction.TB;
        List<Rectangle> obstacles = new ArrayList<>();
        for (IShapeUIModel s : d.toShapeUIModelArray()) {
            if (s.childrenCount() == 0) {
                obstacles.add(tb ? bounds(s) : transpose(bounds(s)));
            }
        }
        for (IConnectorUIModel c : d.toConnectorUIModelArray()) {
            IShapeUIModel a = c.getFromShape();
            IShapeUIModel b = c.getToShape();
            if (a == null || b == null || a == b) {
                continue;
            }
            Rectangle f = tb ? bounds(a) : transpose(bounds(a));
            Rectangle t = tb ? bounds(b) : transpose(bounds(b));
            List<Point> pts = routeTB(f, t, POINTY.contains(a.getShapeType()), POINTY.contains(b.getShapeType()),
                    DIAMONDS.contains(a.getShapeType()), obstacles);
            if (!tb) {
                pts = transpose(pts);
            }
            try {
                c.clearPoints();
                for (Point p : pts) {
                    c.addPoint(p);
                }
            } catch (Throwable t2) {
                if (warnings != null) {
                    warnings.add("connector " + c.getId() + " not routed: " + t2);
                }
            }
        }
    }

    private static Rectangle bounds(IShapeUIModel s) {
        return new Rectangle(s.getX(), s.getY(), s.getWidth(), s.getHeight());
    }

    /** Route for a top-to-bottom flow. */
    static List<Point> routeTB(Rectangle f, Rectangle t, boolean fromPointy, boolean toPointy, boolean fromDiamond,
                               List<Rectangle> obstacles) {
        List<Point> p = new ArrayList<>();
        int fcx = (int) f.getCenterX();
        int tcx = (int) t.getCenterX();
        int fcy = (int) f.getCenterY();
        int tcy = (int) t.getCenterY();
        if (t.y >= f.y + f.height) { // downwards
            // x ranges where each end may be met: the middle only for pointy shapes, anywhere inside otherwise
            int fLo = fromPointy ? fcx : f.x + 6;
            int fHi = fromPointy ? fcx : f.x + f.width - 6;
            int tLo = toPointy ? tcx : t.x + 6;
            int tHi = toPointy ? tcx : t.x + t.width - 6;
            int lo = Math.max(fLo, tLo);
            int hi = Math.min(fHi, tHi);
            if (fromPointy && toPointy && Math.abs(fcx - tcx) <= 6) {
                lo = tcx; // centres differ only by rounding
                hi = tcx;
            }
            if (fromDiamond && Math.abs(tcx - fcx) > f.width / 4 && (lo > hi || tcx < f.x || tcx > f.x + f.width
                    || Math.abs(tcx - fcx) > f.width / 3)) {
                // a branch of a decision leaves from the left or right corner
                p.add(new Point(tcx < fcx ? f.x : f.x + f.width, fcy));
                p.add(new Point(tcx, fcy));
                p.add(new Point(tcx, t.y));
            } else if (lo <= hi) {
                int x = tcx >= lo && tcx <= hi ? tcx : fcx >= lo && fcx <= hi ? fcx : (lo + hi) / 2;
                p.add(new Point(x, f.y + f.height));
                p.add(new Point(x, t.y));
            } else {
                int midY = (f.y + f.height + t.y) / 2;
                p.add(new Point(fcx, f.y + f.height));
                p.add(new Point(fcx, midY));
                p.add(new Point(tcx, midY));
                p.add(new Point(tcx, t.y));
            }
        } else if (f.y >= t.y + t.height && Math.abs(fcx - tcx) > (f.width + t.width) / 2 + 10) {
            // upwards but side by side: leave from the side, enter from below
            boolean right = tcx > fcx;
            p.add(new Point(right ? f.x + f.width : f.x, fcy));
            p.add(new Point(tcx, fcy));
            p.add(new Point(tcx, t.y + t.height));
        } else if (t.y + t.height <= f.y || t.y < f.y) { // back edge: around the right of everything in between
            int top = Math.min(t.y, f.y);
            int bottom = Math.max(t.y + t.height, f.y + f.height);
            int x = Math.max(f.x + f.width, t.x + t.width);
            int left = Math.min(f.x, t.x);
            for (Rectangle o : obstacles) {
                boolean between = o.y < bottom && o.y + o.height > top && o.x + o.width > left;
                if (between && o.x < x + AROUND) {
                    x = Math.max(x, o.x + o.width);
                }
            }
            x += AROUND;
            int xl = Math.min(f.x, t.x);
            for (Rectangle o : obstacles) {
                boolean between = o.y < bottom && o.y + o.height > top && o.x < Math.max(f.x + f.width, t.x + t.width);
                if (between && o.x + o.width > xl - AROUND) {
                    xl = Math.min(xl, o.x);
                }
            }
            xl -= AROUND;
            boolean rightClear = clear(f.x + f.width, x, fcy, f, t, obstacles) && clear(t.x + t.width, x, tcy, f, t,
                    obstacles);
            boolean leftClear = clear(xl, f.x, fcy, f, t, obstacles) && clear(xl, t.x, tcy, f, t, obstacles);
            if (rightClear || !leftClear) {
                p.add(new Point(f.x + f.width, fcy));
                p.add(new Point(x, fcy));
                p.add(new Point(x, tcy));
                p.add(new Point(t.x + t.width, tcy));
            } else {
                p.add(new Point(f.x, fcy));
                p.add(new Point(xl, fcy));
                p.add(new Point(xl, tcy));
                p.add(new Point(t.x, tcy));
            }
        } else { // same rank: side to side
            boolean right = tcx > fcx;
            p.add(new Point(right ? f.x + f.width : f.x, fcy));
            if (fcy != tcy) {
                int midX = right ? (f.x + f.width + t.x) / 2 : (t.x + t.width + f.x) / 2;
                p.add(new Point(midX, fcy));
                p.add(new Point(midX, tcy));
            }
            p.add(new Point(right ? t.x : t.x + t.width, tcy));
        }
        return p;
    }

    /** True when the horizontal segment y, x1..x2 crosses no shape other than the two ends. */
    private static boolean clear(int x1, int x2, int y, Rectangle f, Rectangle t, List<Rectangle> obstacles) {
        int a = Math.min(x1, x2);
        int b = Math.max(x1, x2);
        for (Rectangle o : obstacles) {
            if (o.equals(f) || o.equals(t)) {
                continue;
            }
            if (y > o.y && y < o.y + o.height && a < o.x + o.width && b > o.x) {
                return false;
            }
        }
        return true;
    }

    private static Rectangle transpose(Rectangle r) {
        return new Rectangle(r.y, r.x, r.height, r.width);
    }

    private static List<Point> transpose(List<Point> pts) {
        List<Point> out = new ArrayList<>();
        for (Point p : pts) {
            out.add(new Point(p.y, p.x));
        }
        return out;
    }
}

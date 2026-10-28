package vpmcp.create.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Layered (Sugiyama-style) layout that knows about containers and swimlanes and has no dependency on Visual
 * Paradigm, so it can be unit-tested. Every container lays out its own children first (bottom-up), then is treated
 * as one block by its parent. Lanes ({@link Node#lane}) are laid out as parallel columns (top-to-bottom flow) or rows
 * (left-to-right flow) that share one global ranking, as activity partitions and BPMN lanes need.
 */
public final class LayeredLayout {

    public enum Direction { TB, LR }

    /** A box to place. Coordinates are absolute once {@link #run} returns. */
    public static final class Node {
        public final String id;
        public int w;
        public int h;
        public int x;
        public int y;
        public Node parent;
        public final List<Node> children = new ArrayList<>();
        /** Space reserved inside a container above (TB) or left of (LR) its children, e.g. for the caption. */
        public int header = 30;
        public int padding = 20;
        /** This node is a lane: its siblings that are lanes too share one ranking with it. */
        public boolean lane;
        /** Preferred layout direction inside this container; null means inherit. */
        public Direction direction;
        boolean dummy;

        public Node(String id, int w, int h) {
            this.id = id;
            this.w = w;
            this.h = h;
        }

        public boolean isContainer() {
            return !children.isEmpty();
        }

        public void add(Node child) {
            child.parent = this;
            children.add(child);
        }
    }

    public static final class Edge {
        public final Node from;
        public final Node to;

        public Edge(Node from, Node to) {
            this.from = from;
            this.to = to;
        }
    }

    private final int gapMajor;
    private final int gapMinor;
    private final int margin;

    public LayeredLayout() {
        this(70, 40, 40);
    }

    /** gapMajor is the distance between layers, gapMinor the distance between neighbours in one layer. */
    public LayeredLayout(int gapMajor, int gapMinor, int margin) {
        this.gapMajor = gapMajor;
        this.gapMinor = gapMinor;
        this.margin = margin;
    }

    /** Lays out the given top-level nodes (each may have children) and moves everything to absolute positions. */
    public void run(List<Node> roots, List<Edge> edges, Direction dir) {
        Node root = new Node("<root>", 0, 0);
        root.header = 0;
        root.padding = 0;
        for (Node n : roots) {
            root.children.add(n);
        }
        layoutContainer(root, edges, dir);
        // children positions are relative to their container; make them absolute.
        for (Node n : roots) {
            translate(n, margin, margin);
        }
    }

    private void translate(Node n, int dx, int dy) {
        n.x += dx;
        n.y += dy;
        for (Node c : n.children) {
            translate(c, n.x, n.y);
        }
    }

    /** Lays out c's children relative to c's top-left and sets c's size to fit them. */
    private void layoutContainer(Node c, List<Edge> edges, Direction inherited) {
        Direction dir = c.direction != null ? c.direction : inherited;
        for (Node ch : c.children) {
            if (ch.isContainer()) {
                layoutContainer(ch, edges, dir);
            }
        }
        boolean lanes = !c.children.isEmpty();
        for (Node ch : c.children) {
            lanes &= ch.lane;
        }
        int[] size = lanes ? layoutLanes(c, edges, dir) : layoutLevel(c.children, edges, dir);
        // ordinary containers keep their caption on top; a pool of horizontal lanes has it on the left
        boolean headerLeft = lanes && dir == Direction.LR;
        int pad = lanes ? 0 : c.padding;
        int offX = pad + (headerLeft ? c.header : 0);
        int offY = pad + (headerLeft ? 0 : c.header);
        for (Node ch : c.children) {
            ch.x += offX;
            ch.y += offY;
        }
        c.w = Math.max(c.w, size[0] + offX + pad);
        c.h = Math.max(c.h, size[1] + offY + pad);
        if (lanes) {
            // lanes fill their owner completely
            for (Node ch : c.children) {
                if (dir == Direction.TB) {
                    ch.h = c.h - offY;
                } else {
                    ch.w = c.w - offX;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ one level

    /** Places the sibling nodes at (0,0)-relative positions; returns the bounding width and height. */
    private int[] layoutLevel(List<Node> nodes, List<Edge> edges, Direction dir) {
        if (nodes.isEmpty()) {
            return new int[] {0, 0};
        }
        Map<Node, Integer> index = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            index.put(nodes.get(i), i);
        }
        List<int[]> local = liftEdges(nodes, index, edges);
        int[] rank = rank(nodes.size(), local);
        List<List<Node>> layers = buildLayers(nodes, rank, local, dir);
        order(layers, dir);
        return place(layers, dir);
    }

    /** Maps every edge to the pair of siblings (direct children of the same owner) that contain its ends. */
    private static List<int[]> liftEdges(List<Node> nodes, Map<Node, Integer> index, List<Edge> edges) {
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Edge e : edges) {
            Integer a = liftTo(e.from, index);
            Integer b = liftTo(e.to, index);
            if (a == null || b == null || a.equals(b)) {
                continue;
            }
            long key = ((long) a << 32) | b;
            if (seen.add(key)) {
                out.add(new int[] {a, b});
            }
        }
        return out;
    }

    private static Integer liftTo(Node n, Map<Node, Integer> index) {
        for (Node cur = n; cur != null; cur = cur.parent) {
            Integer i = index.get(cur);
            if (i != null) {
                return i;
            }
        }
        return null;
    }

    /** Longest-path ranking after breaking cycles with a DFS (back edges are reversed). */
    static int[] rank(int n, List<int[]> edges) {
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>());
        }
        int[] state = new int[n];
        List<int[]> dag = new ArrayList<>();
        for (int[] e : edges) {
            out.get(e[0]).add(e[1]);
        }
        Set<Long> back = new HashSet<>();
        for (int s = 0; s < n; s++) {
            if (state[s] == 0) {
                dfs(s, out, state, back);
            }
        }
        for (int[] e : edges) {
            long key = ((long) e[0] << 32) | e[1];
            dag.add(back.contains(key) ? new int[] {e[1], e[0]} : e);
        }
        int[] r = new int[n];
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < n + 2) {
            changed = false;
            for (int[] e : dag) {
                if (r[e[1]] < r[e[0]] + 1) {
                    r[e[1]] = r[e[0]] + 1;
                    changed = true;
                }
            }
        }
        // pull sources down next to their first successor so they do not all pile up in layer 0
        for (int i = 0; i < n; i++) {
            int minSucc = Integer.MAX_VALUE;
            boolean hasPred = false;
            for (int[] e : dag) {
                if (e[0] == i) {
                    minSucc = Math.min(minSucc, r[e[1]]);
                }
                if (e[1] == i) {
                    hasPred = true;
                }
            }
            if (!hasPred && minSucc != Integer.MAX_VALUE && minSucc - 1 > r[i]) {
                r[i] = minSucc - 1;
            }
        }
        return r;
    }

    private static void dfs(int s, List<List<Integer>> out, int[] state, Set<Long> back) {
        // iterative DFS to stay safe on long chains
        List<int[]> stack = new ArrayList<>();
        stack.add(new int[] {s, 0});
        state[s] = 1;
        while (!stack.isEmpty()) {
            int[] top = stack.get(stack.size() - 1);
            List<Integer> succ = out.get(top[0]);
            if (top[1] < succ.size()) {
                int v = succ.get(top[1]++);
                if (state[v] == 1) {
                    back.add(((long) top[0] << 32) | v);
                } else if (state[v] == 0) {
                    state[v] = 1;
                    stack.add(new int[] {v, 0});
                }
            } else {
                state[top[0]] = 2;
                stack.remove(stack.size() - 1);
            }
        }
    }

    /** Groups nodes by rank and inserts dummy nodes on edges spanning several layers. */
    private List<List<Node>> buildLayers(List<Node> nodes, int[] rank, List<int[]> edges, Direction dir) {
        int max = 0;
        for (int r : rank) {
            max = Math.max(max, r);
        }
        List<List<Node>> layers = new ArrayList<>();
        for (int i = 0; i <= max; i++) {
            layers.add(new ArrayList<>());
        }
        for (int i = 0; i < nodes.size(); i++) {
            layers.get(rank[i]).add(nodes.get(i));
        }
        adjacency.clear();
        for (int[] e : edges) {
            int a = e[0];
            int b = e[1];
            if (rank[a] > rank[b]) {
                int t = a;
                a = b;
                b = t;
            }
            Node prev = nodes.get(a);
            for (int r = rank[a] + 1; r < rank[b]; r++) {
                Node d = new Node("<dummy>", dir == Direction.TB ? 16 : 1, dir == Direction.TB ? 1 : 16);
                d.dummy = true;
                layers.get(r).add(d);
                link(prev, d);
                prev = d;
            }
            link(prev, nodes.get(b));
        }
        return layers;
    }

    private final Map<Node, List<Node>> adjacency = new HashMap<>();

    private void link(Node a, Node b) {
        adjacency.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
        adjacency.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
    }

    /** Barycenter crossing reduction, alternating downward and upward sweeps. */
    private void order(List<List<Node>> layers, Direction dir) {
        Map<Node, Double> pos = new HashMap<>();
        for (List<Node> l : layers) {
            for (int i = 0; i < l.size(); i++) {
                pos.put(l.get(i), (double) i);
            }
        }
        for (int iter = 0; iter < 8; iter++) {
            boolean down = iter % 2 == 0;
            for (int k = 0; k < layers.size(); k++) {
                int li = down ? k : layers.size() - 1 - k;
                int ref = down ? li - 1 : li + 1;
                if (ref < 0 || ref >= layers.size()) {
                    continue;
                }
                Set<Node> refSet = new HashSet<>(layers.get(ref));
                List<Node> layer = layers.get(li);
                Map<Node, Double> bary = new HashMap<>();
                for (Node n : layer) {
                    double sum = 0;
                    int cnt = 0;
                    for (Node m : adjacency.getOrDefault(n, Collections.emptyList())) {
                        if (refSet.contains(m)) {
                            sum += pos.get(m);
                            cnt++;
                        }
                    }
                    bary.put(n, cnt == 0 ? pos.get(n) : sum / cnt);
                }
                List<Node> sorted = new ArrayList<>(layer);
                sorted.sort((a, b) -> Double.compare(bary.get(a), bary.get(b)));
                layer.clear();
                layer.addAll(sorted);
                for (int i = 0; i < layer.size(); i++) {
                    pos.put(layer.get(i), (double) i);
                }
            }
        }
    }

    /** Assigns coordinates: layers along the major axis, nodes pulled towards their neighbours' centres. */
    private int[] place(List<List<Node>> layers, Direction dir) {
        boolean tb = dir == Direction.TB;
        // minor-axis coordinate (centre) of every node
        Map<Node, Double> c = new HashMap<>();
        double widest = 0;
        for (List<Node> l : layers) {
            double cur = 0;
            for (Node n : l) {
                double s = tb ? n.w : n.h;
                c.put(n, cur + s / 2);
                cur += s + gap(n);
            }
            widest = Math.max(widest, cur);
        }
        for (List<Node> l : layers) {
            double total = 0;
            for (Node n : l) {
                total += (tb ? n.w : n.h) + gap(n);
            }
            double shift = (widest - total) / 2;
            for (Node n : l) {
                c.put(n, c.get(n) + shift);
            }
        }
        for (int iter = 0; iter < 12; iter++) {
            for (List<Node> l : layers) {
                for (Node n : l) {
                    List<Node> nb = adjacency.getOrDefault(n, Collections.emptyList());
                    if (nb.isEmpty()) {
                        continue;
                    }
                    double sum = 0;
                    for (Node m : nb) {
                        sum += c.get(m);
                    }
                    c.put(n, (c.get(n) + sum / nb.size()) / 2);
                }
                // restore order and minimum gaps, keeping the layer's centre of mass
                double before = 0;
                for (Node n : l) {
                    before += c.get(n);
                }
                for (int i = 1; i < l.size(); i++) {
                    Node a = l.get(i - 1);
                    Node b = l.get(i);
                    double min = c.get(a) + size(a, tb) / 2 + gap(a) + size(b, tb) / 2;
                    if (c.get(b) < min) {
                        c.put(b, min);
                    }
                }
                double after = 0;
                for (Node n : l) {
                    after += c.get(n);
                }
                double d = (before - after) / Math.max(1, l.size());
                for (Node n : l) {
                    c.put(n, c.get(n) + d);
                }
            }
        }
        double minC = Double.MAX_VALUE;
        double maxC = -Double.MAX_VALUE;
        for (List<Node> l : layers) {
            for (Node n : l) {
                minC = Math.min(minC, c.get(n) - size(n, tb) / 2);
                maxC = Math.max(maxC, c.get(n) + size(n, tb) / 2);
            }
        }
        int major = 0;
        for (List<Node> l : layers) {
            int thick = 0;
            for (Node n : l) {
                thick = Math.max(thick, tb ? n.h : n.w);
            }
            for (Node n : l) {
                int minor = (int) Math.round(c.get(n) - size(n, tb) / 2 - minC);
                int mj = major + (thick - (tb ? n.h : n.w)) / 2;
                if (tb) {
                    n.x = minor;
                    n.y = mj;
                } else {
                    n.x = mj;
                    n.y = minor;
                }
            }
            major += thick + (thick > 0 ? gapMajor : 0);
        }
        int span = (int) Math.round(maxC - minC);
        int depth = Math.max(0, major - gapMajor);
        return tb ? new int[] {span, depth} : new int[] {depth, span};
    }

    private double gap(Node n) {
        return n.dummy ? gapMinor / 2.0 : gapMinor;
    }

    private static double size(Node n, boolean tb) {
        return tb ? n.w : n.h;
    }

    // ------------------------------------------------------------------------------------------------ lanes

    /**
     * Lanes of one container: one global ranking over all nodes in all lanes; lanes become columns (TB) or rows
     * (LR) and each lane is as wide as its widest cell. Nested containers inside a lane are kept as blocks.
     */
    private int[] layoutLanes(Node owner, List<Edge> edges, Direction dir) {
        boolean tb = dir == Direction.TB;
        List<Node> lanes = owner.children;
        List<Node> items = new ArrayList<>();
        Map<Node, Integer> laneOf = new HashMap<>();
        for (int li = 0; li < lanes.size(); li++) {
            for (Node n : lanes.get(li).children) {
                laneOf.put(n, li);
                items.add(n);
            }
        }
        Map<Node, Integer> index = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            index.put(items.get(i), i);
        }
        List<int[]> local = liftEdges(items, index, edges);
        int[] rank = rank(items.size(), local);
        int maxRank = 0;
        for (int r : rank) {
            maxRank = Math.max(maxRank, r);
        }
        // cells[rank][lane] -> nodes
        Map<Long, List<Node>> cells = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            long key = ((long) rank[i] << 32) | laneOf.get(items.get(i));
            cells.computeIfAbsent(key, k -> new ArrayList<>()).add(items.get(i));
        }
        int[] laneSpan = new int[lanes.size()];
        int[] rankThick = new int[maxRank + 1];
        for (Map.Entry<Long, List<Node>> e : cells.entrySet()) {
            int r = (int) (e.getKey() >> 32);
            int li = (int) (e.getKey() & 0xffffffffL);
            int span = 0;
            int thick = 0;
            for (Node n : e.getValue()) {
                span += (tb ? n.w : n.h) + gapMinor;
                thick = Math.max(thick, tb ? n.h : n.w);
            }
            laneSpan[li] = Math.max(laneSpan[li], span - gapMinor + 2 * gapMinor);
            rankThick[r] = Math.max(rankThick[r], thick);
        }
        int laneHeader = 0;
        for (Node l : lanes) {
            laneHeader = Math.max(laneHeader, l.header);
        }
        int[] laneStart = new int[lanes.size()];
        int cur = 0;
        for (int li = 0; li < lanes.size(); li++) {
            laneSpan[li] = Math.max(laneSpan[li], 160);
            laneStart[li] = cur;
            cur += laneSpan[li];
        }
        int[] rankStart = new int[maxRank + 1];
        int m = laneHeader + gapMinor;
        for (int r = 0; r <= maxRank; r++) {
            rankStart[r] = m;
            m += rankThick[r] + gapMajor * 3 / 4;
        }
        int totalMajor = m - gapMajor * 3 / 4 + gapMinor;
        for (Map.Entry<Long, List<Node>> e : cells.entrySet()) {
            int r = (int) (e.getKey() >> 32);
            int li = (int) (e.getKey() & 0xffffffffL);
            List<Node> cell = e.getValue();
            int span = -gapMinor;
            for (Node n : cell) {
                span += (tb ? n.w : n.h) + gapMinor;
            }
            int pos = (laneSpan[li] - span) / 2;
            for (Node n : cell) {
                int mn = pos;
                int mj = rankStart[r] + (rankThick[r] - (tb ? n.h : n.w)) / 2;
                // lane children are relative to their lane
                if (tb) {
                    n.x = mn;
                    n.y = mj;
                } else {
                    n.x = mj;
                    n.y = mn;
                }
                pos += (tb ? n.w : n.h) + gapMinor;
            }
        }
        for (int li = 0; li < lanes.size(); li++) {
            Node l = lanes.get(li);
            if (tb) {
                l.x = laneStart[li];
                l.y = 0;
                l.w = laneSpan[li];
                l.h = totalMajor;
            } else {
                l.x = 0;
                l.y = laneStart[li];
                l.w = totalMajor;
                l.h = laneSpan[li];
            }
        }
        return tb ? new int[] {cur, totalMajor} : new int[] {totalMajor, cur};
    }
}

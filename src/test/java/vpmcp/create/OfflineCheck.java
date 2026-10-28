package vpmcp.create;

import com.google.gson.JsonObject;
import vpmcp.create.core.McpHttpServer;
import vpmcp.create.core.McpProtocol;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;
import vpmcp.create.core.ToolInvoker;
import vpmcp.create.layout.LayeredLayout;
import vpmcp.create.layout.LayeredLayout.Direction;
import vpmcp.create.layout.LayeredLayout.Edge;
import vpmcp.create.layout.LayeredLayout.Node;
import vpmcp.create.vp.MemberSpec;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that need no Visual Paradigm: the layered layout (no overlaps, containers enclose children, lanes),
 * the member parser, and the HTTP/JSON-RPC transport with a dummy tool. Run with build.sh --check.
 */
public final class OfflineCheck {

    private static int failures;

    public static void main(String[] args) throws Exception {
        layoutChain();
        layoutContainers();
        layoutLanes();
        members();
        http();
        System.out.println(failures == 0 ? "OFFLINE CHECK: OK" : "OFFLINE CHECK: " + failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        } else {
            System.out.println("ok   " + what);
        }
    }

    private static void layoutChain() {
        Node a = new Node("a", 100, 60);
        Node b = new Node("b", 140, 80);
        Node c = new Node("c", 90, 40);
        Node d = new Node("d", 90, 40);
        List<Node> roots = Arrays.asList(a, b, c, d);
        List<Edge> e = Arrays.asList(new Edge(a, b), new Edge(b, c), new Edge(b, d), new Edge(d, a));
        new LayeredLayout().run(roots, e, Direction.TB);
        check(noOverlap(roots), "chain: no overlaps");
        check(a.y < b.y && b.y < c.y, "chain: ranks follow the edges (cycle broken)");
    }

    private static void layoutContainers() {
        Node sys = new Node("sys", 0, 0);
        Node u1 = new Node("u1", 120, 50);
        Node u2 = new Node("u2", 160, 50);
        Node u3 = new Node("u3", 120, 50);
        sys.add(u1);
        sys.add(u2);
        sys.add(u3);
        Node actor = new Node("actor", 40, 70);
        List<Node> roots = Arrays.asList(actor, sys);
        List<Edge> e = Arrays.asList(new Edge(actor, u1), new Edge(actor, u2), new Edge(u2, u3));
        new LayeredLayout().run(roots, e, Direction.LR);
        check(noOverlap(roots), "use case: actor and system do not overlap");
        check(noOverlap(sys.children), "use case: use cases do not overlap");
        check(encloses(sys, u1) && encloses(sys, u2) && encloses(sys, u3), "use case: system encloses use cases");
        check(actor.x + actor.w < sys.x, "use case: actor left of the system");
    }

    private static void layoutLanes() {
        Node owner = new Node("swimlane", 0, 0);
        owner.header = 0;
        owner.padding = 0;
        Node l1 = new Node("l1", 0, 0);
        Node l2 = new Node("l2", 0, 0);
        l1.lane = true;
        l2.lane = true;
        l1.header = 28;
        l2.header = 28;
        owner.add(l1);
        owner.add(l2);
        Node s = new Node("start", 20, 20);
        Node a1 = new Node("a1", 120, 40);
        Node a2 = new Node("a2", 160, 40);
        Node dd = new Node("dec", 30, 30);
        Node a3 = new Node("a3", 120, 40);
        l1.add(s);
        l1.add(a1);
        l2.add(a2);
        l2.add(dd);
        l1.add(a3);
        List<Edge> e = Arrays.asList(new Edge(s, a1), new Edge(a1, a2), new Edge(a2, dd), new Edge(dd, a3));
        new LayeredLayout().run(new ArrayList<>(Arrays.asList(owner)), e, Direction.TB);
        check(l1.x + l1.w <= l2.x, "lanes: side by side");
        check(l1.h == l2.h && l1.h == owner.h, "lanes: same height as the swimlane");
        check(encloses(l1, s) && encloses(l1, a1) && encloses(l1, a3) && encloses(l2, a2) && encloses(l2, dd),
                "lanes: nodes inside their lane");
        check(s.y < a1.y && a1.y < a2.y && a2.y < dd.y && dd.y < a3.y, "lanes: global ranking top to bottom");
    }

    private static void members() throws ToolException {
        MemberSpec a = MemberSpec.parseText("+path: str", false);
        check("public".equals(a.visibility) && "path".equals(a.name) && "str".equals(a.type), "member: +path: str");
        MemberSpec b = MemberSpec.parseText("-count: int = 0", false);
        check("private".equals(b.visibility) && "0".equals(b.initial), "member: initial value");
        MemberSpec c = MemberSpec.parseText("+load(path: str, n: int): Tensor", true);
        check("load".equals(c.name) && c.params.size() == 2 && "Tensor".equals(c.type), "member: operation");
        MemberSpec d = MemberSpec.parseText("{static} #створити(ім'я: str): Зображення", true);
        check(d.isStatic && "protected".equals(d.visibility) && "створити".equals(d.name)
                && "Зображення".equals(d.type), "member: Cyrillic static operation");
        MemberSpec e = MemberSpec.parseText("+boxes: Box[0..*]", false);
        check("Box".equals(e.type) && "0..*".equals(e.multiplicity), "member: multiplicity");
    }

    private static void http() throws Exception {
        Tool echo = new Tool() {
            public String name() {
                return "echo";
            }

            public String description() {
                return "echo";
            }

            public JsonObject inputSchema() {
                return new JsonObject();
            }

            public boolean mutates() {
                return false;
            }

            public JsonObject execute(JsonObject args) throws Exception {
                if (args.has("fail")) {
                    throw new ToolException("як просили");
                }
                return args;
            }
        };
        McpProtocol p = new McpProtocol(Arrays.asList(echo), ToolInvoker.DIRECT, "test");
        McpHttpServer s = new McpHttpServer(18900, p);
        s.start();
        try {
            String init = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                    + "{\"protocolVersion\":\"2025-03-26\"}}");
            check(init.contains("\"2025-03-26\""), "http: initialize negotiates version");
            String list = post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
            check(list.contains("\"echo\""), "http: tools/list");
            String call = post("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\","
                    + "\"arguments\":{\"назва\":\"Діаграма класів\"}}}");
            check(call.contains("Діаграма класів") && call.contains("\"isError\":false"), "http: UTF-8 round trip");
            String err = post("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\","
                    + "\"arguments\":{\"fail\":1}}}");
            check(err.contains("\"isError\":true") && err.contains("як просили"), "http: tool error is a result");
            String unknown = post("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}");
            check(unknown.contains("-32602"), "http: unknown tool is a JSON-RPC error");
            check(status("GET") == 405, "http: GET is 405");
        } finally {
            s.stop();
        }
    }

    private static String post(String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:18900/mcp").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json, text/event-stream");
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int status(String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:18900/mcp").openConnection();
        c.setRequestMethod(method);
        return c.getResponseCode();
    }

    private static boolean noOverlap(List<Node> ns) {
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                Node a = ns.get(i);
                Node b = ns.get(j);
                if (a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h) {
                    System.out.println("     overlap " + a.id + " " + box(a) + " / " + b.id + " " + box(b));
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean encloses(Node p, Node c) {
        boolean ok = c.x >= p.x && c.y >= p.y && c.x + c.w <= p.x + p.w && c.y + c.h <= p.y + p.h;
        if (!ok) {
            System.out.println("     " + p.id + " " + box(p) + " does not enclose " + c.id + " " + box(c));
        }
        return ok;
    }

    private static String box(Node n) {
        return "[" + n.x + "," + n.y + " " + n.w + "x" + n.h + "]";
    }
}

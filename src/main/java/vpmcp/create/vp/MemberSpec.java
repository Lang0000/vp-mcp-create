package vpmcp.create.vp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import vpmcp.create.core.ToolException;

import java.util.ArrayList;
import java.util.List;

/**
 * An attribute or operation, parsed from UML text such as "+path: str", "-count: int = 0", "+load(p: str): Tensor",
 * "{static} +instance(): App" or "{abstract} +run()", or given as an object with the same fields.
 */
public final class MemberSpec {

    public String visibility;
    public String name;
    public String type;
    public String initial;
    public String multiplicity;
    public boolean isStatic;
    public boolean isAbstract;
    public boolean operation;
    public final List<String[]> params = new ArrayList<>();
    public String documentation;

    public String displayText() {
        StringBuilder sb = new StringBuilder();
        if (visibility != null) {
            sb.append(symbol(visibility));
        }
        sb.append(name);
        if (operation) {
            sb.append('(');
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(params.get(i)[0]);
                if (params.get(i)[1] != null) {
                    sb.append(" : ").append(params.get(i)[1]);
                }
            }
            sb.append(')');
        }
        if (type != null) {
            sb.append(" : ").append(type);
        }
        if (initial != null) {
            sb.append(" = ").append(initial);
        }
        return sb.toString();
    }

    private static String symbol(String vis) {
        switch (vis) {
            case "public":
                return "+";
            case "private":
                return "-";
            case "protected":
                return "#";
            case "package":
                return "~";
            default:
                return "";
        }
    }

    public static MemberSpec parse(JsonElement e, boolean operation) throws ToolException {
        if (e == null || e.isJsonNull()) {
            throw new ToolException("Empty " + (operation ? "operation" : "attribute") + ".");
        }
        if (e.isJsonPrimitive()) {
            return parseText(e.getAsString(), operation);
        }
        if (!e.isJsonObject()) {
            throw new ToolException("An " + (operation ? "operation" : "attribute") + " must be a string or an object.");
        }
        JsonObject o = e.getAsJsonObject();
        MemberSpec m;
        if (o.has("text")) {
            m = parseText(o.get("text").getAsString(), operation);
        } else {
            m = new MemberSpec();
            m.operation = operation;
            m.name = Args.str(o, "name", null);
            if (m.name == null || m.name.isEmpty()) {
                throw new ToolException("Member object needs \"name\" or \"text\": " + o);
            }
        }
        String vis = Args.str(o, "visibility", null);
        if (vis != null) {
            m.visibility = normVisibility(vis);
        }
        m.type = Args.str(o, operation ? "return_type" : "type", m.type);
        if (operation && o.has("type") && !o.has("return_type")) {
            m.type = Args.str(o, "type", m.type);
        }
        m.initial = Args.str(o, "initial", m.initial);
        m.multiplicity = Args.str(o, "multiplicity", m.multiplicity);
        m.isStatic = Args.bool(o, "static", m.isStatic);
        m.isAbstract = Args.bool(o, "abstract", m.isAbstract);
        m.documentation = Args.str(o, "documentation", null);
        if (operation && o.has("parameters") && o.get("parameters").isJsonArray()) {
            m.params.clear();
            for (JsonElement p : o.getAsJsonArray("parameters")) {
                if (p.isJsonPrimitive()) {
                    m.params.add(splitNameType(p.getAsString()));
                } else if (p.isJsonObject()) {
                    m.params.add(new String[] {Args.str(p.getAsJsonObject(), "name", "arg"),
                            Args.str(p.getAsJsonObject(), "type", null)});
                }
            }
        }
        return m;
    }

    public static MemberSpec parseText(String raw, boolean operation) throws ToolException {
        MemberSpec m = new MemberSpec();
        m.operation = operation;
        String s = raw.trim();
        boolean progress = true;
        while (progress) {
            progress = false;
            String low = s.toLowerCase();
            if (low.startsWith("{static}")) {
                m.isStatic = true;
                s = s.substring(8).trim();
                progress = true;
            } else if (low.startsWith("{abstract}")) {
                m.isAbstract = true;
                s = s.substring(10).trim();
                progress = true;
            } else if (low.startsWith("static ")) {
                m.isStatic = true;
                s = s.substring(7).trim();
                progress = true;
            } else if (low.startsWith("abstract ")) {
                m.isAbstract = true;
                s = s.substring(9).trim();
                progress = true;
            }
        }
        if (!s.isEmpty() && "+-#~".indexOf(s.charAt(0)) >= 0) {
            m.visibility = normVisibility(s.substring(0, 1));
            s = s.substring(1).trim();
        }
        // trailing markers used by Mermaid: "$" static, "*" abstract
        while (s.endsWith("$") || s.endsWith("*")) {
            if (s.endsWith("$")) {
                m.isStatic = true;
            } else {
                m.isAbstract = true;
            }
            s = s.substring(0, s.length() - 1).trim();
        }
        int paren = s.indexOf('(');
        if (operation || paren >= 0 && (s.indexOf(':') < 0 || paren < s.indexOf(':'))) {
            m.operation = true;
            if (paren < 0) {
                m.name = s.contains(":") ? s.substring(0, s.indexOf(':')).trim() : s;
                if (s.contains(":")) {
                    m.type = emptyToNull(s.substring(s.indexOf(':') + 1).trim());
                }
            } else {
                int close = s.lastIndexOf(')');
                if (close < paren) {
                    throw new ToolException("Unbalanced parentheses in operation \"" + raw + "\".");
                }
                m.name = s.substring(0, paren).trim();
                String ps = s.substring(paren + 1, close).trim();
                if (!ps.isEmpty()) {
                    for (String p : splitTopLevel(ps)) {
                        m.params.add(splitNameType(p));
                    }
                }
                String rest = s.substring(close + 1).trim();
                if (rest.startsWith(":")) {
                    m.type = emptyToNull(rest.substring(1).trim());
                } else if (!rest.isEmpty()) {
                    m.type = rest; // Mermaid style "load() Tensor"
                }
            }
        } else {
            String body = s;
            int eq = body.indexOf('=');
            if (eq >= 0) {
                m.initial = emptyToNull(body.substring(eq + 1).trim());
                body = body.substring(0, eq).trim();
            }
            int colon = body.indexOf(':');
            if (colon >= 0) {
                m.name = body.substring(0, colon).trim();
                m.type = emptyToNull(body.substring(colon + 1).trim());
            } else {
                String[] ws = body.split("\\s+");
                if (ws.length == 2 && !body.contains("[")) {
                    // "str path" (Mermaid / C style): type first
                    m.type = ws[0];
                    m.name = ws[1];
                } else {
                    m.name = body;
                }
            }
            if (m.type != null && m.type.endsWith("]") && m.type.contains("[")) {
                int lb = m.type.lastIndexOf('[');
                String mult = m.type.substring(lb + 1, m.type.length() - 1).trim();
                if (mult.matches("[0-9*.]+")) {
                    m.multiplicity = mult;
                    m.type = m.type.substring(0, lb).trim();
                }
            }
        }
        if (m.name == null || m.name.isEmpty()) {
            throw new ToolException("Cannot read a name from \"" + raw + "\".");
        }
        return m;
    }

    private static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '<' || ch == '[' || ch == '(') {
                depth++;
            } else if (ch == '>' || ch == ']' || ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                out.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        out.add(s.substring(start).trim());
        return out;
    }

    private static String[] splitNameType(String p) {
        String t = p.trim();
        int eq = t.indexOf('=');
        if (eq >= 0) {
            t = t.substring(0, eq).trim();
        }
        int colon = t.indexOf(':');
        if (colon >= 0) {
            return new String[] {t.substring(0, colon).trim(), emptyToNull(t.substring(colon + 1).trim())};
        }
        String[] ws = t.split("\\s+");
        if (ws.length == 2) {
            return new String[] {ws[1], ws[0]};
        }
        return new String[] {t, null};
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    public static String normVisibility(String v) {
        switch (v.trim().toLowerCase()) {
            case "+":
            case "public":
                return "public";
            case "-":
            case "private":
                return "private";
            case "#":
            case "protected":
                return "protected";
            case "~":
            case "package":
                return "package";
            default:
                return "Unspecified";
        }
    }
}

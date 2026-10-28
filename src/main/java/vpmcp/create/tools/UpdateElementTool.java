package vpmcp.create.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IStereotype;
import vpmcp.create.core.Tool;
import vpmcp.create.core.ToolException;
import vpmcp.create.vp.Args;
import vpmcp.create.vp.MemberSpec;
import vpmcp.create.vp.Members;
import vpmcp.create.vp.Styler;
import vpmcp.create.vp.Vp;

import java.util.ArrayList;
import java.util.List;

public final class UpdateElementTool implements Tool {

    @Override
    public String name() {
        return "update_element";
    }

    @Override
    public String description() {
        return "Change an element: name, documentation, stereotypes, attributes, operations, abstract flag, "
                + "connector label / guard / multiplicity / roles, formatting (fill, line and font colour, bold, "
                + "italic, font size) and bounds. 'id' is a shape/connector id or a model id. Model changes apply "
                + "everywhere the element is shown; style and bounds apply to its view on 'diagram' (or to all "
                + "its views when no diagram is given).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject s = Schemas.parse("{\"type\":\"object\",\"properties\":{"
                + "\"id\":{\"type\":\"string\",\"description\":\"Shape, connector or model element id.\"},"
                + "\"diagram\":{\"type\":\"string\",\"description\":\"Optional diagram URL, id or name.\"},"
                + "\"name\":{\"type\":\"string\"},"
                + "\"documentation\":{\"type\":\"string\"},"
                + "\"stereotypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Replaces "
                + "all stereotypes.\"},"
                + "\"add_stereotypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"remove_stereotypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"attributes\":{\"type\":\"array\",\"items\":" + Schemas.MEMBER + ",\"description\":\"Replaces "
                + "all attributes.\"},"
                + "\"add_attributes\":{\"type\":\"array\",\"items\":" + Schemas.MEMBER + "},"
                + "\"remove_attributes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":"
                + "\"Attribute names.\"},"
                + "\"operations\":{\"type\":\"array\",\"items\":" + Schemas.MEMBER + ",\"description\":\"Replaces "
                + "all operations.\"},"
                + "\"add_operations\":{\"type\":\"array\",\"items\":" + Schemas.MEMBER + "},"
                + "\"remove_operations\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"abstract\":{\"type\":\"boolean\"},"
                + "\"label\":{\"type\":\"string\",\"description\":\"Same as name; for connectors.\"},"
                + "\"guard\":{\"type\":\"string\"},"
                + "\"multiplicity\":{\"type\":\"object\",\"properties\":{\"from\":{\"type\":\"string\"},"
                + "\"to\":{\"type\":\"string\"}}},"
                + "\"roles\":{\"type\":\"object\",\"properties\":{\"from\":{\"type\":\"string\"},"
                + "\"to\":{\"type\":\"string\"}}},"
                + "\"style\":" + Schemas.STYLE + ","
                + "\"bounds\":{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"integer\"},\"y\":{\"type\":"
                + "\"integer\"},\"width\":{\"type\":\"integer\"},\"height\":{\"type\":\"integer\"}}},"
                + "\"refit\":{\"type\":\"boolean\",\"default\":true,\"description\":\"Resize class shapes to their "
                + "new members.\"}"
                + "},\"additionalProperties\":false}");
        s.add("required", Schemas.required("id"));
        return s;
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public JsonObject execute(JsonObject args) throws Exception {
        String id = Args.reqStr(args, "id");
        String diagramRef = Args.str(args, "diagram", null);
        List<IDiagramElement> views = new ArrayList<>();
        IModelElement model;
        if (diagramRef != null) {
            IDiagramUIModel d = Vp.findDiagram(diagramRef);
            IDiagramElement v = Vp.findOnDiagram(d, id);
            if (v != null) {
                views.add(v);
                model = v.getModelElement();
            } else {
                model = Vp.requireProject().getModelElementById(id);
                if (model == null) {
                    throw new ToolException("No element \"" + id + "\" on diagram \"" + d.getName() + "\".");
                }
            }
        } else {
            views.addAll(Vp.findViews(id));
            model = !views.isEmpty() ? views.get(0).getModelElement() : Vp.requireProject().getModelElementById(id);
        }
        if (model == null && views.isEmpty()) {
            throw new ToolException("Element not found: \"" + id + "\".");
        }
        // validate everything first
        List<MemberSpec> attrs = members(args, "attributes", false);
        List<MemberSpec> addAttrs = members(args, "add_attributes", false);
        List<MemberSpec> ops = members(args, "operations", true);
        List<MemberSpec> addOps = members(args, "add_operations", true);
        boolean memberChange = args.has("attributes") || args.has("add_attributes") || args.has("remove_attributes")
                || args.has("operations") || args.has("add_operations") || args.has("remove_operations");
        if (memberChange && !(model instanceof IClass)) {
            throw new ToolException("Attributes and operations can only be changed on classes and interfaces; \""
                    + id + "\" is a " + (model == null ? "view" : model.getModelType()) + ".");
        }
        JsonObject style = Args.obj(args, "style");
        Styler.validate(style);
        JsonObject bounds = Args.obj(args, "bounds");

        List<String> changed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (model != null) {
            String name = Args.str(args, "name", Args.str(args, "label", null));
            if (name != null) {
                model.setName(name);
                changed.add("name");
            }
            String doc = Args.str(args, "documentation", null);
            if (doc != null) {
                model.setDocumentation(doc);
                changed.add("documentation");
            }
            if (args.has("stereotypes")) {
                IStereotype[] old = model.toStereotypeModelArray();
                if (old != null) {
                    for (IStereotype st : old) {
                        model.removeStereotype(st);
                    }
                }
                for (String st : Args.strings(args, "stereotypes")) {
                    model.addStereotype(st);
                }
                changed.add("stereotypes");
            }
            for (String st : Args.strings(args, "add_stereotypes")) {
                model.addStereotype(st);
                changed.add("stereotypes");
            }
            for (String st : Args.strings(args, "remove_stereotypes")) {
                model.removeStereotype(st);
                changed.add("stereotypes");
            }
            if (args.has("abstract")) {
                if (model instanceof IClass) {
                    ((IClass) model).setAbstract(Args.bool(args, "abstract", false));
                    changed.add("abstract");
                } else {
                    warnings.add("abstract ignored: not a class");
                }
            }
            if (model instanceof IClass) {
                IClass c = (IClass) model;
                if (args.has("attributes")) {
                    for (IAttribute a : c.toAttributeArray()) {
                        c.removeAttribute(a);
                    }
                }
                for (String n : Args.strings(args, "remove_attributes")) {
                    IAttribute a = c.getAttributeByName(n);
                    if (a == null) {
                        warnings.add("no attribute named " + n);
                    } else {
                        c.removeAttribute(a);
                    }
                }
                for (MemberSpec m : attrs) {
                    Members.fillAttribute(c.createAttribute(), m);
                }
                for (MemberSpec m : addAttrs) {
                    Members.fillAttribute(c.createAttribute(), m);
                }
                if (args.has("operations")) {
                    for (IOperation o : c.toOperationArray()) {
                        c.removeOperation(o);
                    }
                }
                for (String n : Args.strings(args, "remove_operations")) {
                    IOperation o = c.getOperationByName(n);
                    if (o == null) {
                        warnings.add("no operation named " + n);
                    } else {
                        c.removeOperation(o);
                    }
                }
                for (MemberSpec m : ops) {
                    Members.fillOperation(c.createOperation(), m);
                }
                for (MemberSpec m : addOps) {
                    Members.fillOperation(c.createOperation(), m);
                }
                if (memberChange) {
                    changed.add("members");
                }
            }
            String guard = Args.str(args, "guard", null);
            if (guard != null) {
                if (model instanceof IControlFlow) {
                    ((IControlFlow) model).setGuard(guard);
                    changed.add("guard");
                } else {
                    warnings.add("guard ignored: not a control flow");
                }
            }
            if (args.has("multiplicity") || args.has("roles")) {
                if (model instanceof IAssociation) {
                    IAssociation as = (IAssociation) model;
                    IAssociationEnd fe = (IAssociationEnd) as.getFromEnd();
                    IAssociationEnd te = (IAssociationEnd) as.getToEnd();
                    JsonObject mult = Args.obj(args, "multiplicity");
                    if (mult != null) {
                        if (mult.has("from")) {
                            fe.setMultiplicity(Args.str(mult, "from", ""));
                        }
                        if (mult.has("to")) {
                            te.setMultiplicity(Args.str(mult, "to", ""));
                        }
                    }
                    JsonObject roles = Args.obj(args, "roles");
                    if (roles != null) {
                        if (roles.has("from")) {
                            fe.setName(Args.str(roles, "from", ""));
                        }
                        if (roles.has("to")) {
                            te.setName(Args.str(roles, "to", ""));
                        }
                    }
                    changed.add("association ends");
                } else {
                    warnings.add("multiplicity/roles ignored: not an association");
                }
            }
        }
        if (views.isEmpty() && (style != null || bounds != null)) {
            warnings.add("element has no view; style and bounds ignored");
        }
        boolean refit = Args.bool(args, "refit", true) && memberChange;
        for (IDiagramElement v : views) {
            if (style != null) {
                Styler.apply(v, style, warnings);
            }
            if (refit && v instanceof IShapeUIModel && model instanceof IClass) {
                int[] size = Members.classSize((IClass) model);
                v.setSize(Math.max(v.getWidth(), size[0]), size[1]);
                try {
                    v.resetCaption();
                    ((IShapeUIModel) v).fitSize(); // exact on an opened diagram; the estimate stays otherwise
                } catch (Throwable ignored) {
                    // keep the estimate
                }
            }
            if (bounds != null) {
                Integer x = Args.integer(bounds, "x");
                Integer y = Args.integer(bounds, "y");
                Integer w = Args.integer(bounds, "width");
                Integer h = Args.integer(bounds, "height");
                v.setBounds(x != null ? x : v.getX(), y != null ? y : v.getY(), w != null ? w : v.getWidth(),
                        h != null ? h : v.getHeight());
            }
            try {
                v.resetCaption();
            } catch (Throwable ignored) {
                // caption reset is cosmetic
            }
        }
        if (style != null) {
            changed.add("style");
        }
        if (bounds != null) {
            changed.add("bounds");
        }
        JsonObject out = new JsonObject();
        out.addProperty("model_id", model == null ? null : model.getId());
        out.addProperty("model_type", model == null ? null : model.getModelType());
        out.addProperty("name", model == null ? null : model.getName());
        out.addProperty("views", views.size());
        out.add("changed", Common.toArray(new ArrayList<>(new java.util.LinkedHashSet<>(changed))));
        out.add("warnings", Common.toArray(warnings));
        return out;
    }

    private static List<MemberSpec> members(JsonObject args, String key, boolean op) throws ToolException {
        List<MemberSpec> out = new ArrayList<>();
        for (JsonElement e : Args.array(args, key)) {
            out.add(MemberSpec.parse(e, op));
        }
        return out;
    }
}

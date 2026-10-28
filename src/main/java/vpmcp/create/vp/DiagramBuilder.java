package vpmcp.create.vp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IActivityPartition;
import com.vp.plugin.model.IActivitySwimlane2;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IBPGateway;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IRelationshipEnd;
import vpmcp.create.core.ToolException;
import vpmcp.create.layout.LayeredLayout;

import java.awt.Point;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the JSON description of nodes and edges into model elements, shapes and connectors on one diagram.
 * The whole request is validated before anything is created, so a bad request leaves the project untouched.
 */
public final class DiagramBuilder {

    /** One requested node after validation. */
    static final class NodeSpec {
        String id;
        JsonObject json;
        Kinds.NodeKind kind;
        String name;
        String parentRef;
        NodeSpec parent;
        IDiagramElement existingParent;
        List<MemberSpec> attrs = new ArrayList<>();
        List<MemberSpec> ops = new ArrayList<>();
        IModelElement model;
        IShapeUIModel shape;
        /** Activity partitions share one swimlane shape; this is the partition's own model. */
        IActivityPartition partition;
        /** False when an existing model element was reused; rollback must not delete it. */
        boolean created;
        int w;
        int h;
    }

    static final class EdgeSpec {
        String id;
        JsonObject json;
        Kinds.EdgeKind kind;
        String fromRef;
        String toRef;
        NodeSpec fromNode;
        NodeSpec toNode;
        IDiagramElement fromExisting;
        IDiagramElement toExisting;
        IModelElement model;
        IConnectorUIModel connector;
    }

    private IDiagramUIModel diagram;
    private final Kinds.Family family;
    private final DiagramManager dm;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, NodeSpec> nodes = new LinkedHashMap<>();
    private final List<EdgeSpec> edges = new ArrayList<>();
    private IShapeUIModel swimlaneShape;
    private IActivitySwimlane2 swimlaneModel;
    private Map<String, IShapeUIModel> compartments = new HashMap<>();

    /** diagram may be null during validation and set with {@link #setDiagram} before {@link #build}. */
    public DiagramBuilder(IDiagramUIModel diagram, Kinds.Family family) throws ToolException {
        this.diagram = diagram;
        this.family = family;
        this.dm = Vp.dm();
    }

    public void setDiagram(IDiagramUIModel diagram) {
        this.diagram = diagram;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    private IDiagramElement find(String ref) {
        return diagram == null ? null : Vp.findOnDiagram(diagram, ref);
    }

    public List<String> warnings() {
        return warnings;
    }

    // ------------------------------------------------------------------------------------------------ validation

    public void validate(JsonArray nodeArr, JsonArray edgeArr) throws ToolException {
        int i = 0;
        for (JsonElement e : nodeArr) {
            i++;
            if (!e.isJsonObject()) {
                throw new ToolException("nodes[" + (i - 1) + "] must be an object.");
            }
            JsonObject o = e.getAsJsonObject();
            NodeSpec n = new NodeSpec();
            n.json = o;
            n.id = Args.str(o, "id", "n" + i);
            if (nodes.containsKey(n.id)) {
                throw new ToolException("Duplicate node id \"" + n.id + "\".");
            }
            n.kind = Kinds.nodeKind(family, Args.str(o, "kind", Args.str(o, "type", null)));
            n.name = Args.str(o, "name", Args.str(o, "label", ""));
            n.parentRef = Args.str(o, "parent", Args.str(o, "partition", Args.str(o, "lane", null)));
            for (JsonElement a : Args.array(o, "attributes")) {
                n.attrs.add(MemberSpec.parse(a, false));
            }
            for (JsonElement a : Args.array(o, "operations")) {
                n.ops.add(MemberSpec.parse(a, true));
            }
            Styler.validate(Args.obj(o, "style"));
            nodes.put(n.id, n);
        }
        for (NodeSpec n : nodes.values()) {
            if (n.parentRef == null) {
                continue;
            }
            n.parent = nodes.get(n.parentRef);
            if (n.parent == null) {
                n.existingParent = find(n.parentRef);
                if (n.existingParent == null) {
                    throw new ToolException("Node \"" + n.id + "\" has parent \"" + n.parentRef
                            + "\", which is neither a node of this request nor an element of the diagram.");
                }
            } else if (n.parent == n) {
                throw new ToolException("Node \"" + n.id + "\" cannot be its own parent.");
            }
        }
        for (NodeSpec n : nodes.values()) {
            Set<NodeSpec> seen = new HashSet<>();
            for (NodeSpec p = n; p != null; p = p.parent) {
                if (!seen.add(p)) {
                    throw new ToolException("Parent cycle through node \"" + n.id + "\".");
                }
            }
        }
        int j = 0;
        for (JsonElement e : edgeArr) {
            j++;
            if (!e.isJsonObject()) {
                throw new ToolException("edges[" + (j - 1) + "] must be an object.");
            }
            JsonObject o = e.getAsJsonObject();
            EdgeSpec s = new EdgeSpec();
            s.json = o;
            s.id = Args.str(o, "id", "e" + j);
            s.kind = Kinds.edgeKind(family, Args.str(o, "kind", Args.str(o, "type", null)));
            s.fromRef = Args.reqStr(o, "from");
            s.toRef = Args.reqStr(o, "to");
            s.fromNode = nodes.get(s.fromRef);
            s.toNode = nodes.get(s.toRef);
            if (s.fromNode == null) {
                s.fromExisting = find(s.fromRef);
                if (s.fromExisting == null) {
                    throw new ToolException("Edge \"" + s.id + "\": \"from\" refers to unknown node \"" + s.fromRef + "\".");
                }
            }
            if (s.toNode == null) {
                s.toExisting = find(s.toRef);
                if (s.toExisting == null) {
                    throw new ToolException("Edge \"" + s.id + "\": \"to\" refers to unknown node \"" + s.toRef + "\".");
                }
            }
            if (s.fromNode != null && s.fromNode.kind.lane || s.toNode != null && s.toNode.kind.lane) {
                throw new ToolException("Edge \"" + s.id + "\" connects a partition/lane; connect the nodes inside it.");
            }
            Styler.validate(Args.obj(o, "style"));
            edges.add(s);
        }
    }

    // ------------------------------------------------------------------------------------------------ creation

    public void build() throws ToolException {
        List<NodeSpec> order = parentsFirst();
        for (NodeSpec n : order) {
            if (n.kind.lane && family == Kinds.Family.ACTIVITY) {
                createNode(n);
            }
        }
        if (swimlaneModel != null) {
            createSwimlaneShape();
        }
        for (NodeSpec n : order) {
            if (!(n.kind.lane && family == Kinds.Family.ACTIVITY)) {
                createNode(n);
            }
        }
        for (EdgeSpec e : edges) {
            createEdge(e);
        }
    }

    /**
     * Lets VP size class shapes to their content (exact compartment heights); needs the diagram to be open.
     * Other kinds keep the text-based estimate. Containers are sized by the layout.
     */
    public void fitShapes() {
        resetCaptions(diagram);
        for (NodeSpec n : nodes.values()) {
            if (n.shape == null || n.partition != null || n.kind.container || n.json.has("width")
                    || n.json.has("height") || !FIT_KINDS.contains(n.kind.name)) {
                continue;
            }
            try {
                n.shape.fitSize();
                int w = n.shape.getWidth();
                int h = n.shape.getHeight();
                if (w > 10 && h > 10) {
                    n.w = Math.max(w, Math.min(n.w, w + 40));
                    n.h = h;
                    n.shape.setSize(n.w, n.h);
                }
            } catch (Throwable t) {
                warnings.add("node " + n.id + ": fitSize failed (" + t + "); kept the estimated size");
            }
        }
    }

    private static final Set<String> FIT_KINDS = new HashSet<>(java.util.Arrays.asList(
            "class", "interface", "abstract_class", "enum"));

    /** Deletes everything this builder created, after a failure half-way through {@link #build}. */
    public void rollback() {
        for (EdgeSpec e : edges) {
            safeDelete(e.model);
        }
        List<NodeSpec> order = parentsFirst();
        for (int i = order.size() - 1; i >= 0; i--) {
            NodeSpec n = order.get(i);
            if (n.created) {
                safeDelete(n.model);
            } else if (n.shape != null) {
                try {
                    n.shape.deleteViewOnly();
                } catch (Throwable ignored) {
                    // the diagram goes away anyway
                }
            }
        }
        safeDelete(swimlaneModel);
    }

    private static void safeDelete(IModelElement m) {
        if (m == null) {
            return;
        }
        try {
            m.delete();
        } catch (Throwable ignored) {
            // already gone with its parent
        }
    }

    private List<NodeSpec> parentsFirst() {
        List<NodeSpec> out = new ArrayList<>();
        Set<NodeSpec> done = new HashSet<>();
        for (NodeSpec n : nodes.values()) {
            addWithParents(n, out, done);
        }
        return out;
    }

    private void addWithParents(NodeSpec n, List<NodeSpec> out, Set<NodeSpec> done) {
        if (done.contains(n)) {
            return;
        }
        if (n.parent != null) {
            addWithParents(n.parent, out, done);
        }
        done.add(n);
        out.add(n);
    }

    private void createNode(NodeSpec n) throws ToolException {
        JsonObject o = n.json;
        IModelElement existing = Args.bool(o, "reuse", true) ? findExisting(n) : null;
        if (existing != null) {
            reuseNode(n, existing);
            return;
        }
        IModelElement m = Kinds.create(n.kind.factory);
        n.model = m;
        n.created = true;
        m.setName(n.name);
        if (n.name != null && !n.name.isEmpty() && !n.name.equals(m.getName())) {
            warnings.add("node " + n.id + ": VP named it \"" + m.getName() + "\" because \"" + n.name
                    + "\" is already taken in its namespace (pass reuse=true to show the existing element)");
        }
        String doc = Args.str(o, "documentation", Args.str(o, "doc", null));
        if (doc != null) {
            m.setDocumentation(doc);
        }
        if ("note".equals(n.kind.name) || "text_annotation".equals(n.kind.name)) {
            m.setDescription(n.name); // a note shows its description, not its name
        }
        if (n.kind.stereotype != null) {
            m.addStereotype(n.kind.stereotype);
        }
        for (String st : Args.strings(o, "stereotypes")) {
            m.addStereotype(st);
        }
        String st1 = Args.str(o, "stereotype", null);
        if (st1 != null) {
            m.addStereotype(st1);
        }
        if ("abstract_class".equals(n.kind.name) || Args.bool(o, "abstract", false)) {
            invoke(m, "setAbstract", boolean.class, true);
        }
        if (m instanceof IClass) {
            addMembers((IClass) m, n.attrs, n.ops);
        } else if (!n.attrs.isEmpty() || !n.ops.isEmpty()) {
            addMembersReflective(m, n);
        }
        if (m instanceof IBPGateway) {
            applyGatewayType((IBPGateway) m, Args.str(o, "gateway", null));
        }
        if (n.kind.lane && family == Kinds.Family.ACTIVITY) {
            createPartition(n);
            return;
        }
        attachToParentModel(n);
        placeShape(n);
    }

    /** Model element kinds that are shared between diagrams by name (one actor "User" on many diagrams). */
    private static final Set<String> REUSE_KINDS = new HashSet<>(java.util.Arrays.asList(
            "class", "interface", "abstract_class", "enum", "actor", "usecase", "component", "node", "device",
            "execution_environment", "artifact", "package", "system", "subsystem", "external_entity", "entity",
            "lifeline", "interaction_actor"));

    /** An element of the same type and name in the same namespace (VP refuses duplicate names there). */
    private IModelElement findExisting(NodeSpec n) throws ToolException {
        if (n.name == null || n.name.isEmpty() || !REUSE_KINDS.contains(n.kind.name)
                || n.parent != null && n.parent.created) {
            return null; // a new parent has no existing children
        }
        String type = n.kind.factory.startsWith("create") ? n.kind.factory.substring(6) : null;
        if (type == null) {
            return null;
        }
        IModelElement wantedParent = n.parent != null ? n.parent.model
                : n.existingParent != null ? n.existingParent.getModelElement() : null;
        // project.toModelElementArray lists only top-level elements; children are searched in their parent
        IModelElement[] candidates = wantedParent != null ? wantedParent.toChildArray(type)
                : Vp.requireProject().toModelElementArray(type);
        if (candidates == null) {
            return null;
        }
        for (IModelElement e : candidates) {
            if (n.name.equals(e.getName())) {
                return e;
            }
        }
        return null;
    }

    /** Shows an existing model element: adds only the stereotypes and members it does not have yet. */
    private void reuseNode(NodeSpec n, IModelElement m) throws ToolException {
        n.model = m;
        n.created = false;
        JsonObject o = n.json;
        String doc = Args.str(o, "documentation", Args.str(o, "doc", null));
        if (doc != null) {
            m.setDocumentation(doc);
        }
        List<String> sts = new ArrayList<>(Args.strings(o, "stereotypes"));
        if (n.kind.stereotype != null) {
            sts.add(n.kind.stereotype);
        }
        if (Args.str(o, "stereotype", null) != null) {
            sts.add(Args.str(o, "stereotype", null));
        }
        for (String st : sts) {
            if (!m.hasStereotype(st)) {
                m.addStereotype(st);
            }
        }
        if (m instanceof IClass) {
            IClass c = (IClass) m;
            List<MemberSpec> attrs = new ArrayList<>();
            for (MemberSpec a : n.attrs) {
                if (c.getAttributeByName(a.name) == null) {
                    attrs.add(a);
                }
            }
            List<MemberSpec> ops = new ArrayList<>();
            for (MemberSpec op : n.ops) {
                if (c.getOperationByName(op.name) == null) {
                    ops.add(op);
                }
            }
            addMembers(c, attrs, ops);
        }
        warnings.add("node " + n.id + ": reused the existing " + m.getModelType() + " \"" + m.getName() + "\" ("
                + m.getId() + ")");
        placeShape(n);
    }

    private void placeShape(NodeSpec n) throws ToolException {
        IModelElement m = n.model;
        JsonObject o = n.json;
        IDiagramElement el = createShape(m);
        if (!(el instanceof IShapeUIModel)) {
            throw new ToolException("Visual Paradigm did not create a shape for node \"" + n.id + "\" (" + n.kind.name
                    + ") on a " + diagram.getType() + "; this kind is probably not allowed on this diagram type.");
        }
        n.shape = (IShapeUIModel) el;
        IDiagramElement parentShape = n.parent != null ? parentShapeOf(n.parent) : n.existingParent;
        if (parentShape != null && parentShape != n.shape) {
            try {
                parentShape.addChild(n.shape);
            } catch (Exception ex) {
                warnings.add("node " + n.id + ": could not be nested in its parent shape (" + ex.getMessage() + ")");
            }
        }
        int[] size = estimateSize(n);
        Integer w = Args.integer(o, "width");
        Integer h = Args.integer(o, "height");
        n.w = w != null ? w : size[0];
        n.h = h != null ? h : size[1];
        Integer x = Args.integer(o, "x");
        Integer y = Args.integer(o, "y");
        n.shape.setBounds(x != null ? x : 0, y != null ? y : 0, n.w, n.h);
        Styler.apply(n.shape, Args.obj(o, "style"), warnings);
    }

    /**
     * Creates the shape for a model element. Visual Paradigm's createDiagramElement(diagram, model) fails with a
     * NullPointerException when it finds no shape type for the model on that diagram; in that case the shape is
     * created by shape type (the model type, which is also the shape type for most elements) and bound to the model.
     */
    private IDiagramElement createShape(IModelElement m) throws ToolException {
        Throwable first;
        try {
            IDiagramElement el = dm.createDiagramElement(diagram, m);
            if (el != null) {
                return el;
            }
            first = null;
        } catch (Throwable t) {
            first = t;
        }
        String type = m.getModelType();
        try {
            IDiagramElement el = dm.createDiagramElement(diagram, type);
            if (el != null) {
                el.setModelElement(m);
                return el;
            }
        } catch (Throwable t) {
            if (first == null) {
                first = t;
            }
        }
        throw new ToolException("Visual Paradigm cannot show a " + type + " on a " + diagram.getType()
                + " (shape type allowed: " + diagram.isAllowShapeType(type) + ")"
                + (first == null ? "" : "; Open API error: " + first));
    }

    private IDiagramElement createConnector(IModelElement m, IDiagramElement from, IDiagramElement to)
            throws ToolException {
        Throwable first;
        try {
            IDiagramElement el = dm.createConnector(diagram, m, from, to, null);
            if (el != null) {
                return el;
            }
            first = null;
        } catch (Throwable t) {
            first = t;
        }
        String type = m.getModelType();
        try {
            IDiagramElement el = dm.createConnector(diagram, type, from, to, null);
            if (el != null) {
                el.setModelElement(m);
                return el;
            }
        } catch (Throwable t) {
            if (first == null) {
                first = t;
            }
        }
        throw new ToolException("Visual Paradigm cannot draw a " + type + " between " + from.getModelElement()
                .getModelType() + " and " + to.getModelElement().getModelType() + " on a " + diagram.getType()
                + (first == null ? "" : "; Open API error: " + first));
    }

    private IDiagramElement parentShapeOf(NodeSpec p) {
        if (p.partition != null) {
            IShapeUIModel comp = compartments.get(p.partition.getId());
            return comp != null ? comp : swimlaneShape;
        }
        return p.shape;
    }

    private void attachToParentModel(NodeSpec n) {
        IModelElement child = n.model;
        IModelElement parent = null;
        if (n.parent != null) {
            if (n.parent.partition != null) {
                n.parent.partition.addContainedElement(child);
                if (swimlaneModel != null) {
                    try {
                        swimlaneModel.addChild(child);
                    } catch (Exception ignored) {
                        // the partition reference is what matters
                    }
                }
                return;
            }
            parent = n.parent.model;
        } else if (n.existingParent != null) {
            parent = n.existingParent.getModelElement();
            if (parent instanceof IActivityPartition) {
                ((IActivityPartition) parent).addContainedElement(child);
                return;
            }
        }
        if (parent == null) {
            return;
        }
        try {
            parent.addChild(child);
        } catch (Exception ex) {
            warnings.add("node " + n.id + ": model could not be placed inside " + parent.getModelType()
                    + " (" + ex.getMessage() + "); it stays at project level");
        }
    }

    /** Activity partitions: all top-level partitions of the request become columns of one swimlane. */
    private void createPartition(NodeSpec n) throws ToolException {
        if (swimlaneModel == null) {
            swimlaneModel = (IActivitySwimlane2) Kinds.create("createActivitySwimlane2");
            swimlaneModel.setName("");
        }
        n.partition = (IActivityPartition) n.model;
        swimlaneModel.addVerticalPartition(n.partition);
    }

    /** One swimlane shape for all partitions, created after every partition is in the swimlane model. */
    private void createSwimlaneShape() throws ToolException {
        IDiagramElement el = createShape(swimlaneModel);
        if (!(el instanceof IShapeUIModel)) {
            throw new ToolException("Visual Paradigm did not create a swimlane on this diagram.");
        }
        swimlaneShape = (IShapeUIModel) el;
        List<IActivityPartition> parts = new ArrayList<>();
        for (NodeSpec n : nodes.values()) {
            if (n.partition != null) {
                parts.add(n.partition);
            }
        }
        compartments = Swimlanes.build(diagram, swimlaneShape, parts, warnings);
        for (NodeSpec n : nodes.values()) {
            if (n.partition != null) {
                n.shape = swimlaneShape;
                Styler.apply(swimlaneShape, Args.obj(n.json, "style"), warnings);
            }
        }
    }

    private void addMembers(IClass c, List<MemberSpec> attrs, List<MemberSpec> ops) {
        for (MemberSpec a : attrs) {
            IAttribute at = c.createAttribute();
            Members.fillAttribute(at, a);
        }
        for (MemberSpec o : ops) {
            IOperation op = c.createOperation();
            Members.fillOperation(op, o);
        }
    }

    private void addMembersReflective(IModelElement m, NodeSpec n) {
        for (MemberSpec a : n.attrs) {
            Object at = invokeRet(m, "createAttribute");
            if (at instanceof IAttribute) {
                Members.fillAttribute((IAttribute) at, a);
            } else {
                warnings.add("node " + n.id + ": " + m.getModelType() + " has no attributes; ignored " + a.name);
            }
        }
        for (MemberSpec o : n.ops) {
            Object op = invokeRet(m, "createOperation");
            if (op instanceof IOperation) {
                Members.fillOperation((IOperation) op, o);
            } else {
                warnings.add("node " + n.id + ": " + m.getModelType() + " has no operations; ignored " + o.name);
            }
        }
    }

    private void applyGatewayType(IBPGateway g, String type) {
        if (type == null) {
            return;
        }
        String f;
        switch (Args.norm(type)) {
            case "xor":
            case "exclusive":
                f = "createBPGatewayDataBasedXOR";
                break;
            case "and":
            case "parallel":
                f = "createBPGatewayAND";
                break;
            case "or":
            case "inclusive":
                f = "createBPGatewayOR";
                break;
            case "event":
            case "event_based":
                f = "createBPGatewayEventBasedXOR";
                break;
            case "complex":
                f = "createBPGatewayComplex";
                break;
            default:
                warnings.add("unknown gateway type \"" + type + "\" (use xor, and, or, event, complex)");
                return;
        }
        try {
            g.setGatewayType(Kinds.create(f));
        } catch (Exception ex) {
            warnings.add("gateway type not applied: " + ex.getMessage());
        }
    }

    private void createEdge(EdgeSpec e) throws ToolException {
        IModelElement m = Kinds.create(e.kind.factory);
        e.model = m;
        IDiagramElement fromShape = e.fromNode != null ? e.fromNode.shape : e.fromExisting;
        IDiagramElement toShape = e.toNode != null ? e.toNode.shape : e.toExisting;
        IModelElement fromModel = fromShape.getModelElement();
        IModelElement toModel = toShape.getModelElement();
        if (e.kind.reversed) {
            IDiagramElement t = fromShape;
            fromShape = toShape;
            toShape = t;
            IModelElement tm = fromModel;
            fromModel = toModel;
            toModel = tm;
        }
        JsonObject o = e.json;
        if (m instanceof IRelationship) {
            ((IRelationship) m).setFrom(fromModel);
            ((IRelationship) m).setTo(toModel);
        } else {
            invoke(m, "setFrom", IModelElement.class, fromModel);
            invoke(m, "setTo", IModelElement.class, toModel);
        }
        String label = Args.str(o, "label", Args.str(o, "name", null));
        String guard = Args.str(o, "guard", null);
        if (m instanceof IControlFlow && guard == null && label != null && label.startsWith("[") && label.endsWith("]")) {
            guard = label.substring(1, label.length() - 1);
            label = null;
        }
        if (label != null) {
            m.setName(label);
        }
        if (guard != null) {
            if (m instanceof IControlFlow) {
                ((IControlFlow) m).setGuard(guard);
            } else if (!invoke(m, "setGuard", String.class, guard)) {
                m.setName(label == null ? "[" + guard + "]" : label);
            }
        }
        String doc = Args.str(o, "documentation", null);
        if (doc != null) {
            m.setDocumentation(doc);
        }
        if (e.kind.stereotype != null) {
            m.addStereotype(e.kind.stereotype);
        }
        for (String st : Args.strings(o, "stereotypes")) {
            m.addStereotype(st);
        }
        String st1 = Args.str(o, "stereotype", null);
        if (st1 != null) {
            m.addStereotype(st1);
        }
        String cond = Args.str(o, "condition", null);
        if (cond != null) {
            invoke(m, "setCondition", String.class, cond);
        }
        if (Args.bool(o, "async", false)) {
            invoke(m, "setAsynchronous", boolean.class, true);
        }
        if (m instanceof IAssociation) {
            configureAssociation((IAssociation) m, e);
        }
        IDiagramElement conn = createConnector(m, fromShape, toShape);
        if (!(conn instanceof IConnectorUIModel)) {
            throw new ToolException("Visual Paradigm did not create a connector for edge \"" + e.id + "\" ("
                    + e.kind.name + " " + fromModel.getModelType() + " → " + toModel.getModelType()
                    + "); this relationship is probably not allowed between these elements.");
        }
        e.connector = (IConnectorUIModel) conn;
        String wanted = label != null ? label : "";
        if (m.getName() != null && !m.getName().equals(wanted) && !(guard != null && label == null)) {
            m.setName(wanted); // VP may name new connectors itself on an opened diagram
        }
        Styler.apply(e.connector, Args.obj(o, "style"), warnings);
    }

    private void configureAssociation(IAssociation a, EdgeSpec e) throws ToolException {
        JsonObject o = e.json;
        IRelationshipEnd fe = a.getFromEnd();
        IRelationshipEnd te = a.getToEnd();
        IAssociationEnd from = fe instanceof IAssociationEnd ? (IAssociationEnd) fe : null;
        IAssociationEnd to = te instanceof IAssociationEnd ? (IAssociationEnd) te : null;
        if (from == null || to == null) {
            warnings.add("edge " + e.id + ": association ends unavailable; multiplicity and roles skipped");
            return;
        }
        JsonObject mult = Args.obj(o, "multiplicity");
        if (mult != null) {
            String fm = Args.str(mult, "from", null);
            String tm = Args.str(mult, "to", null);
            if (fm != null) {
                from.setMultiplicity(fm);
            }
            if (tm != null) {
                to.setMultiplicity(tm);
            }
        }
        JsonObject roles = Args.obj(o, "roles");
        if (roles != null) {
            String fr = Args.str(roles, "from", null);
            String tr = Args.str(roles, "to", null);
            if (fr != null) {
                from.setName(fr);
            }
            if (tr != null) {
                to.setName(tr);
            }
        }
        if (e.kind.aggregation != null) {
            // the diamond sits at the whole, which is the edge's "from"
            from.setAggregationKind(e.kind.aggregation);
        }
        String nav = Args.norm(Args.str(o, "navigable", null));
        if (nav != null) {
            boolean f = nav.equals("from") || nav.equals("both");
            boolean t = nav.equals("to") || nav.equals("both");
            from.setNavigable(f ? IAssociationEnd.NAVIGABLE_NAVIGABLE : IAssociationEnd.NAVIGABLE_NON_NAVIGABLE);
            to.setNavigable(t ? IAssociationEnd.NAVIGABLE_NAVIGABLE : IAssociationEnd.NAVIGABLE_NON_NAVIGABLE);
            if (nav.equals("unspecified")) {
                from.setNavigable(IAssociationEnd.NAVIGABLE_UNSPECIFIED);
                to.setNavigable(IAssociationEnd.NAVIGABLE_UNSPECIFIED);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ sizes

    private int[] estimateSize(NodeSpec n) {
        String name = n.name == null ? "" : n.name;
        switch (n.kind.name) {
            case "class":
            case "interface":
            case "abstract_class":
            case "enum": {
                List<String> a = new ArrayList<>();
                for (MemberSpec m : n.attrs) {
                    a.add(m.displayText());
                }
                List<String> op = new ArrayList<>();
                for (MemberSpec m : n.ops) {
                    op.add(m.displayText());
                }
                String st = n.kind.stereotype != null ? n.kind.stereotype
                        : "interface".equals(n.kind.name) ? "Interface" : Args.str(n.json, "stereotype", null);
                return Sizer.classBox(name, st, a, op);
            }
            case "note":
            case "text_annotation":
                return Sizer.box(name, 180, 10, 8, 80, 40, false);
            case "actor":
            case "interaction_actor":
                // the figure is ~40px wide and connectors attach to the shape bounds; the name may overhang
                return new int[] {40, 70};
            case "usecase": {
                int[] b = Sizer.box(name, 130, 0, 0, 0, 0, false);
                return new int[] {Math.max(110, (int) (b[0] * 1.35) + 30), Math.max(46, b[1] + 26)};
            }
            case "initial":
                return new int[] {20, 20};
            case "final":
            case "flow_final":
                return new int[] {24, 24};
            case "decision":
            case "merge":
                return family == Kinds.Family.FLOWCHART ? flowDecision(name) : new int[] {30, 30};
            case "fork":
            case "join":
                return direction() == LayeredLayout.Direction.TB ? new int[] {120, 8} : new int[] {8, 90};
            case "action":
            case "call_activity":
            case "object":
            case "process":
            case "predefined_process":
            case "preparation":
            case "display":
            case "manual_input":
            case "document":
            case "database":
                if (family == Kinds.Family.DFD) {
                    int d = Math.max(80, Sizer.box(name, 100, 0, 0, 0, 0, false)[0] + 30);
                    return new int[] {d, d};
                }
                return Sizer.box(name, 160, 14, 10, 90, 40, false);
            case "data":
                return Sizer.box(name, 150, 28, 10, 110, 40, false);
            case "terminator":
                return Sizer.box(name, 150, 20, 8, 100, 36, false);
            case "start":
            case "end":
                return family == Kinds.Family.FLOWCHART ? Sizer.box(name, 150, 20, 8, 100, 36, false)
                        : family == Kinds.Family.BPMN ? new int[] {30, 30} : new int[] {24, 24};
            case "artifact":
            case "deployment_spec":
                return Sizer.box(name, 150, 30, 14, 110, 50, false);
            case "node":
            case "device":
            case "execution_environment":
            case "component":
            case "subsystem":
                return Sizer.box(name, 180, 30, 20, 130, 70, true);
            case "lifeline":
                return new int[] {Math.max(90, Sizer.textWidth(name, false) + 30), 360};
            case "task":
                return Sizer.box(name, 110, 12, 12, 100, 60, false);
            case "start_event":
            case "end_event":
            case "intermediate_event":
                return new int[] {30, 30};
            case "gateway":
                return new int[] {40, 40};
            case "data_object":
                return new int[] {30, 40};
            case "data_store":
                return Sizer.box(name, 150, 20, 12, 100, 40, false);
            case "external_entity":
            case "entity":
                return Sizer.box(name, 150, 16, 12, 100, 50, false);
            case "state":
                return Sizer.box(name, 150, 16, 12, 100, 40, true);
            default:
                if (n.kind.container) {
                    return Sizer.box(name, 200, 20, 20, 150, 90, true);
                }
                return Sizer.box(name, 160, 14, 10, 80, 40, false);
        }
    }

    private static int[] flowDecision(String name) {
        int[] b = Sizer.box(name, 120, 0, 0, 0, 0, false);
        return new int[] {Math.max(100, b[0] * 2 + 20), Math.max(60, b[1] * 2 + 20)};
    }

    // ------------------------------------------------------------------------------------------------ layout

    public LayeredLayout.Direction direction() {
        switch (family) {
            case USECASE:
            case DEPLOYMENT:
            case BPMN:
            case DFD:
            case COMPONENT:
                return LayeredLayout.Direction.LR;
            default:
                return LayeredLayout.Direction.TB;
        }
    }

    /** Lays out only the nodes of this request; with offset, they are placed next to the existing content. */
    public void layoutNew(LayeredLayout.Direction dir, boolean besideExisting) {
        if (family == Kinds.Family.SEQUENCE) {
            layoutSequence();
            return;
        }
        Map<NodeSpec, LayeredLayout.Node> map = new LinkedHashMap<>();
        LayeredLayout.Node lanesOwner = null;
        for (NodeSpec n : parentsFirst()) {
            LayeredLayout.Node ln = new LayeredLayout.Node(n.id, n.w, n.h);
            ln.lane = n.kind.lane;
            if (n.kind.container) {
                boolean stereo = n.kind.stereotype != null || n.json.has("stereotype");
                ln.header = n.kind.lane ? 28 : stereo ? 48 : 34;
                ln.padding = n.kind.lane ? 0 : 20;
            }
            map.put(n, ln);
        }
        List<LayeredLayout.Node> roots = new ArrayList<>();
        for (NodeSpec n : parentsFirst()) {
            LayeredLayout.Node ln = map.get(n);
            if (n.parent != null) {
                map.get(n.parent).add(ln);
            } else if (n.kind.lane && family == Kinds.Family.ACTIVITY) {
                if (lanesOwner == null) {
                    lanesOwner = new LayeredLayout.Node("<swimlane>", 0, 0);
                    lanesOwner.header = 0;
                    lanesOwner.padding = 0;
                    roots.add(lanesOwner);
                }
                lanesOwner.add(ln);
            } else if (n.existingParent == null) {
                roots.add(ln);
            }
        }
        if (lanesOwner != null) {
            // nodes outside every partition still go into the flow, in an unnamed lane on the left
            List<LayeredLayout.Node> loose = new ArrayList<>();
            for (LayeredLayout.Node r : roots) {
                if (r != lanesOwner) {
                    loose.add(r);
                }
            }
            if (!loose.isEmpty()) {
                LayeredLayout.Node extra = new LayeredLayout.Node("<outside>", 0, 0);
                extra.lane = true;
                extra.header = 0;
                for (LayeredLayout.Node r : loose) {
                    extra.add(r);
                }
                lanesOwner.children.add(0, extra);
                extra.parent = lanesOwner;
                roots.removeAll(loose);
            }
        }
        List<LayeredLayout.Edge> le = new ArrayList<>();
        for (EdgeSpec e : edges) {
            if (e.fromNode == null || e.toNode == null) {
                continue;
            }
            LayeredLayout.Node a = map.get(e.fromNode);
            LayeredLayout.Node b = map.get(e.toNode);
            le.add(e.kind.reversed ? new LayeredLayout.Edge(b, a) : new LayeredLayout.Edge(a, b));
        }
        new LayeredLayout().run(roots, le, dir);
        int dx = 0;
        int dy = 0;
        if (besideExisting) {
            int[] ext = existingExtent();
            if (ext != null) {
                if (dir == LayeredLayout.Direction.TB) {
                    dy = ext[1] + 40;
                } else {
                    dx = ext[0] + 40;
                }
            }
        }
        if (lanesOwner != null && swimlaneShape != null) {
            swimlaneShape.setBounds(lanesOwner.x + dx, lanesOwner.y + dy, lanesOwner.w, lanesOwner.h);
            arrangeSwimlaneCompartments(lanesOwner, dx, dy);
        }
        for (Map.Entry<NodeSpec, LayeredLayout.Node> en : map.entrySet()) {
            NodeSpec n = en.getKey();
            LayeredLayout.Node ln = en.getValue();
            if (n.partition != null || n.shape == null) {
                continue;
            }
            if (n.existingParent != null) {
                continue;
            }
            n.shape.setBounds(ln.x + dx, ln.y + dy, ln.w, ln.h);
        }
        placeInExistingContainers(map);
    }

    /** New children of containers that already exist: stacked below the container's current content. */
    private void placeInExistingContainers(Map<NodeSpec, LayeredLayout.Node> map) {
        Map<IDiagramElement, List<NodeSpec>> byParent = new LinkedHashMap<>();
        for (NodeSpec n : nodes.values()) {
            if (n.existingParent != null && n.shape != null) {
                byParent.computeIfAbsent(n.existingParent, k -> new ArrayList<>()).add(n);
            }
        }
        for (Map.Entry<IDiagramElement, List<NodeSpec>> en : byParent.entrySet()) {
            IDiagramElement p = en.getKey();
            int bottom = p.getY() + 40;
            for (IShapeUIModel c : p.toChildArray()) {
                boolean isNew = false;
                for (NodeSpec n : en.getValue()) {
                    isNew |= n.shape == c;
                }
                if (!isNew) {
                    bottom = Math.max(bottom, c.getY() + c.getHeight());
                }
            }
            int y = bottom + 20;
            int maxRight = p.getX() + p.getWidth();
            for (NodeSpec n : en.getValue()) {
                LayeredLayout.Node ln = map.get(n);
                int x = p.getX() + Math.max(20, (p.getWidth() - ln.w) / 2);
                n.shape.setBounds(x, y, ln.w, ln.h);
                y += ln.h + 20;
                maxRight = Math.max(maxRight, x + ln.w + 20);
            }
            p.setBounds(p.getX(), p.getY(), maxRight - p.getX(), Math.max(p.getHeight(), y - p.getY()));
        }
    }

    /** Sizes the swimlane's partition compartments to the computed columns, where VP exposes them. */
    private void arrangeSwimlaneCompartments(LayeredLayout.Node owner, int dx, int dy) {
        Map<String, java.awt.Rectangle> lanes = new HashMap<>();
        for (NodeSpec n : nodes.values()) {
            if (n.partition != null) {
                for (LayeredLayout.Node l : owner.children) {
                    if (l.id.equals(n.id)) {
                        lanes.put(n.partition.getId(), new java.awt.Rectangle(l.x + dx, l.y + dy, l.w, l.h));
                    }
                }
            }
        }
        try {
            Swimlanes.arrange(swimlaneShape, lanes);
        } catch (Exception ex) {
            warnings.add("swimlane partitions not resized: " + ex.getMessage());
        }
    }

    /** Lifelines side by side; messages top to bottom in request order. */
    private void layoutSequence() {
        int x = 40;
        int top = 40;
        Map<NodeSpec, Integer> centre = new HashMap<>();
        int rows = edges.size();
        int height = Math.max(300, 120 + rows * 45 + 60);
        for (NodeSpec n : nodes.values()) {
            if (n.shape == null) {
                continue;
            }
            int w = n.w;
            int h = "lifeline".equals(n.kind.name) || "interaction_actor".equals(n.kind.name) ? height : n.h;
            n.shape.setBounds(x, top, w, h);
            centre.put(n, x + w / 2);
            x += w + 60;
        }
        int y = top + 90;
        for (EdgeSpec e : edges) {
            if (e.connector != null && e.fromNode != null && e.toNode != null) {
                try {
                    e.connector.clearPoints();
                    e.connector.addPoint(new Point(centre.get(e.fromNode), y));
                    e.connector.addPoint(new Point(centre.get(e.toNode), y));
                } catch (Exception ex) {
                    warnings.add("edge " + e.id + ": message position not set (" + ex.getMessage() + ")");
                }
            }
            y += 45;
        }
    }

    private int[] existingExtent() {
        int maxX = -1;
        int maxY = -1;
        Set<IDiagramElement> mine = new HashSet<>();
        for (NodeSpec n : nodes.values()) {
            if (n.shape != null) {
                mine.add(n.shape);
            }
        }
        for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
            if (mine.contains(s)) {
                continue;
            }
            maxX = Math.max(maxX, s.getX() + s.getWidth());
            maxY = Math.max(maxY, s.getY() + s.getHeight());
        }
        return maxX < 0 ? null : new int[] {maxX, maxY};
    }

    /**
     * Shapes and connectors created through the Open API start with empty (0×0) captions, so class names and
     * multiplicities are not drawn or are drawn in the wrong place; ask VP to recompute every caption.
     */
    public static void resetCaptions(IDiagramUIModel d) {
        for (IDiagramElement e : d.toDiagramElementArray()) {
            try {
                e.resetCaption();
                e.resetCaptionSize();
            } catch (Throwable ignored) {
                // some element types have no caption
            }
        }
    }

    /**
     * Final pass after any layout: connectors keep the points computed when they were created (shapes still at
     * 0,0), so they are re-routed — by the plugin on rectilinear diagrams, by VP otherwise — and captions reset.
     */
    public static void finish(IDiagramUIModel d, LayeredLayout.Direction dir, List<String> warnings) {
        boolean sequence = "InteractionDiagram".equals(d.getType());
        if (Router.isRectilinear(d) && !sequence) {
            Router.route(d, dir, warnings);
        } else {
            for (IConnectorUIModel c : d.toConnectorUIModelArray()) {
                try {
                    c.setRequestRebuild(true);
                } catch (Throwable ignored) {
                    // best effort
                }
            }
        }
        resetCaptions(d);
    }

    // ------------------------------------------------------------------------------------------------ results

    public JsonObject nodesJson() {
        JsonObject out = new JsonObject();
        for (NodeSpec n : nodes.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", n.kind.name);
            o.addProperty("model_id", n.model == null ? null : n.model.getId());
            o.addProperty("model_type", n.model == null ? null : n.model.getModelType());
            o.addProperty("shape_id", n.shape == null ? null : n.shape.getId());
            if (!n.created && n.model != null) {
                o.addProperty("reused", true);
            }
            if (n.shape != null && n.partition == null) {
                JsonArray b = new JsonArray();
                b.add(n.shape.getX());
                b.add(n.shape.getY());
                b.add(n.shape.getWidth());
                b.add(n.shape.getHeight());
                o.add("bounds", b);
            }
            out.add(n.id, o);
        }
        return out;
    }

    public JsonObject edgesJson() {
        JsonObject out = new JsonObject();
        for (EdgeSpec e : edges) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", e.kind.name);
            o.addProperty("from", e.fromRef);
            o.addProperty("to", e.toRef);
            o.addProperty("model_id", e.model == null ? null : e.model.getId());
            o.addProperty("model_type", e.model == null ? null : e.model.getModelType());
            o.addProperty("connector_id", e.connector == null ? null : e.connector.getId());
            out.add(e.id, o);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ reflection

    static boolean invoke(Object target, String method, Class<?> type, Object value) {
        try {
            Method m = target.getClass().getMethod(method, type);
            m.invoke(target, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static Object invokeRet(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }
}

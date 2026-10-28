package vpmcp.create.vp;

import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.factory.IModelElementFactory;
import vpmcp.create.core.ToolException;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Vocabulary of the tools: diagram types, node kinds and edge kinds, each mapped to the Open API factory method
 * that creates its model element. Names not in the tables are tried as factory methods by reflection
 * ("state" → createState(), "raw:BPTextAnnotation" → createBPTextAnnotation()), which opens up the remaining
 * Visual Paradigm element types without code changes.
 */
public final class Kinds {

    /** Diagram family; selects defaults (layout direction, default edge kind) and kind overrides. */
    public enum Family { CLASS, USECASE, ACTIVITY, DEPLOYMENT, SEQUENCE, COMPONENT, BPMN, FLOWCHART, DFD, STATE, OTHER }

    public static final class DiagramType {
        public final String vpType;
        public final Family family;

        DiagramType(String vpType, Family family) {
            this.vpType = vpType;
            this.family = family;
        }
    }

    public static final class NodeKind {
        public final String name;
        public final String factory;
        public final String stereotype;
        public final boolean container;
        public final boolean lane;

        NodeKind(String name, String factory, String stereotype, boolean container, boolean lane) {
            this.name = name;
            this.factory = factory;
            this.stereotype = stereotype;
            this.container = container;
            this.lane = lane;
        }
    }

    public static final class EdgeKind {
        public final String name;
        public final String factory;
        /** Model "from" is the edge's "to" (generalization and realization point at the general element). */
        public final boolean reversed;
        public final String aggregation;
        public final String stereotype;

        EdgeKind(String name, String factory, boolean reversed, String aggregation, String stereotype) {
            this.name = name;
            this.factory = factory;
            this.reversed = reversed;
            this.aggregation = aggregation;
            this.stereotype = stereotype;
        }
    }

    private static final Map<String, DiagramType> DIAGRAMS = new LinkedHashMap<>();
    private static final Map<String, NodeKind> NODES = new LinkedHashMap<>();
    private static final Map<Family, Map<String, NodeKind>> NODE_OVERRIDES = new HashMap<>();
    private static final Map<String, EdgeKind> EDGES = new LinkedHashMap<>();
    private static final Map<Family, Map<String, EdgeKind>> EDGE_OVERRIDES = new HashMap<>();
    private static final Map<Family, String> DEFAULT_EDGE = new HashMap<>();

    static {
        diagram(Family.CLASS, "ClassDiagram", "class", "class_diagram");
        diagram(Family.USECASE, "UseCaseDiagram", "usecase", "use_case", "use_case_diagram");
        diagram(Family.ACTIVITY, "ActivityDiagram", "activity", "activity_diagram");
        diagram(Family.DEPLOYMENT, "DeploymentDiagram", "deployment", "deployment_diagram");
        diagram(Family.SEQUENCE, "InteractionDiagram", "sequence", "interaction", "sequence_diagram");
        diagram(Family.COMPONENT, "ComponentDiagram", "component", "component_diagram");
        diagram(Family.BPMN, "BusinessProcessDiagram", "bpmn", "business_process", "bpd");
        diagram(Family.FLOWCHART, "Flowchart", "flowchart", "block", "block_scheme", "блок_схема");
        diagram(Family.DFD, "DataFlowDiagram", "dfd", "data_flow", "context", "context_diagram");
        diagram(Family.STATE, "StateDiagram", "state", "state_machine", "statechart");
        diagram(Family.OTHER, "PackageDiagram", "package", "package_diagram");
        diagram(Family.OTHER, "ObjectDiagram", "object", "object_diagram");
        diagram(Family.OTHER, "CommunicationDiagram", "communication", "communication_diagram");
        diagram(Family.OTHER, "CompositeStructureDiagram", "composite_structure");
        diagram(Family.OTHER, "InteractionOverviewDiagram", "interaction_overview");
        diagram(Family.OTHER, "ERDiagram", "er", "erd", "entity_relationship");
        diagram(Family.OTHER, "BlockDefinitionDiagram", "sysml_block", "block_definition", "bdd");

        // class / package
        node("class", "createClass", null, false, "class");
        node("interface", "createClass", "Interface", false, "interface");
        node("abstract_class", "createClass", null, false, "abstract");
        node("enum", "createClass", "enumeration", false, "enumeration");
        node("package", "createPackage", null, true, "package");
        node("note", "createNOTE", null, false, "note", "comment");
        // use case
        node("actor", "createActor", null, false, "actor");
        node("usecase", "createUseCase", null, false, "usecase", "use_case");
        node("system", "createSystem", null, true, "system", "boundary", "system_boundary", "subject");
        // activity
        node("initial", "createInitialNode", null, false, "initial", "initial_node");
        node("final", "createActivityFinalNode", null, false, "final", "activity_final", "end");
        node("flow_final", "createFlowFinalNode", null, false, "flow_final");
        node("action", "createActivityAction", null, false, "action", "activity_action");
        node("call_activity", "createActivity", null, false, "call_activity", "subactivity");
        node("decision", "createDecisionNode", null, false, "decision", "decision_node");
        node("merge", "createMergeNode", null, false, "merge", "merge_node");
        node("fork", "createForkNode", null, false, "fork", "fork_node");
        node("join", "createJoinNode", null, false, "join", "join_node");
        node("object", "createObjectNode", null, false, "object", "object_node");
        lane("partition", "createActivityPartition", "partition", "swimlane", "lane");
        // deployment / component
        node("node", "createNode", null, true, "node");
        node("device", "createNode", "device", true, "device");
        node("execution_environment", "createNode", "execution environment", true,
                "execution_environment", "exec_env", "environment");
        node("artifact", "createArtifact", null, false, "artifact");
        node("component", "createComponent", null, true, "component");
        node("deployment_spec", "createDeploymentSpecification", null, false, "deployment_spec",
                "deployment_specification");
        node("instance", "createInstanceSpecification", null, false, "instance", "instance_specification");
        node("subsystem", "createSubsystem", null, true, "subsystem");
        // sequence
        node("lifeline", "createInteractionLifeLine", null, false, "lifeline", "participant");
        node("interaction_actor", "createInteractionActor", null, false, "interaction_actor");
        // BPMN
        node("pool", "createBPPool", null, true, "pool");
        node("task", "createBPTask", null, false, "task");
        node("start_event", "createBPStartEvent", null, false, "start_event");
        node("end_event", "createBPEndEvent", null, false, "end_event");
        node("intermediate_event", "createBPIntermediateEvent", null, false, "intermediate_event");
        node("gateway", "createBPGateway", null, false, "gateway");
        node("subprocess", "createBPSubProcess", null, true, "subprocess", "sub_process");
        node("data_object", "createBPDataObject", null, false, "data_object");
        node("data_store", "createBPDataStore", null, false, "data_store");
        node("text_annotation", "createBPTextAnnotation", null, false, "text_annotation", "annotation");
        // flowchart
        node("terminator", "createFlowchartTerminator", null, false, "terminator");
        node("process", "createFlowchartProcess", null, false, "process");
        node("predefined_process", "createFlowchartPredefinedProcess", null, false, "predefined_process",
                "subroutine");
        node("data", "createFlowchartData", null, false, "data", "io", "input_output");
        node("document", "createFlowchartDocument", null, false, "document");
        node("database", "createFlowchartDatabase", null, false, "database");
        node("manual_input", "createFlowchartManualInput", null, false, "manual_input");
        node("preparation", "createFlowchartPreparation", null, false, "preparation");
        node("display", "createFlowchartDisplay", null, false, "display");
        node("connector", "createFlowchartOnPageReference", null, false, "connector", "on_page_reference");
        // state
        node("state", "createState2", null, true, "state");

        override(Family.SEQUENCE, "actor", "createInteractionActor", false);
        override(Family.BPMN, "start", "createBPStartEvent", false);
        override(Family.BPMN, "initial", "createBPStartEvent", false);
        override(Family.BPMN, "end", "createBPEndEvent", false);
        override(Family.BPMN, "final", "createBPEndEvent", false);
        override(Family.BPMN, "action", "createBPTask", false);
        override(Family.BPMN, "decision", "createBPGateway", false);
        overrideLane(Family.BPMN, "lane", "createBPLane");
        overrideLane(Family.BPMN, "partition", "createBPLane");
        override(Family.FLOWCHART, "start", "createFlowchartTerminator", false);
        override(Family.FLOWCHART, "initial", "createFlowchartTerminator", false);
        override(Family.FLOWCHART, "end", "createFlowchartTerminator", false);
        override(Family.FLOWCHART, "final", "createFlowchartTerminator", false);
        override(Family.FLOWCHART, "action", "createFlowchartProcess", false);
        override(Family.FLOWCHART, "decision", "createFlowchartDecision", false);
        override(Family.FLOWCHART, "merge", "createFlowchartMerge", false);
        override(Family.DFD, "process", "createDFProcess", false);
        override(Family.DFD, "external_entity", "createDFExternalEntity", false);
        override(Family.DFD, "entity", "createDFExternalEntity", false);
        override(Family.DFD, "actor", "createDFExternalEntity", false);
        override(Family.DFD, "data_store", "createDFDataStore", false);
        override(Family.STATE, "initial", "createInitialPseudoState", false);
        override(Family.STATE, "start", "createInitialPseudoState", false);
        override(Family.STATE, "final", "createFinalState", false);
        override(Family.STATE, "end", "createFinalState", false);

        edge("association", "createAssociation", false, null, null, "association", "assoc", "link",
                "communication", "communication_path");
        edge("aggregation", "createAssociation", false, "Aggregation", null, "aggregation", "shared");
        edge("composition", "createAssociation", false, "Composited", null, "composition", "composite");
        edge("generalization", "createGeneralization", true, null, null, "generalization", "inheritance",
                "extends", "inherits");
        edge("realization", "createRealization", true, null, null, "realization", "implements", "realizes");
        edge("dependency", "createDependency", false, null, null, "dependency", "depends");
        edge("usage", "createUsage", false, null, null, "usage", "use", "uses");
        edge("include", "createInclude", false, null, null, "include", "includes");
        edge("extend", "createExtend", true, null, null, "extend");
        edge("control_flow", "createControlFlow", false, null, null, "control_flow", "flow");
        edge("object_flow", "createObjectFlow", false, null, null, "object_flow");
        edge("deploy", "createDependency", false, null, "deploy", "deploy", "deployment");
        edge("manifest", "createDependency", false, null, "manifest", "manifest", "manifestation");
        edge("anchor", "createAnchor", false, null, null, "anchor", "note_link", "comment_link");
        edge("message", "createMessage", false, null, null, "message", "sync", "call");
        edge("sequence_flow", "createBPSequenceFlow", false, null, null, "sequence_flow");
        edge("message_flow", "createBPMessageFlow", false, null, null, "message_flow");
        edge("bpmn_association", "createBPAssociation", false, null, null, "bpmn_association");
        edge("flowline", "createFlowchartFlowline", false, null, null, "flowline");
        edge("data_flow", "createDFDataFlow", false, null, null, "data_flow");
        edge("transition", "createTransition2", false, null, null, "transition");

        edgeOverride(Family.BPMN, "flow", "createBPSequenceFlow");
        edgeOverride(Family.BPMN, "association", "createBPAssociation");
        edgeOverride(Family.FLOWCHART, "flow", "createFlowchartFlowline");
        edgeOverride(Family.FLOWCHART, "control_flow", "createFlowchartFlowline");
        edgeOverride(Family.DFD, "flow", "createDFDataFlow");
        edgeOverride(Family.STATE, "flow", "createTransition2");

        DEFAULT_EDGE.put(Family.CLASS, "association");
        DEFAULT_EDGE.put(Family.USECASE, "association");
        DEFAULT_EDGE.put(Family.ACTIVITY, "control_flow");
        DEFAULT_EDGE.put(Family.DEPLOYMENT, "association");
        DEFAULT_EDGE.put(Family.SEQUENCE, "message");
        DEFAULT_EDGE.put(Family.COMPONENT, "dependency");
        DEFAULT_EDGE.put(Family.BPMN, "sequence_flow");
        DEFAULT_EDGE.put(Family.FLOWCHART, "flowline");
        DEFAULT_EDGE.put(Family.DFD, "data_flow");
        DEFAULT_EDGE.put(Family.STATE, "transition");
        DEFAULT_EDGE.put(Family.OTHER, "association");
    }

    private Kinds() {
    }

    private static void diagram(Family f, String vpType, String... aliases) {
        DiagramType t = new DiagramType(vpType, f);
        DIAGRAMS.put(Args.norm(vpType), t);
        for (String a : aliases) {
            DIAGRAMS.put(a, t);
        }
    }

    private static void node(String name, String factory, String stereo, boolean container, String... aliases) {
        NodeKind k = new NodeKind(name, factory, stereo, container, false);
        NODES.put(name, k);
        for (String a : aliases) {
            NODES.put(a, k);
        }
    }

    private static void lane(String name, String factory, String... aliases) {
        NodeKind k = new NodeKind(name, factory, null, true, true);
        NODES.put(name, k);
        for (String a : aliases) {
            NODES.put(a, k);
        }
    }

    private static void override(Family f, String alias, String factory, boolean container) {
        NODE_OVERRIDES.computeIfAbsent(f, x -> new HashMap<>())
                .put(alias, new NodeKind(alias, factory, null, container, false));
    }

    private static void overrideLane(Family f, String alias, String factory) {
        NODE_OVERRIDES.computeIfAbsent(f, x -> new HashMap<>()).put(alias, new NodeKind("lane", factory, null, true, true));
    }

    private static void edge(String name, String factory, boolean reversed, String aggr, String stereo,
                             String... aliases) {
        EdgeKind k = new EdgeKind(name, factory, reversed, aggr, stereo);
        EDGES.put(name, k);
        for (String a : aliases) {
            EDGES.put(a, k);
        }
    }

    private static void edgeOverride(Family f, String alias, String factory) {
        EDGE_OVERRIDES.computeIfAbsent(f, x -> new HashMap<>()).put(alias, new EdgeKind(alias, factory, false, null, null));
    }

    public static DiagramType diagramType(String type) throws ToolException {
        if (type == null) {
            throw new ToolException("\"type\" is required, one of: " + new TreeSet<>(DIAGRAMS.keySet()));
        }
        DiagramType t = DIAGRAMS.get(Args.norm(type));
        if (t == null) {
            throw new ToolException("Unknown diagram type \"" + type + "\". Known: " + new TreeSet<>(DIAGRAMS.keySet()));
        }
        return t;
    }

    public static Family familyOf(String vpDiagramType) {
        DiagramType t = DIAGRAMS.get(Args.norm(vpDiagramType));
        return t == null ? Family.OTHER : t.family;
    }

    public static String defaultEdge(Family f) {
        return DEFAULT_EDGE.getOrDefault(f, "association");
    }

    public static NodeKind nodeKind(Family f, String kind) throws ToolException {
        String k = Args.norm(kind);
        if (k == null || k.isEmpty()) {
            throw new ToolException("Node \"kind\" is required.");
        }
        NodeKind o = NODE_OVERRIDES.getOrDefault(f, Collections.emptyMap()).get(k);
        if (o != null) {
            return o;
        }
        NodeKind n = NODES.get(k);
        if (n != null) {
            return n;
        }
        String method = reflectiveName(kind);
        if (factoryMethod(method) != null) {
            return new NodeKind(k, method, null, false, false);
        }
        throw new ToolException("Unknown node kind \"" + kind + "\". Known: " + new TreeSet<>(NODES.keySet())
                + "; any Open API model type can be given as \"raw:<Type>\", e.g. \"raw:BPTextAnnotation\".");
    }

    public static EdgeKind edgeKind(Family f, String kind) throws ToolException {
        String k = Args.norm(kind == null ? defaultEdge(f) : kind);
        EdgeKind o = EDGE_OVERRIDES.getOrDefault(f, Collections.emptyMap()).get(k);
        if (o != null) {
            return o;
        }
        EdgeKind e = EDGES.get(k);
        if (e != null) {
            return e;
        }
        String method = reflectiveName(kind);
        if (factoryMethod(method) != null) {
            return new EdgeKind(k, method, false, null, null);
        }
        throw new ToolException("Unknown edge kind \"" + kind + "\". Known: " + new TreeSet<>(EDGES.keySet())
                + "; any Open API relationship type can be given as \"raw:<Type>\".");
    }

    public static java.util.Set<String> diagramTypeNames() {
        return new TreeSet<>(DIAGRAMS.keySet());
    }

    public static java.util.Set<String> nodeKindNames() {
        return new TreeSet<>(NODES.keySet());
    }

    public static java.util.Set<String> edgeKindNames() {
        return new TreeSet<>(EDGES.keySet());
    }

    /** "raw:BPTask" → createBPTask, "flow_final" → createFlowFinal. */
    static String reflectiveName(String kind) {
        String k = kind.trim();
        if (k.regionMatches(true, 0, "raw:", 0, 4)) {
            return "create" + k.substring(4).trim();
        }
        StringBuilder sb = new StringBuilder("create");
        for (String part : k.split("[_\\s-]+")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    static Method factoryMethod(String name) {
        try {
            Method m = IModelElementFactory.class.getMethod(name);
            return IModelElement.class.isAssignableFrom(m.getReturnType()) ? m : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static IModelElement create(String factoryMethod) throws ToolException {
        Method m = factoryMethod(factoryMethod);
        if (m == null) {
            throw new ToolException("This Visual Paradigm has no IModelElementFactory." + factoryMethod + "().");
        }
        try {
            return (IModelElement) m.invoke(IModelElementFactory.instance());
        } catch (Exception e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new ToolException(factoryMethod + "() failed: " + c, c);
        }
    }
}

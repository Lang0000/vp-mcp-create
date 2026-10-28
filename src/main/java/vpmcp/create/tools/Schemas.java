package vpmcp.create.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** JSON Schema fragments shared by the tools. */
final class Schemas {

    private Schemas() {
    }

    static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    static final String STYLE = "{\"type\":\"object\",\"description\":\"Formatting. Either the fields reported by "
            + "get_diagram_by_url ({\\\"fill\\\":{\\\"color1\\\":\\\"#FFFFFF\\\"},\\\"line\\\":{\\\"color\\\":\\\"#000000\\\","
            + "\\\"weight\\\":1,\\\"style\\\":\\\"dashed\\\"},\\\"font\\\":{\\\"name\\\":\\\"Arial\\\",\\\"size\\\":12,"
            + "\\\"color\\\":\\\"#000000\\\",\\\"is_bold\\\":true,\\\"is_italic\\\":false}}) or flat shortcuts: fill, "
            + "line_color, line_weight, line_style, font_color, font_size, font_name, bold, italic. Colours are "
            + "#RRGGBB; fill \\\"none\\\" makes the shape transparent.\",\"additionalProperties\":true}";

    static final String MEMBER = "{\"description\":\"UML text such as \\\"+path: str\\\", \\\"-count: int = 0\\\", "
            + "\\\"+load(path: str): Tensor\\\", \\\"{static} +instance(): App\\\", or an object {name, type | "
            + "return_type, visibility, initial, multiplicity, static, abstract, parameters, documentation}.\","
            + "\"anyOf\":[{\"type\":\"string\"},{\"type\":\"object\"}]}";

    static String node(String kinds) {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"id\":{\"type\":\"string\",\"description\":\"Your id for this node, used by edges and parent. "
                + "The response maps it to Visual Paradigm ids.\"},"
                + "\"kind\":{\"type\":\"string\",\"description\":\"" + kinds + "\"},"
                + "\"name\":{\"type\":\"string\"},"
                + "\"parent\":{\"type\":\"string\",\"description\":\"Id of the container: system boundary, package, "
                + "deployment node, activity partition, BPMN pool or lane. May also be a VP id of an existing "
                + "container on the diagram.\"},"
                + "\"attributes\":{\"type\":\"array\",\"items\":" + MEMBER + "},"
                + "\"operations\":{\"type\":\"array\",\"items\":" + MEMBER + "},"
                + "\"stereotype\":{\"type\":\"string\"},"
                + "\"stereotypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"documentation\":{\"type\":\"string\"},"
                + "\"abstract\":{\"type\":\"boolean\"},"
                + "\"gateway\":{\"type\":\"string\",\"enum\":[\"xor\",\"and\",\"or\",\"event\",\"complex\"]},"
                + "\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"},"
                + "\"width\":{\"type\":\"integer\"},\"height\":{\"type\":\"integer\",\"description\":\"Sizes are "
                + "computed from the text when omitted.\"},"
                + "\"style\":" + STYLE + "},\"required\":[\"kind\"]}";
    }

    static String edge(String kinds) {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"id\":{\"type\":\"string\"},"
                + "\"from\":{\"type\":\"string\",\"description\":\"Node id from this request, or a VP shape/model id "
                + "already on the diagram. For generalization/realization: the child / implementing element. For "
                + "aggregation/composition: the whole (diamond end). For include: the base use case; for extend: "
                + "the extending use case.\"},"
                + "\"to\":{\"type\":\"string\"},"
                + "\"kind\":{\"type\":\"string\",\"description\":\"" + kinds + "\"},"
                + "\"label\":{\"type\":\"string\",\"description\":\"Connector name. On control flows a label in "
                + "square brackets becomes the guard.\"},"
                + "\"guard\":{\"type\":\"string\"},"
                + "\"multiplicity\":{\"type\":\"object\",\"properties\":{\"from\":{\"type\":\"string\"},"
                + "\"to\":{\"type\":\"string\"}}},"
                + "\"roles\":{\"type\":\"object\",\"properties\":{\"from\":{\"type\":\"string\"},"
                + "\"to\":{\"type\":\"string\"}}},"
                + "\"navigable\":{\"type\":\"string\",\"enum\":[\"to\",\"from\",\"both\",\"none\",\"unspecified\"]},"
                + "\"stereotype\":{\"type\":\"string\"},"
                + "\"condition\":{\"type\":\"string\",\"description\":\"Extend condition.\"},"
                + "\"async\":{\"type\":\"boolean\",\"description\":\"Asynchronous message (sequence).\"},"
                + "\"documentation\":{\"type\":\"string\"},"
                + "\"style\":" + STYLE + "},\"required\":[\"from\",\"to\"]}";
    }

    static final String NODE_KINDS = "Class: class, interface, abstract_class, enum, package, note. "
            + "Use case: actor, usecase, system (boundary). Activity: initial, final, flow_final, action, "
            + "call_activity, decision, merge, fork, join, object, partition (swimlane column). Deployment: node, "
            + "device, execution_environment, artifact, component, deployment_spec. Sequence: lifeline, actor. "
            + "BPMN: pool, lane, task, start_event, end_event, intermediate_event, gateway, subprocess, "
            + "data_object, data_store. Flowchart: terminator (start/end), process, decision, data, document, "
            + "predefined_process, database. DFD/context: process, external_entity, data_store. State: state, "
            + "initial, final. Any other Open API type: \\\"raw:<ModelType>\\\".";

    static final String EDGE_KINDS = "association, aggregation, composition, generalization, realization, "
            + "dependency, usage, include, extend, control_flow, object_flow, deploy, manifest, anchor (note link), "
            + "message, sequence_flow, message_flow, flowline, data_flow, transition, or \\\"raw:<ModelType>\\\". "
            + "Default depends on the diagram: association (class, use case, deployment), control_flow (activity), "
            + "message (sequence), sequence_flow (BPMN), flowline (flowchart), data_flow (DFD), transition (state).";

    static final String LAYOUT = "{\"type\":\"string\",\"description\":\"layered (default for new diagrams: the "
            + "plugin's own layered layout that sizes containers and swimlanes), none, grid, or one of Visual "
            + "Paradigm's: hierarchic, orthogonal, organic, smart_organic, tree, directed_tree, balloon_tree, "
            + "compact_tree, circular, compact, route_orthogonal, route_organic. 'auto' = layered.\"}";

    static final String DIRECTION = "{\"type\":\"string\",\"enum\":[\"TB\",\"LR\"],\"description\":\"Flow "
            + "direction for the layered layout. Default: LR for use case, deployment, component, BPMN and DFD, "
            + "TB otherwise.\"}";

    static final String CONNECTOR_STYLE = "{\"type\":\"string\",\"enum\":[\"straight\",\"rectilinear\",\"curved\","
            + "\"round_straight\",\"round_rectilinear\"],\"description\":\"Default: rectilinear for activity, "
            + "flowchart and BPMN, straight otherwise.\"}";

    static JsonArray required(String... names) {
        JsonArray a = new JsonArray();
        for (String n : names) {
            a.add(n);
        }
        return a;
    }
}

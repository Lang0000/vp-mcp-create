# vp-mcp-create — create Visual Paradigm diagrams over MCP

**English** · [Українська](README.uk.md)

A plugin for [Visual Paradigm](https://www.visual-paradigm.com/) (tested with Community Edition 18.1 on macOS)
that runs a [Model Context Protocol](https://modelcontextprotocol.io/) server inside Visual Paradigm. An MCP client
such as Claude Code can then **create, change, lay out, export and save diagrams** in the project that is open in
Visual Paradigm: class, use case, activity (with swimlanes), deployment, component, sequence, flowchart, state
and more. You describe nodes and edges as JSON; the plugin creates real UML model elements, sizes the shapes to
their text, lays the diagram out and routes the connectors.

```
MCP client (Claude Code, ...)  ──HTTP, JSON-RPC 2.0──▶  127.0.0.1:8900/mcp
                                                           │  plugin vpmcp.create inside Visual Paradigm
                                                           ▼
                                          Visual Paradigm Open API  →  the open .vpp project
```

## How it works

- **Visual Paradigm plugin.** `plugin.xml` registers the plugin; on start (`VpCreatePlugin`) it opens a small
  HTTP server on `127.0.0.1:8900`. Visual Paradigm's bundled JRE has no `jdk.httpserver`, so the server is a
  minimal socket-based implementation of MCP's *Streamable HTTP* transport (`core/McpHttpServer`,
  `core/McpProtocol`).
- **UI thread.** Every tool call runs on Visual Paradigm's Swing event thread (`vp/EdtInvoker`), because the Open
  API is not thread-safe. If a modal dialog is open in Visual Paradigm, the call times out after 180 s and is not
  run later.
- **Validate first, then build.** A request is fully validated before anything is created; if creation fails
  half-way, everything created by that call is rolled back and the client gets a readable error
  (`isError: true`). Visual Paradigm does not crash.
- **Model elements, not pictures.** Nodes become real model elements (classes with attributes and operations,
  actors, use cases, actions, partitions, nodes, lifelines…), edges become real relationships (generalization,
  composition with multiplicities, include/extend, control flows with guards, messages…). The result is a normal
  Visual Paradigm diagram you can keep editing by hand.
- **Sizing, layout, routing.** Shapes are sized from their text (`vp/Sizer`). The default `layered` layout
  (`layout/LayeredLayout`, Sugiyama-style, no Visual Paradigm dependency) avoids overlaps, sizes containers to
  their content and turns activity partitions into swimlane columns. Visual Paradigm's own layouts (`hierarchic`,
  `orthogonal`, `organic`, `tree`, …) are available too. Visual Paradigm does not re-route connectors or re-place
  captions for elements created through the API, so the plugin does it: orthogonal routes between the middles of
  facing sides on rectilinear diagrams (activity, flowchart), Visual Paradigm re-routing elsewhere.
- **Tools menu.** **Tools → MCP Create Server** shows whether the server is running, its address and the log
  file (`vpcreate.log` in the plugin folder).

## Requirements

- Visual Paradigm (Community Edition is enough). The build uses its `openapi.jar`, which is **not** included in
  this repository and is never bundled into the plugin.
- JDK 11 or newer, and Maven 3.
- `build.sh` targets macOS. On Windows and Linux, build with Maven and copy the files by hand (see below).

## Installation

### macOS

```bash
git clone https://github.com/Lang0000/vp-mcp-create.git
cd vp-mcp-create
./build.sh            # builds target/vpmcp.create.jar and installs the plugin
./build.sh --check    # the same plus offline checks (layout, attribute parser, HTTP / JSON-RPC)
```

Defaults that can be overridden with environment variables:

| Variable | Default |
|---|---|
| `VP_HOME` | `/Applications/Visual Paradigm CE.app` |
| `VP_PLUGINS` | `~/Library/Application Support/VisualParadigm/plugins` |
| `JAVA_HOME` | Homebrew `openjdk@17` if present, otherwise `/usr/libexec/java_home` |

Restart Visual Paradigm after the first install. **Tools → MCP Create Server** should report `RUNNING`.

### Windows / Linux

1. Find `openapi.jar` in the Visual Paradigm installation (`<VP install dir>/lib/openapi.jar`).
2. Build: `mvn -Dvp.openapi="<path to openapi.jar>" clean package`.
3. Create a folder `vpmcp.create` in Visual Paradigm's plugins folder and put in it `plugin.xml`,
   `icons/mcp-create.png` and `lib/vpmcp.create.jar` (from `target/`).
4. Restart Visual Paradigm and check **Tools → MCP Create Server**.

### Connect an MCP client

Claude Code:

```bash
claude mcp add --transport http vp-create http://127.0.0.1:8900/mcp
```

Any other client that supports the Streamable HTTP transport can use the same URL.

### Settings (optional)

`vpcreate.properties` in the plugin folder:

```properties
port=8900
# dev=true adds a reload_plugin tool: restarts the server from a new jar without restarting Visual Paradigm
```

The server listens on `127.0.0.1` only and has no authentication; requests with a non-local `Origin` header are
rejected. Any program on your machine that can reach that port can change your open project.

## Tools

| Tool | What it does |
|---|---|
| `create_diagram` | New diagram from nodes and edges: text-based sizes, layout, opens it in Visual Paradigm. Returns the diagram URL and, for every id in the request, the `model_id` / `shape_id` / `connector_id`. |
| `add_elements` | Adds nodes and edges to an existing diagram. Edges may point to new nodes (request ids) or existing ones (shape or model ids). `layout`: `append` (only new nodes, next to the existing content), `auto` (whole diagram), `none`. |
| `update_element` | Name, documentation, stereotypes, attributes / operations (replace, add, remove), abstract flag, connector label / guard / multiplicities / roles, formatting, bounds. |
| `delete_element` | Removes the view (default) or the model element too (`from_model: true`); `delete_diagram: true` deletes a diagram. |
| `layout_diagram` | `layered` (own layout with containers and swimlanes) or Visual Paradigm's `hierarchic`, `orthogonal`, `organic`, `tree`, `circular`, …; `route_orthogonal` re-routes connectors without moving shapes. |
| `export_diagram` | PNG / SVG / PDF / JPG into a folder; `scale: 2` for print quality. |
| `list_diagrams` | id, name, type, URL and shape counts; `include_vocabulary: true` lists the supported diagram types and node / edge kinds. |
| `save_project` | Saves to the current `.vpp`, or to `path` (`overwrite: true` to replace a file). |

### `create_diagram` request

```json
{
  "type": "class",
  "name": "Fig. 2.3 Class diagram",
  "nodes": [
    {"id": "n1", "kind": "class", "name": "ThermalImage",
     "attributes": ["+path: str", "+width: int"],
     "operations": ["+load(): Tensor"]},
    {"id": "n2", "kind": "class", "name": "Annotation",
     "style": {"fill": "#FFF2CC", "line_color": "#BF9000", "bold": true}}
  ],
  "edges": [
    {"from": "n1", "to": "n2", "kind": "composition",
     "label": "has", "multiplicity": {"from": "1", "to": "0..*"}}
  ],
  "layout": "auto"
}
```

More in [examples/](examples/): `class`, `usecase`, `activity`, `deployment`, `sequence`, `component`, `flowchart`.

- **Diagram types:** `class`, `usecase`, `activity`, `deployment`, `sequence`, `component`, `flowchart`, `state`,
  `package`, `object`, `communication`, `er`, … (`bpmn` and `dfd` are in the vocabulary but not available in
  Community Edition; the tool says so).
- **Node kinds:** class, interface, abstract_class, enum, package, note · actor, usecase, system · initial, final,
  flow_final, action, decision, merge, fork, join, object, partition · node, device, execution_environment,
  artifact, component · lifeline · terminator, process, data, document, database, predefined_process,
  preparation · state. Any other Open API type: `"raw:<ModelType>"`.
- **Edge kinds:** association, aggregation, composition, generalization, realization, dependency, usage, include,
  extend, control_flow, object_flow, deploy, manifest, anchor, message, flowline, transition. Direction follows the
  UML arrow: generalization/realization from child to parent, aggregation/composition from the whole (diamond)
  to the part, include from the base use case, extend from the extension to the base.
- **Nesting:** `"parent": "<id>"` puts a node into a system boundary, package, deployment node or activity
  partition.
- **Members:** UML strings (`"-count: int = 0"`, `"+load(path: str): Tensor"`, `"{static} +instance(): App"`) or
  objects `{name, type, visibility, initial, static, abstract, parameters}`.
- **Formatting:** `fill`, `line_color`, `line_weight`, `line_style`, `font_color`, `font_size`, `font_name`,
  `bold`, `italic` (colours `#RRGGBB`, `fill: "none"` for transparent), or the nested form
  `{"fill": {"color1"}, "line": {...}, "font": {...}}`.
- **Manual placement:** `x`, `y`, `width`, `height` on nodes together with `"layout": "none"`.

## Scripting

The server is plain HTTP, so diagrams can be generated from a script instead of a chat. That helps when you have
many diagrams and want to rebuild them all after a change. [examples/call.sh](examples/call.sh) sends one tool
call; [examples/acceptance.py](examples/acceptance.py) is a complete Python client (create, export, clean up). The
read-back step in `acceptance.py` uses a separate read-only server on port 8899 (`get_diagram_by_url`), which is
not part of this repository; skip that step if you don't have it.

## Things to know

- **Names are shared across the project.** Visual Paradigm does not allow two elements with the same name in one
  namespace, so a classifier with an existing name is *reused* and shown on the new diagram (`"reused": true` in
  the answer). Generating into a project that already holds other diagrams can therefore link them together, and
  deleting with `from_model: true` removes the element from every diagram. Build generated sets of diagrams in an
  empty project.
- **Undo is not grouped.** The Open API has no way to group Undo steps, so Ctrl+Z undoes one element at a time. To
  undo a whole `create_diagram` call, delete the diagram with `delete_diagram: true, from_model: true`.
- **Moving shapes after a layout.** `update_element` with `bounds` moves a shape but does not re-route its
  connectors. Call `layout_diagram` with `route_orthogonal` afterwards and pass the same `direction` (`LR`/`TB`)
  the diagram was created with; otherwise routes are computed top to bottom.
- **Community Edition limits:** no BPMN and no DFD diagrams (use activity diagrams with partitions, and a class or
  component diagram, instead). Exported images carry the "Powered By Visual Paradigm Community Edition" mark in the
  bottom-right corner; it can cover the lowest node, so leave some empty space there.
- **Sequence diagrams:** self-messages are not drawn, and reply messages look like ordinary messages.
- **Use case diagrams:** an extended use case shows an "extension points" compartment, so give it more height.
- **Notes:** deleting only the view of a note opens a dialog in Visual Paradigm, so notes and their links are
  always deleted together with the model element.
- After a Visual Paradigm update you may need to rebuild the plugin (`./build.sh`).

## Project layout

```
plugin.xml, build.sh, pom.xml, icons/
src/main/java/vpmcp/create/
  VpCreatePlugin, ServerHandle         plugin entry point, server life cycle
  core/   McpHttpServer, McpProtocol   Streamable HTTP over sockets, JSON-RPC 2.0
  vp/     DiagramBuilder, Kinds, ...   element creation, type vocabulary, styles, sizes, swimlanes, routing
  layout/ LayeredLayout                layered layout without Visual Paradigm dependencies
  tools/                               the eight MCP tools (+ reload_plugin in dev mode)
  ui/     StatusActionController       Tools → MCP Create Server
src/test/java/.../OfflineCheck         checks that run without Visual Paradigm (./build.sh --check)
examples/                              JSON examples, call.sh, acceptance.py
```

## License

[MIT](LICENSE). Gson (Apache License 2.0) is shaded into the plugin jar; see [THIRD_PARTY.md](THIRD_PARTY.md).
Visual Paradigm and its Open API belong to Visual Paradigm International; this project is not affiliated with it.

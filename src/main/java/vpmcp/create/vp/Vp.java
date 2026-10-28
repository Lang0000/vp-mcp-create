package vpmcp.create.vp;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.factory.IModelElementFactory;
import vpmcp.create.core.ToolException;

import java.util.ArrayList;
import java.util.List;

/** Access to the running Visual Paradigm and lookups of diagrams and elements by id, URL or name. */
public final class Vp {

    private Vp() {
    }

    public static ApplicationManager app() throws ToolException {
        ApplicationManager am = ApplicationManager.instance();
        if (am == null) {
            throw new ToolException("The Visual Paradigm Open API is unavailable (not running inside Visual Paradigm).");
        }
        return am;
    }

    public static IProject requireProject() throws ToolException {
        IProject p = app().getProjectManager() == null ? null : app().getProjectManager().getProject();
        if (p == null) {
            throw new ToolException("No project is open in Visual Paradigm. Open or create a project first.");
        }
        return p;
    }

    public static DiagramManager dm() throws ToolException {
        return app().getDiagramManager();
    }

    public static IModelElementFactory factory() {
        return IModelElementFactory.instance();
    }

    /** Address in the form understood by get_diagram_by_url, e.g. "MOM.vpp://diagram/YRvbHuaFYFAEMEOa". */
    public static String diagramUrl(IDiagramUIModel d) {
        String project = d.getProject() != null ? d.getProject().getName() : null;
        if (project == null || project.isEmpty()) {
            project = "project";
        }
        return project + ".vpp://diagram/" + d.getId();
    }

    /** Accepts a get_diagram_by_url address, a bare diagram id, or an exact (then case-insensitive) diagram name. */
    public static IDiagramUIModel findDiagram(String ref) throws ToolException {
        if (ref == null || ref.trim().isEmpty()) {
            throw new ToolException("\"diagram\" is required (URL, id or name of a diagram; see list_diagrams).");
        }
        IProject p = requireProject();
        String s = ref.trim();
        String id = s;
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            String rest = s.substring(scheme + 3);
            int q = rest.indexOf('?');
            if (q >= 0) {
                rest = rest.substring(0, q);
            }
            int h = rest.indexOf('#');
            if (h >= 0) {
                rest = rest.substring(0, h);
            }
            String[] parts = rest.split("/");
            id = parts[parts.length - 1];
        }
        IDiagramUIModel d = p.getDiagramById(id);
        if (d != null) {
            return d;
        }
        List<IDiagramUIModel> byName = new ArrayList<>();
        for (IDiagramUIModel x : p.toDiagramArray()) {
            if (s.equals(x.getName())) {
                byName.add(x);
            }
        }
        if (byName.isEmpty()) {
            for (IDiagramUIModel x : p.toDiagramArray()) {
                if (s.equalsIgnoreCase(x.getName())) {
                    byName.add(x);
                }
            }
        }
        if (byName.size() == 1) {
            return byName.get(0);
        }
        if (byName.size() > 1) {
            throw new ToolException("Several diagrams are named \"" + s + "\"; pass the id or URL instead.");
        }
        throw new ToolException("Diagram not found: \"" + s + "\". Use list_diagrams to see the available ids.");
    }

    /** Finds a shape/connector on the diagram by its own id or by the id of the model element it shows. */
    public static IDiagramElement findOnDiagram(IDiagramUIModel d, String ref) {
        if (ref == null) {
            return null;
        }
        IDiagramElement e = d.getDiagramElementById(ref);
        if (e != null) {
            return e;
        }
        for (IDiagramElement x : d.toDiagramElementArray()) {
            IModelElement m = x.getModelElement();
            if (m != null && ref.equals(m.getId())) {
                return x;
            }
        }
        return null;
    }

    /** All views of a model element across the project, or of one diagram element id. */
    public static List<IDiagramElement> findViews(String ref) throws ToolException {
        IProject p = requireProject();
        List<IDiagramElement> out = new ArrayList<>();
        IDiagramElement direct = p.getDiagramElementById(ref);
        if (direct != null) {
            out.add(direct);
            return out;
        }
        for (IDiagramUIModel d : p.toDiagramArray()) {
            for (IDiagramElement x : d.toDiagramElementArray()) {
                IModelElement m = x.getModelElement();
                if (m != null && ref.equals(m.getId())) {
                    out.add(x);
                }
            }
        }
        return out;
    }
}

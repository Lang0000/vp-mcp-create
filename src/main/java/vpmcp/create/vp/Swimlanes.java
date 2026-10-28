package vpmcp.create.vp;

import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IActivityPartitionHeaderUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2CompartmentUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2NewUIModel;
import com.vp.plugin.model.IActivityPartition;
import vpmcp.create.core.ToolException;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity swimlane with vertical partitions. In the UI Visual Paradigm builds a swimlane from three kinds of
 * shapes — the swimlane, one header per partition and one compartment per partition — but createDiagramElement
 * makes only the swimlane, which is then not drawn. This class adds the headers and compartments and keeps their
 * bounds in step with the columns computed by the layout.
 */
public final class Swimlanes {

    public static final int HEADER = 28;

    private Swimlanes() {
    }

    /** Partition id → compartment shape, in column order. */
    public static Map<String, IShapeUIModel> build(IDiagramUIModel d, IShapeUIModel swimlane,
                                                   List<IActivityPartition> partitions, List<String> warnings)
            throws ToolException {
        Map<String, IShapeUIModel> compartments = new LinkedHashMap<>();
        if (!(swimlane instanceof IActivitySwimlane2NewUIModel)) {
            warnings.add("swimlane shape is a " + swimlane.getClass().getSimpleName()
                    + "; partition headers not created");
            return compartments;
        }
        IActivitySwimlane2NewUIModel sw = (IActivitySwimlane2NewUIModel) swimlane;
        List<String> pids = new ArrayList<>();
        List<String> cids = new ArrayList<>();
        for (IActivityPartition p : partitions) {
            pids.add(p.getId());
        }
        sw.setVerticalPartitionIds(pids.toArray(new String[0]));
        sw.setHorizontalPartitionIds(new String[0]);
        // otherwise VP "repairs" the swimlane later by adding default horizontal partitions
        sw.setValidatePartitionsForConvert(false);
        for (IActivityPartition p : partitions) {
            IDiagramElement h = Vp.dm().createDiagramElement(d, "ActivityPartitionHeader");
            if (h instanceof IActivityPartitionHeaderUIModel) {
                IActivityPartitionHeaderUIModel header = (IActivityPartitionHeaderUIModel) h;
                header.setModelElement(p);
                header.setSwimlane(sw);
                header.setHorizontal(false);
                swimlane.addChild(header);
            } else {
                warnings.add("partition header for " + p.getName() + " not created");
            }
            IDiagramElement c = Vp.dm().createDiagramElement(d, "ActivitySwimlane2Compartment");
            if (c instanceof IActivitySwimlane2CompartmentUIModel) {
                IActivitySwimlane2CompartmentUIModel comp = (IActivitySwimlane2CompartmentUIModel) c;
                comp.setVerticalPartitionId(p.getId());
                swimlane.addChild(comp);
                cids.add(comp.getId());
                compartments.put(p.getId(), comp);
            } else {
                warnings.add("partition compartment for " + p.getName() + " not created");
            }
        }
        sw.setCompartmentIds(cids.toArray(new String[0]));
        return compartments;
    }

    /** Places headers and compartments: columns are the lanes' bounds, headers take their top strip. */
    public static void arrange(IShapeUIModel swimlane, Map<String, Rectangle> lanes) {
        for (IShapeUIModel child : swimlane.toChildArray()) {
            String pid = null;
            if (child instanceof IActivityPartitionHeaderUIModel && child.getModelElement() != null) {
                pid = child.getModelElement().getId();
                Rectangle r = lanes.get(pid);
                if (r != null) {
                    child.setBounds(r.x, r.y, r.width, HEADER);
                }
            } else if (child instanceof IActivitySwimlane2CompartmentUIModel) {
                pid = ((IActivitySwimlane2CompartmentUIModel) child).getVerticalPartitionId();
                Rectangle r = lanes.get(pid);
                if (r != null) {
                    child.setBounds(r.x, r.y + HEADER, r.width, Math.max(10, r.height - HEADER));
                }
            }
        }
    }
}

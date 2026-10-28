package vpmcp.create.vp;

import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IStereotype;

import java.util.ArrayList;
import java.util.List;

/** Writes parsed attributes / operations into the model and sizes class shapes from their members. */
public final class Members {

    private Members() {
    }

    public static void fillAttribute(IAttribute at, MemberSpec a) {
        at.setName(a.name);
        if (a.type != null) {
            at.setType(a.type);
        }
        if (a.visibility != null) {
            at.setVisibility(a.visibility);
        }
        if (a.initial != null) {
            at.setInitialValue(a.initial);
        }
        if (a.multiplicity != null) {
            at.setMultiplicity(a.multiplicity);
        }
        if (a.isStatic) {
            at.setScope(IAttribute.SCOPE_CLASSIFIER);
        }
        if (a.documentation != null) {
            at.setDocumentation(a.documentation);
        }
    }

    public static void fillOperation(IOperation op, MemberSpec o) {
        op.setName(o.name);
        if (o.type != null) {
            op.setReturnType(o.type);
        }
        if (o.visibility != null) {
            op.setVisibility(o.visibility);
        }
        if (o.isStatic) {
            op.setStatic(true);
        }
        if (o.isAbstract) {
            op.setAbstract(true);
        }
        if (o.documentation != null) {
            op.setDocumentation(o.documentation);
        }
        for (String[] p : o.params) {
            IParameter par = op.createParameter();
            par.setName(p[0]);
            if (p[1] != null) {
                par.setType(p[1]);
            }
        }
    }

    /** Size estimate for a class shape showing its current members. */
    public static int[] classSize(IClass c) {
        List<String> attrs = new ArrayList<>();
        for (IAttribute a : c.toAttributeArray()) {
            MemberSpec m = new MemberSpec();
            m.name = a.getName();
            m.type = a.getTypeAsString();
            m.visibility = a.getVisibility();
            m.initial = a.getInitialValue();
            attrs.add(m.displayText());
        }
        List<String> ops = new ArrayList<>();
        for (IOperation o : c.toOperationArray()) {
            MemberSpec m = new MemberSpec();
            m.operation = true;
            m.name = o.getName();
            m.type = o.getReturnTypeAsString();
            m.visibility = o.getVisibility();
            for (IParameter p : o.toParameterArray()) {
                m.params.add(new String[] {p.getName(), p.getTypeAsString()});
            }
            ops.add(m.displayText());
        }
        String st = null;
        IStereotype[] sts = c.toStereotypeModelArray();
        if (sts != null && sts.length > 0) {
            st = sts[0].getName();
        }
        return Sizer.classBox(c.getName(), st, attrs, ops);
    }
}

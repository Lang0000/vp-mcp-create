package vpmcp.create.vp;

import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.format.IElementFont;
import com.vp.plugin.diagram.format.IFillColor;
import com.vp.plugin.diagram.format.ILineModel;
import com.vp.plugin.diagram.format.LineStyle;
import vpmcp.create.core.ToolException;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies formatting. Accepts the nested field names reported by get_diagram_by_url
 * ({"fill": {"color1"}, "line": {"color", "weight", "style"}, "font": {"name", "size", "color", "is_bold",
 * "is_italic"}}) and flat shortcuts ("fill", "line_color", "line_weight", "font_color", "font_size", "font_name",
 * "bold", "italic").
 */
public final class Styler {

    private static final Map<String, Color> NAMED = new HashMap<>();

    static {
        NAMED.put("black", Color.BLACK);
        NAMED.put("white", Color.WHITE);
        NAMED.put("red", new Color(0xFF0000));
        NAMED.put("green", new Color(0x00A000));
        NAMED.put("blue", new Color(0x0000FF));
        NAMED.put("yellow", new Color(0xFFFF00));
        NAMED.put("orange", new Color(0xFFA500));
        NAMED.put("gray", new Color(0x808080));
        NAMED.put("grey", new Color(0x808080));
        NAMED.put("lightgray", new Color(0xD3D3D3));
        NAMED.put("lightgrey", new Color(0xD3D3D3));
        NAMED.put("none", null);
        NAMED.put("transparent", null);
    }

    private Styler() {
    }

    /** Validates a style object without touching the model, so bad input fails before anything is created. */
    public static void validate(JsonObject style) throws ToolException {
        if (style == null) {
            return;
        }
        apply(null, style, null);
    }

    public static void apply(IDiagramElement el, JsonObject style, List<String> warnings) throws ToolException {
        if (style == null) {
            return;
        }
        // fill
        JsonObject fillObj = style.has("fill") && style.get("fill").isJsonObject() ? style.getAsJsonObject("fill") : null;
        String fill = fillObj != null ? Args.str(fillObj, "color1", null) : Args.str(style, "fill", null);
        if (fill == null) {
            fill = Args.str(style, "fill_color", Args.str(style, "background", null));
        }
        String fill2 = fillObj != null ? Args.str(fillObj, "color2", null) : null;
        Integer transparency = fillObj != null ? Args.integer(fillObj, "transparency") : Args.integer(style, "transparency");
        boolean fillGiven = fill != null;
        Color fillColor = fillGiven ? color(fill) : null;
        Color fillColor2 = fill2 != null ? color(fill2) : null;
        // line
        JsonObject lineObj = style.has("line") && style.get("line").isJsonObject() ? style.getAsJsonObject("line") : null;
        String line = lineObj != null ? Args.str(lineObj, "color", null) : Args.str(style, "line", null);
        if (line == null) {
            line = Args.str(style, "line_color", Args.str(style, "foreground", null));
        }
        Double weight = lineObj != null ? Args.number(lineObj, "weight") : Args.number(style, "line_weight");
        String lineStyle = lineObj != null ? Args.str(lineObj, "style", null) : Args.str(style, "line_style", null);
        Color lineColor = line != null ? color(line) : null;
        LineStyle ls = lineStyle != null ? lineStyle(lineStyle) : null;
        // font
        JsonObject fontObj = style.has("font") && style.get("font").isJsonObject() ? style.getAsJsonObject("font") : null;
        String fontColor = fontObj != null ? Args.str(fontObj, "color", null) : Args.str(style, "font_color", null);
        Integer fontSize = fontObj != null ? Args.integer(fontObj, "size") : Args.integer(style, "font_size");
        String fontName = fontObj != null ? Args.str(fontObj, "name", null) : Args.str(style, "font_name", null);
        Boolean bold = has(fontObj, "is_bold") ? (Boolean) Args.bool(fontObj, "is_bold", false)
                : has(style, "bold") ? (Boolean) Args.bool(style, "bold", false) : null;
        Boolean italic = has(fontObj, "is_italic") ? (Boolean) Args.bool(fontObj, "is_italic", false)
                : has(style, "italic") ? (Boolean) Args.bool(style, "italic", false) : null;
        Color fc = fontColor != null ? color(fontColor) : null;

        if (el == null) {
            return; // validation only
        }
        if (fillGiven && el instanceof IShapeUIModel) {
            IFillColor f = ((IShapeUIModel) el).getFillColor();
            if (f != null) {
                if (fillColor == null) {
                    f.setTransparency(IFillColor.TRANSPARENT);
                } else {
                    f.setType(fillColor2 != null ? IFillColor.TYPE_GRADIENT : IFillColor.TYPE_SOLID);
                    f.setColor1(fillColor);
                    f.setColor2(fillColor2 != null ? fillColor2 : fillColor);
                    f.setTransparency(transparency != null ? transparency : IFillColor.OPAQUE);
                }
            } else {
                el.setBackground(fillColor);
            }
        } else if (fillGiven && warnings != null) {
            warnings.add("fill ignored on connector " + el.getId());
        }
        if (lineColor != null || weight != null || ls != null) {
            ILineModel lm = el.getLineModel();
            if (lm != null) {
                if (lineColor != null) {
                    lm.setColor(lineColor);
                }
                if (weight != null) {
                    lm.setWeight(weight.floatValue());
                }
                if (ls != null) {
                    lm.setLineStyle(ls);
                }
            } else if (lineColor != null) {
                el.setForeground(lineColor);
            }
        }
        if (fc != null || fontSize != null || fontName != null || bold != null || italic != null) {
            IElementFont font = el.getElementFont();
            if (font == null) {
                if (warnings != null) {
                    warnings.add("element " + el.getId() + " has no font to format");
                }
            } else {
                if (fontName != null) {
                    font.setName(fontName);
                }
                if (fontSize != null) {
                    font.setSize(fontSize);
                }
                if (fc != null) {
                    font.setColor(fc);
                }
                if (bold != null) {
                    font.setBold(bold);
                }
                if (italic != null) {
                    font.setItalic(italic);
                }
            }
        }
    }

    private static boolean has(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull();
    }

    public static Color color(String s) throws ToolException {
        String v = s.trim().toLowerCase();
        if (NAMED.containsKey(v)) {
            return NAMED.get(v);
        }
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        try {
            if (v.length() == 3) {
                v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
            }
            if (v.length() == 6) {
                return new Color(Integer.parseInt(v, 16));
            }
            if (v.length() == 8) {
                long rgba = Long.parseLong(v, 16);
                return new Color((int) (rgba >> 24) & 0xFF, (int) (rgba >> 16) & 0xFF, (int) (rgba >> 8) & 0xFF,
                        (int) rgba & 0xFF);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new ToolException("Bad colour \"" + s + "\"; use \"#RRGGBB\".");
    }

    private static LineStyle lineStyle(String s) throws ToolException {
        String v = Args.norm(s);
        switch (v) {
            case "solid":
                return LineStyle.Style1;
            case "dash":
            case "dashed":
                return LineStyle.Style2;
            case "dot":
            case "dotted":
                return LineStyle.Style3;
            case "none":
                return LineStyle.None;
            default:
                for (LineStyle l : LineStyle.values()) {
                    if (l.name().equalsIgnoreCase(s.trim())) {
                        return l;
                    }
                }
                throw new ToolException("Bad line style \"" + s + "\"; use solid, dashed, dotted or Style1..Style"
                        + (LineStyle.values().length - 1) + ".");
        }
    }
}

package vpmcp.create.vp;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Estimates shape sizes from their text so that labels fit and the layout knows real extents. */
public final class Sizer {

    private static FontMetrics plain;
    private static FontMetrics bold;

    private Sizer() {
    }

    private static synchronized void init() {
        if (plain != null) {
            return;
        }
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        plain = g.getFontMetrics(new Font("Dialog", Font.PLAIN, 11));
        bold = g.getFontMetrics(new Font("Dialog", Font.BOLD, 11));
        g.dispose();
    }

    public static int textWidth(String s, boolean isBold) {
        init();
        if (s == null) {
            return 0;
        }
        return (isBold ? bold : plain).stringWidth(s);
    }

    public static int lineHeight() {
        init();
        return plain.getHeight() + 2;
    }

    /** Greedy word wrap to a maximum pixel width; explicit "\n" breaks are kept. */
    public static List<String> wrap(String text, int maxWidth, boolean isBold) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        for (String para : text.split("\n")) {
            StringBuilder cur = new StringBuilder();
            for (String w : para.split(" ")) {
                String cand = cur.length() == 0 ? w : cur + " " + w;
                if (textWidth(cand, isBold) > maxWidth && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(w);
                } else {
                    cur = new StringBuilder(cand);
                }
            }
            lines.add(cur.toString());
        }
        return lines;
    }

    private static int maxWidth(List<String> lines, boolean isBold) {
        int m = 0;
        for (String l : lines) {
            m = Math.max(m, textWidth(l, isBold));
        }
        return m;
    }

    /** Size of a box that shows wrapped text with the given padding. */
    public static int[] box(String text, int wrapAt, int padX, int padY, int minW, int minH, boolean isBold) {
        List<String> lines = wrap(text, wrapAt, isBold);
        int w = maxWidth(lines, isBold) + 2 * padX;
        int h = lines.size() * lineHeight() + 2 * padY;
        return new int[] {Math.max(minW, w), Math.max(minH, h)};
    }

    /** Class-like box: name compartment (+ stereotype line) and attribute / operation compartments. */
    public static int[] classBox(String name, String stereotype, List<String> attrs, List<String> ops) {
        int lh = lineHeight();
        int w = textWidth(name, true);
        int header = lh + 12;
        if (stereotype != null) {
            w = Math.max(w, textWidth("«" + stereotype + "»", false));
            header += lh;
        }
        for (String a : attrs) {
            w = Math.max(w, textWidth(a, false));
        }
        for (String o : ops) {
            w = Math.max(w, textWidth(o, false));
        }
        int h = header + Math.max(1, attrs.size()) * lh + 8 + Math.max(1, ops.size()) * lh + 8;
        return new int[] {Math.max(90, w + 28), Math.max(50, h)};
    }
}

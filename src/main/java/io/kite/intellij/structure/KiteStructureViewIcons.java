package io.kite.intellij.structure;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * Icon provider for Kite Structure View elements.
 * Creates circular icons with centered letters representing element types.
 */
public class KiteStructureViewIcons {
    private static final int ICON_SIZE = 16;
    private static final int BORDER_WIDTH = 1;

    public static final Icon RESOURCE = createIcon('R', new Color(76, 175, 80));
    public static final Icon COMPONENT = createIcon('C', new Color(33, 150, 243));
    public static final Icon SCHEMA = createIcon('S', new Color(156, 39, 176));
    public static final Icon FUNCTION = createIcon('F', new Color(255, 152, 0));
    public static final Icon TYPE = createIcon('T', new Color(244, 67, 54));
    public static final Icon VARIABLE = createIcon('V', new Color(96, 125, 139));
    public static final Icon INPUT = createIcon('I', new Color(0, 188, 212));
    public static final Icon OUTPUT = createIcon('O', new Color(255, 193, 7));
    public static final Icon IMPORT = createIcon('M', new Color(121, 85, 72));

    private static Icon createIcon(char letter, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g.create();
                try {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    // Draw circle border
                    g2d.setColor(color);
                    Ellipse2D circle = new Ellipse2D.Float(
                        x + BORDER_WIDTH,
                        y + BORDER_WIDTH,
                        ICON_SIZE - 2 * BORDER_WIDTH,
                        ICON_SIZE - 2 * BORDER_WIDTH
                    );
                    g2d.setStroke(new BasicStroke(BORDER_WIDTH));
                    g2d.draw(circle);

                    // Draw letter
                    Font font = JBUI.Fonts.label(10).deriveFont(Font.BOLD);
                    g2d.setFont(font);
                    FontMetrics fm = g2d.getFontMetrics();
                    String text = String.valueOf(letter);
                    int textWidth = fm.stringWidth(text);
                    int textHeight = fm.getAscent();

                    int textX = x + (ICON_SIZE - textWidth) / 2;
                    int textY = y + (ICON_SIZE - textHeight) / 2 + textHeight;

                    g2d.setColor(color);
                    g2d.drawString(text, textX, textY);
                } finally {
                    g2d.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return ICON_SIZE;
            }

            @Override
            public int getIconHeight() {
                return ICON_SIZE;
            }
        };
    }
}
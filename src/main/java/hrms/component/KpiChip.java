package hrms.component;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

import hrms.ui.theme.Palette;
import hrms.ui.theme.Palette.Role;
import hrms.util.UiGraphics;

/**
 * Compact clickable KPI chip for filter bars (spec section 3.1): rounded
 * surface, accent-tinted count and a muted label. Clicking runs the supplied
 * action; the caller toggles {@link #setSelected} to reflect filter state.
 */
public class KpiChip extends JPanel {

    private static final int ARC = 12;
    private static final float COUNT_FONT_SIZE = 18f;
    private static final float LABEL_FONT_SIZE = 10f;

    private final String label;
    private final Role accent;
    private final Runnable onClick;

    private String count = "0";
    private boolean selected;
    private boolean hover;

    public KpiChip(String label, Role accent, Runnable onClick) {
        this.label = label.toUpperCase();
        this.accent = accent;
        this.onClick = onClick;
        setOpaque(false);
        setPreferredSize(new Dimension(132, 52));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Font base = javax.swing.UIManager.getFont("defaultFont");
        if (base == null) {
            base = new javax.swing.JLabel().getFont();
        }
        setFont(base);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null) {
                    onClick.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    /** Big number (or formatted value such as "126.5h") shown in the chip. */
    public void setCount(String newCount) {
        this.count = newCount == null || newCount.isBlank() ? "0" : newCount;
        repaint();
    }

    /** Marks the chip as the active filter (accent border + tint). */
    public void setSelectedChip(boolean selectedChip) {
        if (this.selected != selectedChip) {
            this.selected = selectedChip;
            repaint();
        }
    }

    public boolean isSelectedChip() {
        return selected;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        int width = getWidth();
        int height = getHeight();

        Color accentColor = Palette.color(accent);
        Color background;
        if (selected) {
            background = UiGraphics.blend(Palette.color(Role.CARD_BG), accentColor, 0.16);
        } else if (hover) {
            background = UiGraphics.blend(Palette.color(Role.CARD_BG), accentColor, 0.07);
        } else {
            background = Palette.color(Role.CARD_BG);
        }

        UiGraphics.fillRoundRect(g, 0, 0, width, height, ARC, background);
        UiGraphics.drawRoundRect(g, 0, 0, width, height, ARC,
                selected ? accentColor : Palette.color(Role.CARD_BORDER));

        // Count
        Font countFont = getFont().deriveFont(Font.BOLD, COUNT_FONT_SIZE);
        g.setFont(countFont);
        FontMetrics metrics = g.getFontMetrics(countFont);
        int padding = 14;
        int countY = (height / 2) + (metrics.getAscent() - metrics.getDescent()) / 2 - 3;
        g.setColor(selected ? accentColor : Palette.color(Role.TEXT));
        g.drawString(count, padding, countY);

        // Label under the count
        Font labelFont = getFont().deriveFont(Font.BOLD, LABEL_FONT_SIZE);
        g.setFont(labelFont);
        g.setColor(Palette.color(Role.TEXT_MUTED));
        g.drawString(fitText(label, g.getFontMetrics(labelFont), width - padding * 2),
                padding, height - 10);

        g.dispose();
    }

    private String fitText(String text, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        while (text.length() > 1 && metrics.stringWidth(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}

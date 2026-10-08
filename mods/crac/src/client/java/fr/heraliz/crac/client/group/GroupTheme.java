package fr.heraliz.crac.client.group;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class GroupTheme {
    public static final int PANEL_BG_TOP = -200535002;
    public static final int PANEL_BG_BOTTOM = -201062385;
    public static final int PANEL_BORDER_OUT = -13745040;
    public static final int PANEL_BORDER_IN = -15327174;
    public static final int FIELD_BG = -16381162;
    public static final int FIELD_BORDER = -14009250;
    public static final int FIELD_BORDER_FOCUS = -11634488;
    public static final int TEXT = -3878166;
    public static final int TEXT_DIM = -9536363;
    public static final int TEXT_BRIGHT = -1;
    public static final int TITLE = -6504193;
    public static final int BTN_OK = -12598694;
    public static final int BTN_OFF = -11181958;
    public static final int BTN_OK_HOVER = -10757772;
    public static final int BTN_RED = -4179669;
    public static final int BTN_RED_HOVER = -2074037;
    public static final int ONLINE = -12266886;
    public static final int OFFLINE = -10852744;
    public static final int TAB_ACTIVE = -11634488;
    public static final int TAB_INACTIVE = -14536366;
    public static final int TAB_ACTIVE_BG = -14996400;
    public static final int ROW_BG = -16116954;
    public static final int ROW_HOVER_BG = -14996402;

    private GroupTheme() {
    }

    public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, -200535002, -201062385);
        border(g, x, y, w, h, -13745040);
        border(g, x + 1, y + 1, w - 2, h - 2, -15327174);
    }

    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        g.fill(x, y, x + w, y + h, -16381162);
        border(g, x, y, w, h, focused ? -11634488 : -14009250);
    }

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int color) {
        border(g, x, y, w, h, color);
        int tx = x + (w - font.width(label)) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(font, label, tx, ty, color, false);
    }

    public static void dot(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy <= r * r) {
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    public static void check(GuiGraphics g, int x, int y, int color) {
        int[][] pts = new int[][]{{0, 4}, {1, 5}, {2, 6}, {3, 5}, {4, 3}, {5, 2}, {6, 1}};

        for (int[] p : pts) {
            g.fill(x + p[0], y + p[1], x + p[0] + 2, y + p[1] + 2, color);
        }
    }

    public static void cross(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 7; i++) {
            g.fill(x + i, y + i, x + i + 2, y + i + 2, color);
            g.fill(x + 6 - i, y + i, x + 6 - i + 2, y + i + 2, color);
        }
    }
}

package fr.heraliz.crac.client.group;

import java.awt.Color;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.client.ScaledScreen;
import fr.heraliz.crac.group.GroupNames;
import fr.heraliz.crac.network.C2SCreateGroup;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class GroupCreateScreen extends ScaledScreen {
    private static final int PANEL_W = 200;
    private static final int PANEL_H = 182;
    private int left;
    private int top;
    private int nameX;
    private int nameY;
    private int nameW;
    private int fieldH = 16;
    private int tagX;
    private int tagY;
    private int tagW;
    private int hueX;
    private int hueY;
    private int hueW;
    private int hueH = 12;
    private int swatchX;
    private int swatchW = 26;
    private int btnX;
    private int btnY;
    private int btnW;
    private int btnH = 18;
    private String name = "";
    private String tag = "";
    private float hue = 0.58F;
    private int focus = 1;
    private boolean draggingHue = false;

    public GroupCreateScreen() {
        super(Component.translatable("crac.gui.create_group"));
    }

    @Override
    protected void init() {
        this.left = (this.guiW() - 200) / 2;
        this.top = (this.guiH() - 182) / 2;
        int pad = 14;
        this.nameX = this.left + pad;
        this.nameY = this.top + 44;
        this.nameW = 200 - 2 * pad;
        this.tagX = this.left + pad;
        this.tagY = this.top + 84;
        this.tagW = 72;
        this.hueX = this.left + pad;
        this.hueY = this.top + 124;
        this.hueW = 200 - 2 * pad - (this.swatchW + 6);
        this.swatchX = this.left + 200 - pad - this.swatchW;
        this.btnX = this.left + pad;
        this.btnY = this.top + 182 - 28;
        this.btnW = 200 - 2 * pad;
    }

    /** The server's limit (synced config), never above what the packet carries. */
    private static int nameMax() {
        try {
            return Math.min(GroupNames.WIRE_MAX_NAME, Config.groupNameMaxLength.get());
        } catch (RuntimeException e) {
            return 24;
        }
    }

    private boolean nameValid() {
        return GroupNames.cleanName(this.name, nameMax()) != null;
    }

    private boolean tagValid() {
        return GroupNames.cleanTag(this.tag) != null;
    }

    private boolean valid() {
        return this.nameValid() && this.tagValid();
    }

    private int selectedColor() {
        return Color.HSBtoRGB(this.hue, 0.85F, 0.95F) & 16777215;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        GroupTheme.panel(g, this.left, this.top, 200, 182);
        g.drawCenteredString(this.font, Component.translatable("crac.gui.create_group"), this.left + 100, this.top + 12, -6504193);
        g.fill(this.left + 14, this.top + 26, this.left + 200 - 14, this.top + 27, -13745040);
        boolean blink = System.currentTimeMillis() / 500L % 2L == 0L;
        g.drawString(this.font, Component.translatable("crac.gui.group_name"), this.nameX, this.nameY - 11, -9536363, false);
        GroupTheme.field(g, this.nameX, this.nameY, this.nameW, this.fieldH, this.focus == 1);
        this.drawFieldText(g, this.name, this.nameX, this.nameY, this.nameW, this.focus == 1 && blink);
        g.drawString(this.font, Component.translatable("crac.gui.group_tag"), this.tagX, this.tagY - 11, -9536363, false);
        GroupTheme.field(g, this.tagX, this.tagY, this.tagW, this.fieldH, this.focus == 2);
        this.drawFieldText(g, this.tag, this.tagX, this.tagY, this.tagW, this.focus == 2 && blink);
        g.drawString(this.font, "3-4 letters", this.tagX + this.tagW + 8, this.tagY + 4, -9536363, false);
        g.drawString(this.font, Component.translatable("crac.gui.group_color"), this.hueX, this.hueY - 11, -9536363, false);
        this.drawHueBar(g);
        int col = 0xFF000000 | this.selectedColor();
        g.fill(this.swatchX, this.hueY, this.swatchX + this.swatchW, this.hueY + this.hueH, col);
        GroupTheme.border(g, this.swatchX, this.hueY, this.swatchW, this.hueH, -14009250);
        boolean hover = this.valid() && inRect(mouseX, mouseY, this.btnX, this.btnY, this.btnW, this.btnH);
        int btnCol = !this.valid() ? -11181958 : (hover ? -10757772 : -12598694);
        GroupTheme.button(g, this.font, this.btnX, this.btnY, this.btnW, this.btnH, Component.translatable("crac.gui.accept").getString(), btnCol);
        this.renderWidgets(g, mouseX, mouseY, pt);
    }

    private void drawFieldText(GuiGraphics g, String text, int x, int y, int w, boolean cursor) {
        int tx = x + 5;
        int ty = y + (this.fieldH - 8) / 2;
        // long names scroll: keep the end (where the cursor is) inside the field
        String visible = text;
        while (!visible.isEmpty() && this.font.width(visible + "_") > w - 10) {
            visible = visible.substring(1);
        }
        String shown = visible + (cursor ? "_" : "");
        g.drawString(this.font, shown, tx, ty, -3878166, false);
    }

    private void drawHueBar(GuiGraphics g) {
        for (int i = 0; i < this.hueW; i++) {
            int c = 0xFF000000 | Color.HSBtoRGB((float)i / this.hueW, 0.85F, 0.95F) & 16777215;
            g.fill(this.hueX + i, this.hueY, this.hueX + i + 1, this.hueY + this.hueH, c);
        }

        GroupTheme.border(g, this.hueX, this.hueY, this.hueW, this.hueH, -14009250);
        int mx = this.hueX + Math.round(this.hue * (this.hueW - 1));
        g.fill(mx - 1, this.hueY - 2, mx + 2, this.hueY + this.hueH + 2, -1);
        g.fill(mx, this.hueY - 1, mx + 1, this.hueY + this.hueH + 1, -15724528);
    }

    @Override
    protected boolean clickContent(double mx, double my, int button) {
        if (inRect(mx, my, this.nameX, this.nameY, this.nameW, this.fieldH)) {
            this.focus = 1;
            return true;
        } else if (inRect(mx, my, this.tagX, this.tagY, this.tagW, this.fieldH)) {
            this.focus = 2;
            return true;
        } else if (inRect(mx, my, this.hueX - 2, this.hueY - 2, this.hueW + 4, this.hueH + 4)) {
            this.draggingHue = true;
            this.updateHue(mx);
            this.focus = 0;
            return true;
        } else if (this.valid() && inRect(mx, my, this.btnX, this.btnY, this.btnW, this.btnH)) {
            this.accept();
            return true;
        } else {
            this.focus = 0;
            return this.clickWidgets(mx, my, button);
        }
    }

    @Override
    protected boolean dragContent(double mx, double my, int button, double dx, double dy) {
        if (this.draggingHue) {
            this.updateHue(mx);
            return true;
        } else {
            return this.dragWidgets(mx, my, button, dx, dy);
        }
    }

    @Override
    protected boolean releaseContent(double mx, double my, int button) {
        this.draggingHue = false;
        return this.releaseWidgets(mx, my, button);
    }

    private void updateHue(double mx) {
        this.hue = (float)Mth.clamp((mx - this.hueX) / (this.hueW - 1), 0.0, 1.0);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (this.focus == 1) {
            if (this.name.length() < nameMax() && GroupNames.isNameChar(c)) {
                this.name = this.name + c;
                return true;
            }
        } else if (this.focus == 2 && this.tag.length() < GroupNames.TAG_MAX && GroupNames.isTagChar(c)) {
            this.tag = this.tag + Character.toUpperCase(c);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case 257:
            case 335:
                if (this.valid()) {
                    this.accept();
                }

                return true;
            case 258:
                this.focus = this.focus == 1 ? 2 : 1;
                return true;
            case 259:
                if (this.focus == 1 && !this.name.isEmpty()) {
                    this.name = this.name.substring(0, this.name.length() - 1);
                } else if (this.focus == 2 && !this.tag.isEmpty()) {
                    this.tag = this.tag.substring(0, this.tag.length() - 1);
                }

                return true;
            default:
                return super.keyPressed(key, scan, mods);
        }
    }

    private void accept() {
        NetworkHandler.CHANNEL.sendToServer(new C2SCreateGroup(GroupNames.cleanName(this.name, nameMax()), this.tag, this.selectedColor()));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

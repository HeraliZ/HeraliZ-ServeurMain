package fr.heraliz.crac.client.ui;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.claim.UpkeepResource;
import fr.heraliz.crac.client.ScaledScreen;
import fr.heraliz.crac.client.group.GroupTheme;
import fr.heraliz.crac.menu.ClaimUpkeepMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ClaimUpkeepScreen extends AbstractContainerScreen<ClaimUpkeepMenu> {
    private static final int CELL_BG = -16117472;
    private static final int CELL_BORDER = -14404269;
    private static final int OK_BORDER = -12598694;
    private static final int LOW_BORDER = -2056144;
    private static final int EMPTY_BORDER = -4179669;
    private static final int GHOST_VEIL = -1341517536;
    /** Text and bar colour of the countdown when the server has decay turned off: nothing to warn about. */
    private static final int NEUTRAL = -9536363;
    private List<Component> pendingTooltip;

    private int guiW() {
        return (int)Math.round(this.width / ScaledScreen.scaleFactor());
    }

    private int guiH() {
        return (int)Math.round(this.height / ScaledScreen.scaleFactor());
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.guiW() - this.imageWidth) / 2;
        this.topPos = (this.guiH() - this.imageHeight) / 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double f = ScaledScreen.scaleFactor();
        return super.mouseClicked(mx / f, my / f, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        double f = ScaledScreen.scaleFactor();
        return super.mouseReleased(mx / f, my / f, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        double f = ScaledScreen.scaleFactor();
        return super.mouseDragged(mx / f, my / f, button, dx / f, dy / f);
    }

    public ClaimUpkeepScreen(ClaimUpkeepMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 165;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 71;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        GroupTheme.panel(g, x, y, this.imageWidth, this.imageHeight);
        g.fill(x + 7, y + 18, x + this.imageWidth - 7, y + 19, -15327174);

        for (int i = 0; i < ClaimBlockEntity.SLOTS; i++) {
            int cx = x + ClaimUpkeepMenu.SLOT_X[i] - 1;
            int cy = y + 26 - 1;
            int need = this.needed(i);
            int have = this.stored(i);
            int border = need <= 0 ? -14404269 : (have <= 0 ? -4179669 : (have < need * 0.25 ? -2056144 : -12598694));
            cell(g, cx, cy, border);
        }

        int barX = x + 8;
        int barY = y + 62;
        int barW = this.imageWidth - 16;
        int barH = 5;
        g.fill(barX, barY, barX + barW, barY + barH, -16117472);
        GroupTheme.border(g, barX - 1, barY - 1, barW + 2, barH + 2, -14404269);
        int minutes = this.upkeepMinutes();
        if (minutes != 0) {
            double full = Math.max(1.0, Config.upkeepHours.get() * 60.0);
            double frac = minutes < 0 ? 1.0 : Math.min(1.0, minutes / full);
            int w = (int)Math.round(barW * frac);
            if (w > 0) {
                g.fill(barX, barY, barX + w, barY + barH, this.decays() ? barColor(minutes) : NEUTRAL);
            }
        }

        int ix = x + 8;
        int iy = y + 82;

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                cell(g, ix + c * 18 - 1, iy + r * 18 - 1, -14404269);
            }
        }

        for (int c = 0; c < 9; c++) {
            cell(g, ix + c * 18 - 1, y + 140 - 1, -14404269);
        }
    }

    private static void cell(GuiGraphics g, int x, int y, int border) {
        g.fill(x, y, x + 18, y + 18, -16117472);
        GroupTheme.border(g, x, y, 18, 18, border);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("crac.upkeep.title"), this.titleLabelX, this.titleLabelY, -6504193, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, -9536363, false);

        for (int i = 0; i < ClaimBlockEntity.SLOTS; i++) {
            int sx = ClaimUpkeepMenu.SLOT_X[i];
            int sy = 26;
            int need = this.needed(i);
            if (!this.menu.getSlot(i).hasItem()) {
                Item item = ClaimBlockEntity.resourceForSlot(i).item();
                if (item != Items.AIR) {
                    g.renderFakeItem(new ItemStack(item), sx, sy);
                    g.fill(sx, sy, sx + 16, sy + 16, -1341517536);
                }
            }

            int have = this.stored(i);
            String label = need <= 0 ? String.valueOf(have) : have + "/" + need;
            int col = need <= 0 ? -9536363 : (have <= 0 ? -4179669 : -3878166);
            g.pose().pushPose();
            g.pose().scale(0.5F, 0.5F, 1.0F);
            int tw = this.font.width(label);
            g.drawString(this.font, label, (sx + 8) * 2 - tw / 2, (sy + 19) * 2, col, false);
            g.pose().popPose();
        }

        int minutes = this.upkeepMinutes();
        Component text;
        int col;
        if (minutes < 0) {
            text = Component.translatable("crac.upkeep.none");
            col = -12598694;
        } else if (!this.decays()) {
            // the server turned decay off: upkeep is still used, but nothing decays when it runs out
            text = minutes == 0 ? Component.translatable("crac.upkeep.empty_off") : Component.translatable("crac.upkeep.lasts", duration(minutes));
            col = NEUTRAL;
        } else if (minutes == 0) {
            text = Component.translatable("crac.upkeep.decaying_now");
            col = -4179669;
        } else {
            text = Component.translatable("crac.upkeep.decays_in", duration(minutes));
            col = barColor(minutes);
        }

        g.drawCenteredString(this.font, text, this.imageWidth / 2, 50, col);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        double f = ScaledScreen.scaleFactor();
        int mx = (int)(mouseX / f);
        int my = (int)(mouseY / f);
        this.pendingTooltip = null;
        g.pose().pushPose();
        g.pose().scale((float)f, (float)f, 1.0F);
        this.renderScaled(g, mx, my, pt);
        g.pose().popPose();
        if (this.pendingTooltip != null) {
            g.renderComponentTooltip(this.font, this.pendingTooltip, mouseX, mouseY);
        } else if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            g.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
        }
    }

    @Override
    public void mouseMoved(double mx, double my) {
        double f = ScaledScreen.scaleFactor();
        super.mouseMoved(mx / f, my / f);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        if (this.minecraft != null && this.minecraft.level != null) {
            g.fillGradient(0, 0, this.guiW() + 1, this.guiH() + 1, -1072689136, -804253680);
        } else {
            super.renderBackground(g);
        }
    }

    private void renderScaled(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, pt);
        this.collectUpkeepTooltip(mouseX, mouseY);
    }

    private void collectUpkeepTooltip(int mouseX, int mouseY) {
        for (int i = 0; i < ClaimBlockEntity.SLOTS; i++) {
            int sx = this.leftPos + ClaimUpkeepMenu.SLOT_X[i];
            int sy = this.topPos + 26;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                if (this.menu.getSlot(i).hasItem()) {
                    return;
                }

                UpkeepResource res = ClaimBlockEntity.resourceForSlot(i);
                Item item = res.item();
                int need = this.needed(i);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("crac.upkeep.res_title", Component.translatable("crac.upkeep.res." + res.name().toLowerCase(java.util.Locale.ROOT))).withStyle(net.minecraft.ChatFormatting.AQUA));
                if (item != Items.AIR) {
                    lines.add(Component.translatable("crac.upkeep.item", item.getDescription().copy().withStyle(net.minecraft.ChatFormatting.WHITE)).withStyle(net.minecraft.ChatFormatting.GRAY));
                }

                lines.add(need <= 0 ? Component.translatable("crac.upkeep.no_pieces").withStyle(net.minecraft.ChatFormatting.DARK_GRAY) : Component.translatable("crac.upkeep.needs", need, fmtHours()).withStyle(net.minecraft.ChatFormatting.GRAY));
                this.pendingTooltip = lines;
                return;
            }
        }
    }

    private static String fmtHours() {
        double h = Config.upkeepHours.get();
        return h == Math.floor(h) ? String.valueOf((int)h) : String.valueOf(h);
    }

    private static Component duration(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return h > 0 ? Component.translatable("crac.upkeep.duration_hm", String.valueOf(h), String.valueOf(m))
            : Component.translatable("crac.upkeep.duration_m", String.valueOf(m));
    }

    private static int barColor(int minutes) {
        if (minutes < 0) {
            return -12598694;
        } else if (minutes == 0) {
            return -4179669;
        } else if (minutes < 60) {
            return -4179669;
        } else {
            return minutes < 360 ? -2056144 : -12598694;
        }
    }

    // the numbers come with the menu: the Claim Block itself only tells clients its colour

    private int needed(int slot) {
        return this.menu.needed(slot);
    }

    private int stored(int slot) {
        return this.menu.stored(slot);
    }

    private int upkeepMinutes() {
        return this.menu.upkeepMinutes();
    }

    private boolean decays() {
        return this.menu.decayEnabled();
    }
}

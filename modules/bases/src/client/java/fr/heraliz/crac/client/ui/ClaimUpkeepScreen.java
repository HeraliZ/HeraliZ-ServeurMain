package fr.heraliz.crac.client.ui;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.claim.UpkeepResource;
import fr.heraliz.crac.client.group.GroupTheme;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.menu.ClaimUpkeepMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

public class ClaimUpkeepScreen extends AbstractContainerScreen<ClaimUpkeepMenu> {
    private static final int CELL_BG = -16117472;
    private static final int CELL_BORDER = -14404269;
    private static final int OK_BORDER = -12598694;
    private static final int LOW_BORDER = -2056144;
    private static final int EMPTY_BORDER = -4179669;
    private static final int GHOST_VEIL = -1341517536;
    /** Text and bar colour of the countdown when the server has decay turned off: nothing to warn about. */
    private static final int NEUTRAL = -9536363;
    /**
     * Comptes des cases et compte à rebours : DM Sans gras 10 à sa vraie taille (avant : police de Minecraft réduite à 0,75
     * et 0,8 ; même hauteur des capitales et même largeur à quelques pour cent près). Un compte à rebours trop long passe à
     * la police suivante, plus petite (avant : réduit jusqu'à tenir). Tailles dans le même ordre.
     */
    private static final ResourceLocation[] POLICES = {new ResourceLocation("crac", "ui_compte"), new ResourceLocation("crac", "ui_small_bold"),
            new ResourceLocation("crac", "ui_moyen_bold"), new ResourceLocation("crac", "ui_mini_bold")};
    private static final float[] TAILLES = {10F, 9F, 7.5F, 6.5F};
    private List<Component> pendingTooltip;

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
        // onglet Accès de l'armoire (1.10.0) : qui a l'accès à la base, donné par le propriétaire
        Component acces = Component.translatable("crac.upkeep.onglet_acces");
        int[] r = this.ongletAcces();
        boolean survol = mouseX >= this.leftPos + r[0] && mouseX < this.leftPos + r[0] + r[2] && mouseY >= this.topPos + r[1] && mouseY < this.topPos + r[1] + r[3];
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], survol ? 0x405FD08A : 0x205FD08A);
        GroupTheme.border(g, r[0], r[1], r[2], r[3], survol ? 0xFF5FD08A : 0xA05FD08A);
        g.drawString(this.font, acces, r[0] + 4, r[1] + 2, survol ? 0xFFFFFFFF : 0xFF9BE3B3, false);
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
            // centré sous la case, milieu des capitales à sa place d'avant (police de Minecraft réduite à 0,75 posée à
            // y + 18,5 : capitales de 7, milieu à 18,5 + 0,75 x 3,5)
            Component compte = Component.literal(label);
            this.texte(g, compte, 0, sx + 8 - this.largeur(compte, 0) / 2F, sy + 21.125F, col, false);
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

        // plus petit et sous les comptes des cases (20/10 : « DÉGRADATION EN COURS » les recouvrait) ; la plus grande police
        // qui tient dans le panneau, milieu des capitales à sa place d'avant (y 56 + 0,8 x 3,5)
        int p = 0;
        while (p < POLICES.length - 1 && this.largeur(text, p) > this.imageWidth - 12) p++;
        this.texte(g, text, p, this.imageWidth / 2F - this.largeur(text, p) / 2F, 58.8F, col, true);
    }

    // ------------------------------------------------------------------ texte à sa vraie taille, posé net

    /** Largeur exacte du texte dans la police n° {@code police} de {@link #POLICES}. */
    private float largeur(Component c, int police) {
        return this.font.getSplitter().stringWidth(Ui.in(c, POLICES[police]));
    }

    /**
     * Texte dans la police n° {@code police} : bord gauche en x, milieu des capitales sur cy (DM Sans du mod, décalage 1 :
     * taille x 0,49309 - 1 sous le point de dessin), posé sur un point de l'écran.
     */
    private void texte(GuiGraphics g, Component c, int police, float x, float cy, int couleur, boolean ombre) {
        g.pose().pushPose();
        placer(g, x, cy - (TAILLES[police] * 0.49309F - 1F));
        g.drawString(this.font, Ui.in(c, POLICES[police]), 0, 0, couleur, ombre);
        g.pose().popPose();
    }

    /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes ; repris du kit des Caisses). */
    private static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    /** Bouton « Accès » en haut à droite (x, y, w, h dans l'écran). */
    private int[] ongletAcces() {
        int w = this.font.width(Component.translatable("crac.upkeep.onglet_acces")) + 8;
        return new int[]{this.imageWidth - 7 - w, 3, w, 12};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        int[] r = this.ongletAcces();
        if (bouton == 0 && mx >= this.leftPos + r[0] && mx < this.leftPos + r[0] + r[2] && my >= this.topPos + r[1] && my < this.topPos + r[1] + r[3]) {
            EcranAccesBase.demander(this.menu.pos());
            return true;
        }
        return super.mouseClicked(mx, my, bouton);
    }

    /** Même échelle que l'inventaire (avant : échelle forcée, écran tout petit en grande interface). */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.pendingTooltip = null;
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, pt);
        this.collectUpkeepTooltip(mouseX, mouseY);
        if (this.pendingTooltip != null) {
            g.renderComponentTooltip(this.font, this.pendingTooltip, mouseX, mouseY);
        } else if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            g.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
        }
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

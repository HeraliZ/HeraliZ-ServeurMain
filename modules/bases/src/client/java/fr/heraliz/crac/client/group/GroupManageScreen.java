package fr.heraliz.crac.client.group;

import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.network.C2SGroupAction;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2COpenGroupManage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * The group screen (validated mock-up): the group's colour and name, three tabs (Group, Members · n / max, Land), and
 * at the bottom who else may use the land and the Leave (or Disband) button. Members: head, name (the owner in gold with
 * the OWNER chip), online or not; the owner gets "make owner" and "kick" on the hovered row, and "+ add a member".
 */
public class GroupManageScreen extends EcranGroupe {
    private static final String[] TABS = new String[]{"crac.gui.tab.group", "crac.gui.tab.members", "crac.gui.tab.claim"};
    private static final float W = 340;
    private static final float H = 284;
    private static final float PAD = 13.33F;
    private static final float ROW = 22;
    private S2COpenGroupManage data;
    private int tab = 1;
    private float scroll;

    public GroupManageScreen(S2COpenGroupManage data) {
        super(Component.translatable("crac.gui.tab.group"));
        this.data = data;
    }

    public static void openOrRefresh(S2COpenGroupManage data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GroupManageScreen s) {
            s.data = data;
        } else {
            mc.setScreen(new GroupManageScreen(data));
        }
    }

    @Override
    protected void initCanevas() {
        this.placer(W, H);
    }

    private int color() {
        return this.data.color() & 0xFFFFFF;
    }

    private float contentX() {
        return this.x0 + PAD;
    }

    private float contentW() {
        return W - 2 * PAD;
    }

    private float contentTop() {
        return this.y0 + 65.33F;
    }

    private float contentBottom() {
        return this.y0 + H - 32 - 4;
    }

    private float tabW() {
        return (this.contentW() - 2 * 3.33F) / TABS.length;
    }

    private String tabLabel(int i) {
        if (i == 1) {
            return Component.translatable("crac.gui.tab.members_count", this.data.members().size(), maxMembers()).getString();
        }
        return Component.translatable(TABS[i]).getString();
    }

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        this.fond(g);
        this.panneau(g, this.color());
        String sub = Component.translatable(this.data.hasClaim() ? "crac.gui.subtitle_claim" : "crac.gui.subtitle", this.data.tag()).getString();
        this.entete(g, this.color(), this.data.name(), mix(this.color(), BLANC, 0.72F), sub);
        // tabs
        float tw = this.tabW();
        for (int i = 0; i < TABS.length; i++) {
            float x = this.contentX() + i * (tw + 3.33F), y = this.y0 + 40;
            boolean on = i == this.tab;
            boolean hov = !on && dans(mx, my, x, y, tw, 15.33F);
            if (on) {
                rect(g, x, y, x + tw, y + 15.33F, a(BLANC, 18));
                contour(g, x, y, tw, 15.33F, this.px, a(BLANC, 41));
                rect(g, x, y + 15.33F - 0.67F, x + tw, y + 15.33F, a(ACCENT, 255));
            } else if (hov) {
                rect(g, x, y, x + tw, y + 15.33F, a(BLANC, 8));
            }
            capsCentre(g, this.font, this.tabLabel(i), MINI_GRAS, x + tw / 2, y + 7.67F - 4.21F, 0.8F, a(on || hov ? BLANC : GRIS, 255));
        }
        switch (this.tab) {
            case 0 -> this.renderGroupTab(g);
            case 1 -> this.renderMembersTab(g, mx, my);
            default -> this.renderClaimTab(g);
        }
        // footer: who else may use the land, and leave / disband
        float fy = this.y0 + H - 32;
        rect(g, this.contentX(), fy, this.contentX() + this.contentW(), fy + this.px, a(BLANC, 20));
        float[] b = this.actionRect();
        String action = Component.translatable(this.data.viewerIsOwner() ? "crac.gui.disband" : "crac.gui.leave").getString();
        this.grandBouton(g, b[0], b[1], b[2], b[3], action, 0xFF6B6B, dans(mx, my, b[0], b[1], b[2], b[3]), true);
        List<FormattedCharSequence> hint = this.font.split(lit(Component.translatable("crac.gui.clan_access").getString(), MINI),
            Math.round(Math.min(186.67F, b[0] - this.contentX() - 10)));
        float ly = b[1] + b[3] / 2 - hint.size() * 6.67F / 2 + 3.33F - 4.21F;
        for (FormattedCharSequence line : hint) {
            g.pose().pushPose();
            g.pose().translate(this.contentX(), ly, 0);
            g.drawString(this.font, line, 0, 0, a(ATTENUE, 255), false);
            g.pose().popPose();
            ly += 6.67F;
        }
    }

    private float[] actionRect() {
        String action = Component.translatable(this.data.viewerIsOwner() ? "crac.gui.disband" : "crac.gui.leave").getString();
        float w = this.largeurGrandBouton(action);
        return new float[]{this.x0 + W - PAD - w, this.y0 + H - 25.33F, w, 16};
    }

    // ------------------------------------------------------------------ tabs

    private void info(GuiGraphics g, float y, String label, String value) {
        caps(g, this.font, label, MINI, this.contentX(), y - 4.21F, 0.8F, a(ATTENUE, 255));
        String v = Ui.ellipsis(this.font, value, MOYEN, Math.round(this.contentW() - 80));
        texte(g, this.font, lit(v, MOYEN), this.contentX() + 80, y - 4.7F, a(TEXTE, 255));
    }

    private void renderGroupTab(GuiGraphics g) {
        float y = this.contentTop() + 8;
        this.info(g, y, Component.translatable("crac.gui.name").getString(), this.data.name());
        y += 16;
        this.info(g, y, Component.translatable("crac.gui.tag").getString(), "[" + this.data.tag() + "]");
        y += 16;
        this.info(g, y, Component.translatable("crac.gui.members").getString(), this.data.members().size() + " / " + maxMembers());
        y += 16;
        this.info(g, y, Component.translatable("crac.gui.owner").getString(), this.ownerName());
        y += 16;
        caps(g, this.font, Component.translatable("crac.gui.color").getString(), MINI, this.contentX(), y - 4.21F, 0.8F, a(ATTENUE, 255));
        float sx = this.contentX() + 80;
        rect(g, sx, y - 4, sx + 26.67F, y + 4, a(this.color(), 255));
        contour(g, sx, y - 4, 26.67F, 8, this.px, a(BLANC, 89));
    }

    private void renderMembersTab(GuiGraphics g, float mx, float my) {
        List<S2COpenGroupManage.MemberEntry> members = this.data.members();
        float x = this.contentX(), w = this.contentW();
        float top = this.contentTop(), bottom = this.contentBottom();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.listHeight() - (bottom - top)));
        this.decoupe(g, x - 2, top, x + w + 2, bottom);
        boolean inList = my >= top && my < bottom;
        for (int i = 0; i < members.size(); i++) {
            S2COpenGroupManage.MemberEntry m = members.get(i);
            float y = this.rowY(i);
            if (y + ROW < top || y > bottom) {
                continue;
            }
            boolean isOwner = m.id().equals(this.data.owner());
            boolean hov = inList && dans(mx, my, x, y, w, ROW);
            boolean actions = this.data.viewerIsOwner() && !isOwner;
            if (hov) {
                this.ligneSurvol(g, x, y, w, ROW);
            }
            this.tete(g, m.id(), x + 6, y + 4.67F, 12.67F);
            float nx = x + 24;
            float right = x + w - 6;
            if (isOwner) {
                String chef = Component.translatable("crac.gui.owner_tag").getString();
                float cw = largeurCaps(this.font, chef, MINI, 0.56F) + 6;
                pastille(g, this.font, chef, right - cw, y + 11, OR, this.px);
                right -= cw + 6;
            } else if (actions && hov) {
                float[] k = this.kickRect(i);
                float[] p = this.promoteRect(i);
                this.petitBouton(g, k[0], k[1], k[2], k[3], Component.translatable("crac.gui.kick").getString(), 0xFF6B6B, dans(mx, my, k[0], k[1], k[2], k[3]), false);
                this.petitBouton(g, p[0], p[1], p[2], p[3], Component.translatable("crac.gui.make_owner").getString(), -1, dans(mx, my, p[0], p[1], p[2], p[3]), true);
                right = p[0] - 6;
            }
            String name = Ui.ellipsis(this.font, m.name(), GRAS_9, Math.round(right - nx));
            texte(g, this.font, lit(name, GRAS_9), nx, y + 7.33F - 5.44F, a(isOwner ? OR : BLANC, 255));
            disque(g, nx + 1.33F, y + 16, 1.33F, a(m.online() ? VERT : 0x5A6678, 255));
            String st = Component.translatable(m.online() ? "crac.gui.online" : "crac.gui.offline").getString();
            texte(g, this.font, lit(st, MINI), nx + 5.33F, y + 16 - 4.21F, a(ATTENUE, 255));
        }
        if (this.canAdd()) {
            float[] r = this.addRect();
            if (r[1] < bottom) {
                boolean hov = inList && dans(mx, my, r[0], r[1], r[2], r[3]);
                rect(g, r[0], r[1], r[0] + r[2], r[1] + r[3], a(VERT, hov ? 26 : 8));
                contour(g, r[0], r[1], r[2], r[3], this.px, a(VERT, hov ? 150 : 89));
                capsCentre(g, this.font, Component.translatable("crac.gui.add_member_plus").getString(), MINI_GRAS, r[0] + r[2] / 2,
                    r[1] + r[3] / 2 - 4.21F, 0.7F, a(hov ? 0xCFF3DA : VERT_PALE, 255));
            }
        }
        g.disableScissor();
    }

    private void renderClaimTab(GuiGraphics g) {
        float x = this.contentX(), w = this.contentW();
        float y = this.contentTop() + 8;
        if (this.data.hasClaim()) {
            this.info(g, y, Component.translatable("crac.gui.center").getString(),
                this.data.claimCenter().getX() + ", " + this.data.claimCenter().getZ());
            y += 18;
        } else {
            texte(g, this.font, lit(Component.translatable("crac.gui.no_claim").getString(), MOYEN), x, y - 4.7F, a(TEXTE, 255));
            y += 10;
            texte(g, this.font, lit(Component.translatable("crac.gui.place_claim").getString(), MINI), x, y - 4.21F, a(ATTENUE, 255));
            y += 16;
        }
        this.section(g, Component.translatable("crac.gui.structures").getString(), x, y, w);
        y += 13;
        if (this.data.builds().isEmpty()) {
            texte(g, this.font, lit(Component.translatable("crac.gui.no_structures").getString(), MINI), x, y - 4.21F, a(ATTENUE, 255));
            return;
        }
        // liste découpée sous le titre, molette pour voir la suite (avant : arrêtée en bas du panneau)
        float top = y - 6, bottom = this.contentBottom();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.data.builds().size() * 12 - (bottom - top)));
        this.decoupe(g, x - 2, top, x + w + 2, bottom);
        y -= this.scroll;
        for (S2COpenGroupManage.BuildCount b : this.data.builds()) {
            if (y + 6 >= top && y - 6 <= bottom) {
                String n = String.valueOf(b.count());
                float nw = largeur(this.font, n, MOYEN_GRAS);
                String label = Component.translatable("crac.gui.pieces_of", Component.translatable(b.label())).getString();
                label = Ui.ellipsis(this.font, label, MOYEN, Math.round(w - nw - 8));
                texte(g, this.font, lit(label, MOYEN), x, y - 4.7F, a(TEXTE, 255));
                texte(g, this.font, lit(n, MOYEN_GRAS), x + w - nw, y - 4.7F, a(BLANC, 255));
            }
            y += 12;
        }
        g.disableScissor();
    }

    // ------------------------------------------------------------------ geometry

    private boolean canAdd() {
        return this.data.viewerIsOwner() && this.data.members().size() < maxMembers();
    }

    private float listHeight() {
        return this.data.members().size() * ROW + (this.canAdd() ? 2.67F + 18.67F : 0);
    }

    private float rowY(int i) {
        return this.contentTop() + i * ROW - this.scroll;
    }

    private float[] kickRect(int i) {
        String s = Component.translatable("crac.gui.kick").getString();
        float w = this.largeurPetitBouton(s, false);
        return new float[]{this.contentX() + this.contentW() - 6 - w, this.rowY(i) + 5, w, 12};
    }

    /** "Make owner" button, left of the kick button. */
    private float[] promoteRect(int i) {
        float[] k = this.kickRect(i);
        String s = Component.translatable("crac.gui.make_owner").getString();
        float w = this.largeurPetitBouton(s, true);
        return new float[]{k[0] - 2.67F - w, k[1], w, 12};
    }

    private float[] addRect() {
        return new float[]{this.contentX(), this.rowY(this.data.members().size()) + 2.67F, this.contentW(), 18.67F};
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean clic(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        float tw = this.tabW();
        for (int i = 0; i < TABS.length; i++) {
            if (dans(mx, my, this.contentX() + i * (tw + 3.33F), this.y0 + 40, tw, 15.33F)) {
                this.tab = i;
                this.scroll = 0;
                return true;
            }
        }
        float[] b = this.actionRect();
        if (dans(mx, my, b[0], b[1], b[2], b[3])) {
            boolean owner = this.data.viewerIsOwner();
            this.minecraft.setScreen(new ConfirmationEcran(
                Component.translatable(owner ? "crac.gui.confirm_disband" : "crac.gui.confirm_leave").getString(),
                () -> NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(owner ? C2SGroupAction.Type.DISBAND : C2SGroupAction.Type.LEAVE, null)),
                this, true));
            return true;
        }
        if (this.tab == 1 && this.data.viewerIsOwner() && my >= this.contentTop() && my < this.contentBottom()) {
            List<S2COpenGroupManage.MemberEntry> members = this.data.members();
            for (int i = 0; i < members.size(); i++) {
                if (members.get(i).id().equals(this.data.owner())) {
                    continue;
                }
                float[] k = this.kickRect(i);
                if (dans(mx, my, k[0], k[1], k[2], k[3])) {
                    NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.KICK, members.get(i).id()));
                    return true;
                }
                float[] p = this.promoteRect(i);
                if (dans(mx, my, p[0], p[1], p[2], p[3])) {
                    UUID target = members.get(i).id();
                    this.minecraft.setScreen(new ConfirmationEcran(
                        Component.translatable("crac.gui.confirm_owner", members.get(i).name()).getString(),
                        () -> NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.PROMOTE, target)),
                        this, false));
                    return true;
                }
            }
            if (this.canAdd()) {
                float[] r = this.addRect();
                if (dans(mx, my, r[0], r[1], r[2], r[3])) {
                    NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.REQUEST_ONLINE, null));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean molette(double mx, double my, double delta) {
        if (this.tab != 0) {   // membres, et liste des constructions de l'onglet Terrain
            this.scroll -= (float) delta * 12;
            return true;
        }
        return false;
    }

    private static int maxMembers() {
        return Group.maxMembers();
    }

    private String ownerName() {
        for (S2COpenGroupManage.MemberEntry m : this.data.members()) {
            if (m.id().equals(this.data.owner())) {
                return m.name();
            }
        }
        return "?";
    }

    public static ResourceLocation skinFor(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getSkinLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(id);
    }
}

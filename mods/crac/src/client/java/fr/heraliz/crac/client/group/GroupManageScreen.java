package fr.heraliz.crac.client.group;

import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.client.ScaledScreen;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.network.C2SGroupAction;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2COpenGroupManage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class GroupManageScreen extends ScaledScreen {
    private static final int PANEL_W = 244;
    private static final int PANEL_H = 224;
    private static final String[] TABS = new String[]{"crac.gui.tab.group", "crac.gui.tab.members", "crac.gui.tab.claim"};
    private static final int PAD = 14;
    private static final int TAB_Y = 32;
    private static final int TAB_H = 16;
    private static final int CONTENT_Y = 56;
    private static final int ROW_H = 18;
    private static final int ROW_BOX_H = 16;
    private static final int LINE_H = 16;
    private S2COpenGroupManage data;
    private int tab = 0;
    private int left;
    private int top;
    private int contentX;
    private int contentY;
    private int contentW;

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
    protected void init() {
        this.left = (this.guiW() - 244) / 2;
        this.top = (this.guiH() - 224) / 2;
        this.contentX = this.left + 14;
        this.contentY = this.top + 56;
        this.contentW = 216;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        GroupTheme.panel(g, this.left, this.top, 244, 224);
        int col = 0xFF000000 | this.data.color() & 16777215;
        String title = this.data.name();
        g.drawString(this.font, title, this.left + 14, this.top + 12, col, false);
        g.drawString(this.font, "[" + this.data.tag() + "]", this.left + 14 + this.font.width(title) + 4, this.top + 12, -9536363, false);
        g.fill(this.left + 14, this.top + 26, this.left + 244 - 14, this.top + 27, -13745040);
        this.drawTabs(g);
        switch (this.tab) {
            case 0:
                this.renderGroupTab(g, mouseX, mouseY);
                break;
            case 1:
                this.renderMembersTab(g, mouseX, mouseY);
                break;
            case 2:
                this.renderClaimTab(g);
        }

        this.renderWidgets(g, mouseX, mouseY, pt);
    }

    private void drawTabs(GuiGraphics g) {
        int y = this.top + 32;
        int w = this.contentW / TABS.length;

        for (int i = 0; i < TABS.length; i++) {
            int x = this.contentX + i * w;
            boolean active = i == this.tab;
            g.fill(x, y, x + w - 2, y + 16, active ? -14996400 : -201062385);
            GroupTheme.border(g, x, y, w - 2, 16, active ? -11634488 : -14536366);
            int tc = active ? -1 : -9536363;
            String tab = Component.translatable(TABS[i]).getString();
            g.drawString(this.font, tab, x + (w - 2 - this.font.width(tab)) / 2, y + 4, tc, false);
        }
    }

    private void renderGroupTab(GuiGraphics g, int mouseX, int mouseY) {
        int x = this.contentX;
        int y = this.contentY;
        this.line(g, x, y, Component.translatable("crac.gui.name").getString(), this.data.name());
        y += 16;
        this.line(g, x, y, Component.translatable("crac.gui.tag").getString(), this.data.tag());
        y += 16;
        this.line(g, x, y, Component.translatable("crac.gui.members").getString(), this.data.members().size() + " / " + maxMembers());
        y += 16;
        this.line(g, x, y, Component.translatable("crac.gui.owner").getString(), this.ownerName());
        y += 16;
        g.drawString(this.font, Component.translatable("crac.gui.color"), x, y + 2, -9536363, false);
        g.fill(x + 60, y + 1, x + 100, y + 11, 0xFF000000 | this.data.color() & 16777215);
        GroupTheme.border(g, x + 60, y + 1, 40, 10, -14009250);
        int[] b = this.actionRect();
        boolean hover = inRect(mouseX, mouseY, b[0], b[1], b[2], b[3]);
        String label = Component.translatable(this.data.viewerIsOwner() ? "crac.gui.disband" : "crac.gui.leave").getString();
        GroupTheme.button(g, this.font, b[0], b[1], b[2], b[3], label, hover ? -2074037 : -4179669);
    }

    private void renderMembersTab(GuiGraphics g, int mouseX, int mouseY) {
        int max = maxMembers();
        g.drawString(this.font, Component.translatable("crac.gui.members_count", this.data.members().size(), max), this.contentX, this.contentY, -9536363, false);
        List<S2COpenGroupManage.MemberEntry> members = this.data.members();

        for (int i = 0; i < members.size(); i++) {
            S2COpenGroupManage.MemberEntry m = members.get(i);
            int y = this.rowY(i);
            g.fill(this.contentX, y, this.contentX + this.contentW, y + 16, -16116954);
            GroupTheme.border(g, this.contentX, y, this.contentW, 16, -14009250);
            PlayerFaceRenderer.draw(g, skinFor(m.id()), this.contentX, y, 16);
            boolean isOwner = m.id().equals(this.data.owner());
            boolean kickable = this.data.viewerIsOwner() && !isOwner;
            int rightEdge = this.contentX + this.contentW - 4;
            if (kickable) {
                int[] k = this.kickRect(i);
                boolean hover = inRect(mouseX, mouseY, k[0], k[1], 12, 12);
                GroupTheme.button(g, this.font, k[0], k[1], 12, 12, "x", hover ? -2074037 : -4179669);
                int[] p = this.promoteRect(i);
                boolean hoverPromote = inRect(mouseX, mouseY, p[0], p[1], 12, 12);
                GroupTheme.button(g, this.font, p[0], p[1], 12, 12, "\u2605", hoverPromote ? -10757772 : -12598694);
                rightEdge = p[0] - 4;
            }

            if (isOwner) {
                String ownerTag = Component.translatable("crac.gui.owner_tag").getString();
                int ow = this.font.width(ownerTag);
                g.drawString(this.font, ownerTag, rightEdge - ow, y + 4, -9536363, false);
                rightEdge -= ow + 6;
            }

            int nameX = this.contentX + 16 + 6;
            int dotW = 8;
            String name = this.trim(m.name(), rightEdge - nameX - dotW);
            int nameCol = isOwner ? 0xFF000000 | this.data.color() & 16777215 : -3878166;
            g.drawString(this.font, name, nameX, y + 4, nameCol, false);
            GroupTheme.dot(g, nameX + this.font.width(name) + 5, y + 8, 2, m.online() ? -12266886 : -10852744);
        }

        if (this.data.viewerIsOwner() && members.size() < max) {
            int[] a = this.addRect();
            boolean hover = inRect(mouseX, mouseY, a[0], a[1], this.contentW, 16);
            int c = hover ? -10757772 : -12598694;
            GroupTheme.border(g, a[0], a[1], this.contentW, 16, c);
            g.drawString(this.font, Component.translatable("crac.gui.add_member_plus"), a[0] + 8, a[1] + 4, c, false);
        }
    }

    private void renderClaimTab(GuiGraphics g) {
        int x = this.contentX;
        int y = this.contentY;
        if (this.data.hasClaim()) {
            this.line(g, x, y, Component.translatable("crac.gui.center").getString(), this.data.claimCenter().getX() + ", " + this.data.claimCenter().getZ());
            y += 22;
        } else {
            g.drawString(this.font, Component.translatable("crac.gui.no_claim"), x, y, -9536363, false);
            y += 12;
            g.drawString(this.font, Component.translatable("crac.gui.place_claim"), x, y, -9536363, false);
            y += 18;
        }

        g.drawString(this.font, Component.translatable("crac.gui.structures"), x, y, -6504193, false);
        y += 14;
        if (this.data.builds().isEmpty()) {
            g.drawString(this.font, Component.translatable("crac.gui.no_structures"), x, y, -9536363, false);
        } else {
            for (S2COpenGroupManage.BuildCount b : this.data.builds()) {
                if (y > this.top + 224 - 20) {
                    break;
                }

                g.drawString(this.font, Component.translatable("crac.gui.pieces_of", Component.translatable(b.label())).getString() + " : " + b.count(), x, y, -3878166, false);
                y += 12;
            }
        }
    }

    private void line(GuiGraphics g, int x, int y, String key, String value) {
        g.drawString(this.font, key, x, y + 2, -9536363, false);
        g.drawString(this.font, this.trim(value, this.contentW - 60), x + 60, y + 2, -3878166, false);
    }

    private String trim(String s, int maxWidth) {
        return maxWidth > 0 && this.font.width(s) > maxWidth ? this.font.plainSubstrByWidth(s, Math.max(0, maxWidth - this.font.width("..."))) + "..." : s;
    }

    private static int maxMembers() {
        return Group.maxMembers();
    }

    private int rowY(int i) {
        return this.contentY + 13 + i * 18;
    }

    private int[] kickRect(int i) {
        return new int[]{this.contentX + this.contentW - 16, this.rowY(i) + 2};
    }

    /** "Make owner" button, left of the kick button. */
    private int[] promoteRect(int i) {
        return new int[]{this.contentX + this.contentW - 32, this.rowY(i) + 2};
    }

    private int[] addRect() {
        return new int[]{this.contentX, this.rowY(this.data.members().size())};
    }

    private int[] actionRect() {
        return new int[]{this.left + 244 - 14 - 100, this.top + 224 - 26, 100, 16};
    }

    @Override
    protected boolean clickContent(double mx, double my, int button) {
        int tw = this.contentW / TABS.length;

        for (int i = 0; i < TABS.length; i++) {
            if (inRect(mx, my, this.contentX + i * tw, this.top + 32, tw - 2, 16)) {
                this.tab = i;
                return true;
            }
        }

        if (this.tab == 0) {
            int[] b = this.actionRect();
            if (inRect(mx, my, b[0], b[1], b[2], b[3])) {
                boolean owner = this.data.viewerIsOwner();
                this.minecraft
                    .setScreen(
                        new ConfirmScreen(
                            Component.translatable(owner ? "crac.gui.confirm_disband" : "crac.gui.confirm_leave").getString(),
                            () -> NetworkHandler.CHANNEL
                                .sendToServer(new C2SGroupAction(owner ? C2SGroupAction.Type.DISBAND : C2SGroupAction.Type.LEAVE, null)),
                            null
                        )
                    );
                return true;
            }
        }

        if (this.tab == 1 && this.data.viewerIsOwner()) {
            List<S2COpenGroupManage.MemberEntry> members = this.data.members();

            for (int ix = 0; ix < members.size(); ix++) {
                if (!members.get(ix).id().equals(this.data.owner())) {
                    int[] k = this.kickRect(ix);
                    if (inRect(mx, my, k[0], k[1], 12, 12)) {
                        NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.KICK, members.get(ix).id()));
                        return true;
                    }
                    int[] p = this.promoteRect(ix);
                    if (inRect(mx, my, p[0], p[1], 12, 12)) {
                        UUID target = members.get(ix).id();
                        this.minecraft.setScreen(new ConfirmScreen(
                            Component.translatable("crac.gui.confirm_owner", members.get(ix).name()).getString(),
                            () -> NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.PROMOTE, target)),
                            null
                        ));
                        return true;
                    }
                }
            }

            if (members.size() < maxMembers()) {
                int[] a = this.addRect();
                if (inRect(mx, my, a[0], a[1], this.contentW, 16)) {
                    NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.REQUEST_ONLINE, null));
                    return true;
                }
            }
        }

        return this.clickWidgets(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String ownerName() {
        for (S2COpenGroupManage.MemberEntry m : this.data.members()) {
            if (m.id().equals(this.data.owner())) {
                return m.name();
            }
        }

        return "?";
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
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

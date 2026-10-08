package fr.heraliz.crac.client.group;

import java.util.List;
import fr.heraliz.crac.client.ScaledScreen;
import fr.heraliz.crac.network.C2SInviteResponse;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2COpenInvites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

public class InvitationsScreen extends ScaledScreen {
    private static final int PANEL_W = 252;
    private static final int CARD_H = 64;
    private static final int HEADER = 34;
    private static final int FOOTER = 12;
    private static final int BTN_W = 78;
    private static final int BTN_H = 15;
    private final List<S2COpenInvites.InviteEntry> invites;
    private int left;
    private int top;
    private int panelH;

    public InvitationsScreen(List<S2COpenInvites.InviteEntry> invites) {
        super(Component.translatable("crac.gui.invitations"));
        this.invites = invites;
    }

    public static void open(List<S2COpenInvites.InviteEntry> invites) {
        if (invites.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof InvitationsScreen) {
                mc.setScreen(null);
            }
        } else {
            Minecraft.getInstance().setScreen(new InvitationsScreen(invites));
        }
    }

    @Override
    protected void init() {
        this.panelH = 34 + this.invites.size() * 64 + 12;
        this.left = (this.guiW() - 252) / 2;
        this.top = (this.guiH() - this.panelH) / 2;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        GroupTheme.panel(g, this.left, this.top, 252, this.panelH);
        g.drawCenteredString(this.font, Component.translatable("crac.gui.invitations"), this.left + 126, this.top + 12, -6504193);
        g.fill(this.left + 14, this.top + 26, this.left + 252 - 14, this.top + 27, -13745040);

        for (int i = 0; i < this.invites.size(); i++) {
            this.renderCard(g, mouseX, mouseY, i);
        }

        this.renderWidgets(g, mouseX, mouseY, pt);
    }

    private void renderCard(GuiGraphics g, int mouseX, int mouseY, int i) {
        S2COpenInvites.InviteEntry e = this.invites.get(i);
        int x = this.left + 14;
        int w = 224;
        int cy = this.top + 34 + i * 64;
        g.fill(x, cy, x + w, cy + 64 - 6, -16116954);
        GroupTheme.border(g, x, cy, w, 58, -14009250);
        PlayerFaceRenderer.draw(g, GroupManageScreen.skinFor(e.inviterId()), x + 4, cy + 4, 16);
        int gc = 0xFF000000 | e.groupColor() & 16777215;
        g.drawString(this.font, e.inviterName(), x + 24, cy + 5, -3878166, false);
        g.drawString(this.font, e.groupName() + " [" + e.groupTag() + "]", x + 24, cy + 16, gc, false);
        g.drawString(this.font, Component.translatable("crac.gui.invited_you"), x + 24, cy + 27, -9536363, false);
        int[] a = this.acceptRect(i);
        int[] d = this.declineRect(i);
        boolean ha = inRect(mouseX, mouseY, a[0], a[1], 78, 15);
        boolean hd = inRect(mouseX, mouseY, d[0], d[1], 78, 15);
        int ac = ha ? -10757772 : -12598694;
        int dc = hd ? -2074037 : -4179669;
        GroupTheme.border(g, a[0], a[1], 78, 15, ac);
        GroupTheme.check(g, a[0] + 8, a[1] + 3, ac);
        g.drawString(this.font, Component.translatable("crac.gui.accept"), a[0] + 22, a[1] + 4, ac, false);
        GroupTheme.border(g, d[0], d[1], 78, 15, dc);
        GroupTheme.cross(g, d[0] + 8, d[1] + 4, dc);
        g.drawString(this.font, Component.translatable("crac.gui.decline"), d[0] + 22, d[1] + 4, dc, false);
    }

    private int[] acceptRect(int i) {
        int cy = this.top + 34 + i * 64;
        return new int[]{this.left + 18, cy + 64 - 27};
    }

    private int[] declineRect(int i) {
        int cy = this.top + 34 + i * 64;
        return new int[]{this.left + 18 + 78 + 8, cy + 64 - 27};
    }

    @Override
    protected boolean clickContent(double mx, double my, int button) {
        for (int i = 0; i < this.invites.size(); i++) {
            int[] a = this.acceptRect(i);
            int[] d = this.declineRect(i);
            if (inRect(mx, my, a[0], a[1], 78, 15)) {
                NetworkHandler.CHANNEL.sendToServer(new C2SInviteResponse(this.invites.get(i).groupId(), true));
                return true;
            }

            if (inRect(mx, my, d[0], d[1], 78, 15)) {
                NetworkHandler.CHANNEL.sendToServer(new C2SInviteResponse(this.invites.get(i).groupId(), false));
                return true;
            }
        }

        return this.clickWidgets(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

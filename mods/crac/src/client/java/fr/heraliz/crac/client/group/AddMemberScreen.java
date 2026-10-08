package fr.heraliz.crac.client.group;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.client.ScaledScreen;
import fr.heraliz.crac.network.C2SGroupAction;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2COnlinePlayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class AddMemberScreen extends ScaledScreen {
    private static final int PANEL_W = 204;
    private static final int PANEL_H = 188;
    private static final int ROW_H = 20;
    private static final int VISIBLE = 6;
    private final List<S2COnlinePlayers.Entry> all;
    private final List<S2COnlinePlayers.Entry> filtered = new ArrayList<>();
    private String search = "";
    private int scroll = 0;
    private int left;
    private int top;
    private int listX;
    private int listY;
    private int listW;
    private int searchY;
    private int fieldH = 16;
    private int backY;

    public AddMemberScreen(List<S2COnlinePlayers.Entry> players) {
        super(Component.translatable("crac.gui.add_member"));
        this.all = players;
    }

    public static void open(List<S2COnlinePlayers.Entry> players) {
        Minecraft.getInstance().setScreen(new AddMemberScreen(players));
    }

    @Override
    protected void init() {
        this.left = (this.guiW() - 204) / 2;
        this.top = (this.guiH() - 188) / 2;
        this.listX = this.left + 14;
        this.listW = 176;
        this.searchY = this.top + 34;
        this.listY = this.top + 58;
        this.backY = this.top + 188 - 24;
        this.refilter();
    }

    private void refilter() {
        this.filtered.clear();
        String q = this.search.toLowerCase();

        for (S2COnlinePlayers.Entry e : this.all) {
            if (q.isEmpty() || e.name().toLowerCase().contains(q)) {
                this.filtered.add(e);
            }
        }

        this.scroll = 0;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        GroupTheme.panel(g, this.left, this.top, 204, 188);
        g.drawCenteredString(this.font, Component.translatable("crac.gui.add_member"), this.left + 102, this.top + 12, -6504193);
        g.fill(this.left + 14, this.top + 26, this.left + 204 - 14, this.top + 27, -13745040);
        boolean blink = System.currentTimeMillis() / 500L % 2L == 0L;
        GroupTheme.field(g, this.listX, this.searchY, this.listW, this.fieldH, true);
        String shown = (this.search.isEmpty() ? Component.translatable("crac.gui.search").getString() : this.search) + (blink ? "_" : "");
        int sc = this.search.isEmpty() ? -9536363 : -3878166;
        g.drawString(this.font, shown, this.listX + 5, this.searchY + 4, sc, false);
        if (this.filtered.isEmpty()) {
            g.drawString(this.font, Component.translatable("crac.gui.no_players"), this.listX, this.listY + 6, -9536363, false);
        }

        int end = Math.min(this.filtered.size(), this.scroll + 6);

        for (int idx = this.scroll; idx < end; idx++) {
            S2COnlinePlayers.Entry e = this.filtered.get(idx);
            int y = this.listY + (idx - this.scroll) * 20;
            boolean hover = inRect(mouseX, mouseY, this.listX, y, this.listW, 18);
            g.fill(this.listX, y, this.listX + this.listW, y + 20 - 2, hover ? -14996402 : -16116954);
            GroupTheme.border(g, this.listX, y, this.listW, 18, hover ? -11634488 : -14009250);
            PlayerFaceRenderer.draw(g, GroupManageScreen.skinFor(e.id()), this.listX + 2, y + 1, 16);
            g.drawString(this.font, e.name(), this.listX + 24, y + 5, -3878166, false);
        }

        int bw = 60;
        int bx = this.left + 14;
        boolean hb = inRect(mouseX, mouseY, bx, this.backY, bw, 16);
        GroupTheme.button(g, this.font, bx, this.backY, bw, 16, Component.translatable("crac.gui.back").getString(), hb ? -1 : -9536363);
        this.renderWidgets(g, mouseX, mouseY, pt);
    }

    @Override
    protected boolean clickContent(double mx, double my, int button) {
        int end = Math.min(this.filtered.size(), this.scroll + 6);

        for (int idx = this.scroll; idx < end; idx++) {
            int y = this.listY + (idx - this.scroll) * 20;
            if (inRect(mx, my, this.listX, y, this.listW, 18)) {
                NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.ADD, this.filtered.get(idx).id()));
                return true;
            }
        }

        if (inRect(mx, my, this.left + 14, this.backY, 60, 16)) {
            NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.REFRESH, null));
            return true;
        } else {
            return this.clickWidgets(mx, my, button);
        }
    }

    @Override
    protected boolean scrollContent(double mx, double my, double delta) {
        int max = Math.max(0, this.filtered.size() - 6);
        this.scroll = Mth.clamp(this.scroll - (int)Math.signum(delta), 0, max);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (c >= ' ' && c != 127 && this.search.length() < 16) {
            this.search = this.search + c;
            this.refilter();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 259 && !this.search.isEmpty()) {
            this.search = this.search.substring(0, this.search.length() - 1);
            this.refilter();
            return true;
        } else {
            return super.keyPressed(key, scan, mods);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

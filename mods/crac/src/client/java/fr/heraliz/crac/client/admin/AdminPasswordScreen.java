package fr.heraliz.crac.client.admin;

import fr.heraliz.crac.client.ScaledScreen;
import fr.heraliz.crac.client.group.GroupTheme;
import fr.heraliz.crac.network.C2SAdminLogin;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AdminPasswordScreen extends ScaledScreen {
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 110;
    private EditBox passwordBox;
    private int left;
    private int top;
    private int btnX;
    private int btnY;
    private int btnW = 90;
    private int btnH = 18;
    private boolean wrongPassword = false;

    public AdminPasswordScreen() {
        super(Component.literal("Admin Login"));
    }

    @Override
    protected void init() {
        this.left = (this.guiW() - 220) / 2;
        this.top = (this.guiH() - 110) / 2;
        this.passwordBox = new EditBox(this.font, this.left + 20, this.top + 44, 180, 16, Component.literal("Password"));
        this.passwordBox.setMaxLength(64);
        this.passwordBox.setBordered(false);
        this.passwordBox.setFormatter((text, pos) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY.withColor(-1)));
        this.addRenderableWidget(this.passwordBox);
        this.setInitialFocus(this.passwordBox);
        this.btnX = this.left + (220 - this.btnW) / 2;
        this.btnY = this.top + 110 - 30;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        GroupTheme.panel(g, this.left, this.top, 220, 110);
        g.drawCenteredString(this.font, "RAC admin panel", this.left + 110, this.top + 12, -6504193);
        g.drawString(this.font, "Password:", this.left + 20, this.top + 30, -3878166);
        GroupTheme.field(g, this.left + 16, this.top + 40, 188, 22, this.passwordBox.isFocused());
        if (this.wrongPassword) {
            // the server says why in chat (wrong password, lockout, operator level, console disabled)
            g.drawCenteredString(this.font, "Login refused, see chat", this.left + 110, this.top + 66, -2074037);
        }

        boolean hover = mouseX >= this.btnX && mouseX < this.btnX + this.btnW && mouseY >= this.btnY && mouseY < this.btnY + this.btnH;
        GroupTheme.button(g, this.font, this.btnX, this.btnY, this.btnW, this.btnH, "Login", hover ? -10757772 : -12598694);
        this.renderWidgets(g, mouseX, mouseY, pt);
    }

    @Override
    protected boolean clickContent(double mx, double my, int button) {
        if (mx >= this.btnX && mx < this.btnX + this.btnW && my >= this.btnY && my < this.btnY + this.btnH) {
            this.submit();
            return true;
        } else {
            return this.clickWidgets(mx, my, button);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key != 257 && key != 335) {
            return super.keyPressed(key, scan, mods);
        } else {
            this.submit();
            return true;
        }
    }

    private void submit() {
        String pw = this.passwordBox.getValue();
        if (!pw.isEmpty()) {
            this.wrongPassword = false;
            NetworkHandler.CHANNEL.sendToServer(new C2SAdminLogin(pw));
        }
    }

    public void onWrongPassword() {
        this.wrongPassword = true;
        this.passwordBox.setValue("");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

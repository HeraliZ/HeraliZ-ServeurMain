package fr.heraliz.crac.client.group;

import java.util.List;
import fr.heraliz.crac.client.ScaledScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class ConfirmScreen extends ScaledScreen {
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 104;
    private static final int BTN_DISABLED = -12960187;
    private final String message;
    private final Runnable onYes;
    private final Screen back;
    private final boolean yesEnabled;
    private final String disabledTooltip;
    private int left;
    private int top;
    private int yesX;
    private int noX;
    private int btnY;
    private int btnW = 80;
    private int btnH = 18;

    public ConfirmScreen(String message, Runnable onYes, Screen back) {
        this(message, onYes, back, true, null);
    }

    public ConfirmScreen(String message, Runnable onYes, Screen back, boolean yesEnabled, String disabledTooltip) {
        super(Component.translatable("crac.gui.confirm"));
        this.message = message;
        this.onYes = onYes;
        this.back = back;
        this.yesEnabled = yesEnabled;
        this.disabledTooltip = disabledTooltip;
    }

    @Override
    protected void init() {
        this.left = (this.guiW() - 220) / 2;
        this.top = (this.guiH() - 104) / 2;
        this.btnY = this.top + 104 - 28;
        this.yesX = this.left + 18;
        this.noX = this.left + 220 - 18 - this.btnW;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        GroupTheme.panel(g, this.left, this.top, 220, 104);
        List<FormattedCharSequence> lines = this.font.split(Component.literal(this.message), 192);
        int ty = this.top + 24;

        for (FormattedCharSequence line : lines) {
            g.drawCenteredString(this.font, line, this.left + 110, ty, -3878166);
            ty += 11;
        }

        boolean hy = inRect(mouseX, mouseY, this.yesX, this.btnY, this.btnW, this.btnH);
        boolean hn = inRect(mouseX, mouseY, this.noX, this.btnY, this.btnW, this.btnH);
        if (this.yesEnabled) {
            GroupTheme.button(g, this.font, this.yesX, this.btnY, this.btnW, this.btnH, Component.translatable("crac.gui.yes").getString(), hy ? -10757772 : -12598694);
        } else {
            g.fill(this.yesX, this.btnY, this.yesX + this.btnW, this.btnY + this.btnH, -12960187);
            g.drawCenteredString(this.font, Component.translatable("crac.gui.yes"), this.yesX + this.btnW / 2, this.btnY + (this.btnH - 8) / 2, -8749177);
        }

        GroupTheme.button(g, this.font, this.noX, this.btnY, this.btnW, this.btnH, Component.translatable("crac.gui.no").getString(), hn ? -2074037 : -4179669);
        this.renderWidgets(g, mouseX, mouseY, pt);
        if (!this.yesEnabled && hy && this.disabledTooltip != null) {
            this.tooltip(Component.literal(this.disabledTooltip));
        }
    }

    @Override
    protected boolean clickContent(double mx, double my, int button) {
        if (inRect(mx, my, this.yesX, this.btnY, this.btnW, this.btnH)) {
            if (this.yesEnabled) {
                if (this.onYes != null) {
                    this.onYes.run();
                }

                this.minecraft.setScreen(this.back);
            }

            return true;
        } else if (inRect(mx, my, this.noX, this.btnY, this.btnW, this.btnH)) {
            this.minecraft.setScreen(this.back);
            return true;
        } else {
            return this.clickWidgets(mx, my, button);
        }
    }

    /** Escape answers "No": back to the screen that asked, never past it. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

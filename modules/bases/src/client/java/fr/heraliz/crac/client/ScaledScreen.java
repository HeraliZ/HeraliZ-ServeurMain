package fr.heraliz.crac.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class ScaledScreen extends Screen {
    public static final double TARGET_SCALE = 2.0;
    private List<Component> tooltipLines;

    protected ScaledScreen(Component title) {
        super(title);
    }

    public static double scaleFactor() {
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        return gs <= 0.0 ? 1.0 : 2.0 / gs;
    }

    protected int guiW() {
        return (int)Math.round(this.width / scaleFactor());
    }

    protected int guiH() {
        return (int)Math.round(this.height / scaleFactor());
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        if (this.minecraft != null && this.minecraft.level != null) {
            g.fillGradient(0, 0, this.guiW() + 1, this.guiH() + 1, -1072689136, -804253680);
        } else {
            super.renderBackground(g);
        }
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        double f = scaleFactor();
        this.tooltipLines = null;
        g.pose().pushPose();
        g.pose().scale((float)f, (float)f, 1.0F);
        this.renderContent(g, (int)(mouseX / f), (int)(mouseY / f), pt);
        g.pose().popPose();
        if (this.tooltipLines != null) {
            g.renderComponentTooltip(this.font, this.tooltipLines, mouseX, mouseY);
        }
    }

    protected void tooltip(List<Component> lines) {
        this.tooltipLines = lines;
    }

    protected void tooltip(Component line) {
        this.tooltipLines = List.of(line);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        double f = scaleFactor();
        super.mouseMoved(mx / f, my / f);
    }

    protected abstract void renderContent(GuiGraphics var1, int var2, int var3, float var4);

    protected void renderWidgets(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
    }

    protected void scissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        double f = scaleFactor();
        g.enableScissor((int)Math.floor(x1 * f), (int)Math.floor(y1 * f), (int)Math.ceil(x2 * f), (int)Math.ceil(y2 * f));
    }

    @Override
    public final boolean mouseClicked(double mx, double my, int button) {
        double f = scaleFactor();
        return this.clickContent(mx / f, my / f, button);
    }

    @Override
    public final boolean mouseReleased(double mx, double my, int button) {
        double f = scaleFactor();
        return this.releaseContent(mx / f, my / f, button);
    }

    @Override
    public final boolean mouseScrolled(double mx, double my, double delta) {
        double f = scaleFactor();
        return this.scrollContent(mx / f, my / f, delta);
    }

    @Override
    public final boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        double f = scaleFactor();
        return this.dragContent(mx / f, my / f, button, dx / f, dy / f);
    }

    protected boolean clickContent(double mx, double my, int button) {
        return this.clickWidgets(mx, my, button);
    }

    protected boolean releaseContent(double mx, double my, int button) {
        return this.releaseWidgets(mx, my, button);
    }

    protected boolean scrollContent(double mx, double my, double delta) {
        return false;
    }

    protected boolean dragContent(double mx, double my, int button, double dx, double dy) {
        return this.dragWidgets(mx, my, button, dx, dy);
    }

    protected boolean clickWidgets(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
    }

    protected boolean releaseWidgets(double mx, double my, int button) {
        return super.mouseReleased(mx, my, button);
    }

    protected boolean dragWidgets(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx, my, button, dx, dy);
    }
}

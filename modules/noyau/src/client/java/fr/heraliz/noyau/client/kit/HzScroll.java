package fr.heraliz.noyau.client.kit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Conteneur defilant simple : les enfants sont places en coordonnees de contenu (y a partir du haut du conteneur,
 * sans tenir compte du defilement) ; le conteneur les decale et les coupe (scissor). Barre de 2 px a droite quand le
 * contenu depasse. Molette = 12 px. Pour des listes dessinees a la main, surcharger {@link #renderContent}.
 */
public class HzScroll extends AbstractWidget implements ContainerEventHandler {
    private final List<AbstractWidget> enfants = new ArrayList<>();
    private int contentHeight;
    private int scroll;
    private int accent = 0xFFFFFF;
    @Nullable
    private GuiEventListener focused;
    private boolean dragging;

    public HzScroll(int x, int y, int w, int h) {
        super(x, y, w, h, Component.empty());
    }

    public HzScroll accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    /** Ajoute un enfant (coordonnees de contenu : y = getY() en haut du conteneur, sans defilement). */
    public <T extends AbstractWidget> T add(T w) {
        enfants.add(w);
        contentHeight = Math.max(contentHeight, w.getY() - getY() + w.getHeight());
        return w;
    }

    public void clear() {
        enfants.clear();
        contentHeight = 0;
        scroll = 0;
        focused = null;
    }

    public void setContentHeight(int h) {
        contentHeight = h;
        borner();
    }

    public int getContentHeight() {
        return contentHeight;
    }

    public int getScroll() {
        return scroll;
    }

    public void setScroll(int s) {
        scroll = s;
        borner();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - getHeight());
    }

    private void borner() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    public boolean dansZone(double mx, double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    /** Contenu : par defaut, les enfants. mouseY est deja en coordonnees de contenu. */
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (AbstractWidget w : enfants) {
            if (w.getY() + w.getHeight() < getY() + scroll || w.getY() > getY() + scroll + getHeight()) continue;
            w.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        borner();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.enableScissor(x, y, x + w, y + h);
        g.pose().pushPose();
        g.pose().translate(0, -scroll, 0);
        boolean inside = dansZone(mouseX, mouseY);
        renderContent(g, inside ? mouseX : -10000, inside ? mouseY + scroll : -10000, partialTick);
        g.pose().popPose();
        g.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int thumb = Math.max(12, h * h / contentHeight), ty = y + (h - thumb) * scroll / max;
            g.fill(x + w - 2, y, x + w, y + h, Ui.argb(0xFFFFFF, 14));
            g.fill(x + w - 2, ty, x + w, ty + thumb, Ui.argb(inside ? accent : 0xFFFFFF, inside ? 160 : 70));
        }
    }

    // ------------------------------------------------------------------ evenements (souris en coordonnees de contenu)
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!dansZone(mx, my)) return false;
        scroll -= (int) (delta * 12);
        borner();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!dansZone(mx, my)) {
            setFocused((GuiEventListener) null);
            return false;
        }
        double cy = my + scroll;
        for (AbstractWidget w : enfants) {
            if (w.mouseClicked(mx, cy, button)) {
                setFocused(w);
                if (button == 0) dragging = true;
                return true;
            }
        }
        setFocused((GuiEventListener) null);
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        boolean any = false;
        for (AbstractWidget w : enfants) any |= w.mouseReleased(mx, my + scroll, button);
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return focused != null && focused.mouseDragged(mx, my + scroll, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return focused != null && focused.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return focused != null && focused.charTyped(c, mods);
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return dansZone(mx, my);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return enfants;
    }

    @Override
    public boolean isDragging() {
        return dragging;
    }

    @Override
    public void setDragging(boolean d) {
        dragging = d;
    }

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        return focused;
    }

    @Override
    public void setFocused(@Nullable GuiEventListener f) {
        if (focused != null && focused != f) focused.setFocused(false);
        focused = f;
        if (f != null) f.setFocused(true);
    }

    @Override
    public void setFocused(boolean b) {
        super.setFocused(b);
        if (!b) setFocused((GuiEventListener) null);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
    }
}

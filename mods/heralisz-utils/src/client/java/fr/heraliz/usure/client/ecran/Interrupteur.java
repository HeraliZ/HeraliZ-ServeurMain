package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.client.kit.SonsUi;
import fr.heraliz.usure.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Interrupteur oui / non (style des ecrans de clan) : pastille qui glisse, « OUI / NON » en petites capitales. */
public class Interrupteur extends AbstractWidget {
    private final BooleanSupplier valeur;
    private final Consumer<Boolean> change;
    private boolean etiquette = true;

    public Interrupteur(int x, int y, int w, int h, BooleanSupplier valeur, Consumer<Boolean> change) {
        super(x, y, w, h, Component.empty());
        this.valeur = valeur;
        this.change = change;
    }

    /** Sans le mot « oui / non » a gauche (colonnes etroites). */
    public Interrupteur sansEtiquette() {
        etiquette = false;
        return this;
    }

    @Override
    public void onClick(double mx, double my) {
        change.accept(!valeur.getAsBoolean());
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        SonsUi.clic(0.6F);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean on = valeur.getAsBoolean();
        boolean hover = active && isHovered;
        int accent = Ui.accent();
        int sw = 22, sh = 10;
        int sx = getX() + getWidth() - sw, sy = getY() + (getHeight() - sh) / 2;
        int alpha = active ? 255 : 90;
        Ui.soft(g, sx, sy, sw, sh, on ? Ui.argb(accent, (hover ? 200 : 160) * alpha / 255) : Ui.argb(0xFFFFFF, hover ? 30 : 18));
        Ui.softOutline(g, sx + 1, sy + 1, sw - 2, sh - 2, on ? Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.3f), 200 * alpha / 255) : Ui.argb(0xFFFFFF, 50));
        int kx = on ? sx + sw - 8 : sx + 2;
        Ui.soft(g, kx, sy + 2, 6, 6, on ? Ui.argb(0xFFFFFF, alpha) : Ui.argb(Ui.MUTED, alpha));
        if (etiquette) {
            String mot = Component.translatable(on ? "heraliz_usure.oui" : "heraliz_usure.non").getString();
            Ui.spacedRight(g, Minecraft.getInstance().font, mot, Ui.SMALL, sx - 6, sy + 2,
                    on ? Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.4f), alpha) : Ui.argb(Ui.FAINT, 255), 0.8f);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}

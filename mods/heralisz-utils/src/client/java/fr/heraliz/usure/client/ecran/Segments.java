package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.client.kit.SonsUi;
import fr.heraliz.usure.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Choix en segments cote a cote (le choisi teinte a l'accent). */
public class Segments extends AbstractWidget {
    private final String[] libelles;
    private final IntSupplier choisi;
    private final IntConsumer change;

    public Segments(int x, int y, int w, int h, String[] libelles, IntSupplier choisi, IntConsumer change) {
        super(x, y, w, h, Component.empty());
        this.libelles = libelles;
        this.choisi = choisi;
        this.change = change;
    }

    @Override
    public void onClick(double mx, double my) {
        int n = libelles.length;
        change.accept(Math.max(0, Math.min(n - 1, (int) ((mx - getX()) * n / getWidth()))));
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        SonsUi.clic(0.6F);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int accent = Ui.accent();
        int n = libelles.length, x = getX(), y = getY(), w = getWidth(), h = getHeight();
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < n; i++) {
            int sx = x + w * i / n, ex = x + w * (i + 1) / n;
            boolean over = isHovered && mouseX >= sx && mouseX < ex;
            boolean on = i == choisi.getAsInt();
            Ui.soft(g, sx, y, ex - sx - 2, h, on ? Ui.argb(accent, over ? 90 : 60) : Ui.argb(0xFFFFFF, over ? 20 : 8));
            if (on) Ui.softOutline(g, sx + 1, y + 1, ex - sx - 4, h - 2, Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.25f), 190));
            String t = Ui.ellipsis(font, libelles[i].toUpperCase(Locale.ROOT), Ui.SMALL, ex - sx - 8);
            Ui.spacedCentered(g, font, t, Ui.SMALL, sx + (ex - sx - 2) / 2f, y + (h - 6) / 2f,
                    on ? 0xFFFFFFFF : Ui.argb(over ? Ui.TEXT : Ui.MUTED, 255), 0.6f);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}

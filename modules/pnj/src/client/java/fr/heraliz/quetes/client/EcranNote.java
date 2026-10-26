package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** Une note, une lettre, un indice trouvé sur un point de quête : un panneau au milieu de l'écran, à lire. */
public final class EcranNote extends Screen {
    /** Titre à sa vraie taille (police 14) : jamais de texte agrandi, flou (25/10, audit des écrans). */
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_quetes", "ui_titre");
    private final String titre, texte;
    private int px, py, pw, ph;
    private List<FormattedCharSequence> lignes = List.of();
    /** Défilement du texte (molette) quand la note est plus longue que l'écran. */
    private int defile;

    private EcranNote(Reseau.Note n) {
        super(Component.translatable("gui.heraliz_quetes.note"));
        titre = TexteClient.str(n.titre());
        texte = TexteClient.str(n.texte());
    }

    static void ouvrir(Reseau.Note n) {
        SonsUi.ouvrir(0.5F);
        Minecraft.getInstance().setScreen(new EcranNote(n));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(340, width - 40);
        lignes = font.split(Ui.lit(texte, Ui.FONT), pw - 36);
        ph = Math.min(height - 30, 52 + lignes.size() * 11 + 22);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        onClose();
        return true;
    }

    /** Zone du texte : il défile entre ces deux bords. */
    private int haut() {
        return py + 38;
    }

    private int bas() {
        return py + ph - 22;
    }

    private int maxDefile() {
        return Math.max(0, lignes.size() * 11 + 6 - (bas() - haut()));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        defile = Mth.clamp((int) (defile - delta * 22), 0, maxDefile());
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph, 225);
        g.fill(px + 1, py + 4, px + 3, py + ph - 4, Ui.argb(Ui.OR, 210));
        g.drawString(f, Ui.lit(Ui.ellipsis(f, titre, TITRE, pw - 36), TITRE), px + 18, py + 12, Ui.argb(Ui.TEXT, 255), false);
        Ui.hairline(g, px + 18, px + pw - 18, py + 32, 0xFFFFFF, 40, false, true);
        // texte coupé à sa zone, qui défile à la molette s'il est trop long
        int max = maxDefile(), h = haut(), b = bas();
        defile = Mth.clamp(defile, 0, max);
        g.enableScissor(px + 2, h, px + pw - 2, b);
        int y = h + 4 - defile;
        for (FormattedCharSequence l : lignes) {
            if (y + 11 > h && y < b) g.drawString(f, l, px + 18, y, Ui.argb(Ui.mix(Ui.TEXT, 0xE8DCC0, 0.4f), 255), false);
            y += 11;
        }
        g.disableScissor();
        if (max > 0) {
            int zone = b - h, barre = Math.max(16, zone * zone / (zone + max)), by = h + (zone - barre) * defile / max;
            g.fill(px + pw - 7, by, px + pw - 5, by + barre, Ui.argb(0xFFFFFF, 60));
        }
        Ui.spacedCentered(g, f, TexteClient.tr("gui.heraliz_quetes.note.fermer"), Ui.SMALL, px + pw / 2f, py + ph - 14, Ui.argb(Ui.FAINT, 255), 0.8f);
        super.render(g, mx, my, pt);
    }
}

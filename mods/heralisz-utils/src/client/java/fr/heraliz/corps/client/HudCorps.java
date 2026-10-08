package fr.heraliz.corps.client;

import fr.heraliz.corps.CorpsConfig;
import fr.heraliz.corps.client.kit.Ui;
import fr.heraliz.corps.entite.CorpsEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/**
 * Petit panneau sous le viseur quand on regarde un corps : « Corps de X », la cause (« Tué par Y »), le minuteur de
 * disparition (ou de protection), et un losange a la couleur du clan si le mort en etait. Les textes ne sont refaits que
 * quand la cible ou la seconde affichee change.
 */
final class HudCorps {
    private HudCorps() {
    }

    private static int cibleId = Integer.MIN_VALUE;
    private static long seconde = Long.MIN_VALUE;
    private static Component titre = Component.empty(), cause = Component.empty(), minuteur = Component.empty();
    private static int largeur, couleurMinuteur;
    private static boolean avecCause;

    static void rendre(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!CorpsConfig.hud || mc.options.hideGui || mc.screen != null || mc.level == null
                || !(mc.hitResult instanceof EntityHitResult r) || !(r.getEntity() instanceof CorpsEntity c) || c.isRemoved()) {
            cibleId = Integer.MIN_VALUE;
            return;
        }
        long maintenant = mc.level.getGameTime();
        if (maintenant < c.visibleDes()) return;
        Font font = mc.font;
        boolean protege = c.protegeJusqua() > maintenant;
        long reste = Math.max(0, (protege ? c.protegeJusqua() : c.expire()) - maintenant) / 20;
        if (c.getId() != cibleId || reste != seconde) {
            cibleId = c.getId();
            seconde = reste;
            titre = Ui.lit(Component.translatable("gui.heraliz_corps.titre", c.nom()).getString().toUpperCase(Locale.ROOT), Ui.BOLD);
            avecCause = !c.cause().getString().isEmpty();
            cause = Ui.lit(Ui.ellipsis(font, c.cause().getString(), Ui.FONT, 300), Ui.FONT);
            String temps = String.format(Locale.ROOT, "%d:%02d", reste / 60, reste % 60);
            String cle = protege ? "hud.heraliz_corps.protege" : c.vide() ? "hud.heraliz_corps.vide" : "hud.heraliz_corps.disparait";
            minuteur = Ui.tr(Ui.SMALL, cle, temps);
            couleurMinuteur = protege ? Ui.AMBRE : Ui.MUTED;
            largeur = Math.max(font.width(titre) + (c.clanAmi ? 10 : 0), Math.max(avecCause ? font.width(cause) : 0, font.width(minuteur)));
        }
        int pw = largeur + 16, ph = avecCause ? 42 : 31;
        int x = w / 2 - pw / 2, y = h / 2 + 14;
        Ui.glass(g, x, y, pw, ph, 190);
        int tx = x + 8, ty = y + 7;
        if (c.clanAmi) {
            int a = Ui.accent();
            g.fill(tx + 2, ty + 1, tx + 4, ty + 7, Ui.argb(a, 255));
            g.fill(tx + 1, ty + 2, tx + 5, ty + 6, Ui.argb(a, 255));
            g.fill(tx, ty + 3, tx + 6, ty + 5, Ui.argb(a, 255));
            tx += 10;
        }
        g.drawString(font, titre, tx, ty, Ui.argb(Ui.TEXT, 255), false);
        ty += 12;
        if (avecCause) {
            g.drawString(font, cause, x + 8, ty, Ui.argb(Ui.TEXT, 200), false);
            ty += 11;
        }
        g.drawString(font, minuteur, x + 8, ty + 1, Ui.argb(couleurMinuteur, 255), false);
    }
}

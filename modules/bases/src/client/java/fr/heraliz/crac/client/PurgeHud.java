package fr.heraliz.crac.client;

import static fr.heraliz.crac.client.kit.Dessin.*;

import fr.heraliz.crac.network.S2CPurge;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Petite icône de la Purge en haut à gauche (25/10, choix du propriétaire : « très simple, ultra clean, qu'on la voie
 * sans qu'elle gêne », à la place de la barre rouge de Minecraft) : une pastille de verre avec un point rouge qui
 * respire, « PURGE » et le temps restant (« 1 h 05 », puis « 42:10 » la dernière heure), et un trait fin du temps
 * écoulé. Rien hors Purge, rien avec F1. Les autres panneaux du coin (zone des clans, annonces d'extraction) se posent
 * dessous : ils lisent {@link #hauteur()} par réflexion (à ne jamais renommer).
 */
public final class PurgeHud {
    private PurgeHud() {
    }

    private static final int X = 6, Y = 6, H = 12, ROUGE_PURGE = 0xFF3B30;
    /** Fin de la Purge (horloge du joueur, ms) et durée totale ; 0 = pas de Purge. */
    private static long fin, total;

    public static void recevoir(S2CPurge m) {
        fin = m.resteMs() <= 0 ? 0 : System.currentTimeMillis() + m.resteMs();
        total = m.totalMs();
    }

    public static void vider() {
        fin = 0;
        total = 0;
    }

    private static boolean visible() {
        return fin > System.currentTimeMillis() && !Minecraft.getInstance().options.hideGui;
    }

    /** Hauteur occupée en haut à gauche (icône + écart), 0 hors Purge : les autres panneaux du coin se posent dessous. */
    public static int hauteur() {
        return visible() ? H + 4 : 0;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int largeur, int hauteur) {
        if (!visible()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        long now = System.currentTimeMillis(), reste = fin - now;
        String titre = Component.translatableWithFallback("crac.purge.hud", "Purge").getString();
        String temps = temps(reste);
        float wTitre = largeurCaps(font, titre, MINI_GRAS, 0.9F), wTemps = largeur(font, temps, MINI_GRAS);
        float w = 12 + wTitre + 6 + 1 + 5 + wTemps + 6;
        verre(g, X, Y, w, H, 1 / 3F, 200);
        // point rouge qui respire (une respiration toutes les 2 s)
        float souffle = 0.5F + 0.5F * (float) Math.sin(now / 2000.0 * 2 * Math.PI);
        halo(g, X + 6, Y + H / 2F, 4.5F, 4.5F, a(ROUGE_PURGE, 60 + 70 * souffle));
        disque(g, X + 6, Y + H / 2F, 2.2F, a(ROUGE_PURGE, 255));
        float tx = X + 12;
        caps(g, font, titre, MINI_GRAS, tx, Y + 3, 0.9F, a(ROUGE_PURGE, 255));
        tx += wTitre + 6;
        rect(g, tx, Y + 3, tx + 1 / 3F, Y + H - 3, a(BLANC, 40));
        tx += 5;
        texte(g, font, lit(temps, MINI_GRAS), tx, Y + 3, a(TEXTE, 255));
        // temps écoulé : trait fin en bas de la pastille
        if (total > 0) {
            float part = Math.max(0, Math.min(1, 1 - reste / (float) total));
            rect(g, X + 1 / 3F, Y + H - 2 / 3F, X + 1 / 3F + (w - 2 / 3F) * part, Y + H - 1 / 3F, a(ROUGE_PURGE, 200));
        }
    }

    /** « 1 h 05 » au-delà d'une heure, sinon « 42:10 ». */
    private static String temps(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) {
            return String.format(Locale.ROOT, "%d h %02d", s / 3600, s % 3600 / 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}

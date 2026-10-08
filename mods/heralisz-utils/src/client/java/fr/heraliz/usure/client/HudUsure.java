package fr.heraliz.usure.client;

import fr.heraliz.usure.ClientConfig;
import fr.heraliz.usure.Reparation;
import fr.heraliz.usure.Usure;
import fr.heraliz.usure.client.kit.Ui;
import fr.heraliz.usure.regles.Etat;
import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Petit affichage de l'etat de l'arme en main, en bas a droite, juste SOUS le compteur de munitions de TaCZ
 * (munitions : de h-44 a h-25 ; l'indicateur « EN COMBAT » de HeraliZ Matchs est au-dessus, de h-69 a h-50) : pastille
 * de verre comme « EN COMBAT », point de couleur, etat en petites capitales espacees, pourcentage, jauge fine (avec la
 * part d'etat perdue aux reparations). Arme ruinee : pastille rouge « RUINEE », toujours affichee, qui clignote au clic
 * a vide ; l'etat seul suit le reglage du joueur et du serveur. Textes mis en cache : une image n'alloue rien de plus que
 * le rendu vanilla des glyphes.
 */
public final class HudUsure {
    private HudUsure() {
    }

    static final int LARGEUR = 100, HAUTEUR = 17;

    private static final TexteEspace TITRE = new TexteEspace(Ui.SMALL, 1.0f);
    private static String langue;
    private static final String[] NOMS_ETATS = new String[Etat.TOUS.length];
    private static int pctMontre = -1;
    private static MutableComponent pctTexte = Component.empty();
    private static int pctLargeur;

    static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator()) return;
        ItemStack arme = p.getMainHandItem();
        Profil pr = ReglesClient.suivie(p, arme);
        if (pr == null) return;
        Regles r = ReglesClient.get();
        int etat = Usure.etat(arme, pr);
        Etat st = Etat.de(etat, r);
        if (st != Etat.RUINE) {
            ClientConfig.ModeHud mode = ClientConfig.HUD.get();
            if (!r.hudAutorise || mode == ClientConfig.ModeHud.JAMAIS) return;
            if (mode == ClientConfig.ModeHud.SI_USEE && st == Etat.NEUF) return;
        }
        textes(mc);
        Font font = mc.font;
        int x = w - 8 - LARGEUR, y = h - 23;
        int couleur = st.couleur;
        Ui.glass(g, x, y, LARGEUR, HAUTEUR, 175);
        // flash rouge au clic a vide d'une arme ruinee
        long depuisClic = ClientTirs.depuisClic();
        if (st == Etat.RUINE && depuisClic < 600) {
            float f = 1f - depuisClic / 600f;
            Ui.softOutline(g, x + 1, y + 1, LARGEUR - 2, HAUTEUR - 2, Ui.argb(Ui.DANGER, (int) (200 * f)));
        }
        Ui.soft(g, x + 7, y + 5, 5, 5, Ui.argb(couleur, 255));
        TITRE.texte(NOMS_ETATS[st.ordinal()]).dessiner(g, x + 17, y + 4.5f, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.45f), 255));
        if (etat != pctMontre) {
            pctMontre = etat;
            pctTexte = Ui.lit(Reparation.pct(etat), Ui.BOLD);
            pctLargeur = font.width(pctTexte);
        }
        if (17 + TITRE.largeur() + 6 + pctLargeur + 7 <= LARGEUR) {
            g.drawString(font, pctTexte, x + LARGEUR - 7 - pctLargeur, y + 4, Ui.argb(Ui.TEXT, 255), false);
        }
        // jauge fine : etat, et la part perdue aux reparations en rouge sombre
        int bx = x + 6, bw = LARGEUR - 12, by = y + HAUTEUR - 4;
        Ui.bar(g, bx, by, bw, 1, etat / (float) Usure.PLEIN, couleur);
        int max = Usure.max(arme);
        if (max < Usure.PLEIN) {
            int mx = bx + Math.round(bw * max / (float) Usure.PLEIN);
            g.fill(mx, by, bx + bw, by + 1, Ui.argb(Ui.DANGER, 90));
        }
    }

    /** Noms traduits, relus seulement si la langue change. */
    private static void textes(Minecraft mc) {
        String l = mc.getLanguageManager().getSelected();
        if (l.equals(langue)) return;
        langue = l;
        for (Etat e : Etat.TOUS) NOMS_ETATS[e.ordinal()] = I18n.get(e.cleTraduction());
    }
}

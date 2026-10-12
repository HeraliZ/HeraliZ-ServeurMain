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
 * Petit indicateur de l'etat de l'arme EN MAIN, en bas a droite de l'ecran (plus rien n'est dessine sur l'icone de
 * l'arme) : pastille de verre leger de 12 px de haut, point de la couleur de l'etat (vert neuf, vert-jaune bon, jaune
 * use, orange abime, rouge ruinee : {@link Etat#couleur}), etat en petites capitales espacees, pourcentage, jauge fine
 * dessous (avec, en rouge sombre, la part d'etat perdue aux reparations). Textes a 85 % de la petite police.
 * <p>Place, pour ne rien recouvrir : bord droit a 8 px (comme l'indicateur « en combat » des matchs), bas de la pastille
 * a h-6, donc SOUS le HUD de TaCZ (munitions, mode de tir, icone de l'arme : de h-44 a h-25) et loin de l'indicateur
 * « en combat » (au-dessus de TaCZ, de h-69 a h-50) ; largeur bornee pour ne jamais toucher la barre d'objets DayZ
 * (centree en bas, case de seconde main et jauge comprises : jusqu'a w/2+{@value #BARRE}) : si la place manque (ecran
 * etroit, nom d'etat long en russe), le nom de l'etat est omis. Le micro de CaraCrea reste seul en bas a gauche. En
 * match et en zombies, les armes pretees ne s'usent pas : rien ne s'affiche.
 * <p>Arme ruinee : toujours affichee (alerte), pastille teintee de rouge, contour rouge qui clignote au clic a vide ; les
 * autres etats suivent le reglage du joueur ({@link ClientConfig#HUD}) et du serveur ({@code hudAutorise}). Masque avec
 * F1, F3 et en spectateur. Textes et largeurs en cache : une image n'alloue rien de plus que le rendu vanilla des glyphes.
 */
public final class HudUsure {
    private HudUsure() {
    }

    /** Hauteur de la pastille, marge interieure, ecart au bord droit, ecart entre le bas de la pastille et le bas de l'ecran. */
    static final int HAUTEUR = 12, PAD = 4, DROITE = 8, BAS = 6;
    /** Point d'etat (cote) et ecarts point / nom / pourcentage. */
    private static final int POINT = 3, ECART_POINT = 3, ECART_PCT = 4;
    /** Taille des textes (petite police a 85 %). */
    private static final float TEXTE = 0.85f;
    /**
     * Demi-largeur gardee libre au milieu du bas de l'ecran : barre d'objets DayZ (9 cases de 20 px et leurs ecarts, case
     * de seconde main a 10 px, jauge d'attaque, inclinaison), avec une marge.
     */
    private static final int BARRE = 134;
    /** Opacite du verre (plus leger que les ecrans : 200). */
    private static final int VERRE = 150;

    private static final TexteEspace TITRE = new TexteEspace(Ui.SMALL, 0.6f);
    private static String langue;
    private static final String[] NOMS_ETATS = new String[Etat.TOUS.length];
    private static int pctMontre = -1;
    private static MutableComponent pctTexte = Component.empty();
    private static int pctLargeur;

    static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || mc.options.renderDebug || p.isSpectator()) return;
        ItemStack arme = p.getMainHandItem();
        Profil pr = ReglesClient.suivie(p, arme);
        if (pr == null) return;
        Regles r = ReglesClient.get();
        int etat = Usure.etat(arme, pr);
        Etat st = Etat.de(etat, r);
        boolean ruinee = st == Etat.RUINE;
        if (!ruinee) {
            ClientConfig.ModeHud mode = ClientConfig.HUD.get();
            if (!r.hudAutorise || mode == ClientConfig.ModeHud.JAMAIS) return;
            if (mode == ClientConfig.ModeHud.SI_USEE && st == Etat.NEUF) return;
        }
        textes(mc);
        Font font = mc.font;
        if (etat != pctMontre) {
            pctMontre = etat;
            pctTexte = Ui.lit(Reparation.pct(etat), Ui.SMALL);
            pctLargeur = (int) Math.ceil(font.width(pctTexte) * TEXTE);
        }
        TITRE.texte(NOMS_ETATS[st.ordinal()]);

        // largeur : point, nom de l'etat, pourcentage ; sans le nom si la pastille toucherait la barre d'objets
        int place = w - DROITE - (w / 2 + BARRE);
        int sansNom = PAD + POINT + ECART_POINT + pctLargeur + PAD;
        int avecNom = sansNom + (int) Math.ceil(TITRE.largeur() * TEXTE) + ECART_PCT;
        boolean nom = avecNom <= place;
        int lw = nom ? avecNom : sansNom;
        int x = w - DROITE - lw, y = h - BAS - HAUTEUR;
        int couleur = st.couleur;

        Ui.glass(g, x, y, lw, HAUTEUR, VERRE);
        if (ruinee) {
            Ui.soft(g, x + 1, y + 1, lw - 2, HAUTEUR - 2, Ui.argb(Ui.DANGER, 28));
            // flash rouge au clic a vide d'une arme ruinee
            long depuisClic = ClientTirs.depuisClic();
            if (depuisClic < 600) {
                float f = 1f - depuisClic / 600f;
                Ui.softOutline(g, x + 1, y + 1, lw - 2, HAUTEUR - 2, Ui.argb(Ui.DANGER, (int) (200 * f)));
            }
        }
        Ui.soft(g, x + PAD, y + 4, POINT, POINT, Ui.argb(couleur, 255));
        // textes a 85 % : nom de l'etat apres le point, pourcentage cale a droite
        int couleurPct = ruinee ? Ui.mix(Ui.DANGER, 0xFFFFFF, 0.3f) : Ui.TEXT;
        g.pose().pushPose();
        g.pose().translate(x, y + 2, 0);
        g.pose().scale(TEXTE, TEXTE, 1);
        if (nom) TITRE.dessiner(g, (PAD + POINT + ECART_POINT) / TEXTE, 0, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.45f), 255));
        g.drawString(font, pctTexte, Math.round((lw - PAD - pctLargeur) / TEXTE), 0, Ui.argb(couleurPct, 255), false);
        g.pose().popPose();

        // jauge fine : etat, et la part perdue aux reparations en rouge sombre
        int bx = x + PAD, bw = lw - 2 * PAD, by = y + HAUTEUR - 3;
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

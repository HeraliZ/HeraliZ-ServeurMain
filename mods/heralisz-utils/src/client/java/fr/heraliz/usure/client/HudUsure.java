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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Indicateur de l'etat de l'arme EN MAIN, en bas a droite de l'ecran (maquette C « anneau », choisie par le
 * proprietaire) : pastille de verre de 16 px de haut ; a gauche un petit anneau de la couleur de l'etat (vert neuf, vert-jaune bon,
 * jaune use, orange abime, rouge ruinee : {@link Etat#couleur}) qui se vide dans le sens horaire, la part perdue aux
 * reparations en rouge au bout du tour ; a droite le pourcentage en gras et, dessous, l'etat en petites capitales
 * espacees. Police DM Sans aux tailles de la maquette (polices {@code hud_pct} et {@code hud_etat}), formes dessinees
 * en maillages adoucis ({@link FormesHud}) : net a toute echelle d'interface.
 * <p>Place, pour ne rien recouvrir : bord droit a 8 px (comme l'indicateur « en combat », combat.client.CombatClient), bas de la pastille
 * a h-6, donc SOUS le HUD de TaCZ (munitions, mode de tir, icone de l'arme : de h-44 a h-25) et loin de l'indicateur
 * « en combat » (au-dessus de TaCZ, de h-69 a h-50) ; largeur bornee pour ne jamais toucher la barre d'objets DayZ
 * (centree en bas, case de seconde main et jauge comprises : jusqu'a w/2+{@value #BARRE}) : si la place manque (ecran
 * etroit, nom d'etat long en russe), le nom de l'etat est omis et le pourcentage se centre. La largeur reservee au
 * pourcentage ne depend que du nombre de chiffres : la pastille ne bouge pas a chaque tir. Le micro de CaraCrea reste
 * seul en bas a gauche. En match et en zombies, les armes pretees ne s'usent pas : rien ne s'affiche.
 * <p>Arme ruinee : toujours affichee (alerte), anneau et contour rouges, contour qui s'avive au clic a vide ; les
 * autres etats suivent le reglage du joueur ({@link ClientConfig#HUD}) et du serveur ({@code hudAutorise}). Masque avec
 * F1, F3 et en spectateur. Textes, largeurs et maillages en cache : refaits seulement quand l'etat change.
 */
public final class HudUsure {
    private HudUsure() {
    }

    /** Hauteur de la pastille, ecart au bord droit, ecart entre le bas de la pastille et le bas de l'ecran. */
    static final int HAUTEUR = 16, DROITE = 8, BAS = 6;
    /** Centre de l'anneau dans la pastille (anneau de 12 px, coin haut-gauche a 3, 2). */
    private static final int ANNEAU_X = 9, ANNEAU_Y = 8;
    /** Bord gauche des textes, marge apres le plus long. */
    private static final int TEXTE_X = 19, MARGE = 5;
    /**
     * Haut des textes (ligne de base de la maquette : 8,04 pour le pourcentage, 14 pour l'etat ; le jeu pose la ligne de
     * base de ces polices a 7,14 et 4,16 sous le point de dessin) ; pourcentage seul : chiffres centres dans la pastille.
     */
    private static final float PCT_Y = 0.9f, NOM_Y = 9.84f, PCT_SEUL_Y = 3.73f;
    /** Espace entre les capitales de l'etat (0,6 px). */
    private static final float ESPACE_NOM = 0.6f;
    /**
     * Demi-largeur gardee libre au milieu du bas de l'ecran : barre d'objets DayZ (9 cases de 20 px et leurs ecarts, case
     * de seconde main a 10 px, jauge d'attaque, inclinaison), avec une marge.
     */
    private static final int BARRE = 134;
    /** Opacite du verre (plus leger que les ecrans : 200) et de son lisere haut (comme Ui.glass). */
    private static final int VERRE = 150, LISERE = 41;
    /** Duree de l'eclat du contour apres un clic a vide (ms). */
    private static final int ECLAT_MS = 600;

    /** DM Sans gras a 8,2 px (pourcentage) et 5,2 px (etat). */
    private static final ResourceLocation POLICE_PCT = new ResourceLocation("heraliz_usure", "hud_pct"),
            POLICE_ETAT = new ResourceLocation("heraliz_usure", "hud_etat");

    private static final TexteEspace NOM = new TexteEspace(POLICE_ETAT, ESPACE_NOM);
    private static String langue;
    private static final String[] NOMS_ETATS = new String[Etat.TOUS.length];
    private static int pctMontre = -1;
    private static MutableComponent pctTexte = Component.empty();
    private static float pctLargeur;

    private static final Maillage ANNEAU = new Maillage(), TROU = new Maillage(), CONTOUR = new Maillage(), BORD = new Maillage();
    private static int anneauEtat = -1, anneauMax = -1, anneauCouleur = -1;
    private static boolean anneauRuinee;
    private static float anneauF = -1, trouF = -1, contourF = -1;
    private static int contourW = -1, contourEclat = -1, verreW = -1;
    private static float verreF = -1;

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
            String pct = Reparation.pct(etat);
            pctTexte = Ui.lit(pct, POLICE_PCT);
            // place reservee : chaque chiffre compte comme le plus large (0), la pastille ne bouge pas a chaque tir
            // (sauf 100 %, seul nombre a trois chiffres)
            String gabarit = etat >= Usure.PLEIN ? pct : pct.replaceAll("[0-9]", "0");
            pctLargeur = font.getSplitter().stringWidth(Ui.lit(gabarit, POLICE_PCT));
        }
        NOM.texte(NOMS_ETATS[st.ordinal()]);

        // largeur : anneau, pourcentage et nom ; sans le nom si la pastille toucherait la barre d'objets
        int place = w - DROITE - (w / 2 + BARRE);
        int avecNom = Math.round(TEXTE_X + Math.max(pctLargeur, NOM.largeur()) + MARGE);
        boolean nom = avecNom <= place;
        int lw = nom ? avecNom : Math.round(TEXTE_X + pctLargeur + MARGE);
        int x = w - DROITE - lw, y = h - BAS - HAUTEUR;
        int couleur = st.couleur;
        float f = (float) (1 / Math.max(1, mc.getWindow().getGuiScale()));

        // verre des ecrans de clan : dedans, bord d'un pixel aux coins arrondis, lisere clair en haut
        if (lw != verreW || f != verreF) {
            verreW = lw;
            verreF = f;
            FormesHud.bordVerre(BORD, lw, HAUTEUR, f, VERRE);
        }
        g.fill(x + 1, y + 1, x + lw - 1, y + HAUTEUR - 1, Ui.argb(Ui.VERRE, VERRE));
        BORD.dessiner(g, x, y);
        g.fill(x + 2, y + 1, x + lw - 2, y + 2, Ui.argb(0xFFFFFF, LISERE));
        if (ruinee) {
            // contour rouge (alerte), avive au clic a vide
            long depuisClic = ClientTirs.depuisClic();
            int eclat = depuisClic < ECLAT_MS ? Math.round(255 * (1f - depuisClic / (float) ECLAT_MS)) : 0;
            if (lw != contourW || f != contourF || eclat != contourEclat) {
                contourW = lw;
                contourF = f;
                contourEclat = eclat;
                FormesHud.contour(CONTOUR, lw, HAUTEUR, f, eclat / 255f);
            }
            CONTOUR.dessiner(g, x, y);
        }

        // anneau : refait seulement si l'etat, l'etat maximal, la couleur ou l'echelle de l'interface change
        int max = Usure.max(arme);
        if (etat != anneauEtat || max != anneauMax || couleur != anneauCouleur || ruinee != anneauRuinee || f != anneauF) {
            anneauEtat = etat;
            anneauMax = max;
            anneauCouleur = couleur;
            anneauRuinee = ruinee;
            anneauF = f;
            FormesHud.anneau(ANNEAU, etat / (float) Usure.PLEIN, max / (float) Usure.PLEIN, couleur, ruinee, f);
        }
        if (f != trouF) {
            trouF = f;
            FormesHud.trou(TROU, f);
        }
        ANNEAU.dessiner(g, x + ANNEAU_X, y + ANNEAU_Y);
        TROU.dessiner(g, x + ANNEAU_X, y + ANNEAU_Y);

        // pourcentage en gras, etat en petites capitales dessous (couleur de l'etat eclaircie)
        int couleurPct = ruinee ? Ui.mix(Ui.DANGER, 0xFFFFFF, 0.25f) : Ui.TEXT;
        g.pose().pushPose();
        g.pose().translate(x + TEXTE_X, y + (nom ? PCT_Y : PCT_SEUL_Y), 0);
        g.drawString(font, pctTexte, 0, 0, Ui.argb(couleurPct, 255), false);
        g.pose().popPose();
        if (nom) NOM.dessiner(g, x + TEXTE_X, y + NOM_Y, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.3f), 255));
    }

    /** Noms traduits, relus seulement si la langue change. */
    private static void textes(Minecraft mc) {
        String l = mc.getLanguageManager().getSelected();
        if (l.equals(langue)) return;
        langue = l;
        for (Etat e : Etat.TOUS) NOMS_ETATS[e.ordinal()] = I18n.get(e.cleTraduction());
    }
}

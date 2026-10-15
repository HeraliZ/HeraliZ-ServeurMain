package fr.heralisz.matchs.duels.killcam.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.killcam.Clip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/**
 * Bandeau de la killcam (calque de HUD {@code heralisz_matchs:killcam}, au-dessus de tout ; les autres calques sont
 * masques pendant la lecture) : bandes cinema en degrade haut et bas, « ● KILLCAM » en haut a gauche, raison en
 * haut a droite (derniere elimination, essai), en bas « TUE PAR » + nom du tueur a sa couleur d'equipe, arme et
 * distance, touche pour passer, fine barre de progression ; pendant la fin vue par les yeux du tueur, « vue du tueur »
 * sous le titre et un petit viseur au centre ; voiles noirs des fondus d'entree, de sortie et de retour.
 * Tous les textes sont prepares au lancement ({@link #preparer}) : aucune allocation par image.
 */
final class KillcamHud {
    private KillcamHud() {
    }

    static final int ROUGE = 0xFF4F4F;
    /** Duree du voile de retour apres la killcam (ms). */
    static final int RETOUR_MS = 260;

    private static Espace titre, tuePar, raison, passer, cle, vueTueur;
    private static Component nom = Component.empty(), detail = Component.empty();
    private static int nomCouleur = Ui.TEXT;
    private static int nomLargeur;

    /** Texte en capitales espacees pre-calcule (un composant par caractere). */
    private static final class Espace {
        final Component[] car;
        final int[] l;
        final int largeur;
        final float espacement;

        Espace(Font font, String s, ResourceLocation police, float espacement) {
            String up = s.toUpperCase(Locale.ROOT);
            int n = up.codePointCount(0, up.length());
            car = new Component[n];
            l = new int[n];
            this.espacement = espacement;
            float w = 0;
            int k = 0;
            for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
                MutableComponent c = Ui.lit(new String(Character.toChars(up.codePointAt(i))), police);
                car[k] = c;
                l[k] = font.width(c);
                w += l[k] + espacement;
                k++;
            }
            largeur = Math.max(0, Math.round(w - espacement));
        }

        void dessiner(GuiGraphics g, Font font, float x, int y, int argb) {
            float cx = x;
            for (int k = 0; k < car.length; k++) {
                g.drawString(font, car[k], Math.round(cx), y, argb, false);
                cx += l[k] + espacement;
            }
        }
    }

    /** Prepare les textes d'un clip (fil client, une fois par killcam). */
    static void preparer(Clip c) {
        Font font = Minecraft.getInstance().font;
        titre = new Espace(font, Component.translatable("killcam.titre").getString(), Ui.BOLD, 1.6f);
        tuePar = new Espace(font, Component.translatable("killcam.tue_par").getString(), Ui.SMALL, 1.0f);
        String r = c.raison == Clip.RAISON_DERNIERE ? Component.translatable("killcam.derniere").getString()
                : c.raison == Clip.RAISON_ESSAI ? Component.translatable("killcam.essai").getString() : "";
        raison = r.isEmpty() ? null : new Espace(font, r, Ui.SMALL, 0.9f);
        passer = new Espace(font, Component.translatable("killcam.passer").getString(), Ui.SMALL, 0.9f);
        cle = new Espace(font, KillcamClient.CLE_PASSER.getTranslatedKeyMessage().getString(), Ui.SMALL, 0.6f);
        vueTueur = new Espace(font, Component.translatable("killcam.vue_tueur").getString(), Ui.SMALL, 0.9f);
        Clip.Participant t = c.participants.get(c.tueur);
        nom = Ui.lit(t.nom, Ui.BOLD);
        nomLargeur = font.width(nom);
        nomCouleur = Ui.mix(t.couleur == 0 ? Ui.TEXT : t.couleur, 0xFFFFFF, 0.25f);
        String arme;
        if (!c.armeNom.isEmpty()) arme = c.armeNom;
        else if (c.arme >= 0 && c.arme < c.objets.size()) {
            ItemStack s = c.objets.get(c.arme);
            arme = s.getHoverName().getString();
        } else arme = Component.translatable("killcam.mains_nues").getString();
        String dist = Component.translatable("killcam.distance", String.format(Locale.ROOT, "%.0f", c.distance)).getString();
        detail = Ui.lit(arme + "  ·  " + dist, Ui.FONT);
    }

    /** Calque de HUD (enregistre par KillcamClient). */
    static void dessiner(ForgeGui gui, GuiGraphics g, float partiel, int largeur, int hauteur) {
        Minecraft mc = Minecraft.getInstance();
        Lecteur l = KillcamClient.lecteur();
        long retour = KillcamClient.retourMs();
        if (l == null) {
            // voile de retour : la camera du joueur reapparait en douceur
            long dt = System.currentTimeMillis() - retour;
            if (retour > 0 && dt < RETOUR_MS) {
                float a = 1f - dt / (float) RETOUR_MS;
                g.fill(0, 0, largeur, hauteur, Ui.argb(0x000000, (int) (235 * a * a)));
            }
            return;
        }
        Font font = mc.font;
        float s = Echelle.voulue() / (float) mc.getWindow().getGuiScale();
        int W = Math.round(largeur / s), H = Math.round(hauteur / s);
        float a = l.opacite();
        g.pose().pushPose();
        g.pose().scale(s, s, 1);
        // bandes cinema (degrades, jamais unies)
        g.fillGradient(0, 0, W, 24, Ui.argb(0, (int) (215 * a)), Ui.argb(0, (int) (150 * a)));
        g.fillGradient(0, 24, W, 42, Ui.argb(0, (int) (150 * a)), Ui.argb(0, 0));
        g.fillGradient(0, H - 62, W, H - 44, Ui.argb(0, 0), Ui.argb(0, (int) (150 * a)));
        g.fillGradient(0, H - 44, W, H, Ui.argb(0, (int) (150 * a)), Ui.argb(0, (int) (225 * a)));
        int ta = (int) (255 * a);
        if (ta >= 8) {
            // ● KILLCAM
            float pouls = 0.55f + 0.45f * Mth.sin((float) (l.ecouleMs() / 1000.0 * Math.PI * 2 * 1.1));
            Ui.soft(g, 16, 10, 5, 5, Ui.argb(ROUGE, (int) (ta * pouls)));
            Ui.soft(g, 15, 9, 7, 7, Ui.argb(ROUGE, (int) (ta * pouls * 0.25f)));
            titre.dessiner(g, font, 27, 8, Ui.argb(Ui.TEXT, ta));
            Ui.hairline(g, 27 + titre.largeur + 10, 27 + titre.largeur + 70, 12, ROUGE, (int) (140 * a), false, true);
            // fin par les yeux du tueur : « vue du tueur » sous le titre et petit viseur au centre
            float fp = l.premiere();
            int tf = (int) (ta * fp);
            if (tf >= 8) {
                vueTueur.dessiner(g, font, 27, 21, Ui.argb(Ui.mix(ROUGE, 0xFFFFFF, 0.45f), tf));
                // en visee, l'organe de visee de l'arme fait office de viseur
                viseur(g, W / 2, H / 2, (int) (220 * a * fp * (1f - 0.9f * l.visee())));
            }
            // raison (en haut a droite)
            if (raison != null) {
                int w = raison.largeur + 12, x = W - 16 - w;
                Ui.soft(g, x, 7, w, 12, Ui.argb(ROUGE, (int) (40 * a)));
                Ui.softOutline(g, x + 1, 8, w - 2, 10, Ui.argb(ROUGE, (int) (110 * a)));
                raison.dessiner(g, font, x + 6, 9, Ui.argb(Ui.mix(ROUGE, 0xFFFFFF, 0.45f), ta));
            }
            // tue par + nom + arme
            int yb = H - 40;
            tuePar.dessiner(g, font, 16, yb, Ui.argb(Ui.MUTED, ta));
            g.pose().pushPose();
            g.pose().translate(16, yb + 11, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            g.drawString(font, nom, 0, 0, Ui.argb(nomCouleur, ta), false);
            g.pose().popPose();
            int xd = 16 + Math.round(nomLargeur * 1.6f) + 12;
            g.fill(xd - 6, yb + 15, xd - 5, yb + 25, Ui.argb(0xFFFFFF, (int) (50 * a)));
            g.drawString(font, detail, xd, yb + 16, Ui.argb(Ui.mix(Ui.MUTED, 0xFFFFFF, 0.35f), ta), false);
            // touche pour passer (en bas a droite)
            int xp = W - 16 - passer.largeur;
            passer.dessiner(g, font, xp, yb + 16, Ui.argb(Ui.MUTED, ta));
            int wc = Math.max(14, cle.largeur + 10), xc = xp - 7 - wc;
            Ui.soft(g, xc, yb + 13, wc, 13, Ui.argb(0xFFFFFF, (int) (20 * a)));
            Ui.softOutline(g, xc + 1, yb + 14, wc - 2, 11, Ui.argb(0xFFFFFF, (int) (60 * a)));
            cle.dessiner(g, font, xc + (wc - cle.largeur) / 2f, yb + 16, Ui.argb(Ui.TEXT, ta));
            // progression
            int x0 = 16, x1 = W - 16, yp = H - 7;
            g.fill(x0, yp, x1, yp + 1, Ui.argb(0xFFFFFF, (int) (26 * a)));
            int xf = x0 + Math.round((x1 - x0) * l.progression());
            if (xf > x0) g.fill(x0, yp, xf, yp + 1, Ui.argb(ROUGE, (int) (230 * a)));
        }
        g.pose().popPose();
        // voiles noirs des fondus (plein ecran, par-dessus)
        float v = l.voile();
        if (v > 0.003f) g.fill(0, 0, largeur, hauteur, Ui.argb(0x000000, (int) (255 * v)));
    }

    /** Petit viseur discret (point et quatre traits ecartes) : ou visait le tueur, vu par ses yeux. */
    private static void viseur(GuiGraphics g, int cx, int cy, int alpha) {
        if (alpha < 8) return;
        int c = Ui.argb(0xFFFFFF, alpha), ombre = Ui.argb(0x000000, alpha / 2);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, ombre);
        g.fill(cx, cy, cx + 1, cy + 1, c);
        int ecart = 4, long_ = 4;
        g.fill(cx - ecart - long_, cy, cx - ecart, cy + 1, c);
        g.fill(cx + ecart + 1, cy, cx + ecart + 1 + long_, cy + 1, c);
        g.fill(cx, cy - ecart - long_, cx + 1, cy - ecart, c);
        g.fill(cx, cy + ecart + 1, cx + 1, cy + ecart + 1 + long_, c);
    }
}

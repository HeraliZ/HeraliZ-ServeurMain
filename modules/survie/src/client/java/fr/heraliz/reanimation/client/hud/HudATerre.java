package fr.heraliz.reanimation.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.client.kit.Anim;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import fr.heraliz.noyau.client.kit.ToucheHud;
import fr.heraliz.reanimation.client.ClientDowned;
import fr.heraliz.reanimation.client.ClientSetup;
import fr.heraliz.reanimation.client.LocalEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/**
 * HUD du joueur à terre (refonte du 25/10 ; retours du propriétaire : « trop gros, faut un truc bien plus smooth »,
 * docs/HUD_REFONTE.md, maquettes {@code maquettes/hud_refonte/a_terre_*}). Plus de bloc : au bas de l'écran, au centre,
 * une ligne fine « ● À TERRE · 0:24 » (point qui bat avec le cœur, capitales espacées, temps en Oswald 8), dessous une
 * barre très fine ({@link #BARRE_H} unité) du sang restant qui se vide en douceur, la ligne d'état en petit, puis l'aide
 * d'abandon en tout petit (la touche se remplit pendant le maintien). Un voile sombre aux bords fondus, sans cadre, garde
 * le texte lisible sur le monde. Réanimé par un allié : « ● RÉANIMATION · 0:03 » et barre en vert (la couleur passe en
 * douceur). Vignette rouge très légère.
 * <p>Fondu doux d'apparition ({@value #ENTREE_MS} ms) et de disparition ({@value #SORTIE_MS} ms), la ligne glisse de
 * {@link #GLISSE} unités ; le HUD garde ce qu'il montrait pendant sa sortie (relevé, mort, exécuté). Textes en cache :
 * recalculés seulement quand la langue, la touche ou la valeur affichée change. Rien ne change côté serveur
 * (saignement, réanimation, abandon).
 */
public final class HudATerre {
    private HudATerre() {
    }

    /** Distances au bas de l'écran : milieu de la ligne principale, haut de la barre, milieu de l'état, milieu de l'aide. */
    static final float LIGNE_BAS = 43f, BARRE_BAS = 35.5f, ETAT_BAS = 27.5f, AIDE_BAS = 16f;
    /** Barre (largeur, épaisseur), point du cœur (rayon), écart entre les morceaux de la ligne, espace des capitales. */
    static final float BARRE_L = 150f, BARRE_H = 1f, POULS_R = 2f, ECART = 4f, TITRE_ESPACE = 1f, ECART_AIDE = 3f;
    /** Voile derrière le texte : demi-largeur, fondu des côtés, haut et bas (depuis le bas de l'écran), fondu vertical, opacité. */
    static final float VOILE_DL = 130f, VOILE_FX = 80f, VOILE_HAUT = 56f, VOILE_BAS = 4f, VOILE_FY = 14f;
    static final int VOILE_ALPHA = 90;
    /** Glissement vertical pendant le fondu. */
    static final float GLISSE = 3f;
    /** Fondu doux (ms). */
    static final int ENTREE_MS = 350, SORTIE_MS = 450;
    /** Voile sombre et vignette rouge (opacités 0..1) selon la vie restante et le battement ; taches de sang. Très légers. */
    static final float ECRAN_MIN = 0.04f, ECRAN_VIE = 0.10f, VIGNETTE_MIN = 0.16f, VIGNETTE_VIE = 0.16f, VIGNETTE_BAT = 0.08f,
            TACHES = 0.22f;
    /** Vitesse des transitions (par seconde) : barre, couleur. */
    static final float VITESSE = 12f;

    private static final ResourceLocation VIGNETTE = new ResourceLocation("reanimation", "textures/gui/vignette.png");
    private static final ResourceLocation SPLATTER = new ResourceLocation("reanimation", "textures/gui/splatter.png");

    private static final Anim.Fondu FONDU = new Anim.Fondu(ENTREE_MS, SORTIE_MS, true);
    private static final Anim.Horloge HORLOGE = new Anim.Horloge();
    private static final TexteHud TITRE = new TexteHud(PoliceHud.MINI_GRAS, TITRE_ESPACE);
    private static final TexteHud SEPARATEUR = new TexteHud(PoliceHud.MINI);
    private static final TexteHud CHIFFRE = new TexteHud(PoliceHud.CHIFFRE_PETIT);
    private static final TexteHud ETAT = new TexteHud(PoliceHud.MINI);
    private static final TexteHud AVANT = new TexteHud(PoliceHud.MINI), APRES = new TexteHud(PoliceHud.MINI);
    private static final TexteHud AIDE_REANIME = new TexteHud(PoliceHud.MINI);
    private static final ToucheHud ABANDON = new ToucheHud(ClientSetup.GIVE_UP);

    /** Ce que montre le HUD (gardé pendant la sortie) et ses valeurs animées. */
    private static boolean reanime, premiere = true;
    private static float part, partAffichee, maintien, rouge, vert, bleu;
    private static int couleur = Palette.DANGER, couleurEtat = Palette.ATTENUE;
    /** Clés des textes en cache. */
    private static String langue;
    private static int secondes = -1, etatTexte = -1, partenaireTexte = Integer.MIN_VALUE;

    /** Appelé à chaque image par DownedHud (tous les cas : il décide lui-même s'il est visible). */
    public static void rendre(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
        boolean aTerre = me != null && me.has(1) && !me.has(256);
        float dt = HORLOGE.dt();
        if (aTerre) majEtat(mc, me, pt, dt);
        float a = FONDU.maj(aTerre);
        if (!FONDU.visible() || a <= 0f) {
            premiere = true;
            return;
        }
        partAffichee = Anim.lisser(partAffichee, part, dt, VITESSE);
        rouge = Anim.lisser(rouge, couleur >> 16 & 255, dt, VITESSE * 0.7f);
        vert = Anim.lisser(vert, couleur >> 8 & 255, dt, VITESSE * 0.7f);
        bleu = Anim.lisser(bleu, couleur & 255, dt, VITESSE * 0.7f);
        int c = Math.round(rouge) << 16 | Math.round(vert) << 8 | Math.round(bleu);

        // effets plein écran (même sous F1), très légers : voile, vignette rouge qui bat, taches de sang
        float vie = reanime ? 1f : part;
        float bat = Math.max(0f, 1f - (LocalEffects.ticksSinceBeat + pt) / 7f);
        float effacement = reanime ? 1f - 0.7f * part : 1f;
        g.fill(0, 0, w, h, Palette.argb(0x000000, (ECRAN_MIN + ECRAN_VIE * (1f - vie)) * effacement * a));
        teinte(g, VIGNETTE, 0, 0, w, h, 256, 0.55f, 0.02f, 0.02f, (VIGNETTE_MIN + VIGNETTE_VIE * (1f - vie) + VIGNETTE_BAT * bat) * effacement * a);
        int s = Math.min(w, h) / 2;
        teinte(g, SPLATTER, 0, 0, s, s, 128, 0.42f, 0f, 0f, TACHES * effacement * a);
        g.pose().pushPose();
        g.pose().translate(w, h, 0f);
        g.pose().scale(-1f, -1f, 1f);
        teinte(g, SPLATTER, 0, 0, Math.round(s * 0.8f), Math.round(s * 0.8f), 128, 0.42f, 0f, 0f, TACHES * effacement * a);
        g.pose().popPose();
        if (mc.options.hideGui) return;

        // ligne principale : point, titre, « · », temps ; centrée
        float cx = w / 2f, glisse = (1f - a) * GLISSE;
        float largeurLigne = 2f * POULS_R + ECART + TITRE.largeur() + ECART + SEPARATEUR.largeur() + ECART + CHIFFRE.largeur();
        float x = cx - largeurLigne / 2f;
        float cyLigne = h - LIGNE_BAS + glisse, cyEtat = h - ETAT_BAS + glisse, cyAide = h - AIDE_BAS + glisse;
        float xTitre = x + 2f * POULS_R + ECART;
        float xSep = xTitre + TITRE.largeur() + ECART;
        float xChiffre = xSep + SEPARATEUR.largeur() + ECART;
        float aide = reanime ? AIDE_REANIME.largeur() : AVANT.largeur() + ECART_AIDE + ABANDON.largeur() + ECART_AIDE + APRES.largeur();
        float xAide = cx - aide / 2f, xTouche = xAide + AVANT.largeur() + ECART_AIDE;
        float bx = cx - BARRE_L / 2f, by = Formes.caler(h - BARRE_BAS + glisse);

        // formes, un seul vidage
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        float px = Formes.pixel();
        float dl = Math.max(VOILE_DL, Math.max(aide, ETAT.largeur()) / 2f + VOILE_FX / 2f);
        Formes.voile(vc, m, cx - dl, h - VOILE_HAUT, cx + dl, h - VOILE_BAS, VOILE_FX, VOILE_FY, 0x000000, Math.round(VOILE_ALPHA * a));
        Formes.halo(vc, m, x + POULS_R, cyLigne, POULS_R * 3f * (1f + 0.4f * bat), Palette.argb(c, (0.25f + 0.35f * bat) * a));
        Formes.disque(vc, m, x + POULS_R, cyLigne, POULS_R * (1f + 0.25f * bat), Palette.argb(c, a), px);
        Formes.rect(vc, m, bx, by, bx + BARRE_L, by + BARRE_H, Palette.argb(0xFFFFFF, 0.12f * a));
        float fw = BARRE_L * Mth.clamp(partAffichee, 0f, 1f);
        if (fw > 0f) {
            Formes.degradeH(vc, m, bx, by, bx + fw, by + BARRE_H, Palette.argb(Palette.mix(c, 0x000000, 0.55f), a), Palette.argb(c, a));
            Formes.halo(vc, m, bx + fw, by + BARRE_H / 2f, 3f, Palette.argb(c, 0.45f * a));
        }
        if (!reanime) ABANDON.fond(vc, m, xTouche, cyAide, maintien, Palette.DANGER, a * 0.85f);
        g.flush();

        // textes, un seul vidage
        TITRE.ajouter(g, xTitre, cyLigne, Palette.argb(c, a), TexteHud.GAUCHE, true);
        SEPARATEUR.ajouter(g, xSep, cyLigne, Palette.argb(Palette.ATTENUE, a), TexteHud.GAUCHE, true);
        CHIFFRE.ajouter(g, xChiffre, cyLigne, Palette.argb(Palette.TEXTE, a), TexteHud.GAUCHE, true);
        ETAT.ajouter(g, cx, cyEtat, Palette.argb(couleurEtat, a), TexteHud.CENTRE, true);
        if (reanime) {
            AIDE_REANIME.ajouter(g, cx, cyAide, Palette.argb(Palette.ATTENUE, 0.85f * a), TexteHud.CENTRE, true);
        } else {
            AVANT.ajouter(g, xAide, cyAide, Palette.argb(Palette.ATTENUE, 0.85f * a), TexteHud.GAUCHE, true);
            ABANDON.texte(g, xTouche, cyAide, 0.85f * a);
            APRES.ajouter(g, xTouche + ABANDON.largeur() + ECART_AIDE, cyAide, Palette.argb(Palette.ATTENUE, 0.85f * a), TexteHud.GAUCHE, true);
        }
        g.flush();
    }

    /** Relève l'état du joueur à terre (une image) et met à jour les textes qui ont changé. */
    private static void majEtat(Minecraft mc, ClientDowned.Info me, float pt, float dt) {
        String l = mc.getLanguageManager().getSelected();
        boolean nouvelleLangue = !l.equals(langue);
        Language lang = Language.getInstance();
        if (nouvelleLangue) {
            langue = l;
            SEPARATEUR.texte("·");
            AVANT.texte(lang.getOrDefault("hud.reanimation.hold"));
            APRES.texte(lang.getOrDefault("hud.reanimation.to_give_up"));
            AIDE_REANIME.texte(lang.getOrDefault("hud.reanimation.aide_reanime"));
            secondes = -1;
            etatTexte = -1;
        }
        ABANDON.maj();
        boolean r = me.has(4);
        if (r != reanime || nouvelleLangue || TITRE.vide()) {
            TITRE.texte(lang.getOrDefault(r ? "hud.reanimation.reviving_title" : "hud.reanimation.downed"));
        }
        boolean change = r != reanime;
        reanime = r;
        couleur = r ? Palette.OK : Palette.DANGER;
        if (r) {
            part = me.reviveProgress(pt);
        } else {
            float pause = me.has(32) ? 0f : pt;
            part = me.bleedMax <= 0 ? 0f : Mth.clamp((me.bleed - pause) / me.bleedMax, 0f, 1f);
        }
        if (premiere || change) {
            // première image ou passage saigne / réanimé : la barre repart de sa vraie valeur
            partAffichee = part;
            if (premiere) {
                rouge = couleur >> 16 & 255;
                vert = couleur >> 8 & 255;
                bleu = couleur & 255;
            }
            premiere = false;
        }
        // temps : avant la mort, ou avant la fin de la réanimation
        int ticks = r ? Math.max(0, me.reviveMax - me.revive) : Math.max(0, me.bleed);
        int sec = (ticks + 19) / 20;
        if (sec != secondes) {
            secondes = sec;
            CHIFFRE.texte(sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60);
        }
        // ligne d'état : saigne / fouillé par X / réanimé par X
        int etat = r ? 2 : me.has(2) ? 1 : 0;
        if (etat != etatTexte || (etat != 0 && me.partner != partenaireTexte)) {
            etatTexte = etat;
            partenaireTexte = me.partner;
            Entity p = me.partner >= 0 && mc.level != null ? mc.level.getEntity(me.partner) : null;
            String nom = p != null ? p.getName().getString() : "?";
            ETAT.texte(etat == 2 ? I18n.get("hud.reanimation.being_revived", nom)
                    : etat == 1 ? I18n.get("hud.reanimation.being_searched", nom)
                    : lang.getOrDefault("hud.reanimation.statut_saigne"));
            couleurEtat = etat == 2 ? Palette.clair(Palette.OK, 0.45f) : etat == 1 ? Palette.ALERTE : Palette.ATTENUE;
        }
        // abandon (maintenir la touche) : la touche se remplit, lissé entre deux ticks
        float cible = r ? 0f : Mth.clamp(LocalEffects.giveUpHold / (float) LocalEffects.GIVE_UP_TICKS, 0f, 1f);
        maintien = cible < maintien ? cible : Anim.lisser(maintien, cible, dt, 30f);
    }

    /** Image plein écran teintée (vignette, taches). */
    private static void teinte(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int taille, float r, float gr, float b, float a) {
        if (a <= 0.01f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(r, gr, b, Mth.clamp(a, 0f, 1f));
        g.blit(texture, x, y, w, h, 0f, 0f, taille, taille, taille, taille);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}

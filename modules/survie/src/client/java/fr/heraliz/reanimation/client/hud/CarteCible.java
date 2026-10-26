package fr.heraliz.reanimation.client.hud;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Matrix4f;

/**
 * Carte affichée quand on vise un joueur à terre (refonte du 25/10, docs/HUD_REFONTE.md, maquettes
 * {@code maquettes/hud_refonte/carte_*}), à droite sous le réticule : pseudo et pastille « À TERRE », sang restant
 * (barre interpolée) et « Mort dans 24 s », puis les actions avec les vraies touches du joueur : réanimer, fouiller,
 * exécuter (maintenir : la touche se remplit). Si quelqu'un s'en occupe déjà : une ligne d'état à la place des actions.
 * Fondu d'apparition et de disparition (la carte garde ce qu'elle montrait pendant sa sortie). Textes en cache.
 */
public final class CarteCible {
    private CarteCible() {
    }

    /** Position (depuis le centre de l'écran), largeur, marge intérieure, écart entre deux éléments. */
    static final float DX = 16f, DY = 10f, L = 152f, PAD = 8f, ECART = 6f;
    /** Ligne 1 (pseudo et pastille) : milieu ; pastille : hauteur, marge, espace entre les capitales. */
    static final float LIGNE1_CY = 10f, PUCE_H = 10f, PUCE_PAD = 4f, PUCE_ESPACE = 0.6f;
    /** Barre du sang (haut, épaisseur), séparation, première action (milieu), pas entre deux actions. */
    static final float BARRE_Y = 20.5f, BARRE_H = 2f, SEP_Y = 28f, ACTION_Y = 37f, ACTION_PAS = 14f;
    /** Écart autour du « + » entre deux touches, avant « (maintenir) ». */
    static final float ECART_PLUS = 2.5f, ECART_SUITE = 3f;
    /** Hauteur de la carte avec les actions, avec une ligne d'état. */
    static final float H_ACTIONS = 76f, H_ETAT = 46f;

    private static final Anim.Fondu FONDU = new Anim.Fondu();
    private static final TexteHud PSEUDO = new TexteHud(PoliceHud.TEXTE_GRAS);
    private static final TexteHud PUCE = new TexteHud(PoliceHud.MINI_GRAS, PUCE_ESPACE);
    private static final TexteHud MORT = new TexteHud(PoliceHud.MINI);
    private static final TexteHud PLUS = new TexteHud(PoliceHud.MINI);
    private static final TexteHud REANIMER = new TexteHud(PoliceHud.MINI), FOUILLER = new TexteHud(PoliceHud.MINI);
    private static final TexteHud EXECUTER = new TexteHud(PoliceHud.MINI), MAINTENIR = new TexteHud(PoliceHud.MINI);
    private static final TexteHud ETAT = new TexteHud(PoliceHud.MINI);
    private static ToucheHud accroupir, utiliser, executer;

    /** Ce que montre la carte (gardé pendant la sortie). */
    private static int cibleId = Integer.MIN_VALUE, etat = -1, secondes = -1;
    private static float vie;
    private static String langue;

    /** Appelé à chaque image par DownedHud. */
    public static void rendre(Minecraft mc, GuiGraphics g, float pt, int w, int h) {
        Entity cible = null;
        ClientDowned.Info info = null;
        if (mc.hitResult instanceof EntityHitResult hit && mc.screen == null && ClientDowned.localFlags() == 0) {
            Entity e = hit.getEntity();
            ClientDowned.Info i = ClientDowned.get(e.getId());
            if (i != null && i.has(1)) {
                cible = e;
                info = i;
            }
        }
        if (cible != null) majEtat(mc, cible, info, pt);
        float a = FONDU.maj(cible != null);
        if (!FONDU.visible() || a <= 0f || mc.options.hideGui || etat < 0) return;

        boolean actions = etat == 0;
        float largeurActions = actions ? largeurActions() : ETAT.largeur();
        float lc = Math.max(L, Math.max(largeurActions, PSEUDO.largeur() + ECART + PUCE.largeur() + 2f * PUCE_PAD) + 2f * PAD);
        float hc = actions ? H_ACTIONS : H_ETAT;
        float x0 = Formes.caler(w / 2f + DX), y0 = Formes.caler(h / 2f + DY);
        float pw = PUCE.largeur() + 2f * PUCE_PAD, px0 = x0 + lc - PAD - pw, cy1 = y0 + LIGNE1_CY;
        float bx = x0 + PAD, by = y0 + BARRE_Y, bw = lc - 2f * PAD - MORT.largeur() - ECART;
        float maintien = Mth.clamp(LocalEffects.executeHold / (float) LocalEffects.EXECUTE_TICKS, 0f, 1f);

        // formes, un seul vidage
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        float px = Formes.pixel();
        Formes.verre(vc, m, x0, y0, lc, hc, Palette.VERRE_ALPHA, a);
        Formes.rect(vc, m, px0, cy1 - PUCE_H / 2f, px0 + pw, cy1 + PUCE_H / 2f, Palette.argb(Palette.DANGER, 40f / 255f * a));
        Formes.bord(vc, m, px0, cy1 - PUCE_H / 2f, pw, PUCE_H, px, Palette.argb(Palette.DANGER, 110f / 255f * a));
        Formes.rect(vc, m, bx, by, bx + bw, by + BARRE_H, Palette.argb(0xFFFFFF, 26f / 255f * a));
        if (vie > 0f) Formes.degradeH(vc, m, bx, by, bx + bw * vie, by + BARRE_H, Palette.argb(0x7A0A0A, a), Palette.argb(Palette.DANGER, a));
        Formes.rect(vc, m, x0 + PAD, y0 + SEP_Y, x0 + lc - PAD, y0 + SEP_Y + px, Palette.argb(0xFFFFFF, 22f / 255f * a));
        if (actions) {
            float x = x0 + PAD, cy = y0 + ACTION_Y;
            accroupir.fond(vc, m, x, cy, 0f, 0, a);
            utiliser.fond(vc, m, x + accroupir.largeur() + 2f * ECART_PLUS + PLUS.largeur(), cy, 0f, 0, a);
            utiliser.fond(vc, m, x, cy + ACTION_PAS, 0f, 0, a);
            executer.fond(vc, m, x, cy + 2f * ACTION_PAS, maintien, Palette.DANGER, a);
        }
        g.flush();

        // textes, un seul vidage
        PSEUDO.ajouter(g, x0 + PAD, cy1, Palette.argb(0xFFFFFF, a), TexteHud.GAUCHE, true);
        PUCE.ajouter(g, px0 + PUCE_PAD, cy1, Palette.argb(Palette.clair(Palette.DANGER, 0.35f), a), TexteHud.GAUCHE, false);
        MORT.ajouter(g, x0 + lc - PAD, by + BARRE_H / 2f, Palette.argb(Palette.clair(Palette.DANGER, 0.55f), a), TexteHud.DROITE, true);
        if (actions) {
            float x = x0 + PAD, cy = y0 + ACTION_Y;
            accroupir.texte(g, x, cy, a);
            float xp = x + accroupir.largeur() + ECART_PLUS;
            PLUS.ajouter(g, xp, cy, Palette.argb(Palette.ATTENUE, a), TexteHud.GAUCHE, true);
            float xu = xp + PLUS.largeur() + ECART_PLUS;
            utiliser.texte(g, xu, cy, a);
            REANIMER.ajouter(g, xu + utiliser.largeur() + ECART, cy, Palette.argb(Palette.OK, a), TexteHud.GAUCHE, true);
            utiliser.texte(g, x, cy + ACTION_PAS, a);
            FOUILLER.ajouter(g, x + utiliser.largeur() + ECART, cy + ACTION_PAS, Palette.argb(Palette.TEXTE, a), TexteHud.GAUCHE, true);
            executer.texte(g, x, cy + 2f * ACTION_PAS, a);
            float xe = x + executer.largeur() + ECART;
            EXECUTER.ajouter(g, xe, cy + 2f * ACTION_PAS, Palette.argb(Palette.clair(Palette.DANGER, 0.25f), a), TexteHud.GAUCHE, true);
            MAINTENIR.ajouter(g, xe + EXECUTER.largeur() + ECART_SUITE, cy + 2f * ACTION_PAS, Palette.argb(Palette.TRES_ATTENUE, a),
                    TexteHud.GAUCHE, true);
        } else {
            int c = etat == 1 ? Palette.OK : etat == 2 ? Palette.ALERTE : Palette.clair(Palette.DANGER, 0.25f);
            ETAT.ajouter(g, x0 + PAD, y0 + ACTION_Y, Palette.argb(c, a), TexteHud.GAUCHE, true);
        }
        g.flush();
    }

    /** Largeur de la plus longue ligne d'actions. */
    private static float largeurActions() {
        float r = accroupir.largeur() + 2f * ECART_PLUS + PLUS.largeur() + utiliser.largeur() + ECART + REANIMER.largeur();
        float f = utiliser.largeur() + ECART + FOUILLER.largeur();
        float e = executer.largeur() + ECART + EXECUTER.largeur() + ECART_SUITE + MAINTENIR.largeur();
        return Math.max(r, Math.max(f, e));
    }

    /** Relève la cible (une image) et met à jour les textes qui ont changé. */
    private static void majEtat(Minecraft mc, Entity cible, ClientDowned.Info info, float pt) {
        if (accroupir == null) {
            accroupir = new ToucheHud(mc.options.keyShift);
            utiliser = new ToucheHud(mc.options.keyUse);
            executer = new ToucheHud(ClientSetup.EXECUTE);
        }
        accroupir.maj();
        utiliser.maj();
        executer.maj();
        String l = mc.getLanguageManager().getSelected();
        boolean nouvelleLangue = !l.equals(langue);
        if (nouvelleLangue) {
            langue = l;
            Language lang = Language.getInstance();
            PUCE.texte(lang.getOrDefault("hud.reanimation.downed"));
            PLUS.texte("+");
            REANIMER.texte(lang.getOrDefault("hud.reanimation.revive_action"));
            FOUILLER.texte(lang.getOrDefault("hud.reanimation.search_action"));
            EXECUTER.texte(lang.getOrDefault("hud.reanimation.execute_action"));
            MAINTENIR.texte(lang.getOrDefault("hud.reanimation.hold_hint"));
            secondes = -1;
            etat = -1;
        }
        if (cible.getId() != cibleId) {
            cibleId = cible.getId();
            PSEUDO.texte(cible.getName().getString());
        }
        int e = info.has(4) ? 1 : info.has(2) ? 2 : info.has(256) ? 3 : 0;
        if (e != etat) {
            etat = e;
            if (e != 0) {
                ETAT.texte(Language.getInstance().getOrDefault(e == 1 ? "hud.reanimation.target_revived"
                        : e == 2 ? "hud.reanimation.target_searched" : "hud.reanimation.target_executed"));
            }
        }
        int s = (info.bleed + 19) / 20;
        if (s != secondes) {
            secondes = s;
            MORT.texte(I18n.get("hud.reanimation.bleed_timer", s));
        }
        float pause = info.has(32) ? 0f : pt;
        vie = info.bleedMax <= 0 ? 0f : Mth.clamp((info.bleed - pause) / info.bleedMax, 0f, 1f);
    }
}

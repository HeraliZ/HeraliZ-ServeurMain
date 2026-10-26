package fr.heraliz.usure.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.client.kit.Anim;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import fr.heraliz.usure.ClientConfig;
import fr.heraliz.usure.Reparation;
import fr.heraliz.usure.Usure;
import fr.heraliz.usure.regles.Etat;
import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Indicateur de l'état de l'arme EN MAIN (refonte du 25/10 ; retours du propriétaire : « sur une ligne, un truc plus
 * moderne », docs/HUD_REFONTE.md, maquettes {@code maquettes/hud_refonte/usure_*}) : UNE ligne compacte sur un verre
 * penché comme les cases de la barre d'objets DayZ ({@link #INCLINAISON}°) : barre de {@link #SEGMENTS} segments penchés
 * de la couleur de l'état (palette du noyau : ok, alerte, danger ; le segment en cours rempli en partie, segments perdus
 * aux réparations en rouge pâle), pourcentage (Oswald 8) puis nom de l'état (petites capitales). Largeur FIXE (calculée
 * une fois par langue sur « 100 % » et le plus long nom d'état), ancrée en bas à droite ({@link #DROITE} unités du bord),
 * centrée sur la hauteur des cases de la barre DayZ ; la place se lit sur le bord droit RÉEL de cette barre
 * ({@code HotbarOverlay.bordDroit}, par réflexion : espacement, inclinaison, case de seconde main ou jauge d'attaque) :
 * s'il manque de la place (écran étroit), version courte (les segments seuls, leur couleur dit l'état), elle aussi de
 * largeur fixe. Plus aucune cote reprise du HUD de TaCZ.
 * <p>Changement d'état : couleur et barre passent en douceur ; changement d'arme : tout de suite. Apparition et
 * disparition en fondu. Aucune allocation par tir ni par image : pourcentages et noms en cache (un texte par valeur),
 * formes envoyées au tampon puis un seul vidage. Rien pour une arme sans usure ; en match et en zombies, les armes
 * prêtées ne s'usent pas : rien ne s'affiche. Arme ruinée : toujours affichée (alerte), contour rouge avivé au clic à
 * vide ; les autres états suivent le réglage du joueur ({@link ClientConfig#HUD}) et du serveur ({@code hudAutorise}).
 * Masqué avec F1, F3 et en spectateur. Règles d'usure inchangées.
 */
public final class HudUsure {
    private HudUsure() {
    }

    /** Hauteur, écart au bord droit, écart minimal avec la barre DayZ, milieu des cases de la barre (depuis le bas). */
    static final float H = 11f, DROITE = 8f, ECART_BARRE = 6f, BARRE_MILIEU = 12f;
    /** Marge intérieure, écart entre la barre, le pourcentage et le nom, inclinaison (degrés, comme la barre DayZ). */
    static final float PAD = 6f, ECART = 5f, INCLINAISON = 12f;
    /** Barre segmentée : nombre de segments, largeur d'un segment, écart, hauteur. */
    static final int SEGMENTS = 10;
    static final float SEG_L = 3.2f, SEG_ECART = 1f, SEG_H = 5f;
    /** Espace entre les capitales du nom de l'état. */
    static final float NOM_ESPACE = 0.8f;
    /** Opacité du verre (plus léger que les écrans). */
    static final int VERRE_ALPHA = 150;
    /** Couleurs des états entre deux rôles de la palette : bon (ok vers alerte), abîmé (alerte vers danger). */
    static final float MELANGE_BON = 0.45f, MELANGE_ABIME = 0.5f;
    /** Opacités : segments vides, segments perdus aux réparations, segments d'une arme ruinée. */
    static final float PISTE_ALPHA = 0.16f, PERDU_ALPHA = 0.45f, RUINE_ALPHA = 0.45f;
    /** Éclat du contour après un clic à vide (ms), vitesse des transitions (par seconde). */
    static final int ECLAT_MS = 600;
    static final float VITESSE = 10f;
    private static final float PENTE = Formes.pente(INCLINAISON);
    private static final float BARRE_W = SEGMENTS * SEG_L + (SEGMENTS - 1) * SEG_ECART;

    private static final Anim.Fondu FONDU = new Anim.Fondu();
    private static final Anim.Horloge HORLOGE = new Anim.Horloge();
    /** Un texte par pourcentage affiché (0 à 100), fait à la première utilisation. */
    private static final TexteHud[] POURCENTS = new TexteHud[101];
    private static final TexteHud[] NOMS = new TexteHud[Etat.TOUS.length];

    static {
        for (int i = 0; i < NOMS.length; i++) NOMS[i] = new TexteHud(PoliceHud.MINI_GRAS, NOM_ESPACE);
    }

    /** Largeur fixe de la version pleine (refaite quand la langue change) et place réservée au pourcentage ; version
     *  courte : les segments seuls. */
    private static String langue;
    private static float largeur, largeurPct;
    private static final float LARGEUR_COURTE = PAD + BARRE_W + PAD;
    /** Ce que montre la pastille (gardé pendant la sortie) et ses valeurs animées. */
    private static Item arme;
    private static int pct = -1, etatAffiche, couleurCible = Palette.OK;
    private static float part = 1f, partCible = 1f, partMax = 1f, rouge = -1f, vert, bleu;
    private static boolean ruinee;
    /** Bord droit de la barre DayZ (réflexion, sans allocation). */
    private static MethodHandle bordBarre;
    private static boolean bordCherche;

    static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean montre = p != null && !p.isSpectator() && suivre(p);
        float dt = HORLOGE.dt();
        float a = FONDU.maj(montre);
        if (!FONDU.visible() || a <= 0f || p == null || pct < 0 || mc.options.hideGui || mc.options.renderDebug) return;
        textes(mc);
        part = Anim.lisser(part, partCible, dt, VITESSE);
        rouge = Anim.lisser(rouge, couleurCible >> 16 & 255, dt, VITESSE);
        vert = Anim.lisser(vert, couleurCible >> 8 & 255, dt, VITESSE);
        bleu = Anim.lisser(bleu, couleurCible & 255, dt, VITESSE);
        int couleur = Math.round(rouge) << 16 | Math.round(vert) << 8 | Math.round(bleu);

        // place : version pleine si elle ne touche pas la barre DayZ, sinon version courte
        float x1 = w - DROITE;
        boolean courte = x1 - largeur < bordDroitBarre(mc, w) + ECART_BARRE;
        float lw = courte ? LARGEUR_COURTE : largeur;
        float x0 = Formes.caler(x1 - lw), y0 = Formes.caler(h - BARRE_MILIEU - H / 2f), cy = y0 + H / 2f;
        float sx = x0 + PAD, sy = cy - SEG_H / 2f;
        float xPct = sx + BARRE_W + ECART, xNom = xPct + largeurPct + ECART;
        float px = Formes.pixel();

        // formes, un seul vidage
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        Formes.verrePenche(vc, m, x0, y0, x0 + lw, y0 + H, PENTE, VERRE_ALPHA, a);
        if (ruinee) {
            long depuisClic = ClientTirs.depuisClic();
            float eclat = depuisClic < ECLAT_MS ? 1f - depuisClic / (float) ECLAT_MS : 0f;
            Formes.bordPenche(vc, m, x0, y0, x0 + lw, y0 + H, PENTE, 2f * px, Palette.argb(Palette.DANGER, (0.75f + 0.25f * eclat) * a));
            Formes.bordPenche(vc, m, x0 - 2f * px, y0 - 2f * px, x0 + lw + 2f * px, y0 + H + 2f * px, PENTE, 3f * px,
                    Palette.argb(Palette.DANGER, (0.18f + 0.3f * eclat) * a));
        }
        int piste = Palette.argb(0xFFFFFF, PISTE_ALPHA * a);
        if (ruinee) {
            int r = Palette.argb(Palette.DANGER, RUINE_ALPHA * a);
            Formes.segments(vc, m, sx, sy, BARRE_W, SEG_H, PENTE, SEGMENTS, SEG_ECART, 0f, r, r, 1f, r, px);
        } else {
            Formes.segments(vc, m, sx, sy, BARRE_W, SEG_H, PENTE, SEGMENTS, SEG_ECART, part, Palette.argb(couleur, a), piste,
                    partMax, Palette.argb(Palette.DANGER, PERDU_ALPHA * a), px);
        }
        g.flush();

        // textes, un seul vidage
        if (!courte) {
            int couleurPct = ruinee ? Palette.clair(Palette.DANGER, 0.25f) : Palette.TEXTE;
            POURCENTS[pct].ajouter(g, xPct, cy, Palette.argb(couleurPct, a), TexteHud.GAUCHE, true);
            NOMS[etatAffiche].ajouter(g, xNom, cy, Palette.argb(Palette.clair(couleur, 0.3f), a), TexteHud.GAUCHE, true);
            g.flush();
        }
    }

    /** Lit l'arme en main ; vrai si la pastille doit être visible. */
    private static boolean suivre(LocalPlayer p) {
        ItemStack stack = p.getMainHandItem();
        Profil pr = ReglesClient.suivie(p, stack);
        if (pr == null) return false;
        Regles r = ReglesClient.get();
        int etat = Usure.etat(stack, pr);
        Etat st = Etat.de(etat, r);
        boolean ruine = st == Etat.RUINE;
        if (!ruine) {
            ClientConfig.ModeHud mode = ClientConfig.HUD.get();
            if (!r.hudAutorise || mode == ClientConfig.ModeHud.JAMAIS) return false;
            if (mode == ClientConfig.ModeHud.SI_USEE && st == Etat.NEUF) return false;
        }
        // même arrondi que Reparation.pct (1 % au moins tant que l'arme n'est pas ruinée, 99 % tant qu'elle n'est pas neuve)
        int v = Math.round(etat / 10f);
        if (v == 0 && etat > 0) v = 1;
        if (v == 100 && etat < Usure.PLEIN) v = 99;
        v = Math.max(0, Math.min(100, v));
        if (POURCENTS[v] == null) POURCENTS[v] = new TexteHud(PoliceHud.CHIFFRE_PETIT).texte(Reparation.pct(etat));
        pct = v;
        etatAffiche = st.ordinal();
        ruinee = ruine;
        couleurCible = couleur(st);
        partCible = etat / (float) Usure.PLEIN;
        partMax = Usure.max(stack) / (float) Usure.PLEIN;
        // autre arme (ou première fois) : pas de transition
        if (stack.getItem() != arme || rouge < 0f) {
            arme = stack.getItem();
            part = partCible;
            rouge = couleurCible >> 16 & 255;
            vert = couleurCible >> 8 & 255;
            bleu = couleurCible & 255;
        }
        return true;
    }

    /** Couleur d'un état : rôles de la palette du noyau (ok, alerte, danger) et deux mélanges. */
    private static int couleur(Etat e) {
        return switch (e) {
            case NEUF -> Palette.OK;
            case BON -> Palette.mix(Palette.OK, Palette.ALERTE, MELANGE_BON);
            case USE -> Palette.ALERTE;
            case ABIME -> Palette.mix(Palette.ALERTE, Palette.DANGER, MELANGE_ABIME);
            default -> Palette.DANGER;
        };
    }

    /** Noms des états et largeurs fixes, refaits seulement si la langue change. */
    private static void textes(Minecraft mc) {
        String l = mc.getLanguageManager().getSelected();
        if (l.equals(langue)) return;
        langue = l;
        Language lang = Language.getInstance();
        float noms = 0f;
        for (Etat e : Etat.TOUS) noms = Math.max(noms, NOMS[e.ordinal()].texte(lang.getOrDefault(e.cleTraduction())).largeur());
        if (POURCENTS[100] == null) POURCENTS[100] = new TexteHud(PoliceHud.CHIFFRE_PETIT).texte(Reparation.pct(Usure.PLEIN));
        largeurPct = POURCENTS[100].largeur();
        largeur = (float) Math.ceil(PAD + BARRE_W + ECART + largeurPct + ECART + noms + PAD);
    }

    /** Bord droit réel de la barre d'objets DayZ (HotbarOverlay.bordDroit), sinon celui de la barre de Minecraft. */
    private static float bordDroitBarre(Minecraft mc, int w) {
        if (!bordCherche) {
            bordCherche = true;
            try {
                Class<?> c = Class.forName("com.dayzhotbar.client.HotbarOverlay");
                bordBarre = MethodHandles.publicLookup().findStatic(c, "bordDroit", MethodType.methodType(float.class, int.class));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                bordBarre = null;
            }
        }
        if (bordBarre != null) {
            try {
                return (float) bordBarre.invokeExact(w);
            } catch (Throwable ex) {
                bordBarre = null;
            }
        }
        return w / 2 + 91 + (mc.options.mainHand().get() == HumanoidArm.LEFT ? 29 : 0);
    }
}

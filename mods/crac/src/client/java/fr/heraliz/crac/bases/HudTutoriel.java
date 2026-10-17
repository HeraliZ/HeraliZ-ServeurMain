package fr.heraliz.crac.bases;

import java.util.List;
import fr.heraliz.crac.build.client.BuildKeys;
import fr.heraliz.crac.client.kit.SonsUi;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.network.S2CTutoriel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Carte du tutoriel des bases (CRac 1.7.0, maquettes à valider) : une étape à la fois, petite carte de verre en haut à
 * gauche (style des écrans de clan). « TUTORIEL · ÉTAPE n/N », titre court, une à trois lignes de consigne, touches à
 * presser (nom de la touche réglée chez le joueur), barre d'avancée du parcours. Étape terminée : « ✓ Étape terminée »
 * en vert, puis la carte suivante glisse depuis la gauche. Tout vient du serveur ({@link S2CTutoriel}) ; rien n'est
 * décidé ici. Mesures en unités d'interface : la carte suit l'échelle d'interface du joueur et le texte passe à la ligne
 * (jamais plus large que {@link #LARGEUR}, ni que l'écran).
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class HudTutoriel {
    static final float X0 = 8, Y0 = 8, LARGEUR = 156, PAD = 7, LIGNE = 9;
    /** Durées des animations (ms). */
    private static final long SORTIE_MS = 200, ENTREE_MS = 320, FLASH_MS = 450, FONDU_MS = 250;

    /** Étape affichée, et celle qui s'en va (glisse vers la gauche) quand une nouvelle arrive. */
    private static S2CTutoriel actuelle, ancienne;
    private static long arrivee, depart, faitDepuis, cacheDepuis;
    private static boolean cachee = true;
    private static float barre;
    private static long derniereImage;

    private HudTutoriel() {
    }

    /** Paquet du serveur (fil du jeu). */
    public static void recevoir(S2CTutoriel p) {
        long now = System.currentTimeMillis();
        if (!p.visible()) {
            if (!cachee) {
                cachee = true;
                cacheDepuis = now;
            }
            return;
        }
        boolean nouvelle = actuelle == null || cachee || actuelle.numero() != p.numero() || !actuelle.titre().equals(p.titre());
        if (nouvelle) {
            if (actuelle != null && !cachee) {
                ancienne = actuelle;
                depart = now;
            } else {
                ancienne = null;
            }
            arrivee = now + (ancienne != null ? SORTIE_MS - 40 : 0);
            faitDepuis = p.fait() ? now : 0;
            if (cachee || actuelle == null) {
                barre = p.total() > 0 ? (p.numero() - 1) / (float) p.total() : 0;
            }
        } else if (p.fait() && !actuelle.fait()) {
            faitDepuis = now;
            SonsUi.clic(0.7F);
        }
        actuelle = p;
        cachee = false;
    }

    @SubscribeEvent
    public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        actuelle = null;
        ancienne = null;
        cachee = true;
    }

    // ---------------------------------------------------------------------------------------------- dessin

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (actuelle == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        long now = System.currentTimeMillis();
        float dt = derniereImage == 0 ? 0 : Math.min(0.1F, (now - derniereImage) / 1000F);
        derniereImage = now;
        Font font = mc.font;
        float px = 1.0F / (float) mc.getWindow().getGuiScale();
        float w = Math.min(LARGEUR, width - 2 * X0);
        if (w < 80) {
            return;
        }
        if (cachee) {
            float a = 1 - (now - cacheDepuis) / (float) FONDU_MS;
            if (a > 0) {
                carte(g, font, actuelle, X0 - 10 * (1 - a), Y0, w, a, px, now, false);
            }
            return;
        }
        // l'ancienne carte part vers la gauche en s'effaçant
        if (ancienne != null) {
            float t = (now - depart) / (float) SORTIE_MS;
            if (t >= 1) {
                ancienne = null;
            } else {
                float e = t * t;
                carte(g, font, ancienne, X0 - 14 * e, Y0, w, 1 - e, px, now, false);
            }
        }
        // barre du parcours : avance en douceur vers sa valeur
        float cible = cible(actuelle, now);
        barre += (cible - barre) * Math.min(1, dt * 6);
        if (now < arrivee) {
            return;
        }
        float t = Math.min(1, (now - arrivee) / (float) ENTREE_MS);
        float e = 1 - (1 - t) * (1 - t) * (1 - t);
        carte(g, font, actuelle, X0 - 12 * (1 - e), Y0, w, e, px, now, true);
    }

    /** Avancée du parcours : étapes faites, plus la part de l'étape en cours (lecture, compteur). */
    private static float cible(S2CTutoriel s, long now) {
        if (s.total() <= 0) {
            return 0;
        }
        float part;
        if (s.fait()) {
            part = 1;
        } else if (s.lecture() > 0) {
            part = Mth.clamp((now - arrivee) / (s.lecture() * 50F), 0, 1);
        } else if (s.objectif() > 0) {
            part = Mth.clamp(s.compte() / (float) s.objectif(), 0, 1) * 0.9F;
        } else {
            part = 0;
        }
        return Mth.clamp((s.numero() - 1 + part) / s.total(), 0, 1);
    }

    /** Une carte ; {@code o} = opacité de 0 à 1. */
    private static void carte(GuiGraphics g, Font font, S2CTutoriel s, float x, float y, float w, float o, float px, long now,
                              boolean principale) {
        if (o <= 0.02F) {
            return;
        }
        boolean fait = s.fait();
        float lx = x + PAD, larg = w - 2 * PAD - 2;
        List<FormattedCharSequence> lignes = font.split(Ui.in(s.texte(), MOYEN), (int) Math.floor(larg));
        if (lignes.size() > 4) {
            lignes = lignes.subList(0, 4);
        }
        List<FormattedCharSequence> titre = font.split(Ui.in(s.titre(), GRAS_9), (int) Math.floor(larg));
        if (titre.size() > 2) {
            titre = titre.subList(0, 2);
        }
        float hTitre = fait ? 11 : 11 * titre.size();
        float h = PAD + 8 + hTitre + 2 + lignes.size() * LIGNE + (s.touches().isEmpty() ? 0 : 4 + 10) + 5 + 1.5F + PAD - 2;
        int accent = fait ? VERT : ACCENT;

        // ombre douce, verre, contour, liseré d'accent
        degradeV(g, x, y + h, x + w, y + h + 5, a(0, 60 * o), a(0, 0));
        rect(g, x, y, x + w, y + h, a(VERRE, 200 * o));
        contour(g, x, y, w, h, px, fait ? a(VERT, 110 * o) : a(BLANC, 30 * o));
        rect(g, x + px, y + px, x + w - px, y + 2 * px, a(BLANC, 40 * o));
        rect(g, x, y, x + 1.5F, y + h, a(accent, 235 * o));
        if (fait && principale) {
            // éclat vert bref au moment où l'étape se termine
            float f = 1 - (now - faitDepuis) / (float) FLASH_MS;
            if (f > 0) {
                rect(g, x, y, x + w, y + h, a(VERT, 40 * f * o));
            }
        }

        // TUTORIEL · ÉTAPE n/N, compteur à droite
        float ly = y + PAD - 1;
        float cx = caps(g, font, Component.translatable("crac.tuto.hud.titre").getString(), MINI_GRAS, lx, ly, 0.7F,
            a(fait ? VERT : ROSE, 255 * o));
        texte(g, font, lit("·", MINI_GRAS), lx + cx + 1.5F, ly, a(ATTENUE, 200 * o));
        caps(g, font, Component.translatable("crac.tuto.hud.etape", String.valueOf(s.numero()), String.valueOf(s.total())).getString(),
            MINI_GRAS, lx + cx + 5, ly, 0.7F, a(ATTENUE, 230 * o));
        if (s.objectif() > 0) {
            String c = Math.min(s.compte(), s.objectif()) + "/" + s.objectif();
            float cw = largeur(font, c, MINI_GRAS);
            texte(g, font, lit(c, MINI_GRAS), x + w - PAD - cw, ly, a(fait ? VERT : OR, 255 * o));
        }

        // titre, ou « ✓ Étape terminée »
        float ty = y + PAD + 8;
        if (fait) {
            float r = 4;
            float ccx = lx + r, ccy = ty + 0.5F + r;
            disque(g, ccx, ccy, r, a(VERT, 60 * o));
            anneau(g, ccx, ccy, r - Math.max(px, 0.6F), r, a(VERT, 255 * o));
            coche(g, ccx - r * 0.5F, ccy - r * 0.45F, r, Math.max(px * 2, 0.9F), a(VERT, 255 * o));
            texte(g, font, Ui.tr(GRAS_9, "crac.tuto.hud.fait"), lx + 11, ty, a(VERT, 255 * o));
        } else {
            for (int i = 0; i < titre.size(); i++) {
                ligne(g, font, titre.get(i), lx, ty + i * 11, a(BLANC, 255 * o));
            }
        }

        // consigne
        float y2 = ty + hTitre + 2;
        for (int i = 0; i < lignes.size(); i++) {
            ligne(g, font, lignes.get(i), lx, y2 + i * LIGNE, fait ? a(ATTENUE, 170 * o) : a(TEXTE, 205 * o));
        }
        y2 += lignes.size() * LIGNE;

        // touches
        if (!s.touches().isEmpty()) {
            y2 += 3;
            float kx = lx;
            float fin = x + w - PAD;
            for (int i = 0; i < s.touches().size(); i++) {
                String nom = nomTouche(s.touches().get(i));
                String cle = i < s.legendes().size() ? s.legendes().get(i) : "";
                String leg = cle.isEmpty() ? "" : Component.translatable(cle).getString();
                boolean commande = nom.startsWith("/");
                float tw = commande ? largeur(font, nom, MINI_GRAS) : largeurCaps(font, nom, MINI_GRAS, 0.5F);
                float kw = Math.max(10, tw + 6);
                float lw = leg.isEmpty() ? 0 : 3 + largeur(font, leg, MINI);
                if (kx + kw + lw > fin && kx > lx) {
                    break;   // pas de seconde ligne de touches : la consigne les nomme aussi
                }
                rect(g, kx, y2, kx + kw, y2 + 10, a(BLANC, (fait ? 16 : 30) * o));
                contour(g, kx, y2, kw, 10, px, a(BLANC, (fait ? 35 : 70) * o));
                rect(g, kx + px, y2 + 10 - px, kx + kw - px, y2 + 10, a(BLANC, 50 * o));
                int coul = fait ? a(ATTENUE, 200 * o) : a(BLANC, 255 * o);
                if (commande) {
                    texte(g, font, lit(nom, MINI_GRAS), kx + (kw - tw) / 2, y2 + 1.6F, coul);
                } else {
                    caps(g, font, nom, MINI_GRAS, kx + (kw - tw) / 2, y2 + 1.6F, 0.5F, coul);
                }
                kx += kw;
                if (!leg.isEmpty()) {
                    texte(g, font, lit(leg, MINI), kx + 3, y2 + 1.6F, a(GRIS, 230 * o));
                    kx += lw;
                }
                kx += 7;
            }
            y2 += 10;
        }

        // barre du parcours
        float by = y + h - PAD + 1;
        float bx0 = lx, bx1 = x + w - PAD;
        rect(g, bx0, by, bx1, by + 1.5F, a(BLANC, 26 * o));
        float v = principale ? barre : cible(s, now);
        rect(g, bx0, by, bx0 + (bx1 - bx0) * Mth.clamp(v, 0, 1), by + 1.5F, a(accent, 230 * o));
    }

    private static void ligne(GuiGraphics g, Font font, FormattedCharSequence s, float x, float y, int argb) {
        if (argb >>> 24 < 4) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.drawString(font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Nom de la touche réglée chez le joueur (« Clic gauche », « R »...), ou la commande telle quelle. */
    static String nomTouche(String jeton) {
        if (jeton.startsWith("/")) {
            return jeton;
        }
        Minecraft mc = Minecraft.getInstance();
        KeyMapping k = switch (jeton) {
            case "attaque" -> mc.options.keyAttack;
            case "utiliser" -> mc.options.keyUse;
            case "inventaire" -> mc.options.keyInventory;
            case "tourner" -> BuildKeys.ROTATE;
            case "roue" -> BuildKeys.MENU;
            default -> null;
        };
        if (k == null) {
            return jeton;
        }
        return k.getTranslatedKeyMessage().getString();
    }
}

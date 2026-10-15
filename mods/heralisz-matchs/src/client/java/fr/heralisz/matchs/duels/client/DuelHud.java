package fr.heralisz.matchs.duels.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.Registres;
import fr.heralisz.matchs.duels.net.Vues;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;

/**
 * HUD des duels : recherche et destruction (porteur de l'ordinateur, ordinateur au sol, bombe amorcee avec minuterie et
 * bips, invites « restez accroupi », barre d'amorcage « CODE n / 3 » et de piratage avec les secondes restantes, reperes
 * A / B sur les bombes et ordinateur a l'ecran), eclair de
 * l'explosion, toasts de fin des defis (refuse, annule, expire : en haut a gauche ; les defis recus sont dans
 * {@link NotifDefis}, en haut a droite). DM Sans, verre, aucun element vanilla. Les textes sont prepares a la
 * reception (pas d'allocation par image hors chiffres qui changent).
 * <p>Reperes A / B (16/10, Matchs 0.10.0 : le badge a pointe et distance faisait « bulle de texte ») : petit carre de
 * {@link #TAILLE} px d'interface, contour fin, fond presque transparent, lettre fine, couleur de l'equipe attenuee ; ni
 * pointe, ni distance. Toujours legers ({@link #OPACITE_DISCRETE}), un peu plus pendant le choix de classe, les
 * {@link #REPERES_DEBUT_MS} premieres secondes de chaque manche et Tab enfonce ({@link #OPACITE_FORTE}) ; encore plus pales
 * pres du viseur (jamais devant la cible) ; effaces quand le joueur est sur le site. Meme carre avec « ! » pour la bombe
 * amorcee et l'ordinateur au sol. Formes lisses tirees de {@code textures/gui/duel_repere.png} (outils/reperes/reperes_sites.py,
 * jamais retouchee a la main). Rien pendant une killcam.
 */
final class DuelHud {
    private DuelHud() {
    }

    /** Reperes A / B bien visibles pendant les premieres secondes de chaque manche (ms). */
    static final long REPERES_DEBUT_MS = 8000;
    /** Passage de « bien visibles » a « discrets » (ms). */
    static final long REPERES_FONDU_MS = 1200;
    /** Opacite d'un repere : en temps normal, et bien visible (debut de manche, Tab). */
    private static final float OPACITE_DISCRETE = 0.40f, OPACITE_FORTE = 0.62f, OPACITE_ALERTE = 0.55f;
    /** Pres du viseur (pixels d'interface depuis le centre) : opacite x VISEUR_RESTE en dessous de MIN, entiere au-dela de MAX. */
    private static final float VISEUR_MIN = 10f, VISEUR_MAX = 48f, VISEUR_RESTE = 0.22f;
    /** Couleur de l'equipe melee de ce gris clair (attenuee). */
    private static final int GRIS = 0xC8CCD2;
    private static final float ATTENUE = 0.45f;
    /** Pres du site (blocs, a plat) : efface en dessous de MIN, entier au-dela de MAX. */
    private static final double SITE_MIN = 8, SITE_MAX = 14;
    /** Reperes bien visibles jusqu'a cette heure (Util.getMillis) ; MAX_VALUE pendant le choix de classe. */
    private static long reperesFortsJusque;

    // ------------------------------------------------------------------ etat R&D
    @Nullable
    private static Vues.RdVue rd;
    private static long rdRecuMs;
    private static long ticksDepuis;
    private static Component ligneHaut = Component.empty(), ligneAction = Component.empty(), ligneInvite = Component.empty();
    private static int couleurHaut = Ui.TEXT;
    private static boolean aLigneHaut, aInvite;
    private static int derniereSeconde = -1;
    private static Component texteMinuterie = Component.empty();
    private static int prochainBip;
    /** Formes du repere (planche de 256 x 32, cases de 32 x 32 : contour, fond, A, B, « ! » ; filtrage lisse). */
    private static final ResourceLocation REPERE = HeraliszMatchs.id("textures/gui/duel_repere.png");
    private static final int CASE = 32, CASE_CONTOUR = 0, CASE_FOND = 1, CASE_A = 2, CASE_B = 3, CASE_ALERTE = 4;
    /** Cote du carre, en pixels d'interface. */
    private static final float TAILLE = 10f;
    /** Couleur du piratage (ecran de l'ordinateur, barre du HUD). */
    private static final int PIRATE = 0x46DCFF;
    /** Libelle de la barre d'action garde tant qu'il ne change pas (code n / 3, dixiemes de seconde du piratage). */
    private static int cleAction = Integer.MIN_VALUE;

    // ------------------------------------------------------------------ toasts, eclair
    private record Toast(Component titre, Component texte, long debutMs, boolean defi) {
    }

    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static long flashMs = -1;

    // ------------------------------------------------------------------ projection monde -> ecran
    private static final Matrix4f PROJ = new Matrix4f(), VUE = new Matrix4f();
    private static final Vector4f V = new Vector4f();
    private static double camX, camY, camZ;
    private static boolean matricesOk;
    private static float sx, sy;

    static void rd(@Nullable Vues.RdVue v) {
        rd = v;
        rdRecuMs = Util.getMillis();
        ticksDepuis = 0;
        derniereSeconde = -1;
        prochainBip = 0;
        if (v == null) {
            reperesFortsJusque = 0;
            return;
        }
        // textes prepares une fois
        couleurHaut = Ui.TEXT;
        ligneHaut = Component.empty();
        if (v.etat() == Vues.CHARGE_POSEE) {
            ligneHaut = Ui.tr(Ui.BOLD, "duel.hud.posee", v.site());
            couleurHaut = Ui.DANGER;
        } else if (v.attaque() && v.etat() == Vues.CHARGE_PORTEE) {
            ligneHaut = v.moiPorteur() ? Ui.tr(Ui.BOLD, "duel.hud.vous_portez") : Ui.tr(Ui.BOLD, "duel.hud.porte", v.porteur());
            couleurHaut = Ui.AMBRE;
        } else if (v.attaque() && v.etat() == Vues.CHARGE_AU_SOL) {
            ligneHaut = Ui.tr(Ui.BOLD, "duel.hud.au_sol");
            couleurHaut = Ui.AMBRE;
        } else if (v.etat() == Vues.CHARGE_DESAMORCEE) {
            ligneHaut = Ui.tr(Ui.BOLD, "duel.hud.desamorcee");
            couleurHaut = Ui.OK;
        }
        ligneAction = Component.empty();
        cleAction = Integer.MIN_VALUE;
        ligneInvite = v.invite().isEmpty() ? Component.empty() : Ui.tr(Ui.FONT, "duel.hud.invite_" + v.invite());
        aLigneHaut = !ligneHaut.getString().isEmpty();
        aInvite = !v.invite().isEmpty();
    }

    /**
     * Choix de classe recu (debut de manche) : {@code secondes} &gt; 0 = choix ouvert, reperes bien visibles tant qu'il
     * dure ; 0 = debut du combat, encore {@link #REPERES_DEBUT_MS}, puis ils deviennent discrets.
     */
    static void choix(int secondes) {
        reperesFortsJusque = secondes > 0 ? Long.MAX_VALUE : Util.getMillis() + REPERES_DEBUT_MS;
    }

    /** 1 = reperes bien visibles, 0 = discrets (fondu entre les deux). */
    private static float forceReperes(long now) {
        if (now <= reperesFortsJusque) return 1f;
        long t = now - reperesFortsJusque;
        return t >= REPERES_FONDU_MS ? 0f : 1f - t / (float) REPERES_FONDU_MS;
    }

    /** 0 en dessous de a, 1 au-dela de b, courbe douce entre les deux. */
    private static float lisse(double a, double b, double v) {
        if (v <= a) return 0f;
        if (v >= b) return 1f;
        float t = (float) ((v - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    static void toast(Component titre, Component texte, boolean defi) {
        while (TOASTS.size() >= 3) TOASTS.pollFirst();
        TOASTS.addLast(new Toast(Ui.in(titre, Ui.SMALL), Ui.in(texte, Ui.FONT), Util.getMillis(), defi));
    }

    static void flash() {
        flashMs = Util.getMillis();
    }

    static void vider() {
        rd(null);
        TOASTS.clear();
        flashMs = -1;
    }

    /** Vue R&D courante (banc d'essai), ou null. */
    @Nullable
    static Vues.RdVue vue() {
        return rd;
    }

    /** Resume pour le journal du banc. */
    static String etat() {
        return rd == null ? "aucun" : "etat " + rd.etat() + " attaque " + rd.attaque() + " action " + rd.action() + " invite '" + rd.invite() + "'";
    }

    // ------------------------------------------------------------------ tick : bips de la charge
    static void tick() {
        ticksDepuis++;
        Minecraft mc = Minecraft.getInstance();
        if (rd == null || rd.etat() != Vues.CHARGE_POSEE || mc.level == null) return;
        int reste = (int) (rd.resteTicks() - ticksDepuis);
        if (reste <= 0) return;
        if (--prochainBip > 0) return;
        prochainBip = reste > 400 ? 20 : reste > 200 ? 10 : reste > 100 ? 6 : 3;
        float hauteur = reste > 200 ? 1f : reste > 100 ? 1.12f : 1.25f;
        mc.level.playLocalSound(rd.x(), rd.y(), rd.z(), Registres.BIP.get(), SoundSource.PLAYERS, 2.2f, hauteur, false);
    }

    // ------------------------------------------------------------------ matrices
    static void matrices(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        PROJ.set(e.getProjectionMatrix());
        VUE.set(e.getPoseStack().last().pose());
        Vec3 c = e.getCamera().getPosition();
        camX = c.x;
        camY = c.y;
        camZ = c.z;
        matricesOk = true;
    }

    /** Projette un point du monde ; resultat dans sx, sy (coordonnees d'interface). Faux si derriere la camera. */
    private static boolean projeter(double x, double y, double z, int w, int h) {
        if (!matricesOk) return false;
        V.set((float) (x - camX), (float) (y - camY), (float) (z - camZ), 1f);
        VUE.transform(V);
        PROJ.transform(V);
        if (V.w <= 0.05f) return false;
        float nx = V.x / V.w, ny = V.y / V.w;
        if (nx < -1.2f || nx > 1.2f || ny < -1.2f || ny > 1.2f) return false;
        sx = (nx * 0.5f + 0.5f) * w;
        sy = (1f - (ny * 0.5f + 0.5f)) * h;
        return true;
    }

    // ------------------------------------------------------------------ rendu
    static void rendre(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (mc.screen instanceof EcranDuels) return;
        // pendant une killcam, rien par-dessus le rejeu (ce HUD ne passe pas par un calque Forge, que la killcam masque)
        if (DuelsClient.killcamActive()) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        long now = Util.getMillis();
        if (rd != null && rd.etat() != Vues.CHARGE_AUCUNE) rendreRd(g, font, w, h, now);
        rendreToasts(g, font, now);
        if (flashMs > 0) {
            long t = now - flashMs;
            if (t > 900) flashMs = -1;
            else {
                float a = t < 80 ? 1f : 1f - (t - 80) / 820f;
                g.fill(0, 0, w, h, Ui.argb(0xFFF4E0, (int) (230 * a * a)));
            }
        }
    }

    private static void rendreRd(GuiGraphics g, Font font, int w, int h, long now) {
        Vues.RdVue v = rd;
        if (v == null) return;
        float cx = w / 2f;
        // sous le bandeau du moteur (titre, pastilles, temps et score : jusqu'a y 64 environ)
        int y = 72;
        // ligne d'etat (masquee pendant une grande annonce du moteur, dessinee dans la meme zone de l'ecran)
        boolean annonce = !fr.heralisz.matchs.moteur.client.MoteurClient.annonces().isEmpty();
        if (annonce) {
            // rien : l'annonce (« charge posee », « manche gagnee »...) a la priorite
        } else if (v.etat() == Vues.CHARGE_POSEE) {
            int resteMs = (int) Math.max(0, v.resteTicks() * 50L - (now - rdRecuMs));
            int s = (resteMs + 999) / 1000;
            if (s != derniereSeconde) {
                derniereSeconde = s;
                texteMinuterie = Ui.lit(String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60), Ui.BOLD);
            }
            float pulse = s <= 10 ? 0.55f + 0.45f * (float) Math.abs(Math.sin(now / (s <= 5 ? 90.0 : 160.0))) : 1f;
            int lw = font.width(ligneHaut) + 20;
            Ui.glass(g, (int) (cx - Math.max(lw, 90) / 2f), y - 4, Math.max(lw, 90), 40, 170);
            Ui.centered(g, font, ligneHaut, cx, y + 2, Ui.argb(Ui.DANGER, 255));
            g.pose().pushPose();
            float sc = 1.8f;
            float tw = font.width(texteMinuterie) * sc;
            g.pose().translate(cx - tw / 2f, y + 14, 0);
            g.pose().scale(sc, sc, 1);
            g.drawString(font, texteMinuterie, 0, 0, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.25f), (int) (255 * pulse)), false);
            g.pose().popPose();
        } else if (aLigneHaut) {
            int lw = font.width(ligneHaut) + 20;
            Ui.soft(g, (int) (cx - lw / 2f), y - 3, lw, 14, Ui.argb(couleurHaut, 30));
            Ui.softOutline(g, (int) (cx - lw / 2f) + 1, y - 2, lw - 2, 12, Ui.argb(couleurHaut, 90));
            Ui.centered(g, font, ligneHaut, cx, y, Ui.argb(Ui.mix(couleurHaut, 0xFFFFFF, 0.3f), 255));
        }
        // reperes a l'ecran
        reperes(g, w, h, v, now);
        // barre d'action (amorcage : code n / 3 ; piratage : secondes restantes)
        if (v.action() != Vues.ACTION_AUCUNE) {
            float fait = Math.min(v.totalTicks(), v.faitTicks() + (now - rdRecuMs) / 50f);
            float r = fait / v.totalTicks();
            Component lib = libelleAction(v.action(), r, (v.totalTicks() - fait) / 20f);
            int bw = 180, bx = (int) (cx - bw / 2f), by = h / 2 + 26;
            Ui.glass(g, bx - 10, by - 16, bw + 20, 30, 190);
            Ui.centered(g, font, lib, cx, by - 10, Ui.argb(Ui.TEXT, 255));
            int c = v.action() == Vues.ACTION_POSE ? Ui.AMBRE : PIRATE;
            Ui.bar(g, bx, by + 2, bw, 4, r, c);
        } else if (aInvite) {
            int iw = font.width(ligneInvite) + 24, iy = h - 78;
            Ui.glass(g, (int) (cx - iw / 2f), iy - 5, iw, 18, 170);
            Ui.centered(g, font, ligneInvite, cx, iy, Ui.argb(Ui.TEXT, 255));
        }
    }

    /**
     * Libelle de la barre d'action : « AMORCAGE · CODE n / 3 » (un code par tiers des 5 s) ou « PIRATAGE… 3,3 s »
     * (secondes restantes, virgule sauf en anglais) ; recree seulement quand il change.
     */
    private static Component libelleAction(int action, float r, float resteS) {
        int cle = action == Vues.ACTION_POSE ? 1000 + Math.min(3, 1 + (int) (r * 3f)) : 2000 + Math.max(0, Math.round(resteS * 10f));
        if (cle != cleAction) {
            cleAction = cle;
            if (action == Vues.ACTION_POSE) {
                ligneAction = Ui.tr(Ui.SMALL, "duel.hud.action_amorcage", String.valueOf(cle - 1000));
            } else {
                String t = String.format(Locale.ROOT, "%.1f", (cle - 2000) / 10f);
                String langue = Minecraft.getInstance().getLanguageManager().getSelected();
                if (langue == null || !langue.startsWith("en")) t = t.replace('.', ',');
                ligneAction = Ui.tr(Ui.SMALL, "duel.hud.action_piratage", t);
            }
        }
        return ligneAction;
    }

    private static void reperes(GuiGraphics g, int w, int h, Vues.RdVue v, long now) {
        double[] s = v.sites();
        boolean sitesConnus = s.length >= 6 && (s[0] != 0 || s[2] != 0);
        if (sitesConnus && v.etat() != Vues.CHARGE_POSEE) {
            Minecraft mc = Minecraft.getInstance();
            // Tab (liste des joueurs) enfonce : on regarde ou sont les sites, reperes un peu plus visibles
            float force = mc.options.keyPlayerList.isDown() ? 1f : forceReperes(now);
            int c = v.attaque() ? Ui.AMBRE : Ui.ACCENT_NEUTRE;
            // au-dessus de la bombe (1,75 bloc de haut, gyrophare compris)
            repere(g, w, h, s[0], s[1] + 1.9, s[2], CASE_A, c, force);
            repere(g, w, h, s[3], s[4] + 1.9, s[5], CASE_B, c, force);
        }
        boolean chargeVisible = v.etat() == Vues.CHARGE_POSEE || (v.attaque() && v.etat() == Vues.CHARGE_AU_SOL);
        if (chargeVisible && projeter(v.x(), v.y() + 0.9, v.z(), w, h)) {
            int c = v.etat() == Vues.CHARGE_POSEE ? Ui.DANGER : Ui.AMBRE;
            float pulse = 0.85f + 0.15f * (float) Math.abs(Math.sin(now / 260.0));
            carre(g, sx, sy, CASE_ALERTE, c, OPACITE_ALERTE * pulse * pres(sx, sy, w, h));
        }
    }

    /** Facteur d'opacite pres du viseur (centre de l'ecran) : VISEUR_RESTE au centre, 1 au-dela de VISEUR_MAX. */
    private static float pres(float x, float y, int w, int h) {
        double ex = x - w / 2.0, ey = y - h / 2.0;
        return VISEUR_RESTE + (1f - VISEUR_RESTE) * lisse(VISEUR_MIN, VISEUR_MAX, Math.sqrt(ex * ex + ey * ey));
    }

    /**
     * Un repere de site : {@code force} 1 = un peu plus visible (debut de manche, Tab), 0 = discret. Plus pale pres du
     * viseur (jamais devant un adversaire qu'on vise), efface quand le joueur est sur le site.
     */
    private static void repere(GuiGraphics g, int w, int h, double x, double y, double z, int caseLettre, int couleur, float force) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !projeter(x, y, z, w, h)) return;
        double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
        float loin = lisse(SITE_MIN, SITE_MAX, Math.sqrt(dx * dx + dz * dz));
        float a = (OPACITE_DISCRETE + (OPACITE_FORTE - OPACITE_DISCRETE) * force) * loin * pres(sx, sy, w, h);
        if (a < 0.03f) return;
        carre(g, sx, sy, caseLettre, couleur, a);
    }

    /**
     * Petit carre centre sur (px, py) : fond sombre presque transparent, contour fin et lettre de la couleur attenuee
     * {@code couleur}. {@code a} : opacite (0 a 1).
     */
    private static void carre(GuiGraphics g, float px, float py, int caseLettre, int couleur, float a) {
        int c = Ui.mix(couleur, GRIS, ATTENUE);
        float e = TAILLE / CASE;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(e, e, 1f);
        teinte(0x0D0F14, 0.18f * a);
        g.blit(REPERE, -CASE / 2, -CASE / 2, CASE_FOND * CASE, 0f, CASE, CASE, 256, 32);
        teinte(c, a);
        g.blit(REPERE, -CASE / 2, -CASE / 2, CASE_CONTOUR * CASE, 0f, CASE, CASE, 256, 32);
        teinte(Ui.mix(c, 0xFFFFFF, 0.35f), Math.min(1f, a * 1.1f));
        g.blit(REPERE, -CASE / 2, -CASE / 2, caseLettre * CASE, 0f, CASE, CASE, 256, 32);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private static void teinte(int rgb, float a) {
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, Math.max(0f, Math.min(1f, a)));
    }

    private static void rendreToasts(GuiGraphics g, Font font, long now) {
        int y = 8;
        Iterator<Toast> it = TOASTS.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            long age = now - t.debutMs();
            long vie = t.defi() ? 7000 : 4000;
            if (age > vie) {
                it.remove();
                continue;
            }
            float a = age < 250 ? age / 250f : age > vie - 500 ? (vie - age) / 500f : 1f;
            if (a < 0.03f) continue;
            int x = 8 - (int) ((1 - Math.min(1, age / 250f)) * 12);
            int tw = Math.max(150, Math.max(font.width(t.texte()), font.width(t.titre())) + 26);
            Ui.glass(g, x, y, tw, 30, (int) (200 * a));
            g.fill(x + 1, y + 2, x + 3, y + 28, Ui.argb(t.defi() ? Ui.OR : Ui.ACCENT_NEUTRE, (int) (230 * a)));
            int al = Math.max(4, (int) (255 * a));
            g.drawString(font, t.titre(), x + 10, y + 5, Ui.argb(Ui.MUTED, al), false);
            g.drawString(font, t.texte(), x + 10, y + 16, Ui.argb(Ui.TEXT, al), false);
            y += 34;
        }
    }

    /** Ligne mise en forme pour les ecrans : « 2 — 1 » etc. (non utilise par image). */
    static MutableComponent ligne(String s) {
        return Ui.lit(s, Ui.FONT);
    }
}

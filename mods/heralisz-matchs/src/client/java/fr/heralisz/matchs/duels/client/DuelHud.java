package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.Registres;
import fr.heralisz.matchs.duels.net.Vues;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * HUD des duels : recherche et destruction (porteur de la charge, charge au sol, charge posee avec minuterie et bips,
 * invite « maintenez accroupi », barre de pose / desamorcage, reperes A / B et charge a l'ecran), eclair de
 * l'explosion, toasts de fin des defis (refuse, annule, expire : en haut a gauche ; les defis recus sont dans
 * {@link NotifDefis}, en haut a droite). DM Sans, verre, aucun element vanilla. Les textes sont prepares a la
 * reception (pas d'allocation par image hors chiffres qui changent).
 */
final class DuelHud {
    private DuelHud() {
    }

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
    private static final Component LETTRE_A = Ui.lit("A", Ui.BOLD), LETTRE_B = Ui.lit("B", Ui.BOLD);

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
        if (v == null) return;
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
        ligneAction = v.action() == Vues.ACTION_POSE ? Ui.tr(Ui.SMALL, "duel.hud.action_pose")
                : v.action() == Vues.ACTION_DESAMORCAGE ? Ui.tr(Ui.SMALL, "duel.hud.action_desamorcage") : Component.empty();
        ligneInvite = v.invite().isEmpty() ? Component.empty() : Ui.tr(Ui.FONT, "duel.hud.invite_" + v.invite());
        aLigneHaut = !ligneHaut.getString().isEmpty();
        aInvite = !v.invite().isEmpty();
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
        reperes(g, font, w, h, v, now);
        // barre d'action (pose / desamorcage)
        if (v.action() != Vues.ACTION_AUCUNE) {
            float fait = Math.min(v.totalTicks(), v.faitTicks() + (now - rdRecuMs) / 50f);
            float r = fait / v.totalTicks();
            int bw = 180, bx = (int) (cx - bw / 2f), by = h / 2 + 26;
            Ui.glass(g, bx - 10, by - 16, bw + 20, 30, 190);
            Ui.centered(g, font, ligneAction, cx, by - 10, Ui.argb(Ui.TEXT, 255));
            int c = v.action() == Vues.ACTION_POSE ? Ui.AMBRE : Ui.OK;
            Ui.bar(g, bx, by + 2, bw, 4, r, c);
        } else if (aInvite) {
            int iw = font.width(ligneInvite) + 24, iy = h - 78;
            Ui.glass(g, (int) (cx - iw / 2f), iy - 5, iw, 18, 170);
            Ui.centered(g, font, ligneInvite, cx, iy, Ui.argb(Ui.TEXT, 255));
        }
    }

    private static void reperes(GuiGraphics g, Font font, int w, int h, Vues.RdVue v, long now) {
        double[] s = v.sites();
        boolean sitesConnus = s.length >= 6 && (s[0] != 0 || s[2] != 0);
        if (sitesConnus && v.etat() != Vues.CHARGE_POSEE) {
            repere(g, font, w, h, s[0], s[1] + 1.2, s[2], LETTRE_A, v.attaque() ? Ui.AMBRE : Ui.ACCENT_NEUTRE);
            repere(g, font, w, h, s[3], s[4] + 1.2, s[5], LETTRE_B, v.attaque() ? Ui.AMBRE : Ui.ACCENT_NEUTRE);
        }
        boolean chargeVisible = v.etat() == Vues.CHARGE_POSEE || (v.attaque() && v.etat() == Vues.CHARGE_AU_SOL);
        if (chargeVisible && projeter(v.x(), v.y() + 0.6, v.z(), w, h)) {
            int c = v.etat() == Vues.CHARGE_POSEE ? Ui.DANGER : Ui.AMBRE;
            float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(now / 220.0));
            losange(g, (int) sx, (int) sy, 5, Ui.argb(c, (int) (230 * pulse)));
            losange(g, (int) sx, (int) sy, 3, Ui.argb(0x0D0F14, 220));
        }
    }

    private static void repere(GuiGraphics g, Font font, int w, int h, double x, double y, double z, Component lettre, int couleur) {
        if (!projeter(x, y, z, w, h)) return;
        int ix = (int) sx, iy = (int) sy;
        Ui.soft(g, ix - 6, iy - 6, 13, 13, Ui.argb(0x0D0F14, 170));
        Ui.softOutline(g, ix - 5, iy - 5, 11, 11, Ui.argb(couleur, 200));
        Ui.centered(g, font, lettre, ix + 0.5f, iy - 3.5f, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.4f), 255));
    }

    private static void losange(GuiGraphics g, int x, int y, int r, int argb) {
        for (int i = -r; i <= r; i++) {
            int l = r - Math.abs(i);
            g.fill(x - l, y + i, x + l + 1, y + i + 1, argb);
        }
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

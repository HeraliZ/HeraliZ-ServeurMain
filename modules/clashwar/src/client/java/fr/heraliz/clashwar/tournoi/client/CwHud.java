package fr.heraliz.clashwar.tournoi.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.clashwar.tournoi.net.CwPaquets;
import fr.heraliz.noyau.client.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * HUD du ClashWar (echelle du joueur) : toasts en haut a droite sous le fil des eliminations (annonces, inscription,
 * rappels ; 3 au plus, 5 s, fondus) et paroi de la zone de mort subite dans le monde (cylindre rouge degrade autour du
 * centre, rayon interpole cote client). Textes prepares a la reception, tableaux fixes : aucune allocation par image.
 */
public final class CwHud {
    private CwHud() {
    }

    private static final int MAX = 3, LARGEUR = 176, HAUTEUR = 34;
    private static final long DUREE_MS = 5000, FONDU_MS = 300;
    private static final Component[] TITRES = new Component[MAX], TEXTES = new Component[MAX];
    private static final int[] COULEURS = new int[MAX];
    private static final long[] DEBUTS = new long[MAX];
    private static int nombre;

    // zone de mort subite
    private static boolean zoneActive;
    private static double zoneX, zoneZ, zoneY;
    private static float r0, r1;
    private static int duree;
    private static long debutZone;
    private static final int SEGMENTS = 72;
    private static final float[] COS = new float[SEGMENTS + 1], SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = i * Math.PI * 2 / SEGMENTS;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    /** Nouveau toast (le plus ancien est pousse dehors si la pile est pleine). */
    public static void toast(Component titre, Component texte, int couleur) {
        Font font = Minecraft.getInstance().font;
        if (nombre == MAX) {
            for (int i = 1; i < MAX; i++) {
                TITRES[i - 1] = TITRES[i];
                TEXTES[i - 1] = TEXTES[i];
                COULEURS[i - 1] = COULEURS[i];
                DEBUTS[i - 1] = DEBUTS[i];
            }
            nombre--;
        }
        TITRES[nombre] = Ui.lit(titre.getString().toUpperCase(Locale.ROOT), Ui.BOLD);
        TEXTES[nombre] = Ui.lit(Ui.ellipsis(font, texte.getString(), Ui.FONT, LARGEUR - 22), Ui.FONT);
        COULEURS[nombre] = couleur & 0xFFFFFF;
        DEBUTS[nombre] = Util.getMillis();
        nombre++;
    }

    static void zone(CwPaquets.ZoneS2C p) {
        zoneActive = p.actif();
        zoneX = p.cx();
        zoneZ = p.cz();
        zoneY = p.y();
        r0 = p.r0();
        r1 = p.r1();
        duree = Math.max(1, p.dureeTicks());
        debutZone = Util.getMillis();
    }

    static void vider() {
        nombre = 0;
        zoneActive = false;
    }

    /**
     * Bas (y, echelle du joueur) de la pile de toasts du ClashWar en haut a droite, 0 si elle est vide : les autres HUD
     * de ce coin (defis de duel) se placent dessous. Sans allocation.
     */
    public static int basToasts(long now) {
        int n = 0;
        for (int i = 0; i < nombre; i++) if (now - DEBUTS[i] < DUREE_MS) n++;
        return n == 0 ? 0 : 74 + n * (HAUTEUR + 4) - 4;
    }

    // ------------------------------------------------------------------ toasts
    static void rendreGui(RenderGuiEvent.Post e) {
        if (nombre == 0) return;
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        // purge des toasts finis (decalage sur place)
        int k = 0;
        for (int i = 0; i < nombre; i++) {
            if (now - DEBUTS[i] < DUREE_MS) {
                TITRES[k] = TITRES[i];
                TEXTES[k] = TEXTES[i];
                COULEURS[k] = COULEURS[i];
                DEBUTS[k] = DEBUTS[i];
                k++;
            }
        }
        nombre = k;
        // nos ecrans masquent tout le HUD (charte) : les toasts ne transparaissent pas sous leur panneau
        if (mc.options.hideGui || nombre == 0 || mc.screen instanceof EcranInscription || mc.screen instanceof EcranTableau) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int x = g.guiWidth() - LARGEUR - 8, y = 74;
        for (int i = 0; i < nombre; i++) {
            long age = now - DEBUTS[i];
            float a = Math.min(1f, Math.min(age / (float) FONDU_MS, (DUREE_MS - age) / (float) FONDU_MS));
            if (a <= 0.03f) continue;
            int dx = Math.round((1 - a) * 14);
            int c = COULEURS[i];
            g.pose().pushPose();
            g.pose().translate(x + dx, y, 0);
            Ui.soft(g, 0, 0, LARGEUR, HAUTEUR, Ui.argb(Ui.VERRE, (int) (215 * a)));
            Ui.softOutline(g, 1, 1, LARGEUR - 2, HAUTEUR - 2, Ui.argb(0xFFFFFF, (int) (24 * a)));
            g.fill(0, 2, 2, HAUTEUR - 2, Ui.argb(c, (int) (240 * a)));
            int ta = Math.max(4, (int) (255 * a));
            g.drawString(font, TITRES[i], 10, 6, Ui.argb(Ui.mix(c, 0xFFFFFF, 0.45f), ta), false);
            g.drawString(font, TEXTES[i], 10, 19, Ui.argb(Ui.TEXT, ta), false);
            g.pose().popPose();
            y += HAUTEUR + 4;
        }
    }

    // ------------------------------------------------------------------ zone
    static void rendreMonde(RenderLevelStageEvent e) {
        if (!zoneActive || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = Math.min(1f, (Util.getMillis() - debutZone) / (duree * 50f));
        float r = r0 + (r1 - r0) * t;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(zoneX - cam.x, zoneY - cam.y, zoneZ - cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float bas = -6f, haut = 30f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(Util.getMillis() / 260.0);
        int aBas = (int) (110 * pulse);
        for (int i = 0; i < SEGMENTS; i++) {
            float xa = COS[i] * r, za = SIN[i] * r, xb = COS[i + 1] * r, zb = SIN[i + 1] * r;
            bb.vertex(m, xa, bas, za).color(255, 70, 80, aBas).endVertex();
            bb.vertex(m, xb, bas, zb).color(255, 70, 80, aBas).endVertex();
            bb.vertex(m, xb, haut, zb).color(255, 120, 90, 0).endVertex();
            bb.vertex(m, xa, haut, za).color(255, 120, 90, 0).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }
}

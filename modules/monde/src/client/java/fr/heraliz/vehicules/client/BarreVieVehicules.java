package fr.heraliz.vehicules.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.ywzj.vehicle.entity.misc.VehiclePart;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * Petite barre de vie au-dessus des véhicules (27/11, demande du propriétaire : « une barre de vie légère au-dessus des
 * véhicules », après le retrait du nom et de la barre d'ywzj, {@code showVehicleInfoDistance = 0}). Seulement pour un
 * véhicule abîmé, ni épave ni débris, pas celui où l'on est, en vue directe, à {@link #DISTANCE_MAX} blocs au plus ;
 * pleine jusqu'à {@link #DISTANCE_NETTE}, puis s'efface. Barre fine (30 x 3 unités d'interface), fond sombre, verte,
 * ambre sous 50 %, rouge sous 25 % ; ni nom ni chiffre. Dessinée à l'écran (nette), placée par la projection de la caméra
 * comme les chiffres des cibles d'essai ({@code ChiffresCible} de l'Armurerie). PV lus dans les données synchronisées
 * d'ywzj ({@code getHealth}, {@code getMaxHealth}, PV réduits par {@code PointsDeVie} compris) : rien du serveur.
 * Liste et vue directe refaites à chaque tick (au plus {@link #MAX} véhicules), projection à chaque image ; rien n'est
 * alloué par image.
 */
public final class BarreVieVehicules {
    static final double DISTANCE_MAX = 24.0, DISTANCE_NETTE = 16.0, AU_DESSUS = 0.45;
    static final float LARGEUR = 30f, HAUTEUR = 3f;
    private static final int MAX = 12;

    private static final class Barre {
        AbstractVehicle v;
        float vie, alpha, sx, sy;
        boolean visible;
    }

    private static final Barre[] BARRES = new Barre[MAX];
    private static int n;
    private static final Matrix4f VUE = new Matrix4f();
    private static final Vector4f POINT = new Vector4f();

    static {
        for (int i = 0; i < MAX; i++) BARRES[i] = new Barre();
    }

    private BarreVieVehicules() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(BarreVieVehicules::tick);
        MinecraftForge.EVENT_BUS.addListener(BarreVieVehicules::placer);
        MinecraftForge.EVENT_BUS.addListener(BarreVieVehicules::dessiner);
    }

    /** Chaque tick : les véhicules abîmés proches, en vue directe (un lancer de rayon par véhicule). */
    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        n = 0;
        if (mc.level == null || mc.player == null) return;
        Vec3 oeil = mc.player.getEyePosition();
        double max2 = DISTANCE_MAX * DISTANCE_MAX;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (n >= MAX) break;
            if (!(en instanceof AbstractVehicle v) || v instanceof VehiclePart || v.isDestroyed() || v.isRemoved()) continue;
            if (mc.player.getVehicle() == v) continue;
            float maxi = v.getMaxHealth();
            if (maxi <= 0f || v.getHealth() >= maxi - 0.5f) continue;
            double d2 = v.distanceToSqr(oeil);
            if (d2 > max2) continue;
            Vec3 haut = new Vec3(v.getX(), v.getBoundingBox().maxY + AU_DESSUS, v.getZ());
            if (mc.level.clip(new ClipContext(oeil, haut, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType()
                    == HitResult.Type.BLOCK) continue;
            Barre b = BARRES[n++];
            b.v = v;
            b.vie = Mth.clamp(v.getHealth() / maxi, 0f, 1f);
            double d = Math.sqrt(d2);
            b.alpha = d <= DISTANCE_NETTE ? 1f : (float) Mth.clamp(1.0 - (d - DISTANCE_NETTE) / (DISTANCE_MAX - DISTANCE_NETTE), 0.0, 1.0);
        }
    }

    /** Pendant le rendu du monde : place à l'écran (unités d'interface) de chaque barre, au-dessus de la boîte du véhicule. */
    private static void placer(RenderLevelStageEvent e) {
        if (n == 0 || e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick();
        VUE.set(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose());
        Window w = Minecraft.getInstance().getWindow();
        float gw = w.getGuiScaledWidth(), gh = w.getGuiScaledHeight();
        for (int i = 0; i < n; i++) {
            Barre b = BARRES[i];
            AbstractVehicle v = b.v;
            double x = Mth.lerp(pt, v.xo, v.getX()), z = Mth.lerp(pt, v.zo, v.getZ());
            double y = Mth.lerp(pt, v.yo, v.getY()) + v.getBbHeight() + AU_DESSUS;
            POINT.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1F);
            VUE.transform(POINT);
            b.visible = POINT.w > 0.05F;
            if (!b.visible) continue;
            b.sx = (POINT.x / POINT.w * 0.5F + 0.5F) * gw;
            b.sy = (0.5F - POINT.y / POINT.w * 0.5F) * gh;
        }
    }

    /** Avec le HUD : fond sombre bordé, puis la vie de sa couleur. */
    private static void dessiner(RenderGuiEvent.Post e) {
        if (n == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        float px = Formes.pixel();
        for (int i = 0; i < n; i++) {
            Barre b = BARRES[i];
            if (!b.visible || b.alpha <= 0f) continue;
            float x0 = Formes.caler(b.sx - LARGEUR / 2f), y0 = Formes.caler(b.sy - HAUTEUR / 2f);
            float x1 = x0 + LARGEUR, y1 = y0 + HAUTEUR;
            int couleur = b.vie < 0.25f ? Palette.DANGER : b.vie < 0.5f ? Palette.ALERTE : Palette.OK;
            Formes.rect(vc, m, x0 - px, y0 - px, x1 + px, y1 + px, Palette.argb(0x000000, 0.6f * b.alpha));
            Formes.rect(vc, m, x0, y0, x0 + LARGEUR * b.vie, y1, Palette.argb(couleur, 0.95f * b.alpha));
        }
        g.flush();
    }
}

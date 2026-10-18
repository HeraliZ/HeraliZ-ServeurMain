package fr.heraliz.crac.bases;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import fr.heraliz.crac.build.client.render.GhostMesh;
import fr.heraliz.crac.build.logic.Variants;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.network.S2CTuto;
import fr.heraliz.crac.network.S2CTutoFin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Le tutoriel des bases chez le joueur (HeraliZ, CRac 1.8.0), d'après ce que le serveur envoie ({@link S2CTuto}) :
 * <ul>
 *   <li>les pièces à poser, en fantômes verts qui clignotent à leur place exacte (la seule où le serveur les accepte) ;
 *   à l'étape du bois, les pièces encore en brindilles redessinées en orange par-dessus, qui clignotent ;</li>
 *   <li>le Claim Block et la caisse : leur place entourée d'un cadre vert qui clignote ;</li>
 *   <li>l'objet à prendre en main, surligné dans la barre d'objets DayZ ({@code HotbarOverlay.surligner}, par
 *   réflexion) ; la pièce à choisir dans la roue du plan et le choix « Bois » de la roue du marteau clignotent aussi
 *   (PlanRadialScreen, HammerRadialScreen) ;</li>
 *   <li>à la fin ({@link S2CTutoFin}) : la caméra fait lentement le tour de la base, l'interface cachée sauf la radio
 *   du Commandant Varga (HeraliZ Quêtes) ; maintenir Espace pour passer.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class TutoClient {
    private static final int VERT = 0x55E07A, AMBRE = 0xFF9A2E;
    /** Pièce à passer en bois : redessinée un peu plus grande que la vraie, pour se voir par-dessus. */
    private static final float GROSSI = 1.04F;
    /** Distance (blocs) jusqu'où l'on peut viser une pièce verte. */
    private static final double PORTEE = 6.5;
    /** Durée du tour de caméra (ticks), hauteur au-dessus du centre (blocs). */
    private static final int TOUR_TICKS = 300;
    private static final double TOUR_HAUT = 5.0;

    private static S2CTuto guide = S2CTuto.AUCUN;
    private static Item objet;

    private static ArmorStand camera;
    private static Vec3 centre;
    private static float rayon;
    private static int tourTicks;
    private static int passer;

    private static boolean hotbarCherche;
    private static MethodHandle surligner;
    private static int surligne = -1;

    private TutoClient() {
    }

    // ---------------------------------------------------------------------------------------------- lectures

    /** Le tutoriel des bases est en cours chez ce joueur. */
    public static boolean actif() {
        return guide.etape() > 0;
    }

    /** La pièce à choisir dans la roue du plan (ordinal de PieceKind), -1 : aucune. */
    public static int roue() {
        return guide.roue();
    }

    /** Le choix « Bois » à montrer dans la roue du marteau. */
    public static boolean bois() {
        return guide.bois();
    }

    /**
     * Pendant le tutoriel, la pièce que le joueur s'apprête à poser est-elle permise ? Null si oui, sinon la clé du
     * message (le serveur revérifie de toute façon).
     */
    public static String refus(PieceKey key) {
        int e = guide.etape();
        if (e <= 0) {
            return null;
        }
        if (e <= 2) {
            return key.kind() == PieceKind.FOUNDATION ? null : "crac.tuto.refus.piece";
        }
        if (e > 5) {
            return "crac.tuto.refus.piece";
        }
        if (guide.cibles().isEmpty()) {
            return key.kind() == PieceKind.FOUNDATION ? null : "crac.tuto.refus.piece";
        }
        for (PieceKey c : guide.cibles()) {
            if (memePlace(c, key)) {
                return null;
            }
        }
        return "crac.tuto.refus.cible";
    }

    /**
     * La pièce verte (de ce genre) que le joueur regarde, à portée : la ligne du regard passe près de son centre. Null
     * sinon. Permet de poser un mur en visant le mur vert lui-même, et pas le bord de la fondation dessous.
     */
    public static PieceKey vise(PieceKind kind, Vec3 oeil, Vec3 regard) {
        if (!actif() || guide.bois()) {
            return null;
        }
        Vec3 r = regard.normalize();
        PieceKey best = null;
        double bestT = PORTEE;
        for (PieceKey c : guide.cibles()) {
            if (c.kind() != kind) {
                continue;
            }
            Vec3 d = c.centreWorld().subtract(oeil);
            double t = d.dot(r);
            if (t < 0.3 || t > bestT) {
                continue;
            }
            if (d.subtract(r.scale(t)).length() < (kind.isWall() ? 1.2 : 1.0)) {
                best = c;
                bestT = t;
            }
        }
        return best;
    }

    private static boolean memePlace(PieceKey a, PieceKey b) {
        if (a.kind() != b.kind() || a.h() != b.h()) {
            return false;
        }
        return a.kind().isWall() ? a.edgeKey().equals(b.edgeKey()) : a.footprint().equals(b.footprint());
    }

    // ---------------------------------------------------------------------------------------------- réception

    public static void recevoir(S2CTuto m) {
        guide = m;
        ResourceLocation id = m.objet().isEmpty() ? null : ResourceLocation.tryParse(m.objet());
        objet = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }

    public static void fin(S2CTutoFin m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        centre = new Vec3(m.x(), m.y(), m.z());
        rayon = m.rayon();
        tourTicks = 0;
        passer = 0;
        camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        camera.setInvisible(true);
        camera.setNoGravity(true);
        placerCamera(0, true);
        mc.setCameraEntity(camera);
    }

    private static void finirTour() {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && mc.player != null && mc.getCameraEntity() == camera) {
            mc.setCameraEntity(mc.player);
        }
        camera = null;
    }

    /** La caméra sur le cercle autour de la base, au tick {@code t}, tournée vers le centre. */
    private static void placerCamera(int t, boolean sansLissage) {
        double a = 2 * Math.PI * t / TOUR_TICKS;
        double x = centre.x + rayon * Math.cos(a), z = centre.z + rayon * Math.sin(a), y = centre.y + TOUR_HAUT;
        double dx = centre.x - x, dy = centre.y - y, dz = centre.z - z;
        float lacet = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float tangage = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        if (sansLissage) {
            camera.moveTo(x, y, z, lacet, tangage);
            return;
        }
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        camera.setPos(x, y, z);
        // pas de saut de 360° à 0° entre deux images
        camera.setYRot(camera.yRotO + Mth.wrapDegrees(lacet - camera.yRotO));
        camera.setXRot(tangage);
    }

    // ---------------------------------------------------------------------------------------------- tick

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (camera != null) {
            tourTicks++;
            passer = mc.options.keyJump.isDown() ? passer + 1 : 0;
            if (tourTicks >= TOUR_TICKS || passer > 20 || mc.getCameraEntity() != camera) {
                finirTour();
            } else {
                placerCamera(tourTicks, false);
            }
        }
        // l'objet à prendre en main, surligné dans la barre d'objets
        int slot = -1;
        if (objet != null && actif()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).is(objet)) {
                    slot = i;
                    break;
                }
            }
            if (slot >= 0 && mc.player.getInventory().selected == slot) {
                slot = -1;   // déjà en main
            }
        }
        if (slot != surligne) {
            surligne = slot;
            surlignerBarre(slot);
        }
    }

    private static void surlignerBarre(int slot) {
        if (!hotbarCherche) {
            hotbarCherche = true;
            if (ModList.get().isLoaded("dayzhotbar")) {
                try {
                    Class<?> c = Class.forName("com.dayzhotbar.client.HotbarOverlay");
                    surligner = MethodHandles.publicLookup().findStatic(c, "surligner", MethodType.methodType(void.class, int.class));
                } catch (ReflectiveOperationException | LinkageError ex) {
                    surligner = null;
                }
            }
        }
        if (surligner != null) {
            try {
                surligner.invokeExact(slot);
            } catch (Throwable ex) {
                surligner = null;
            }
        }
    }

    @SubscribeEvent
    public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        guide = S2CTuto.AUCUN;
        objet = null;
        camera = null;
        if (surligne >= 0) {
            surligne = -1;
            surlignerBarre(-1);
        }
    }

    /**
     * Pendant le tour de caméra, le regard est forcé vers le centre de la base à chaque image (d'autres mods recalent la
     * caméra sur le regard du joueur : avant, on tournait autour de la base sans la regarder).
     */
    @SubscribeEvent
    public static void regard(ViewportEvent.ComputeCameraAngles e) {
        if (camera == null || centre == null) {
            return;
        }
        Vec3 c = e.getCamera().getPosition();
        double dx = centre.x - c.x, dy = centre.y - c.y, dz = centre.z - c.z;
        e.setYaw((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F);
        e.setPitch((float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        e.setRoll(0F);
    }

    /** Ni bras ni objet en main pendant le tour de caméra. */
    @SubscribeEvent
    public static void main(RenderHandEvent e) {
        if (camera != null) {
            e.setCanceled(true);
        }
    }

    /** Pendant le tour de caméra de la fin : seule la radio de Varga reste à l'écran. */
    @SubscribeEvent
    public static void interface_(RenderGuiOverlayEvent.Pre e) {
        if (camera != null && !"heraliz_quetes".equals(e.getOverlay().id().getNamespace())) {
            e.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------------------------------------- rendu

    @SubscribeEvent
    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !actif()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float k = 0.5F + 0.5F * (float) Math.sin(System.nanoTime() / 1.8e8);
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = e.getPoseStack();
        List<PieceKey> cibles = guide.cibles();
        if (guide.bois()) {
            // pièces encore en brindilles : la pièce elle-même redessinée en orange, un peu plus grande, qui clignote
            int argb = ((int) (50 + 120 * k) << 24) | AMBRE;
            for (PieceKey c : cibles) {
                Vec3 m = c.centreWorld().subtract(cam);
                pose.pushPose();
                pose.translate(m.x, m.y, m.z);
                pose.scale(GROSSI, GROSSI, GROSSI);
                pose.translate(-m.x, -m.y, -m.z);
                GhostMesh.render(pose, buffers, cam, c, Tier.TWIG, 0, variante(c), argb);
                pose.popPose();
            }
        } else {
            int argb = ((int) (60 + 110 * k) << 24) | VERT;
            for (PieceKey c : cibles) {
                GhostMesh.render(pose, buffers, cam, c, Tier.TWIG, 0, variante(c), argb);
            }
        }
        if (!guide.blocs().isEmpty()) {
            VertexConsumer lignes = buffers.getBuffer(RenderType.lines());
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            // deux cases l'une sur l'autre : un seul cadre (l'armoire à outils fait 2 blocs de haut)
            for (BlockPos b : guide.blocs()) {
                if (guide.blocs().contains(b.below())) {
                    continue;
                }
                double haut = guide.blocs().contains(b.above()) ? 1 : 0;
                AABB box = new AABB(b).expandTowards(0, haut, 0).deflate(0.02);
                LevelRenderer.renderLineBox(pose, lignes, box, rouge(VERT), vert(VERT), bleu(VERT), 0.35F + 0.65F * k);
                LevelRenderer.renderLineBox(pose, lignes, box.inflate(0.08 + 0.06 * k), rouge(VERT), vert(VERT), bleu(VERT), 0.25F * k);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** Les murs avec leurs poteaux (comme le fantôme du plan), le reste sans variante. */
    private static long variante(PieceKey key) {
        if (key.kind().isWall()) {
            return key.topUnits() >= 2
                ? Variants.POST_A_LOW | Variants.POST_A_HIGH | Variants.POST_B_LOW | Variants.POST_B_HIGH
                : Variants.POST_A_LOW | Variants.POST_B_LOW;
        }
        return 0L;
    }

    private static float rouge(int rgb) {
        return (rgb >> 16 & 255) / 255F;
    }

    private static float vert(int rgb) {
        return (rgb >> 8 & 255) / 255F;
    }

    private static float bleu(int rgb) {
        return (rgb & 255) / 255F;
    }
}

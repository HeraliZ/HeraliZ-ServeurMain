package fr.heralisz.utils.train;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bagages du train chez le joueur : dessinés à leur place dans chaque voiture (modèle d'une malle des décors), et fouille
 * par le clic droit (touche « Utiliser ») quand on est à bord, près d'un bagage et tourné vers lui ; le serveur vérifie
 * tout (BagagesServeur). Aide affichée au-dessus de la barre d'objets.
 */
public final class BagagesClient {
    private static final Horaire.Placement PL = new Horaire.Placement();
    private static BlockState malle;

    private BagagesClient() {}

    private static BlockState malle() {
        if (malle == null) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("heraliz_decors", "coffre_capitaine"));
            malle = b == null || b == Blocks.AIR ? Blocks.BARREL.defaultBlockState() : b.defaultBlockState();
        }
        return malle;
    }

    /** Dessin des bagages (étape AFTER_ENTITIES, comme les voitures). */
    static void dessiner(PoseStack ps, Vec3 cam, float partial) {
        Horaire h = Horaire.get();
        Minecraft mc = Minecraft.getInstance();
        if (h == null || mc.level == null) return;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        BlockState st = malle();
        for (int rame = 0; rame < Horaire.RAMES; rame++) {
            Horaire.Etat e = h.etatRame(TrainClient.temps(partial), rame);
            for (int i = 0; i < Horaire.N; i++) {
                int veh = rame * Horaire.N + i;
                List<Bagages.Place> places = Bagages.places(veh);
                if (places.isEmpty()) continue;
                h.placer(veh, e.s(), PL);
                if (cam.distanceToSqr(PL.x, PL.y, PL.z) > 64 * 64) continue;
                for (Bagages.Place b : places) {
                    Vec3 w = Voyageurs.versMonde(PL, b.lx(), Reseau.PLANCHER, b.lz());
                    BlockPos bp = BlockPos.containing(w.x, w.y + 0.5, w.z);
                    int lum = LightTexture.pack(Math.max(mc.level.getBrightness(LightLayer.BLOCK, bp), 9),
                            mc.level.getBrightness(LightLayer.SKY, bp));
                    ps.pushPose();
                    ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
                    ps.mulPose(Axis.YP.rotation((float) -PL.cap + b.yaw()));
                    ps.scale(0.55f, 0.55f, 0.55f);
                    ps.translate(-0.5, 0, -0.5);
                    mc.getBlockRenderer().renderSingleBlock(st, ps, buf, lum, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                            RenderType.cutout());
                    ps.popPose();
                }
            }
        }
        buf.endBatch();
    }

    /** Bagage visé par le passager local : indice de la place, ou -1. */
    private static int vise(Voyageur v, LocalPlayer p) {
        Horaire h = Horaire.get();
        if (h == null || v.pv < 0) return -1;
        Horaire.Etat e = h.etatRame(TrainClient.temps(1f), Horaire.rame(v.pv));
        h.placer(v.pv, e.s(), PL);
        double yaw = Math.toRadians(p.getYRot());
        double rx = -Math.sin(yaw), rz = Math.cos(yaw);
        double lxDir = rx * Math.cos(PL.cap) + rz * Math.sin(PL.cap);
        double lzDir = -rx * Math.sin(PL.cap) + rz * Math.cos(PL.cap);
        List<Bagages.Place> places = Bagages.places(v.pv);
        int meilleur = -1;
        double dMin = Bagages.PORTEE;
        for (int k = 0; k < places.size(); k++) {
            Bagages.Place b = places.get(k);
            double dx = b.lx() - v.px, dz = b.lz() - v.pz, d = Math.hypot(dx, dz);
            if (d > Bagages.PORTEE) continue;
            if (d > 0.4 && (dx * lxDir + dz * lzDir) / d < 0.35) continue;          // tourné vers le bagage
            if (d < dMin) {
                dMin = d;
                meilleur = k;
            }
        }
        return meilleur;
    }

    /** Aide au-dessus de la barre d'objets (appelé à chaque tick du passager local). */
    static void aide(Voyageur v, LocalPlayer p) {
        if (v.tickCount % 10 != 0 || Minecraft.getInstance().screen != null) return;
        if (vise(v, p) >= 0) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatableWithFallback("heralisz_utils.train.bagage.aide",
                    "Clic droit : fouiller le bagage"), false);
        }
    }

    @Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Evenements {
        private Evenements() {}

        @SubscribeEvent
        public static void utiliser(InputEvent.InteractionKeyMappingTriggered e) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (!e.isUseItem() || p == null || !(p.getVehicle() instanceof Voyageur v) || v.pv < 0) return;
            int k = vise(v, p);
            if (k < 0) return;
            Train.CANAL.sendToServer(new Train.Bagage(v.pv, k));
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }
}

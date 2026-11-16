package fr.heralisz.utils.train;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bagages du train chez le joueur : dessinés à leur place dans chaque voiture avec le vrai modèle de leur conteneur de
 * HeraliZ Loot ; ceux qu'on peut fouiller pendant cette période scintillent (mêmes étoiles que les conteneurs de HeraliZ
 * Loot) jusqu'à leur première ouverture. Les étoiles sont dessinées ici, dans le repère de la voiture, à chaque image :
 * des particules posées dans le monde restaient sur place pendant que le train avançait (on ne les voyait jamais sur
 * les bagages). Fouille par le clic droit (touche « Utiliser ») en visant le bagage lui-même (rayon du regard
 * contre sa boîte, à 3 blocs au plus) ; le serveur revérifie tout (BagagesServeur).
 */
public final class BagagesClient {
    private static final Horaire.Placement PL = new Horaire.Placement();
    private static final Direction[] FACES = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private static final Map<String, BlockState> ETATS = new HashMap<>();
    private static final Set<Long> OUVERTS = new HashSet<>();
    private static long epoqueOuverts = -1;
    /** Images du scintillement (celles de la particule heraliz_loot:etoile), de la plus grande à la plus petite. */
    private static final ResourceLocation[] ECLATS = new ResourceLocation[8];
    private static final int ETOILES = 3;

    static {
        for (int i = 0; i < 8; i++) ECLATS[i] = new ResourceLocation("minecraft", "textures/particle/glitter_" + (7 - i) + ".png");
    }

    private BagagesClient() {}

    static void recevoir(Train.BagagesOuverts m) {
        OUVERTS.clear();
        epoqueOuverts = m.epoque();
        for (long c : m.cles()) OUVERTS.add(c);
    }

    private static BlockState etat(Bagages.Place b) {
        String cle = b.modele() + "|" + b.quart();
        BlockState s = ETATS.get(cle);
        if (s == null) {
            Block bloc = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("heraliz_loot", b.modele()));
            s = bloc == null || bloc == Blocks.AIR ? Blocks.BARREL.defaultBlockState() : bloc.defaultBlockState();
            if (s.hasProperty(HorizontalDirectionalBlock.FACING)) s = s.setValue(HorizontalDirectionalBlock.FACING, FACES[b.quart() & 3]);
            ETATS.put(cle, s);
        }
        return s;
    }

    /** À fouiller maintenant : choisi pour cette période et pas encore ouvert. */
    private static boolean scintille(int veh, int place, long epoque) {
        return Bagages.actif(veh, place, epoque) && !(epoque == epoqueOuverts && OUVERTS.contains(Bagages.cle(veh, place)));
    }

    /** Dessin des bagages (étape AFTER_ENTITIES, comme les voitures). */
    static void dessiner(PoseStack ps, Vec3 cam, float partial) {
        Horaire h = Horaire.get();
        Minecraft mc = Minecraft.getInstance();
        if (h == null || mc.level == null) return;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        long epoque = Bagages.epoque(mc.level.getGameTime());
        double tics = mc.level.getGameTime() + (double) partial;
        // deux passes : les bagages, puis leurs étoiles par-dessus (dessinées avant, leurs bords transparents
        // masqueraient le bagage placé derrière)
        for (int passe = 0; passe < 2; passe++) {
        for (int rame = 0; rame < Horaire.RAMES; rame++) {
            Horaire.Etat e = h.etatRame(TrainClient.temps(partial), rame);
            for (int i = 0; i < Horaire.N; i++) {
                int veh = rame * Horaire.N + i;
                List<Bagages.Place> places = Bagages.places(veh);
                if (places.isEmpty()) continue;
                h.placer(veh, e.s(), PL);
                if (cam.distanceToSqr(PL.x, PL.y, PL.z) > 64 * 64) continue;
                for (int k = 0; k < places.size(); k++) {
                    Bagages.Place b = places.get(k);
                    if (passe == 1) {
                        if (scintille(veh, k, epoque)) etoiles(ps, buf, cam, veh, k, b, tics);
                        continue;
                    }
                    Vec3 w = Voyageurs.versMonde(PL, b.lx(), Reseau.PLANCHER, b.lz());
                    BlockPos bp = BlockPos.containing(w.x, w.y + 0.5, w.z);
                    int lum = LightTexture.pack(Math.max(mc.level.getBrightness(LightLayer.BLOCK, bp), 9),
                            mc.level.getBrightness(LightLayer.SKY, bp));
                    ps.pushPose();
                    ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
                    ps.mulPose(Axis.YP.rotation((float) -PL.cap));
                    ps.scale(Bagages.ECHELLE, Bagages.ECHELLE, Bagages.ECHELLE);
                    ps.translate(-0.5, 0, -0.5);
                    mc.getBlockRenderer().renderSingleBlock(etat(b), ps, buf, lum, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                            RenderType.cutout());
                    ps.popPose();
                }
            }
        }
        buf.endBatch();
        }
    }

    private static float alea(long g) {
        g ^= g >>> 33;
        g *= 0xff51afd7ed558ccdL;
        g ^= g >>> 33;
        g *= 0xc4ceb9fe1a85ec53L;
        g ^= g >>> 33;
        return (g >>> 40) / (float) (1 << 24);
    }

    /** Étoiles du bagage (PL est déjà réglé sur sa voiture) : chacune apparaît à une place tirée au hasard autour du
     *  bagage, grandit, puis s'éteint en moins d'une seconde, comme la particule de HeraliZ Loot, mais accrochée à la
     *  voiture. Toujours face à la caméra, en pleine lumière. */
    private static void etoiles(PoseStack ps, MultiBufferSource buf, Vec3 cam, int veh, int place, Bagages.Place b, double tics) {
        Minecraft mc = Minecraft.getInstance();
        for (int n = 0; n < ETOILES; n++) {
            long base = ((long) veh * 31 + place) * 31 + n;
            double periode = 24 + alea(base * 7919L) * 14;
            double t = tics + alea(base * 104729L) * periode;
            long tour = (long) Math.floor(t / periode);
            float f = (float) ((t - tour * periode) / 16.0);           // 16 ticks de vie visible, puis une attente
            if (f >= 1f) continue;
            long g = base * 1000003L + tour;
            float eclat = f < 0.25f ? f / 0.25f : 1f - (f - 0.25f) / 0.75f;
            float taille = (0.055f + alea(g * 3L) * 0.03f) * (0.4f + 0.6f * eclat);
            Vec3 w = Voyageurs.versMonde(PL, b.lx() + (alea(g * 5L) - 0.5f) * 0.7f,
                    Reseau.PLANCHER + 0.25f + alea(g * 11L) * 0.75f, b.lz() + (alea(g * 13L) - 0.5f) * 0.7f);
            ps.pushPose();
            ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
            ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
            org.joml.Matrix4f m = ps.last().pose();
            VertexConsumer vc = buf.getBuffer(RenderType.text(ECLATS[Math.min(7, (int) (f * 8))]));
            int a = Math.max(0, Math.min(255, (int) (eclat * 255)));
            // les deux sens : visible quel que soit le côté dessiné par ce type de rendu
            float[][] q = {{-taille, -taille, 1, 1}, {-taille, taille, 1, 0}, {taille, taille, 0, 0}, {taille, -taille, 0, 1}};
            for (int s = 0; s < 4; s++) vc.vertex(m, q[s][0], q[s][1], 0).color(255, 255, 255, a).uv(q[s][2], q[s][3]).uv2(0xF000F0).endVertex();
            for (int s = 3; s >= 0; s--) vc.vertex(m, q[s][0], q[s][1], 0).color(255, 255, 255, a).uv(q[s][2], q[s][3]).uv2(0xF000F0).endVertex();
            ps.popPose();
        }
    }

    /** Bagage à fouiller que regarde le passager local : indice de la place, ou -1. Rayon du regard (repère de la
     *  voiture) contre la boîte du bagage, à 3 blocs au plus : on ne fouille plus en regardant à côté. */
    private static int vise(Voyageur v, LocalPlayer p) {
        Horaire h = Horaire.get();
        Minecraft mc = Minecraft.getInstance();
        if (h == null || v.pv < 0 || mc.level == null) return -1;
        Horaire.Etat e = h.etatRame(TrainClient.temps(1f), Horaire.rame(v.pv));
        h.placer(v.pv, e.s(), PL);
        double yaw = Math.toRadians(p.getYRot()), pitch = Math.toRadians(p.getXRot());
        double dx = -Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
        double c = Math.cos(PL.cap), s = Math.sin(PL.cap);
        double[] d = {dx * c + dz * s, dy, -dx * s + dz * c};
        double[] o = {v.px, Reseau.PLANCHER + v.ph + p.getEyeHeight(), v.pz};
        long epoque = Bagages.epoque(mc.level.getGameTime());
        List<Bagages.Place> places = Bagages.places(v.pv);
        int meilleur = -1;
        double tMin = 3.0;
        for (int k = 0; k < places.size(); k++) {
            if (!scintille(v.pv, k, epoque)) continue;
            Bagages.Place b = places.get(k);
            double[] mn = {b.lx() - Bagages.DEMI, Reseau.PLANCHER, b.lz() - Bagages.DEMI};
            double[] mx = {b.lx() + Bagages.DEMI, Reseau.PLANCHER + Bagages.HAUT, b.lz() + Bagages.DEMI};
            double t0 = 0, t1 = tMin;
            boolean touche = true;
            for (int a = 0; a < 3 && touche; a++) {
                if (Math.abs(d[a]) < 1e-9) {
                    touche = o[a] >= mn[a] && o[a] <= mx[a];
                } else {
                    double ta = (mn[a] - o[a]) / d[a], tb = (mx[a] - o[a]) / d[a];
                    t0 = Math.max(t0, Math.min(ta, tb));
                    t1 = Math.min(t1, Math.max(ta, tb));
                    touche = t0 <= t1;
                }
            }
            if (touche && t0 < tMin) {
                tMin = t0;
                meilleur = k;
            }
        }
        return meilleur;
    }

    /** Aide au-dessus de la barre d'objets (appelé à chaque tick du passager local). */
    static void aide(Voyageur v, LocalPlayer p) {
        if (v.tickCount % 5 != 0 || Minecraft.getInstance().screen != null) return;
        if (vise(v, p) >= 0) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatableWithFallback("heralisz_utils.train.bagage.aide",
                    "Clic droit : fouiller le bagage"), false);
        }
    }

    @Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class EcouteBagages {
        private EcouteBagages() {}

        @SubscribeEvent
        public static void fouiller(InputEvent.InteractionKeyMappingTriggered e) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (!e.isUseItem() || p == null || !(p.getVehicle() instanceof Voyageur v) || v.pv < 0) return;
            int k = vise(v, p);
            if (k < 0) return;
            Train.CANAL.sendToServer(new Train.Bagage(v.pv, k));
            e.setCanceled(true);
            e.setSwingHand(false);
        }

        /** Passager accroupi dans le train : le modèle est penché comme tout joueur accroupi, pour lui-même (vue F5) et
         *  pour les autres (la pose du jeu n'y suffit pas : le joueur local la recalcule d'après sa touche). */
        @SubscribeEvent
        public static void modeleAccroupi(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> e) {
            if (e.getEntity() instanceof net.minecraft.world.entity.player.Player pl && pl.getVehicle() instanceof Voyageur v
                    && !v.isRemoved() && v.accroupi()
                    && e.getRenderer().getModel() instanceof net.minecraft.client.model.HumanoidModel<?> m) {
                m.crouching = true;
            }
        }
    }
}

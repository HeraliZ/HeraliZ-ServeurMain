package fr.heralisz.utils.train;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
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
 * HeraliZ Loot ; ceux qu'on peut fouiller pendant cette période scintillent (étoiles de HeraliZ Loot) jusqu'à leur
 * première ouverture. Fouille par le clic droit (touche « Utiliser ») en visant le bagage lui-même (rayon du regard
 * contre sa boîte, à 3 blocs au plus) ; le serveur revérifie tout (BagagesServeur).
 */
public final class BagagesClient {
    private static final Horaire.Placement PL = new Horaire.Placement();
    private static final Direction[] FACES = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private static final Map<String, BlockState> ETATS = new HashMap<>();
    private static final Set<Long> OUVERTS = new HashSet<>();
    private static long epoqueOuverts = -1;
    private static ParticleOptions etoile;
    private static int tic;

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

    /** Étoiles sur les bagages à fouiller (à chaque tick du client, un bagage sur quatre à la fois). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Horaire h = Horaire.get();
        if (h == null || mc.level == null || mc.player == null || mc.isPaused() || !TrainClient.actif()) return;
        if (++tic % 3 != 0) return;
        if (etoile == null) {
            ParticleType<?> t = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("heraliz_loot", "etoile"));
            etoile = t instanceof ParticleOptions o ? o : ParticleTypes.WAX_OFF;
        }
        long epoque = Bagages.epoque(mc.level.getGameTime());
        var alea = mc.level.random;
        for (int rame = 0; rame < Horaire.RAMES; rame++) {
            Horaire.Etat e = h.etatRame(TrainClient.temps(1f), rame);
            for (int i = 0; i < Horaire.N; i++) {
                int veh = rame * Horaire.N + i;
                List<Bagages.Place> places = Bagages.places(veh);
                if (places.isEmpty()) continue;
                h.placer(veh, e.s(), PL);
                if (mc.player.distanceToSqr(PL.x, PL.y, PL.z) > 40 * 40) continue;
                for (int k = 0; k < places.size(); k++) {
                    if (!scintille(veh, k, epoque) || alea.nextInt(3) != 0) continue;
                    Bagages.Place b = places.get(k);
                    Vec3 w = Voyageurs.versMonde(PL, b.lx() + (alea.nextFloat() - 0.5f) * 0.7f,
                            Reseau.PLANCHER + 0.25f + alea.nextFloat() * 0.75f, b.lz() + (alea.nextFloat() - 0.5f) * 0.7f);
                    mc.level.addParticle(etoile, w.x, w.y, w.z, 0, 0.01, 0);
                }
            }
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

        @SubscribeEvent
        public static void etoiles(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.END) tick();
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

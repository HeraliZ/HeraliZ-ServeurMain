package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lecture des sons du train (voir TrainSons). */
@OnlyIn(Dist.CLIENT)
public final class TrainSonsClient {
    private TrainSonsClient() {}

    /** Boucle qui suit un point du train (recalculé à chaque tick). */
    @OnlyIn(Dist.CLIENT)
    static final class Boucle extends AbstractTickableSoundInstance {
        final boolean moteur;
        float volVoulu, pitchVoulu;

        Boucle(SoundEvent e, boolean moteur) {
            super(e, SoundSource.AMBIENT, RandomSource.create());
            this.moteur = moteur;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }

        void placer(Vec3 p, float vol, float pitch) {
            x = p.x;
            y = p.y;
            z = p.z;
            volVoulu = vol;
            pitchVoulu = pitch;
        }

        @Override
        public void tick() {
            volume += (volVoulu - volume) * 0.15f;
            pitch += (pitchVoulu - pitch) * 0.15f;
            if (!TrainClient.actif()) stop();
        }

        void arreter() {
            stop();
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static Boucle roulement, moteur;
    @OnlyIn(Dist.CLIENT)
    private static Horaire.Etat prec;
    private static int ramePrec = -1;

    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Evenements {
        private Evenements() {}

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            Horaire h = Horaire.get();
            if (!TrainClient.actif() || h == null || mc.player == null || mc.isPaused()) {
                prec = null;
                return;
            }
            Vec3 oreille = mc.gameRenderer.getMainCamera().getPosition();
            // la rame la plus proche de l'oreille (deux rames sur la boucle)
            Horaire.Etat et = null;
            int rr = 0;
            double dm = Double.MAX_VALUE;
            for (int r = 0; r < Horaire.RAMES; r++) {
                Horaire.Etat e_ = h.etatRame(TrainClient.tTick, r);
                double[] c_ = new double[3];
                h.reseau.point(e_.s(), c_);
                double d_ = oreille.distanceToSqr(c_[0], c_[1], c_[2]);
                if (d_ < dm) {
                    dm = d_;
                    et = e_;
                    rr = r;
                }
            }
            if (rr != ramePrec) prec = null;                                   // autre rame : pas d'événement à cheval
            ramePrec = rr;
            // point de la rame le plus proche (milieu de chaque véhicule), locomotive
            Horaire.Placement pl = new Horaire.Placement();
            Vec3 proche = null, loco = null;
            double best = 1e18;
            for (int i = 0; i < Horaire.RAME.length; i++) {
                h.placer(i, et.s(), pl);
                double demi = Horaire.RAME[i].longueur / 2 - 1;
                for (double lx = -demi; lx <= demi; lx += demi / 2) {          // cinq points par véhicule
                    Vec3 p = Voyageurs.versMonde(pl, lx, 1.5, 0);
                    double d = p.distanceToSqr(oreille);
                    if (d < best) {
                        best = d;
                        proche = p;
                    }
                }
                if (i == 0) loco = Voyageurs.versMonde(pl, 0, 2.5, 0);
            }
            double dist = Math.sqrt(best);
            boolean aBord = mc.player.getVehicle() instanceof Voyageur;
            float v = (float) et.v();
            if (dist < 90) {
                if (roulement == null || roulement.isStopped()) {
                    roulement = new Boucle(TrainSons.ROULEMENT.get(), false);
                    mc.getSoundManager().play(roulement);
                }
                float vol = Math.min(1f, v / 9f) * (aBord ? 0.55f : 1.4f);
                roulement.placer(proche, vol, 0.55f + Math.min(1f, v / 15f) * 0.6f);
                if (moteur == null || moteur.isStopped()) {
                    moteur = new Boucle(TrainSons.MOTEUR.get(), true);
                    mc.getSoundManager().play(moteur);
                }
                moteur.placer(loco, aBord ? 0.35f : 0.9f, 0.85f + Math.min(1f, v / 15f) * 0.35f);
            } else {
                if (roulement != null) roulement.arreter();
                if (moteur != null) moteur.arreter();
                roulement = moteur = null;
            }
            // fumée d'échappement : bouffées au ralenti, panache à l'accélération (elle reste dans l'air, derrière le train)
            if (dist < 110 && !mc.isPaused()) {
                h.placer(0, et.s(), pl);
                double acc = prec != null ? et.v() - prec.v() : 0;
                int n = acc > 0.004 ? 3 : (v > 1 ? 1 : (mc.level.getGameTime() % 4 == 0 ? 1 : 0));
                for (int k = 0; k < n; k++) {
                    for (double zs : new double[]{-0.22, 0.22}) {
                        Vec3 p = Voyageurs.versMonde(pl, 1.1, 4.62, zs);
                        double r = mc.level.random.nextDouble();
                        mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z,
                                (r - 0.5) * 0.02, 0.06 + r * 0.05 + (acc > 0.004 ? 0.06 : 0), (mc.level.random.nextDouble() - 0.5) * 0.02);
                    }
                }
            }
            // événements : départ (klaxon), approche (klaxon, freins), arrivée (carillon)
            if (prec != null && dist < 220) {
                if (prec.aQuai() >= 0 && et.aQuai() < 0) jouer(TrainSons.KLAXON.get(), loco, 6f, 1f);
                if (et.aQuai() < 0) {
                    double avant = h.attente(et.suivante(), prec), apres = h.attente(et.suivante(), et);
                    if (avant > 25 && apres <= 25) jouer(TrainSons.KLAXON.get(), loco, 6f, 0.97f);
                    if (avant > 5.5 && apres <= 5.5) jouer(TrainSons.FREINS.get(), proche, 2.5f, 1f);
                }
                if (prec.aQuai() < 0 && et.aQuai() >= 0) {
                    double[] c = new double[3];
                    h.reseau.point(h.reseau.gares[et.aQuai()].s(), c);
                    jouer(TrainSons.CARILLON.get(), new Vec3(c[0], c[1] + 3, c[2]), 3f, 1f);
                }
            }
            prec = et;
        }

        private static void jouer(SoundEvent s, Vec3 p, float vol, float pitch) {
            if (p == null) return;
            Minecraft.getInstance().level.playLocalSound(p.x, p.y, p.z, s, SoundSource.AMBIENT, vol, pitch, false);
        }
    }
}

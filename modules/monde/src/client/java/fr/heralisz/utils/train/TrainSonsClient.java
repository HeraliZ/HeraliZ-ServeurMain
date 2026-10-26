package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
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

/**
 * Lecture des sons du train chez le joueur (voir TrainSons), tirée de l'horaire : rien ne vient du serveur. Pour la rame
 * la plus proche de l'oreille :
 * <ul>
 * <li>boucles : roulement (« voie ») sur le point de la rame le plus proche, volume et hauteur selon la vitesse ; à bord,
 * la boucle « cabine » jouée sur le joueur (son plus présent) et le roulement extérieur baissé ; moteur sur la
 * locomotive, son régime suit l'effort (ralenti à quai et au freinage, monte au départ) ;</li>
 * <li>joints de rail (un tous les 25 blocs) : chaque essieu qui en passe un joue un « tac » à sa place, réglé à
 * l'image près : le rythme « ta-dam ... ta-dam » suit exactement la vitesse ;</li>
 * <li>événements : avertisseur 2 s avant le départ et 25 s avant chaque gare ; freins 5,5 s avant l'arrivée (le son
 * finit sur l'arrêt) ; à l'arrêt, détente de l'air puis carillon dans le haut-parleur du quai le plus proche ; portes
 * côté quai (les trois plus proches) qui s'ouvrent 3 s après l'arrêt et, 8 s avant le départ, bips puis fermeture, le
 * son calé sur l'animation (RameRendu) ; au départ, desserrage et turbo, et les attelages qui se tendent l'un après
 * l'autre ; portes intérieures (intercirculation, vestibules) quand elles s'ouvrent ou se referment.</li>
 * </ul>
 * Portées (blocs, atténuation linéaire du jeu) : dans sounds.json (attenuation_distance), les volumes passés ici restent
 * à 1 au plus pour ne pas les allonger. Sons fabriqués par outils/sons_train.py (mêmes temps que l'horaire et
 * l'animation des portes).
 */
@OnlyIn(Dist.CLIENT)
public final class TrainSonsClient {
    /** portes_fermeture.ogg : les bips d'abord, la porte bouge BIPS secondes après le début (outils/sons_train.py). */
    static final double BIPS = 1.4;
    /** freins.ogg : du début du son à l'arrêt (outils/sons_train.py, FREINAGE). */
    static final double FREINAGE = 5.5;
    /** Avertisseur : secondes avant le départ (portes fermées depuis 6 s), et avant l'arrivée en gare. */
    static final double KLAXON_DEPART = 2.0, KLAXON_APPROCHE = 25.0;
    /** Joints de rail : un tous les JOINT blocs le long de la voie (rails de 25 m). */
    static final double JOINT = 25.0;
    /** Essieux de chaque bogie, de part et d'autre de son pivot (tools/gen_train.py, comme RameRendu). */
    private static final double[] ESSIEUX_LOCO = {-1.9, 0, 1.9}, ESSIEUX_VOITURE = {-1.25, 1.25};
    /** Au-delà, on ne lance pas le son (la portée elle-même est dans sounds.json). */
    private static final double PORTEE_JOINT = 42, PORTEE_PORTES = 30, PORTEE_PORTE_INT = 14, PORTEE_ATTELAGE = 42;
    /** Tant que la rame est à moins de ça, les boucles tournent (un peu plus que la plus longue portée : 72). */
    private static final double BOUCLES_ON = 80, BOUCLES_OFF = 96;

    private TrainSonsClient() {}

    /** Boucle qui suit un point du train (recalculé à chaque tick) ; volume et hauteur glissent vers leur cible ; elle
     *  s'éteint en douceur avant de s'arrêter (pas de clic). Relative : jouée sur le joueur, sans atténuation. */
    @OnlyIn(Dist.CLIENT)
    static final class Boucle extends AbstractTickableSoundInstance {
        float volVoulu, pitchVoulu;
        private boolean finir;

        Boucle(SoundEvent e, boolean relative) {
            super(e, SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = relative;
            this.attenuation = relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR;
        }

        void placer(Vec3 p, float vol, float pitch) {
            if (p != null) {
                x = p.x;
                y = p.y;
                z = p.z;
            }
            volVoulu = vol;
            pitchVoulu = pitch;
        }

        @Override
        public void tick() {
            if (finir) volVoulu = 0f;
            volume += (volVoulu - volume) * 0.15f;
            pitch += (pitchVoulu - pitch) * 0.15f;
            if (!TrainClient.actif() || finir && volume < 0.004f) stop();
        }

        void arreter() {
            finir = true;
        }

        boolean vivante() {
            return !isStopped() && !finir && Minecraft.getInstance().getSoundManager().isActive(this);
        }
    }

    /** Son ponctuel qui suit un point qui bouge (la locomotive, le point de la rame le plus proche). */
    @OnlyIn(Dist.CLIENT)
    static final class Suiveur extends AbstractTickableSoundInstance {
        private final Supplier<Vec3> point;

        Suiveur(SoundEvent e, float vol, float pitch, Supplier<Vec3> point) {
            super(e, SoundSource.AMBIENT, RandomSource.create());
            this.point = point;
            this.looping = false;
            this.delay = 0;
            this.volume = vol;
            this.pitch = pitch;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            suivre();
        }

        private void suivre() {
            Vec3 p = point.get();
            if (p != null) {
                x = p.x;
                y = p.y;
                z = p.z;
            }
        }

        @Override
        public void tick() {
            if (!TrainClient.actif()) {
                stop();
                return;
            }
            suivre();
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static Boucle voie, cabine, moteur;
    @OnlyIn(Dist.CLIENT)
    private static Horaire.Etat prec;
    private static int ramePrec = -1;
    /** Rame la plus proche (pour les joints, lus à chaque image). */
    private static volatile int rameProche = 0;
    private static boolean ecoute;
    /** Régime du moteur (0 ralenti .. 1 plein effort), accélération lissée (blocs/s²), temps du tick précédent. */
    private static float regime;
    private static double accLisse, tPrecSons = Double.NaN;
    /** Joints : abscisse de la rame à l'image précédente. */
    private static double sJoint = Double.NaN;
    private static int rameJoint = -1;
    /** Portes intérieures : ouverture au tick précédent et sens du dernier mouvement (+1 ouvre, -1 ferme). */
    private static final float[][] PORTE_INT = new float[Horaire.N * Horaire.RAMES][6];
    private static final byte[][] SENS_INT = new byte[Horaire.N * Horaire.RAMES][6];
    private static final int[] PORTES_INT = {0, 4, 5};
    private static final RandomSource HASARD = RandomSource.create();
    private static final double[] PT = new double[3], PT2 = new double[3];

    /** Point de la rame le plus proche de l'oreille (cinq points par véhicule, à hauteur des roues). */
    static Vec3 procheDe(Horaire h, Horaire.Etat et, Vec3 oreille, Horaire.Placement pl) {
        Vec3 proche = null;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < Horaire.N; i++) {
            h.placer(i, et.s(), pl);
            double demi = Horaire.RAME[i].longueur / 2 - 1;
            for (double lx = -demi; lx <= demi + 1e-6; lx += demi / 2) {
                Vec3 p = Voyageurs.versMonde(pl, lx, 1.5, 0);
                double d = p.distanceToSqr(oreille);
                if (d < best) {
                    best = d;
                    proche = p;
                }
            }
        }
        return proche;
    }

    /** Point local (lx, ly, lz) du véhicule `place` de la rame r, maintenant. */
    static Vec3 pointRame(int r, int place, double lx, double ly, double lz) {
        Horaire h = Horaire.get();
        if (h == null) return null;
        Horaire.Placement pl = new Horaire.Placement();
        h.placer(place, h.etatRame(TrainClient.tTick, r).s(), pl);
        return Voyageurs.versMonde(pl, lx, ly, lz);
    }

    private static Vec3 oreille() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    /** Sons joués seulement si la catégorie (ambiance) et le volume général ne sont pas coupés. */
    private static boolean audible(Minecraft mc) {
        return mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0 && mc.options.getSoundSourceVolume(SoundSource.AMBIENT) > 0;
    }

    private static void jouer(SoundEvent s, Vec3 p, float vol, float pitch, int delai) {
        if (p == null) return;
        var son = new SimpleSoundInstance(s, SoundSource.AMBIENT, vol, pitch, RandomSource.create(HASARD.nextLong()), p.x, p.y, p.z);
        if (delai > 0) Minecraft.getInstance().getSoundManager().playDelayed(son, delai);
        else Minecraft.getInstance().getSoundManager().play(son);
    }

    private static void suivre(SoundEvent s, float vol, float pitch, Supplier<Vec3> point) {
        if (point.get() == null) return;
        Minecraft.getInstance().getSoundManager().play(new Suiveur(s, vol, pitch, point));
    }

    private static Boucle boucle(Boucle b, SoundEvent e, boolean relative) {
        if (b != null && b.vivante()) return b;
        if (b != null) b.arreter();
        Boucle n = new Boucle(e, relative);
        Minecraft.getInstance().getSoundManager().play(n);
        return n;
    }

    private static void finirBoucles() {
        if (voie != null) voie.arreter();
        if (cabine != null) cabine.arreter();
        if (moteur != null) moteur.arreter();
        voie = cabine = moteur = null;
    }

    /** Portes latérales côté quai : le son joué aux trois portes les plus proches de l'oreille (la plus proche à la
     *  hauteur exacte, calée sur l'animation ; les autres un peu plus loin et à peine désaccordées). */
    private static void portes(Horaire h, Horaire.Etat et, int r, Vec3 oreille, SoundEvent son) {
        if (et.aQuai() < 0) return;
        int cote = h.reseau.gares[et.aQuai()].cote();
        Horaire.Placement pl = new Horaire.Placement();
        List<Vec3> ps = new ArrayList<>();
        for (int i = 0; i < Horaire.N; i++) {
            int veh = r * Horaire.N + i;
            if (!Voyageurs.voiture(veh)) continue;
            h.placer(veh, et.s(), pl);
            for (double lx : new double[]{(Voyageurs.PX0 + Voyageurs.PX1) / 2, -(Voyageurs.PX0 + Voyageurs.PX1) / 2}) {
                Vec3 p = Voyageurs.versMonde(pl, lx, Reseau.PLANCHER + 1.3, cote * (Voyageurs.VW + 0.1));
                if (p.distanceToSqr(oreille) < PORTEE_PORTES * PORTEE_PORTES) ps.add(p);
            }
        }
        ps.sort((a, b) -> Double.compare(a.distanceToSqr(oreille), b.distanceToSqr(oreille)));
        float[] vols = {1f, 0.7f, 0.5f};
        for (int k = 0; k < Math.min(3, ps.size()); k++) {
            float pitch = k == 0 ? 1f : 0.98f + HASARD.nextFloat() * 0.04f;
            jouer(son, ps.get(k), vols[k], pitch, 0);
        }
    }

    /** Haut-parleur du quai le plus proche de l'oreille (un tous les 10 blocs le long du quai, côté quai, en hauteur). */
    private static Vec3 hautParleur(Horaire h, int gare, Vec3 oreille) {
        Reseau.Gare g = h.reseau.gares[gare];
        Vec3 best = null;
        double bd = Double.MAX_VALUE;
        for (double ds = -80; ds <= 80; ds += 10) {
            h.reseau.point(g.s() + ds, PT);
            h.reseau.point(g.s() + ds + 1, PT2);
            double cap = Math.atan2(PT2[2] - PT[2], PT2[0] - PT[0]);
            double off = g.cote() * 4.5;                              // +lz à droite du sens de la marche
            Vec3 p = new Vec3(PT[0] - Math.sin(cap) * off, g.yQuai() + 3.2, PT[2] + Math.cos(cap) * off);
            double d = p.distanceToSqr(oreille);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Evenements {
        private Evenements() {}

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            Horaire h = Horaire.get();
            if (!TrainClient.actif() || h == null || mc.player == null || mc.level == null || mc.isPaused()) {
                prec = null;
                ecoute = false;
                tPrecSons = Double.NaN;
                if (!TrainClient.actif()) finirBoucles();
                return;
            }
            ecoute = audible(mc);
            Vec3 oreille = oreille();
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
            if (rr != ramePrec) {                                          // autre rame : pas d'événement à cheval
                prec = null;
                tPrecSons = Double.NaN;
            }
            ramePrec = rr;
            rameProche = rr;
            final int rame = rr;
            Horaire.Placement pl = new Horaire.Placement();
            Vec3 proche = procheDe(h, et, oreille, pl);
            h.placer(0, et.s(), pl);
            Vec3 loco = Voyageurs.versMonde(pl, 0, 2.5, 0);
            double dist = Math.sqrt(proche.distanceToSqr(oreille));
            boolean aBord = mc.player.getVehicle() instanceof Voyageur;
            float v = (float) et.v();
            float vr = Math.min(1f, v / 15f);
            // effort du moteur : accélération lissée -> régime voulu (ralenti à quai et au freinage)
            double dt = Double.isNaN(tPrecSons) ? 0 : TrainClient.tTick - tPrecSons;
            if (prec != null && dt > 1e-3 && dt < 1) accLisse += ((et.v() - prec.v()) / dt - accLisse) * 0.2;
            tPrecSons = TrainClient.tTick;
            float cible = et.aQuai() >= 0 ? 0f : accLisse > 0.08 ? 1f : accLisse < -0.08 ? 0.12f : 0.55f;
            regime += (cible - regime) * 0.04f;
            // boucles (lancées à moins de BOUCLES_ON blocs, gardées jusqu'à BOUCLES_OFF)
            if (ecoute && (dist < BOUCLES_ON || aBord || voie != null && dist < BOUCLES_OFF)) {
                voie = boucle(voie, TrainSons.VOIE.get(), false);
                float vol = (float) Math.pow(Math.min(1.0, v / 12.0), 1.2) * (aBord ? 0.3f : 1f);
                voie.placer(proche, vol, 0.6f + 0.5f * vr);
                moteur = boucle(moteur, TrainSons.MOTEUR.get(), false);
                moteur.placer(loco, (0.45f + 0.55f * regime) * (aBord ? 0.6f : 1f), 0.8f + 0.35f * regime);
            } else {
                if (voie != null) voie.arreter();
                if (moteur != null) moteur.arreter();
                voie = moteur = null;
            }
            if (ecoute && aBord) {
                cabine = boucle(cabine, TrainSons.CABINE.get(), true);
                cabine.placer(Vec3.ZERO, 0.7f * Math.min(1f, v / 10f), 0.75f + 0.35f * vr);
            } else if (cabine != null) {
                cabine.arreter();
                cabine = null;
            }
            // fumée d'échappement : bouffées au ralenti, panache à l'accélération (elle reste dans l'air, derrière le train)
            if (dist < 110) {
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
            if (ecoute && prec != null && dist < 220) evenements(h, prec, et, rame, oreille, proche, loco);
            if (ecoute) portesInterieures(h, oreille);
            prec = et;
        }

        /** Avertisseur, freins, arrêt, portes et départ : chacun au moment où l'horaire passe son instant. */
        private static void evenements(Horaire h, Horaire.Etat p, Horaire.Etat et, int rame, Vec3 oreille, Vec3 proche, Vec3 loco) {
            int q = et.aQuai(), qp = p.aQuai();
            Supplier<Vec3> pointLoco = () -> pointRame(rame, 0, 0, 3.5, 0);
            if (q >= 0 && q == qp) {                                       // à quai
                double avant = Horaire.ARRET - p.reste(), apres = Horaire.ARRET - et.reste();
                if (avant < Horaire.OUVERTURE && apres >= Horaire.OUVERTURE) {
                    portes(h, et, rame, oreille, TrainSons.PORTES_OUVERTURE.get());
                }
                double f = Horaire.FERMETURE + Horaire.MOUVEMENT_PORTE + BIPS;
                if (p.reste() > f && et.reste() <= f) portes(h, et, rame, oreille, TrainSons.PORTES_FERMETURE.get());
                if (p.reste() > KLAXON_DEPART && et.reste() <= KLAXON_DEPART) suivre(TrainSons.KLAXON.get(), 1f, 1f, pointLoco);
            }
            if (qp >= 0 && q < 0) {                                        // départ
                suivre(TrainSons.DEPART.get(), 1f, 1f, () -> pointRame(rame, 0, 0, 2.5, 0));
                for (int i = 0; i + 1 < Horaire.N; i++) {                  // les attelages se tendent de l'avant à l'arrière
                    double s = et.s() + Horaire.DECALAGE[i] - Horaire.RAME[i].longueur / 2 - Horaire.ATTELAGE / 2;
                    h.reseau.point(s, PT);
                    Vec3 a = new Vec3(PT[0], PT[1] + Reseau.RAIL + 0.9, PT[2]);
                    if (a.distanceToSqr(oreille) > PORTEE_ATTELAGE * PORTEE_ATTELAGE) continue;
                    jouer(TrainSons.ATTELAGE.get(), a, 0.85f, 0.96f + HASARD.nextFloat() * 0.08f, 12 + 4 * i);
                }
            }
            if (q < 0) {                                                   // en route : approche de la gare suivante
                double avant = h.attente(et.suivante(), p), apres = h.attente(et.suivante(), et);
                if (avant > KLAXON_APPROCHE && apres <= KLAXON_APPROCHE) suivre(TrainSons.KLAXON.get(), 1f, 0.97f, pointLoco);
                if (avant > FREINAGE && apres <= FREINAGE) {
                    suivre(TrainSons.FREINS.get(), 1f, 1f, () -> {
                        Horaire hh = Horaire.get();
                        return hh == null ? null : procheDe(hh, hh.etatRame(TrainClient.tTick, rame), oreille(), new Horaire.Placement());
                    });
                }
            }
            if (qp < 0 && q >= 0) {                                        // arrêt en gare
                Vec3 bas = new Vec3(proche.x, proche.y - 0.9, proche.z);
                jouer(TrainSons.DETENTE.get(), bas, 1f, 1f, 12);
                if (loco.distanceToSqr(proche) > 20 * 20) jouer(TrainSons.DETENTE.get(), loco, 0.8f, 0.94f, 16);
                jouer(TrainSons.CARILLON.get(), hautParleur(h, q, oreille), 0.9f, 1f, 30);
            }
        }

        /** Portes d'intercirculation et des vestibules : un son quand l'une se met à s'ouvrir ou à se refermer. */
        private static void portesInterieures(Horaire h, Vec3 oreille) {
            for (int veh = 1; veh < Horaire.N * Horaire.RAMES; veh++) {
                if (!Voyageurs.voiture(veh)) continue;
                for (int n : PORTES_INT) {
                    float a = Voyageurs.animPorte(veh, n), d = a - PORTE_INT[veh][n];
                    PORTE_INT[veh][n] = a;
                    byte s = d > 1e-4f ? (byte) 1 : d < -1e-4f ? (byte) -1 : 0;
                    if (s == 0 || s == SENS_INT[veh][n]) continue;
                    SENS_INT[veh][n] = s;
                    double lx = n == 0 ? Voyageurs.VL + 0.35 : n == 4 ? Voyageurs.VEST : -Voyageurs.VEST;
                    Vec3 p = pointRame(Horaire.rame(veh), veh, lx, Reseau.PLANCHER + 1.3, 0);
                    if (p == null || p.distanceToSqr(oreille) > PORTEE_PORTE_INT * PORTEE_PORTE_INT) continue;
                    jouer(TrainSons.PORTE_INTERIEURE.get(), p, 0.8f, s > 0 ? 1f : 0.93f, 0);
                }
            }
        }

        /** Joints de rail, à chaque image (le rythme des essieux reste juste même à 15 blocs/s) : un essieu qui passe
         *  un joint (abscisse multiple de JOINT) entre l'image précédente et celle-ci joue un « tac » à sa place.
         *  Nom propre à ce paquet (voir TrainClient.Evenements.horaire). */
        @SubscribeEvent
        public static void jointsImage(TickEvent.RenderTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            Minecraft mc = Minecraft.getInstance();
            Horaire h = Horaire.get();
            if (!ecoute || !TrainClient.actif() || h == null || mc.player == null || mc.level == null || mc.isPaused()) {
                sJoint = Double.NaN;
                return;
            }
            int r = rameProche;
            Horaire.Etat et = h.etatRame(TrainClient.temps(e.renderTickTime), r);
            double s = et.s();
            if (r != rameJoint || Double.isNaN(sJoint)) {
                sJoint = s;
                rameJoint = r;
                return;
            }
            double ds = h.reseau.ecart(sJoint, s);
            if (ds < 0) {                                                  // image à contretemps : on attend
                if (ds < -4) sJoint = s;                                   // horloge recalée en arrière
                return;
            }
            sJoint = s;
            double v = et.v();
            if (ds == 0 || ds > 4 || v < 0.8) return;
            Vec3 oreille = oreille();
            boolean aBord = mc.player.getVehicle() instanceof Voyageur;
            float vol = (float) Math.min(1.0, 0.3 + v / 12.0);
            int joues = 0;
            for (int i = 0; i < Horaire.N && joues < 8; i++) {
                Horaire.Vehicule veh = Horaire.RAME[i];
                double c = s + Horaire.DECALAGE[i];
                h.reseau.point(c, PT);                                     // véhicule trop loin : rien à entendre
                double dx = PT[0] - oreille.x, dz = PT[2] - oreille.z;
                if (dx * dx + dz * dz > (PORTEE_JOINT + 14) * (PORTEE_JOINT + 14)) continue;
                double[] ecarts = veh == Horaire.Vehicule.LOCO ? ESSIEUX_LOCO : ESSIEUX_VOITURE;
                for (int b = -1; b <= 1; b += 2) {
                    for (double ex : ecarts) {
                        double sa = c + b * veh.bogie + ex;
                        if (Math.floor(sa / JOINT) == Math.floor((sa - ds) / JOINT)) continue;
                        h.reseau.point(sa, PT);
                        Vec3 p = new Vec3(PT[0], PT[1] + Reseau.RAIL + 0.3, PT[2]);
                        if (p.distanceToSqr(oreille) > PORTEE_JOINT * PORTEE_JOINT) continue;
                        float pitch = (aBord ? 0.85f : 1f) * (h.reseau.genre(sa) == Reseau.PONT ? 0.92f : 1f)
                                * (0.94f + HASARD.nextFloat() * 0.12f);
                        jouer(TrainSons.JOINT.get(), p, vol, pitch, 0);
                        joues++;
                    }
                }
            }
        }
    }
}

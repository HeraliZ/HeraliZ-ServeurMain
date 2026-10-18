package fr.heralisz.utils.train;

import com.tacz.guns.entity.EntityKineticBullet;
import fr.heralisz.utils.HeraliszUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

/**
 * Les balles de TaCZ et le train (le train n'est pas fait de blocs : TaCZ ne le voit pas, ses balles le traversaient).
 * <ul>
 *    <li><b>Murs, sièges, vitres</b> : au début de chaque tick du monde, avant que les balles ne bougent, le trajet de
 *    chaque balle proche d'une rame est comparé à la caisse de chaque véhicule (Blindage), dans le repère du véhicule
 *    (début du trajet avec le train d'avant, fin avec le train d'après : le train bouge pendant le tick). Si le train est
 *    touché, la balle est raccourcie jusqu'au point d'impact : TaCZ fait son tick comme d'habitude (blocs et joueurs
 *    AVANT le mur touchés normalement), puis, à la fin du tick, la balle qui a atteint le train s'y arrête (retirée, avec
 *    étincelles, bourre, éclats de bois ou de verre et le bruit qui va avec ; une balle explosive explose là, avec les
 *    réglages de son arme).</li>
 *    <li><b>Vitres</b> : comme les vitres de la carte (mod tactique), une balle casse la vitre touchée et s'y arrête ; la
 *    suivante passe par le trou. Réparée au bout de 5 minutes (pas gardée au redémarrage), {@code /train vitres reparer}
 *    pour le staff ; les joueurs reçoivent la liste (paquet Vitres) et ne dessinent plus le verre cassé.</li>
 *    <li><b>Tireur dans le train</b> : sa balle part avec la vitesse du train (sinon, dans une voiture qui roule, elle
 *    « reculait » de 0,75 bloc par tick et passait à côté) ; murs et passagers de SA rame sont pris au même instant.</li>
 *    <li><b>Tireur hors du train</b> (compensation du ping, comme TaCZ pour les joueurs) : il a visé le train tel que son
 *    jeu le montrait, une demi-seconde plus tôt à 100 ms de ping ; murs et passagers sont pris à l'instant
 *    (temps du serveur - ping), sinon la balle tapait le montant à côté de la fenêtre visée.</li>
 *    <li><b>Passagers visés</b> (mixin TaczBoiteTrainMixin sur HitboxHelper de TaCZ) : TaCZ recule la boîte d'un joueur
 *    de plusieurs ticks de sa vitesse (correction faite pour les entités qui s'interpolent chez le tireur) ; un passager
 *    est placé chez chacun d'après l'horaire, sans retard : à pleine vitesse, sa boîte était 2 à 4 blocs derrière lui.
 *    Ici sa boîte est celle de sa place dans la voiture, au même instant que les murs ({@link #boite}).</li>
 * </ul>
 * Seulement dans l'overworld (le train n'y roule qu'ici) ; rien si TaCZ est absent. Les autres projectiles (flèches,
 * grenades du mod tactique) ne sont pas concernés.
 */
public final class BallesTrain {
    private BallesTrain() {}

    /** Au plus une seconde de ping compensée (comme le retard maximal que TaCZ garde pour ses boîtes). */
    static final int RETARD_MAX = 20;
    /** Vitre cassée : réparée 5 minutes après (comme les vitres de la carte, mod tactique). */
    static final long DUREE_VITRE = 6000L;
    /** La balle s'arrête un millième de bloc avant la paroi. */
    private static final double MARGE = 1e-3;

    /** Une balle suivie : rame du tireur (-1 : tireur hors du train), retard compensé (ticks), impact prévu à ce tick. */
    private static final class Suivi {
        final Entity balle;
        final int rame, retard;
        Blindage.Impact impact;
        int veh = -1;

        Suivi(Entity balle, int rame, int retard) {
            this.balle = balle;
            this.rame = rame;
            this.retard = retard;
        }
    }

    private static final List<Suivi> SUIVIES = new ArrayList<>();
    /** Vitres cassées : clé (Blindage.cle) -> heure de jeu (ticks) de la réparation. */
    private static final Map<Integer, Long> CASSEES = new HashMap<>();
    private static final Horaire.Placement PA = new Horaire.Placement(), PB = new Horaire.Placement();
    private static final double[] CENTRE = new double[3];

    static void init() {
        if (!ModList.get().isLoaded("tacz")) return;
        // LOWEST : seulement les balles vraiment ajoutées au monde (pas annulées par un autre mod)
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, BallesTrain::arrivee);
        MinecraftForge.EVENT_BUS.addListener(BallesTrain::tick);
    }

    private static boolean actif(MinecraftServer srv) {
        return srv != null && Horaire.get() != null && TrainServeur.actif(srv);
    }

    /** Ping du tireur en ticks, borné (0 pour un tireur qui n'est pas un joueur). */
    private static int retard(Entity tireur) {
        return tireur instanceof ServerPlayer sp ? Mth.clamp(Math.round(sp.latency / 50f), 0, RETARD_MAX) : 0;
    }

    /** Rame du voyageur que porte ce tireur, ou -1. */
    private static int rameDe(Entity e) {
        return e != null && e.getVehicle() instanceof Voyageur v && !v.isRemoved() ? Horaire.rame(v.vehicule()) : -1;
    }

    private static void arrivee(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk() || !(e.getEntity() instanceof EntityKineticBullet balle)
                || e.getLevel().dimension() != Level.OVERWORLD) return;
        MinecraftServer srv = e.getLevel().getServer();
        if (!actif(srv)) return;
        Horaire h = Horaire.get();
        Entity tireur = balle.getOwner();
        int rame = rameDe(tireur), retard = 0;
        if (rame >= 0 && tireur.getVehicle() instanceof Voyageur v) {
            // la balle part avec la vitesse du point de la voiture où se tient le tireur (repère du train)
            double t = TrainServeur.temps(srv);
            h.placer(v.vehicule(), h.etatRame(t, rame).s(), PA);
            h.placer(v.vehicule(), h.etatRame(t + 0.05, rame).s(), PB);
            double ly = Reseau.PLANCHER + 1.5;
            Vec3 dv = Voyageurs.versMonde(PB, v.lx(), ly, v.lz()).subtract(Voyageurs.versMonde(PA, v.lx(), ly, v.lz()));
            if (dv.lengthSqr() < 4.0) balle.setDeltaMovement(balle.getDeltaMovement().add(dv));
        } else {
            retard = retard(tireur);
        }
        SUIVIES.add(new Suivi(balle, rame, retard));
    }

    private static void tick(TickEvent.LevelTickEvent e) {
        if (e.level.isClientSide || e.level.dimension() != Level.OVERWORLD || !(e.level instanceof ServerLevel lvl)) return;
        if (e.phase == TickEvent.Phase.START) {
            debut(lvl);
        } else {
            fin(lvl);
            if (lvl.getGameTime() % 20 == 0) reparer(lvl.getServer(), lvl.getGameTime());
        }
    }

    /** Début du tick (heure du jeu g, avant qu'elle ne passe à g + 1) : les balles qui vont toucher le train pendant ce
     *  tick sont raccourcies jusqu'à l'impact. */
    private static void debut(ServerLevel lvl) {
        if (SUIVIES.isEmpty()) return;
        MinecraftServer srv = lvl.getServer();
        boolean actif = actif(srv);
        Horaire h = Horaire.get();
        double t0 = actif ? TrainServeur.temps(srv) : 0;
        for (Iterator<Suivi> it = SUIVIES.iterator(); it.hasNext(); ) {
            Suivi s = it.next();
            s.impact = null;
            s.veh = -1;
            if (s.balle.isRemoved()) {
                it.remove();
                continue;
            }
            if (!actif) continue;
            Vec3 p = s.balle.position(), d = s.balle.getDeltaMovement();
            double longueur = d.length();
            if (longueur < 1e-4) continue;
            Vec3 q = p.add(d);
            double best = 2;
            for (int r = 0; r < Horaire.RAMES; r++) {
                double ta = t0 - (s.rame == r ? 0 : s.retard) / 20.0, tb = ta + 0.05;
                Horaire.Etat ea = h.etatRame(ta, r), eb = h.etatRame(tb, r);
                h.reseau.point(eb.s(), CENTRE);
                if (distanceSegment(p, q, CENTRE[0], CENTRE[1], CENTRE[2]) > Horaire.LONGUEUR / 2 + 8) continue;
                int pq = Voyageurs.portesQuai(h, eb);
                for (int i = 0; i < Horaire.N; i++) {
                    int veh = r * Horaire.N + i;
                    h.placer(veh, eb.s(), PB);
                    if (distanceSegment(p, q, PB.x, PB.y, PB.z) > 16) continue;
                    h.placer(veh, ea.s(), PA);
                    double[] a = Voyageurs.versLocal(PA, p), b = Voyageurs.versLocal(PB, q);
                    final int v = veh;
                    Blindage.Impact imp = Blindage.traverser(veh, a, b, pq,
                            n -> Voyageurs.ouverteServeur(v, n), CASSEES::containsKey);
                    if (imp != null && imp.t() < best) {
                        best = imp.t();
                        s.impact = imp;
                        s.veh = veh;
                    }
                }
            }
            if (s.impact != null) s.balle.setDeltaMovement(d.scale(Math.max(0, best - MARGE / longueur)));
        }
    }

    /** Fin du tick : une balle raccourcie encore en vol a atteint le train (rien touché avant) : elle s'y arrête. */
    private static void fin(ServerLevel lvl) {
        if (SUIVIES.isEmpty()) return;
        MinecraftServer srv = lvl.getServer();
        Horaire h = Horaire.get();
        for (Iterator<Suivi> it = SUIVIES.iterator(); it.hasNext(); ) {
            Suivi s = it.next();
            Blindage.Impact imp = s.impact;
            s.impact = null;
            if (s.balle.isRemoved()) {
                it.remove();
                continue;
            }
            if (imp == null || h == null || !actif(srv)) continue;
            // point d'impact sur le train tel qu'il est maintenant (là où les joueurs le voient)
            h.placer(s.veh, h.etatRame(TrainServeur.temps(srv), Horaire.rame(s.veh)).s(), PA);
            Vec3 w = Voyageurs.versMonde(PA, imp.lx(), imp.ly(), imp.lz());
            effets(lvl, imp, w);
            if (imp.nature() == Blindage.VERRE) casser(lvl, Blindage.cle(s.veh, imp.cote(), imp.baie()));
            exploser(s.balle, w);
            s.balle.discard();
            it.remove();
        }
    }

    private static double distanceSegment(Vec3 p, Vec3 q, double x, double y, double z) {
        double dx = q.x - p.x, dy = q.y - p.y, dz = q.z - p.z;
        double l2 = dx * dx + dy * dy + dz * dz;
        double t = l2 < 1e-9 ? 0 : Mth.clamp(((x - p.x) * dx + (y - p.y) * dy + (z - p.z) * dz) / l2, 0, 1);
        double ex = p.x + dx * t - x, ey = p.y + dy * t - y, ez = p.z + dz * t - z;
        return Math.sqrt(ex * ex + ey * ey + ez * ez);
    }

    /** Ce que voient et entendent les joueurs autour : étincelles sur la tôle, bourre des sièges, éclats de bois des
     *  tablettes et des caisses, éclats de verre d'une vitre. */
    private static void effets(ServerLevel lvl, Blindage.Impact imp, Vec3 w) {
        float hasard = lvl.random.nextFloat();
        switch (imp.nature()) {
            case Blindage.VERRE -> {
                lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), w.x, w.y, w.z, 28,
                        0.35, 0.35, 0.35, 0.15);
                lvl.playSound(null, w.x, w.y, w.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 0.9f + hasard * 0.2f);
            }
            case Blindage.SIEGE -> {
                lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_WOOL.defaultBlockState()), w.x, w.y, w.z, 8,
                        0.05, 0.05, 0.05, 0.05);
                lvl.playSound(null, w.x, w.y, w.z, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 0.9f, 0.8f + hasard * 0.3f);
            }
            case Blindage.BOIS -> {
                lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()), w.x, w.y, w.z, 8,
                        0.05, 0.05, 0.05, 0.05);
                lvl.playSound(null, w.x, w.y, w.z, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.9f, 0.9f + hasard * 0.3f);
            }
            default -> {
                lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), w.x, w.y, w.z, 6,
                        0.04, 0.04, 0.04, 0.05);
                lvl.sendParticles(ParticleTypes.CRIT, w.x, w.y, w.z, 5, 0.02, 0.02, 0.02, 0.35);
                lvl.playSound(null, w.x, w.y, w.z, SoundEvents.METAL_HIT, SoundSource.BLOCKS, 1.0f, 1.3f + hasard * 0.4f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ balles explosives

    private static Field fExplosion, fDegats, fRayon, fRecul, fCasse;
    private static boolean champsCherches;

    /** Balle explosive (roquette, grenade de lance-grenades...) : explose au point d'impact, avec les réglages de son
     *  arme (champs privés de TaCZ, lus une fois ; illisibles : la balle s'arrête simplement). */
    private static void exploser(Entity balle, Vec3 w) {
        if (!champsCherches) {
            champsCherches = true;
            try {
                fExplosion = champ("explosion");
                fDegats = champ("explosionDamage");
                fRayon = champ("explosionRadius");
                fRecul = champ("explosionKnockback");
                fCasse = champ("explosionDestroyBlock");
            } catch (Exception ex) {
                fExplosion = null;
                HeraliszUtils.LOG.warn("Train : réglages d'explosion des balles de TaCZ illisibles ({})", ex.toString());
            }
        }
        if (fExplosion == null) return;
        try {
            if (!fExplosion.getBoolean(balle)) return;
            com.tacz.guns.util.ExplodeUtil.createExplosion(((EntityKineticBullet) balle).getOwner(), balle, fDegats.getFloat(balle),
                    fRayon.getFloat(balle), fRecul.getBoolean(balle), fCasse.getBoolean(balle), w);
        } catch (Exception ex) {
            HeraliszUtils.LOG.warn("Train : explosion d'une balle impossible ({})", ex.toString());
        }
    }

    private static Field champ(String nom) throws NoSuchFieldException {
        Field f = EntityKineticBullet.class.getDeclaredField(nom);
        f.setAccessible(true);
        return f;
    }

    // ------------------------------------------------------------------------------------------------ vitres

    private static void casser(ServerLevel lvl, int cle) {
        if (CASSEES.put(cle, lvl.getGameTime() + DUREE_VITRE) == null) diffuser();
    }

    private static void reparer(MinecraftServer srv, long maintenant) {
        if (CASSEES.isEmpty()) return;
        if (CASSEES.values().removeIf(fin -> fin <= maintenant)) diffuser();
    }

    private static int[] cles() {
        return CASSEES.keySet().stream().mapToInt(Integer::intValue).toArray();
    }

    private static void diffuser() {
        Train.CANAL.send(PacketDistributor.ALL.noArg(), new Train.Vitres(cles()));
    }

    /** À la connexion d'un joueur : les vitres déjà cassées. */
    static void envoyerVitres(ServerPlayer p) {
        if (!CASSEES.isEmpty()) Train.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new Train.Vitres(cles()));
    }

    static int vitresCassees() {
        return CASSEES.size();
    }

    static int reparerTout() {
        int n = CASSEES.size();
        CASSEES.clear();
        diffuser();
        return n;
    }

    // ------------------------------------------------------------------------------------------------ passagers visés

    /**
     * Boîte d'un passager du train touchée par une balle de TaCZ (mixin TaczBoiteTrainMixin, à la place de
     * HitboxHelper.getFixedBoundingBox), ou null hors du train (TaCZ décide). Même instant que les murs (debut) :
     * maintenant pour un tireur de la même rame, (maintenant - ping) pour un tireur hors du train.
     */
    public static AABB boite(Entity cible, Entity tireur) {
        if (cible.level().isClientSide || !(cible.getVehicle() instanceof Voyageur v) || v.isRemoved()) return null;
        MinecraftServer srv = cible.getServer();
        if (!actif(srv)) return null;
        Horaire h = Horaire.get();
        int rame = Horaire.rame(v.vehicule());
        int retard = rameDe(tireur) == rame ? 0 : retard(tireur);
        Horaire.Placement pl = new Horaire.Placement();
        h.placer(v.vehicule(), h.etatRame(TrainServeur.temps(srv) - retard / 20.0, rame).s(), pl);
        Vec3 pied = Voyageurs.versMonde(pl, v.lx(), Reseau.PLANCHER, v.lz()).add(0, v.assis() ? -0.15 : v.hauteur(), 0);
        return cible.getDimensions(cible.getPose()).makeBoundingBox(pied).expandTowards(0, 0.0625, 0).inflate(0.05);
    }
}

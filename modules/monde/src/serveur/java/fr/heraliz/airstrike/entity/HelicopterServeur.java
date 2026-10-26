package fr.heraliz.airstrike.entity;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.api.HelicoApi;
import fr.heraliz.airstrike.helico.PointCrash;
import fr.heraliz.airstrike.helico.Veille;
import fr.heraliz.airstrike.network.HelicoPositionPacket;
import fr.heraliz.airstrike.network.HelicoTirPacket;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModEntities;
import fr.heraliz.airstrike.registry.ModParticles;
import fr.heraliz.airstrike.registry.ModSounds;
import fr.heraliz.airstrike.strike.FlightPath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Partie SERVEUR de {@link Helicopter} (docs/METHODE_CLIENT_SERVEUR.md §5) : le pilote. Créée par l'usine du code commun,
 * seulement par {@link HelicoApi#lancer} (par {@link Veille}, quand le tronçon d'arrivée est prêt ; le type refuse /summon).
 * <ul>
 * <li><b>Vol</b> : entre par le bord de la carte (56 blocs en dedans), patrouille entre des points tirés sur la terre
 * ferme, de préférence à ±150 blocs d'un joueur en survie, jamais au-dessus du point d'apparition, d'une zone VIP ni près
 * d'une base ; altitude = terrain relevé devant lui (tous les 16 blocs, jamais à chaque tick) + 30 à 45 ; virages doux
 * (lacet limité), roulis et tangage pour l'affichage. Il tourne 5 à 10 min (tiré au hasard) puis part par le bord le plus
 * proche, sauf s'il a été touché depuis moins de 60 s ; au bout de 25 min, il part quoi qu'il arrive.</li>
 * <li><b>Combat</b> (règle du propriétaire) : il n'attaque QUE les joueurs qui lui ont fait des dégâts (oubliés après
 * {@code oubli} secondes sans nouveau tir). Toutes les 10 ticks : l'agresseur le plus proche (96 blocs au plus) en survie
 * sur la carte, et un seul rayon de vue depuis le canon. Il tourne autour (rayon ~40, cible + 22), alerte chez la cible
 * seule, puis rafale de 8 coups en 0,8 s, toutes les 3 à 5 s ; chaque coup vérifie la vue depuis le canon et que la cible
 * est encore un agresseur debout, puis part en rayon vers elle avec dispersion, arrêté par le premier bloc : un joueur
 * sous un toit ne prend rien, et personne d'autre que la cible n'est touché. Aucun bloc cassé.</li>
 * <li><b>Dégâts reçus</b> : seulement d'un joueur (balle, projectile, explosion dont il est l'auteur), par les morceaux de
 * la boîte de touche ; une balle qui perce ne compte que sur le premier morceau touché ; explosions ×
 * {@code mult_explosifs} (une fois par tick et par auteur : elles touchent tous les morceaux), queue × {@code mult_queue}.
 * Un joueur en créatif ou en spectateur compte aussi (essais du staff), mais l'hélicoptère devient « essai » (boîte noire
 * sans points). Sans coup reçu depuis 30 s, il regagne 1,5 % de ses PV max par seconde.</li>
 * <li><b>Chute</b> : à 0 PV, point de crash cherché une fois ({@link PointCrash}) ; chute scénarisée de 5 à 8 s (spirale,
 * rotation de plus en plus rapide, descente qui accélère) jouée tick par tick ; à l'impact : explosion sans casse, petits
 * dégâts tout près, bruit pour les zombies, épave en feu ({@link HelicopterWreckServeur}). Sans point valide : explosion
 * en l'air, sans épave ; la boîte noire tombe sur le dernier sol valide survolé.</li>
 * </ul>
 * Le tronçon où il vole reste chargé et actif (ticket déplacé à chaque changement de tronçon, rendu à la fin). Jamais
 * sauvegardé : au redémarrage, il n'est plus là.
 */
public class HelicopterServeur extends Helicopter {
    /** Garde chargés (7 × 7) et actifs (3 × 3) les tronçons autour de l'hélicoptère ; rafraîchi chaque seconde, expire seul. */
    public static final TicketType<Integer> TICKET = TicketType.create("airstrike_helico", Integer::compareTo, 300);
    public static final int RAYON_TICKET = 3;
    public static final ResourceKey<DamageType> DEGATS_TIR = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AirstrikeMod.MODID, "helico"));
    public static final ResourceKey<DamageType> DEGATS_CRASH = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AirstrikeMod.MODID, "helico_crash"));
    /** Joueurs qui reçoivent les traçantes, et la position pour le son lointain (blocs). */
    public static final double PORTEE_TRACANTES = 192, PORTEE_SON = 320;
    /** Entrée et sortie : à tant de blocs au moins en dedans de la bordure du monde. */
    public static final int MARGE_BORD = 56;
    /** Dispersion d'un coup du minigun (radians, écart type). */
    private static final double DISPERSION = 0.035;

    private static final Map<String, HelicopterServeur> EN_VOL = new HashMap<>();

    /** Dégâts d'un joueur (clé « joueur|clan »). */
    private static final class Part {
        final UUID joueur;
        final String nom;
        @Nullable
        final UUID clan;
        double degats;

        Part(UUID joueur, String nom, @Nullable UUID clan) {
            this.joueur = joueur;
            this.nom = nom;
            this.clan = clan;
        }
    }

    // ------------------------------------------------------------------ réglages et suivi (null : créé hors de l'API)
    @Nullable
    private HelicoApi.Reglages r;
    @Nullable
    private HelicoApi.Suivi suivi;
    @Nullable
    private Function<UUID, UUID> clanDe;
    private String id = "";
    private boolean fini;
    @Nullable
    private ChunkPos ticket;

    private float pv = 1, pvMax = 1;
    private double vitesse;
    private long naissance, finPatrouille, departTick;
    private boolean depart;
    /** Un joueur en créatif ou en spectateur l'a touché : boîte noire sans points. */
    private boolean essai;
    // patrouille
    private double butX, butZ;
    private boolean aBut;
    private double solVise, parcouru;
    private int altitude = 35;
    /** Dernier sol valide survolé (contrôles d'un point de crash) : la boîte noire y tombe s'il explose en l'air. */
    @Nullable
    private BlockPos dernierSolValide;
    // combat
    private final Map<UUID, Long> agresseurs = new HashMap<>();
    private final Map<String, Part> parts = new LinkedHashMap<>();
    @Nullable
    private ServerPlayer cible;
    private boolean vue, droiteRafale;
    private int alerte = -1, rafale;
    private long prochaineRafale, derniereExplosion = -1, derniersEclats, dernierCoup = -1;
    @Nullable
    private UUID auteurExplosion;
    /** Dernière balle comptée : numéro d'entité, tick et morceau (une balle qui perce ne compte qu'une fois). */
    private int dernierProjectile = Integer.MIN_VALUE, dernierRang = -2;
    private long dernierProjectileTick = -10;
    private final int sens;
    // chute
    private Vec3 chuteA = Vec3.ZERO, chuteB = Vec3.ZERO;
    private int chuteT, chuteDuree = 1, sensChute = 1;
    private float lacet0;
    private double phi0;

    public HelicopterServeur(EntityType<? extends Helicopter> type, Level level) {
        super(type, level);
        this.sens = level.random.nextBoolean() ? 1 : -1;
    }

    // ------------------------------------------------------------------ API (HelicoApi, Veille)
    @Nullable
    public static HelicopterServeur enVol(String id) {
        HelicopterServeur h = EN_VOL.get(id);
        return h != null && !h.isRemoved() ? h : null;
    }

    /** Les hélicoptères en vol ou en chute (marques de la carte M). */
    public static Collection<HelicopterServeur> tous() {
        return EN_VOL.values();
    }

    /** Retire l'hélicoptère sans rien donner (staff, arrêt). */
    public static boolean retirer(String id) {
        HelicopterServeur h = EN_VOL.remove(id);
        if (h == null) return false;
        h.fini = true;
        h.prevenir(s -> s.fini(id, "annule"));
        h.discard();
        return true;
    }

    /**
     * Pose l'hélicoptère (le tronçon est prêt : {@link Veille}). Son propre ticket est posé avant l'entité, le ticket
     * d'attente est rendu ensuite par la veille. Rend l'entité, ou null si c'est impossible.
     */
    @Nullable
    public static HelicopterServeur creer(ServerLevel level, String id, HelicoApi.Reglages r, Vec3 p, float lacet,
                                          @Nullable Function<UUID, UUID> clanDe, HelicoApi.Suivi suivi) {
        if (!(ModEntities.HELICOPTER.get().create(level) instanceof HelicopterServeur h)) return null;
        h.preparer(level, id, r, clanDe, suivi, p, lacet);
        ChunkPos cp = h.chunkPosition();
        level.getChunkSource().addRegionTicket(TICKET, cp, RAYON_TICKET, h.getId());
        h.ticket = cp;
        if (!level.addFreshEntity(h)) {
            h.relacherTicket();
            return null;
        }
        EN_VOL.put(id, h);
        return h;
    }

    private void preparer(ServerLevel level, String id, HelicoApi.Reglages r, @Nullable Function<UUID, UUID> clanDe,
                          HelicoApi.Suivi suivi, Vec3 p, float lacet) {
        this.id = id;
        this.r = r;
        this.clanDe = clanDe;
        this.suivi = suivi;
        this.pv = this.pvMax = Math.max(1f, r.pv);
        this.vitesse = r.vitesse;
        this.naissance = level.getGameTime();
        int patrouille = r.patrouilleMinS + random.nextInt(Math.max(1, r.patrouilleMaxS - r.patrouilleMinS + 1));
        this.finPatrouille = naissance + patrouille * 20L;
        this.altitude = r.altitudeMin + random.nextInt(Math.max(1, r.altitudeMax - r.altitudeMin + 1));
        this.solVise = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
        this.moveTo(p.x, Math.max(p.y, solVise + altitude), p.z, lacet, 0f);
        this.yRotO = lacet;
        placerMorceaux();
        AirstrikeMod.LOGGER.info("[helico] {} en vol depuis {} pour {} s de patrouille", id, blockPosition().toShortString(), patrouille);
    }

    /**
     * Point d'entrée : sur le bord de la carte le plus proche d'un joueur tiré au hasard ({@link #MARGE_BORD} blocs en
     * dedans de la bordure), tourné vers lui. {x, z, lacet}. Ne charge rien.
     */
    public static double[] entree(ServerLevel level, RandomSource rnd) {
        WorldBorder b = level.getWorldBorder();
        List<ServerPlayer> js = joueurs(level);
        double cx = b.getCenterX(), cz = b.getCenterZ();
        if (!js.isEmpty()) {
            ServerPlayer p = js.get(rnd.nextInt(js.size()));
            cx = p.getX();
            cz = p.getZ();
        }
        double x1 = b.getMinX() + MARGE_BORD, x2 = b.getMaxX() - MARGE_BORD, z1 = b.getMinZ() + MARGE_BORD, z2 = b.getMaxZ() - MARGE_BORD;
        double x, z;
        if (x2 - x1 > 8000 || z2 - z1 > 8000 || x2 <= x1 || z2 <= z1) {
            // bordure du monde pas réglée (immense) : à 600 blocs du joueur, direction au hasard
            double a = rnd.nextDouble() * Math.PI * 2;
            x = cx + Math.cos(a) * 600;
            z = cz + Math.sin(a) * 600;
        } else {
            double o = cx - x1, e = x2 - cx, n = cz - z1, s = z2 - cz;
            double m = Math.min(Math.min(o, e), Math.min(n, s));
            x = Mth.clamp(cx, x1, x2);
            z = Mth.clamp(cz, z1, z2);
            if (m == o) x = x1;
            else if (m == e) x = x2;
            else if (m == n) z = z1;
            else z = z2;
        }
        return new double[]{x, z, FlightPath.yawOf(cx - x, cz - z)};
    }

    /** Joueurs en survie ou en aventure de ce monde (la liste du monde, pas une recherche d'entités). */
    private static List<ServerPlayer> joueurs(ServerLevel level) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && !p.isCreative()) l.add(p);
        }
        return l;
    }

    // ------------------------------------------------------------------ tick
    @Override
    void tickServeur() {
        if (r == null || fini) {
            if (!fini) discard();   // créé hors de l'API : rien à piloter
            return;
        }
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        tenirTicket(level);
        if (phase() == CHUTE) {
            chuter(level, now);
            return;
        }
        if (depart && now - departTick > Veille.SORTIE_MAX_S * 20L) {
            finir("parti");
            return;
        }
        regenerer(now);
        if (!depart) {
            // patrouille finie et plus attaqué depuis un moment, ou durée maximale du vol : il s'en va
            boolean attaque = dernierCoup >= 0 && now - dernierCoup < r.resteApresAttaqueS * 20L;
            if (now - naissance >= r.volMaxS * 20L || now >= finPatrouille && !attaque) partir(level, now);
        }
        if (!depart && tickCount % 10 == 0) choisirCible(level, now);
        voler(level);
        if (fini) return;
        if (cible != null) tirer(level, now);
        if (tickCount % 20 == 0) diffuser(level);
    }

    /** Sans coup reçu depuis {@code regen_apres_s}, il regagne {@code regen_pct_par_s} % de ses PV max par seconde. */
    private void regenerer(long now) {
        if (pv >= pvMax || dernierCoup < 0 || tickCount % 20 != 0 || now - dernierCoup < r.regenApresS * 20L) return;
        pv = Math.min(pvMax, pv + pvMax * r.regenPctParS / 100f);
        entityData.set(SANTE, pv / pvMax);
    }

    private void voler(ServerLevel level) {
        HelicoApi.Reglages r = this.r;
        double vVoulue = r.vitesse, bx, bz, yVise;
        ServerPlayer c = cible;
        if (c != null) {
            double dx = getX() - c.getX(), dz = getZ() - c.getZ();
            if (dx * dx + dz * dz > (r.rayonCercle * 1.6) * (r.rayonCercle * 1.6)) {
                bx = c.getX();
                bz = c.getZ();
            } else {
                // un point un peu plus loin sur le cercle : il tourne autour de la cible
                double a = Math.atan2(dz, dx) + sens * 0.6;
                bx = c.getX() + Math.cos(a) * r.rayonCercle;
                bz = c.getZ() + Math.sin(a) * r.rayonCercle;
                vVoulue = r.vitesse * 0.75;
            }
            yVise = Math.max(c.getY() + r.altitudeCombat, solVise + 12);
        } else {
            if (!depart && (!aBut || distance2(butX, butZ) < 24 * 24)) nouveauBut(level);
            bx = butX;
            bz = butZ;
            yVise = solVise + altitude;
        }
        float lacet = getYRot();
        float ecart = Mth.wrapDegrees((float) FlightPath.yawOf(bx - getX(), bz - getZ()) - lacet);
        float max = c != null ? 3.5f : 2.5f;
        float tour = Mth.clamp(ecart, -max, max);
        if (Math.abs(ecart) > 100) vVoulue *= 0.7;   // demi-tour : il ralentit
        lacet += tour;
        double avant = vitesse;
        vitesse += Mth.clamp(vVoulue - vitesse, -0.01, 0.008);
        double rad = Math.toRadians(lacet);
        double nx = getX() - Math.sin(rad) * vitesse, nz = getZ() + Math.cos(rad) * vitesse;
        parcouru += vitesse;
        if (parcouru >= 16 || tickCount % 40 == 0) {
            parcouru = 0;
            solVise = releverSol(level, nx, nz, rad);
            // le sol survolé, s'il ferait un bon point de crash : la boîte noire y tombera s'il explose en l'air
            BlockPos v = PointCrash.valide(level, nx, nz, r);
            if (v != null) dernierSolValide = v;
        }
        double ny = getY() + Mth.clamp(yVise - getY(), -0.18, 0.25);
        setPos(nx, ny, nz);
        setYRot(lacet);
        // assiette affichée : penché dans les virages, nez bas en avançant et en accélérant
        float roulis = entityData.get(ROULIS), tangage = entityData.get(TANGAGE);
        float roulisVise = Mth.clamp(tour * 9f, -28f, 28f);
        float tangageVise = Mth.clamp((float) (4 + 9 * vitesse / Math.max(0.05, r.vitesse) + (vitesse - avant) * 600), -10f, 18f);
        majFloat(ROULIS, roulis + (roulisVise - roulis) * 0.12f);
        majFloat(TANGAGE, tangage + (tangageVise - tangage) * 0.1f);
        if (depart && distance2(butX, butZ) < 20 * 20) finir("parti");
    }

    private double distance2(double x, double z) {
        double dx = getX() - x, dz = getZ() - z;
        return dx * dx + dz * dz;
    }

    /** Terrain le plus haut sous lui et devant lui (0 à 64 blocs, tous les 16) ; un tronçon pas chargé ne compte pas. */
    private double releverSol(ServerLevel level, double x, double z, double rad) {
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        int h = level.getMinBuildHeight();
        for (int d = 0; d <= 64; d += 16) {
            // Level.getHeight ne charge rien : tronçon absent = hauteur minimale, sans effet sur le maximum
            h = Math.max(h, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x + fx * d), Mth.floor(z + fz * d)));
        }
        return h;
    }

    /**
     * Prochain point de patrouille : sur la terre ferme, de préférence à ±150 blocs d'un joueur, hors spawn, zones VIP et
     * bases des clans (un contrôle par point essayé).
     */
    private void nouveauBut(ServerLevel level) {
        List<ServerPlayer> js = joueurs(level);
        WorldBorder b = level.getWorldBorder();
        PointCrash.lireVip(level.getServer());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int essai = 0; essai < 8; essai++) {
            double x, z;
            if (!js.isEmpty() && random.nextFloat() < 0.85f) {
                ServerPlayer p = js.get(random.nextInt(js.size()));
                x = p.getX() + (random.nextDouble() * 2 - 1) * 150;
                z = p.getZ() + (random.nextDouble() * 2 - 1) * 150;
            } else {
                double a = Math.toRadians(getYRot()) + (random.nextDouble() - 0.5) * 2.0;
                x = getX() - Math.sin(a) * 250;
                z = getZ() + Math.cos(a) * 250;
            }
            x = Mth.clamp(x, b.getMinX() + 64, b.getMaxX() - 64);
            z = Mth.clamp(z, b.getMinZ() + 64, b.getMaxZ() - 64);
            if (PointCrash.interdit(level, x, z, r)) continue;
            int ix = Mth.floor(x), iz = Mth.floor(z);
            if (level.getChunkSource().getChunkNow(ix >> 4, iz >> 4) == null) continue;   // jamais de chargement
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
            if (!level.getBlockState(m.set(ix, h - 1, iz)).getFluidState().isEmpty()) continue;   // pas au-dessus de l'eau
            if (PointCrash.procheDUneBase(level, m, ix, h, iz, r.loinDesBases)) continue;
            butX = x;
            butZ = z;
            aBut = true;
            altitude = r.altitudeMin + random.nextInt(Math.max(1, r.altitudeMax - r.altitudeMin + 1));
            return;
        }
        // rien de valide pour l'instant : vers le centre de la carte
        butX = b.getCenterX();
        butZ = b.getCenterZ();
        aBut = true;
    }

    /** Fin de la patrouille : vers le bord de la carte le plus proche ({@link #MARGE_BORD} blocs en dedans). */
    private void partir(ServerLevel level, long now) {
        depart = true;
        departTick = now;
        cible = null;
        alerte = -1;
        rafale = 0;
        WorldBorder b = level.getWorldBorder();
        double x1 = b.getMinX() + MARGE_BORD, x2 = b.getMaxX() - MARGE_BORD, z1 = b.getMinZ() + MARGE_BORD, z2 = b.getMaxZ() - MARGE_BORD;
        if (x2 - x1 > 8000 || z2 - z1 > 8000 || x2 <= x1 || z2 <= z1) {
            double rad = Math.toRadians(getYRot());
            butX = getX() - Math.sin(rad) * 600;
            butZ = getZ() + Math.cos(rad) * 600;
        } else {
            double o = getX() - x1, e = x2 - getX(), n = getZ() - z1, s = z2 - getZ();
            double m = Math.min(Math.min(o, e), Math.min(n, s));
            butX = Mth.clamp(getX(), x1, x2);
            butZ = Mth.clamp(getZ(), z1, z2);
            if (m == o) butX = x1;
            else if (m == e) butX = x2;
            else if (m == n) butZ = z1;
            else butZ = z2;
        }
        aBut = true;
        entityData.set(PHASE, DEPART);
        prevenir(s -> s.depart(id));
    }

    // ------------------------------------------------------------------ combat
    private void choisirCible(ServerLevel level, long now) {
        long oubli = r.oubliAgresseursS * 20L;
        agresseurs.values().removeIf(t -> now - t > oubli);
        ServerPlayer meilleur = null;
        double dMin = (double) r.poursuite * r.poursuite;
        for (UUID u : agresseurs.keySet()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
            if (p == null || p.level() != level || !p.isAlive() || p.isSpectator() || p.isCreative() || aTerre(p)) continue;
            double d = p.distanceToSqr(this);
            if (d < dMin) {
                dMin = d;
                meilleur = p;
            }
        }
        if (meilleur != cible) {
            cible = meilleur;
            alerte = -1;
            rafale = 0;
            prochaineRafale = Math.max(prochaineRafale, now + 20);
        }
        vue = cible != null && dMin <= (double) r.porteeTir * r.porteeTir && ligneDeVue(level, cible);
        byte phase = cible != null ? COMBAT : VOL;
        if (phase() != phase) entityData.set(PHASE, phase);
    }

    private static boolean aTerre(ServerPlayer p) {
        try {
            return fr.heraliz.noyau.api.ATerre.aTerre(p);
        } catch (LinkageError e) {
            return false;
        }
    }

    /** Un seul rayon : du canon tourné vers la cible jusqu'à ses yeux, arrêté par le premier bloc (feuillage compris). */
    private boolean ligneDeVue(ServerLevel level, ServerPlayer p) {
        Vec3 from = canon(cote(p));
        BlockHitResult bh = level.clip(new ClipContext(from, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return bh.getType() == HitResult.Type.MISS;
    }

    /** Vrai si le joueur est à droite de l'hélicoptère (minigun droit). */
    private boolean cote(LivingEntity p) {
        double rad = Math.toRadians(getYRot());
        return (p.getX() - getX()) * -Math.cos(rad) + (p.getZ() - getZ()) * -Math.sin(rad) > 0;
    }

    private Vec3 canon(boolean droite) {
        double[] c = droite ? CANON_D : CANON_G;
        return position().add(versMonde(c[0], c[1], c[2], getYRot(), entityData.get(TANGAGE), entityData.get(ROULIS)));
    }

    private void tirer(ServerLevel level, long now) {
        ServerPlayer c = cible;
        if (c == null) return;
        if (rafale > 0) {
            if ((now & 1) == 0) {
                coup(level, c, now);
                if (rafale > 0 && --rafale == 0) pause(now);
            }
            return;
        }
        if (!vue || c.distanceToSqr(this) > (double) r.porteeTir * r.porteeTir) {
            alerte = -1;
            return;
        }
        if (alerte >= 0) {
            if (--alerte <= 0) {
                alerte = -1;
                rafale = Math.max(1, r.coupsRafale);
                droiteRafale = cote(c);
                level.playSound(null, getX(), getY() + 1, getZ(), ModSounds.HELICO_TIR.get(), SoundSource.HOSTILE, 1f, 0.95f + random.nextFloat() * 0.1f);
            }
            return;
        }
        if (now >= prochaineRafale) {
            alerte = Math.max(1, r.alerteTicks);
            // l'alerte de verrouillage, chez la cible seulement
            c.playNotifySound(ModSounds.HELICO_ALERTE.get(), SoundSource.HOSTILE, 1f, 1f);
        }
    }

    private void pause(long now) {
        rafale = 0;
        prochaineRafale = now + (r.pauseMinS + random.nextInt(Math.max(1, r.pauseMaxS - r.pauseMinS + 1))) * 20L;
    }

    /**
     * Un coup : la rafale s'arrête si la cible n'est plus un agresseur debout ou si la vue depuis le canon est perdue ;
     * sinon un rayon du canon vers la cible avec dispersion ; seule la cible peut être touchée ; aucun bloc cassé.
     */
    private void coup(ServerLevel level, ServerPlayer c, long now) {
        if (!c.isAlive() || c.level() != level || aTerre(c) || !agresseurs.containsKey(c.getUUID()) || !ligneDeVue(level, c)) {
            pause(now);
            return;
        }
        Vec3 from = canon(droiteRafale);
        Vec3 vise = c.position().add(0, c.getBbHeight() * 0.6, 0);
        Vec3 dir = vise.subtract(from);
        double dist = dir.length();
        if (dist < 1e-3) return;
        dir = dir.scale(1.0 / dist).add(random.nextGaussian() * DISPERSION, random.nextGaussian() * DISPERSION,
                random.nextGaussian() * DISPERSION).normalize();
        Vec3 fin = from.add(dir.scale(dist + 12));
        BlockHitResult bh = level.clip(new ClipContext(from, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean bloc = bh.getType() != HitResult.Type.MISS;
        Vec3 arret = bloc ? bh.getLocation() : fin;
        byte impact = bloc ? HelicoTirPacket.BLOC : HelicoTirPacket.AIR;
        Optional<Vec3> touche = c.getBoundingBox().inflate(0.15).clip(from, arret);
        if (touche.isPresent()) {
            arret = touche.get();
            impact = HelicoTirPacket.CIBLE;
            DamageSource src = source(level, DEGATS_TIR);
            c.invulnerableTime = 0;
            c.hurt(src, r.degatsCoup);
        }
        Network.sendNear(level, getX(), getY(), getZ(), PORTEE_TRACANTES,
                new HelicoTirPacket(getId(), droiteRafale, from.x, from.y, from.z, arret.x, arret.y, arret.z, impact));
    }

    private DamageSource source(ServerLevel level, ResourceKey<DamageType> type) {
        Optional<Holder.Reference<DamageType>> h = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(type);
        return h.isPresent() ? new DamageSource(h.get(), this) : level.damageSources().generic();
    }

    // ------------------------------------------------------------------ dégâts reçus
    @Override
    boolean toucheMorceau(@Nullable HelicopterPart m, DamageSource source, float degats) {
        if (r == null || fini || phase() == CHUTE || pv <= 0 || degats <= 0) return false;
        if (!(source.getEntity() instanceof ServerPlayer p)) return false;   // seulement les joueurs (balle, explosion, coup)
        long now = level().getGameTime();
        int rang = m != null ? m.rang : -1;
        float mult = 1f;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            // une explosion touche tous les morceaux à la fois : elle ne compte qu'une fois par tick et par auteur
            if (now == derniereExplosion && p.getUUID().equals(auteurExplosion)) return false;
            derniereExplosion = now;
            auteurExplosion = p.getUUID();
            mult = r.multExplosifs;
        } else {
            Entity balle = source.getDirectEntity();
            if (balle != null && balle != p) {
                // une balle qui perce ne compte que sur le premier morceau touché ; les deux coups de TaCZ (normal et
                // perce-armure) sur ce même morceau comptent tous les deux
                if (balle.getId() == dernierProjectile && now - dernierProjectileTick <= 2) {
                    if (rang != dernierRang) return false;
                } else {
                    dernierProjectile = balle.getId();
                    dernierProjectileTick = now;
                    dernierRang = rang;
                }
            }
            if (m != null && m.queue()) mult = r.multQueue;
        }
        float reel = Math.min(degats * mult, pv);
        pv -= reel;
        dernierCoup = now;
        entityData.set(SANTE, Math.max(0f, pv / pvMax));
        if (p.isCreative() || p.isSpectator()) {
            // essai du staff : les dégâts comptent, la boîte noire n'aura pas de points
            if (!essai) AirstrikeMod.LOGGER.info("[helico] {} touche par {} en creatif ou spectateur : essai, boite noire sans points", id, p.getGameProfile().getName());
            essai = true;
        } else {
            agresseurs.put(p.getUUID(), now);
        }
        UUID clan = clanDe(p.getUUID());
        parts.computeIfAbsent(p.getUUID() + "|" + clan, k -> new Part(p.getUUID(), p.getGameProfile().getName(), clan)).degats += reel;
        eclats((ServerLevel) level(), m != null ? m.getBoundingBox().getCenter() : getBoundingBox().getCenter(), now);
        if (pv <= 0) abattre((ServerLevel) level());
        return true;
    }

    @Nullable
    private UUID clanDe(UUID joueur) {
        if (clanDe == null) return null;
        try {
            return clanDe.apply(joueur);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Étincelles et claquement sur la carlingue (au plus toutes les 3 ticks), visibles de loin. */
    private void eclats(ServerLevel level, Vec3 ou, long now) {
        if (now - derniersEclats < 3) return;
        derniersEclats = now;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(ou) < 160 * 160) level.sendParticles(p, ModParticles.SPARK.get(), true, ou.x, ou.y, ou.z, 4, 0.4, 0.4, 0.4, 0.25);
        }
        level.playSound(null, ou.x, ou.y, ou.z, ModSounds.BULLET_IMPACT.get(), SoundSource.HOSTILE, 4f, 1.5f + random.nextFloat() * 0.3f);
    }

    // ------------------------------------------------------------------ chute
    private void abattre(ServerLevel level) {
        entityData.set(PHASE, CHUTE);
        entityData.set(SANTE, 0f);
        cible = null;
        alerte = -1;
        rafale = 0;
        List<HelicoApi.Contribution> contribs = contributions();
        double rad = Math.toRadians(getYRot());
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        int duree = 100 + random.nextInt(61);
        double avance = vitesse * duree * 0.5;
        BlockPos crash = PointCrash.chercher(level, getX() + fx * avance, getZ() + fz * avance, r, random);
        if (crash == null) {
            // aucun point valide : il explose en l'air au-dessus de sa position, sans épave ; la boîte noire tombe sur le
            // dernier sol valide survolé, sinon sur le sol le plus proche (contrôles relâchés), sinon nulle part
            Vec3 ou = position();
            BlockPos sol = dernierSolValide != null ? dernierSolValide : PointCrash.solRelache(level, ou.x, ou.z, r.rechercheLoin);
            Network.sendImpact(level, new ImpactPacket(ImpactPacket.HEAVY, ou.x, ou.y + 1.5, ou.z, 7f));
            level.playSound(null, ou.x, ou.y, ou.z, ModSounds.HELICO_CRASH.get(), SoundSource.HOSTILE, 1f, 1.1f);
            AirstrikeMod.LOGGER.info("[helico] {} abattu sans point de crash valide : explosion en l'air en {}", id, BlockPos.containing(ou).toShortString());
            terminer();
            boolean e = essai;
            prevenir(s -> s.abattu(new HelicoApi.Abattu(id, ou, null, sol, e, contribs)));
            prevenir(s -> s.fini(id, "en_l_air"));
            return;
        }
        chuteA = position();
        chuteB = Vec3.atBottomCenterOf(crash);
        double d = Math.sqrt((chuteB.x - chuteA.x) * (chuteB.x - chuteA.x) + (chuteB.z - chuteA.z) * (chuteB.z - chuteA.z));
        // point de crash plus loin que prévu (le plus proche valide) : chute un peu plus longue, 8 s au plus
        chuteDuree = Mth.clamp((int) Math.round(d / Math.max(0.15, vitesse * 0.5)), duree, 160);
        chuteT = 0;
        lacet0 = getYRot();
        sensChute = random.nextBoolean() ? 1 : -1;
        phi0 = random.nextDouble() * Math.PI * 2;
        entityData.set(CHUTE_DEBUT, (int) (level.getGameTime() & 0x7FFFFFFFL));
        entityData.set(CHUTE_DUREE, chuteDuree);
        AirstrikeMod.LOGGER.info("[helico] {} abattu en {}, crash prevu en {} ({} s)", id, blockPosition().toShortString(), crash.toShortString(), chuteDuree / 20);
        Vec3 ou = position();
        boolean e = essai;
        prevenir(s -> s.abattu(new HelicoApi.Abattu(id, ou, crash, crash, e, contribs)));
    }

    private List<HelicoApi.Contribution> contributions() {
        List<HelicoApi.Contribution> l = new ArrayList<>(parts.size());
        for (Part x : parts.values()) l.add(new HelicoApi.Contribution(x.joueur, x.nom, x.clan, x.degats));
        return l;
    }

    /**
     * Chute scénarisée, calculée une fois, jouée tick par tick : la trajectoire va du point de destruction au point de crash
     * (départ à la vitesse du vol, puis freinée), une spirale d'autorotation autour (nulle au départ et à l'arrivée), une
     * descente qui accélère et une rotation sur lui-même de plus en plus rapide.
     */
    private void chuter(ServerLevel level, long now) {
        chuteT++;
        double u = Math.min(1.0, chuteT / (double) chuteDuree);
        double e = 2 * u - u * u;
        double rs = 6.0 * Math.sin(Math.PI * u);
        double phi = phi0 + sensChute * Math.PI * 3 * u;
        double x = Mth.lerp(e, chuteA.x, chuteB.x) + rs * Math.cos(phi);
        double z = Mth.lerp(e, chuteA.z, chuteB.z) + rs * Math.sin(phi);
        double y = chuteA.y + (chuteB.y - chuteA.y) * (0.15 * u + 0.85 * u * u);
        setPos(x, y, z);
        setYRot(lacet0 + sensChute * 360f * 3.2f * (float) (u * u));
        majFloat(ROULIS, (float) (Math.sin(u * 11) * 22));
        majFloat(TANGAGE, (float) (10 + 18 * u));
        majFloat(ROTOR, (float) (1 - 0.55 * u));
        if (chuteT >= chuteDuree) {
            impact(level);
        } else if (chuteT % 20 == 0) {
            diffuser(level);
        }
    }

    private void impact(ServerLevel level) {
        Vec3 p = chuteB;
        setPos(p.x, p.y, p.z);
        Network.sendImpact(level, new ImpactPacket(ImpactPacket.HEAVY, p.x, p.y + 1.0, p.z, 7f));
        level.playSound(null, p.x, p.y, p.z, ModSounds.HELICO_CRASH.get(), SoundSource.HOSTILE, 1f, 1f);
        blesser(level, p);
        // le bruit attire les zombies : une explosion sans puissance (rien de cassé, personne de touché) prévient les
        // écouteurs d'explosions (StimuliZombies de HeraliZ Utils)
        level.explode(this, null, null, p.x, p.y + 0.5, p.z, 0f, false, Level.ExplosionInteraction.NONE);
        HelicopterWreckServeur.poser(level, id, p, getYRot(), r, suivi);
        terminer();
        prevenir(s -> s.impact(id, p));
    }

    /** Petits dégâts aux joueurs (et créatures) tout près de l'impact, dégressifs. */
    private void blesser(ServerLevel level, Vec3 p) {
        double rayon = r.crashRayon;
        if (rayon <= 0 || r.crashDegats <= 0) return;
        DamageSource src = source(level, DEGATS_CRASH);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(rayon, 4, rayon))) {
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            double d = Math.sqrt(e.distanceToSqr(p.x, e.getY(), p.z));
            if (d > rayon) continue;
            e.hurt(src, (float) (r.crashDegats * (1 - 0.75 * d / rayon)));
        }
    }

    // ------------------------------------------------------------------ diffusion, ticket, fin
    /** Position et vitesse pour le son lointain, une fois par seconde, aux joueurs à moins de {@link #PORTEE_SON}. */
    private void diffuser(ServerLevel level) {
        Network.sendNear(level, getX(), getY(), getZ(), PORTEE_SON, new HelicoPositionPacket(getId(), getX(), getY(), getZ(),
                getX() - xo, getY() - yo, getZ() - zo, rotor(), phase()));
    }

    private void tenirTicket(ServerLevel level) {
        ChunkPos cp = chunkPosition();
        if (!cp.equals(ticket)) {
            relacherTicket();
            level.getChunkSource().addRegionTicket(TICKET, cp, RAYON_TICKET, getId());
            ticket = cp;
        } else if (tickCount % 20 == 0) {
            level.getChunkSource().addRegionTicket(TICKET, cp, RAYON_TICKET, getId());   // rafraîchi (expire seul sinon)
        }
    }

    private void relacherTicket() {
        if (ticket != null && level() instanceof ServerLevel sl) sl.getChunkSource().removeRegionTicket(TICKET, ticket, RAYON_TICKET, getId());
        ticket = null;
    }

    /** Fin sans épave (parti, durée maximale) : prévient le suivi puis retire l'entité. */
    private void finir(String cause) {
        if (fini) return;
        terminer();
        prevenir(s -> s.fini(id, cause));
    }

    /** Retire l'entité sans rien dire (la suite est à l'épave, ou déjà dite). */
    private void terminer() {
        fini = true;
        EN_VOL.remove(id, this);
        discard();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        relacherTicket();
        RemovalReason raison = getRemovalReason();
        if (level() instanceof ServerLevel sl && raison != null && raison.shouldDestroy()) Veille.retire(sl, getId());
        if (!fini) {
            // retiré par autre chose que le pilote (/kill du staff, arrêt du serveur)
            fini = true;
            EN_VOL.remove(id, this);
            prevenir(s -> s.fini(id, "perdu"));
        }
    }

    private void prevenir(Consumer<HelicoApi.Suivi> appel) {
        if (suivi == null) return;
        try {
            appel.accept(suivi);
        } catch (RuntimeException ex) {
            AirstrikeMod.LOGGER.error("[helico] {} : suivi en erreur", id, ex);
        }
    }

    private void majFloat(EntityDataAccessor<Float> cle, float v) {
        if (Math.abs(entityData.get(cle) - v) > 0.05f) entityData.set(cle, v);
    }

    /** État pour le staff. */
    public HelicoApi.Etat etat() {
        List<String> noms = new ArrayList<>();
        if (level().getServer() != null) {
            for (UUID u : agresseurs.keySet()) {
                ServerPlayer p = level().getServer().getPlayerList().getPlayer(u);
                noms.add(p != null ? p.getGameProfile().getName() : u.toString().substring(0, 8));
            }
        }
        String phase = switch (phase()) {
            case COMBAT -> "combat" + (cible != null ? " (" + cible.getGameProfile().getName() + ")" : "");
            case CHUTE -> "chute";
            case DEPART -> "depart";
            default -> "patrouille";
        };
        return new HelicoApi.Etat(phase, pv, pvMax, position(), essai, noms, contributions());
    }
}

package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link HelicoEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : tout ce que décide l'hélicoptère.
 * <ul><li><b>Vol</b> : d'une ville à l'autre (cercles de config/heralisz_clans/zones.json, sinon des points au hasard
 * dans la bordure), à {@code hauteur} blocs au-dessus du relief (relief lu sous lui et devant lui, seulement dans les
 * tronçons chargés), virages et montées doux ; au bout de {@code dureeMinutes}, il part vers le bord de la carte et
 * disparaît en l'atteignant.</li>
 * <li><b>Tronçons</b> : un ticket de chargement de courte durée sur son tronçon et les deux devant lui (sinon il se
 * figerait en l'air dès qu'aucun joueur n'est près de lui, Minecraft ne faisant vivre une entité que dans un tronçon
 * « actif »). Les tickets expirent seuls 3 s après le passage.</li>
 * <li><b>Riposte</b> : il retient les joueurs qui le touchent (et ceux qui le visent arme à la main plus de
 * {@code viseeSecondes}) pendant {@code memoireSecondes} ; il vise le plus proche à vue, tourne autour de lui et tire
 * des rafales (tir instantané le long d'un rayon, dispersion, ligne de vue obligatoire, vraie source de dégâts de type
 * projectile : les armures de l'Armurerie protègent). Jamais sur un joueur en créatif, spectateur, en match, à terre,
 * caché, ni (par défaut) sur les bots de charge.</li>
 * <li><b>Dégâts reçus</b> : balles (projectiles, dont celles de TaCZ) et explosions seulement ; la mêlée ne fait rien.</li>
 * <li><b>Chute</b> : vie à zéro, il part vers un point au hasard autour de lui qui n'est pas dans l'eau
 * ({@link Helicos#pointDeChute}), en tournant sur lui-même ; à l'impact, explosion qui ne casse aucun bloc, puis l'épave
 * ({@link Helicos#crash}).</li></ul>
 */
public class HelicoEntityServeur extends HelicoEntity {
    public static final ResourceKey<DamageType> DEGATS = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("airstrike", "helico"));
    /** Ticket de chargement (3 s) : tronçons sous et devant l'hélicoptère. */
    static final TicketType<ChunkPos> TICKET = TicketType.create("airstrike_helico", Comparator.comparingLong(ChunkPos::toLong), 60);

    enum Phase { PATROUILLE, DEPART, CHUTE }

    private Phase phase = Phase.PATROUILLE;
    private float vieMax = 1500f, vieRestante = 1500f;
    private Vec3 vitesse = Vec3.ZERO;
    private final List<Vec3> points = new ArrayList<>();
    private int pointCourant;
    private long finPatrouille;
    private final Map<UUID, Long> agresseurs = new HashMap<>();
    private final Map<UUID, Integer> visees = new HashMap<>();
    @Nullable
    private UUID cibleId;
    private boolean cibleVue;
    private long cibleDepuis;
    private int rafale;
    private long prochainTir, prochaineRafale;
    private double orbite;
    private double solConnu = Double.NaN;
    @Nullable
    private Vec3 sortie;
    // chute
    private Vec3 chuteDepart = Vec3.ZERO, chuteImpact = Vec3.ZERO;
    private int chuteT, chuteDuree = 1;
    @Nullable
    private UUID tueur;

    public HelicoEntityServeur(EntityType<? extends HelicoEntity> type, Level level) {
        super(type, level);
    }

    /** Appelé par Helicos juste après la création : vie, trajet, durée. */
    void preparer(List<Vec3> trajet, long fin) {
        vieMax = vieRestante = (float) (double) AirstrikeConfig.HELICO_VIE.get();
        entityData.set(VIE, 1f);
        points.clear();
        points.addAll(trajet);
        pointCourant = 0;
        finPatrouille = fin;
        orbite = random.nextDouble() * Math.PI * 2;
    }

    float vieRestante() {
        return vieRestante;
    }

    float vieMax() {
        return vieMax;
    }

    String phase() {
        return phase.name().toLowerCase(java.util.Locale.ROOT);
    }

    int agresseurs() {
        return agresseurs.size();
    }

    // ================================================================================================ tick
    @Override
    protected void tickServeur() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (sl.dimension() != Level.OVERWORLD) {
            discard();
            return;
        }
        long now = sl.getGameTime();
        if (tickCount % 10 == 0) tickets(sl);
        if (phase == Phase.CHUTE) {
            tickChute(sl);
            return;
        }
        if (tickCount % 10 == 0) cibles(sl, now);
        ServerPlayer cible = cibleJoueur(sl);
        if (phase == Phase.PATROUILLE && now >= finPatrouille && cible == null) depart(sl);
        voler(sl, cible);
        tirer(sl, cible, now);
        if (phase == Phase.DEPART && sortie != null && horizontal(position(), sortie) < 30) {
            Helicos.parti(this);
            discard();
        }
    }

    private void tickets(ServerLevel sl) {
        ChunkPos ici = chunkPosition();
        sl.getChunkSource().addRegionTicket(TICKET, ici, 2, ici);
        Vec3 dir = new Vec3(vitesse.x, 0, vitesse.z);
        if (dir.lengthSqr() > 1e-4) {
            dir = dir.normalize();
            for (int k = 1; k <= 2; k++) {
                ChunkPos c = new ChunkPos(BlockPos.containing(position().add(dir.scale(16.0 * k))));
                // jamais hors de la bordure : rien n'y est généré pour un hélicoptère qui passe
                if (!c.equals(ici) && sl.getWorldBorder().isWithinBounds(c)) sl.getChunkSource().addRegionTicket(TICKET, c, 2, c);
            }
        }
    }

    // ================================================================================================ vol
    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static float lacetVers(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Hauteur du sol (dessus des blocs, feuillage compris) en x, z ; NaN si le tronçon n'est pas chargé. */
    static double sol(ServerLevel sl, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!sl.hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
        return sl.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    /** Sol le plus haut sous l'hélicoptère et devant lui (relief à franchir), avec le dernier connu en secours. */
    private double solDevant(ServerLevel sl, Vec3 p, Vec3 v) {
        double s = Double.NaN;
        for (int k = 0; k <= 3; k++) {
            Vec3 q = p.add(v.x * 12 * k, 0, v.z * 12 * k);
            double h = sol(sl, q.x, q.z);
            if (!Double.isNaN(h)) s = Double.isNaN(s) ? h : Math.max(s, h);
        }
        if (!Double.isNaN(s)) solConnu = s;
        return Double.isNaN(solConnu) ? sl.getSeaLevel() : solConnu;
    }

    private void voler(ServerLevel sl, @Nullable ServerPlayer cible) {
        Vec3 p = position();
        double vmax = AirstrikeConfig.HELICO_VITESSE.get();
        Vec3 but;
        double yBut;
        double solIci = solDevant(sl, p, vitesse);
        if (cible != null && phase == Phase.PATROUILLE) {
            // tourne autour de sa cible, un peu plus lentement
            double r = 30.0;
            orbite += vmax * 0.6 / r;
            but = cible.position().add(Math.cos(orbite) * r, 0, Math.sin(orbite) * r);
            yBut = Math.max(cible.getY() + AirstrikeConfig.HELICO_HAUTEUR_COMBAT.get(), solIci + 14);
            vmax *= 0.75;
        } else if (phase == Phase.DEPART && sortie != null) {
            but = sortie;
            yBut = solIci + AirstrikeConfig.HELICO_HAUTEUR.get() + 10;
        } else {
            if (points.isEmpty()) points.add(p);
            Vec3 w = points.get(pointCourant % points.size());
            if (horizontal(p, w) < 30) {
                pointCourant = (pointCourant + 1) % points.size();
                w = points.get(pointCourant);
            }
            but = w;
            yBut = solIci + AirstrikeConfig.HELICO_HAUTEUR.get();
        }
        // horizontal : vers le but, ralentit en approchant, accélération limitée (virages doux)
        Vec3 d = new Vec3(but.x - p.x, 0, but.z - p.z);
        double dist = d.length();
        Vec3 voulu = dist < 1e-3 ? Vec3.ZERO : d.scale(Math.min(vmax, dist * 0.05) / dist);
        Vec3 h = new Vec3(vitesse.x, 0, vitesse.z);
        Vec3 dv = voulu.subtract(h);
        double acc = 0.022;
        if (dv.length() > acc) dv = dv.normalize().scale(acc);
        h = h.add(dv);
        // vertical : vers l'altitude voulue ; relief devant trop haut : il grimpe et ralentit
        double vy = Mth.clamp((yBut - p.y) * 0.04, -0.22, 0.3);
        if (solIci > p.y - 8) {
            vy = 0.35;
            h = h.scale(0.85);
        }
        vy = vitesse.y + Mth.clamp(vy - vitesse.y, -0.03, 0.03);
        vitesse = new Vec3(h.x, vy, h.z);
        Vec3 n = p.add(vitesse);
        setPos(n.x, Math.min(n.y, sl.getMaxBuildHeight() + 40), n.z);
        setDeltaMovement(vitesse);
        // lacet : vers sa cible en combat (il vole de côté autour d'elle), sinon dans le sens de la marche
        float lacetBut = getYRot();
        if (cible != null && phase == Phase.PATROUILLE) lacetBut = lacetVers(cible.getX() - getX(), cible.getZ() - getZ());
        else if (h.lengthSqr() > 1e-3) lacetBut = lacetVers(h.x, h.z);
        setYRot(getYRot() + Mth.clamp(Mth.wrapDegrees(lacetBut - getYRot()), -3.5f, 3.5f));
    }

    private void depart(ServerLevel sl) {
        phase = Phase.DEPART;
        var b = sl.getWorldBorder();
        // bord le plus proche, 20 blocs à l'intérieur
        double x = getX(), z = getZ();
        double[] dists = {x - b.getMinX(), b.getMaxX() - x, z - b.getMinZ(), b.getMaxZ() - z};
        int k = 0;
        for (int i = 1; i < 4; i++) if (dists[i] < dists[k]) k = i;
        sortie = switch (k) {
            case 0 -> new Vec3(b.getMinX() + 20, 0, z);
            case 1 -> new Vec3(b.getMaxX() - 20, 0, z);
            case 2 -> new Vec3(x, 0, b.getMinZ() + 20);
            default -> new Vec3(x, 0, b.getMaxZ() - 20);
        };
        cibleId = null;
    }

    // ================================================================================================ cibles
    @Nullable
    private ServerPlayer cibleJoueur(ServerLevel sl) {
        if (cibleId == null) return null;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(cibleId);
        return p != null && p.level() == sl && agresseurs.containsKey(cibleId) && Ponts.cible(p, this) ? p : null;
    }

    /** Toutes les 10 ticks : mémoire des agresseurs, visées prolongées, choix de la cible et ligne de vue. */
    private void cibles(ServerLevel sl, long now) {
        agresseurs.values().removeIf(fin -> fin < now);
        long memoire = AirstrikeConfig.HELICO_MEMOIRE.get() * 20L;
        double portee = AirstrikeConfig.HELICO_PORTEE_TIR.get();
        int visee = AirstrikeConfig.HELICO_VISEE.get() * 20;
        Vec3 centre = getBoundingBox().getCenter();
        if (visee > 0) {
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(centre) > portee * portee || !Ponts.cible(p, this) || !arme(p) || !vise(p, centre)) {
                    visees.remove(p.getUUID());
                    continue;
                }
                if (visees.merge(p.getUUID(), 10, Integer::sum) >= visee) agresseurs.put(p.getUUID(), now + memoire);
            }
        }
        // cible : l'actuelle si elle est encore là et à vue, sinon l'agresseur le plus proche à vue
        ServerPlayer actuelle = cibleJoueur(sl);
        if (actuelle != null && actuelle.distanceToSqr(centre) <= portee * portee * 1.5 && aVue(sl, actuelle)) {
            cibleVue = true;
            return;
        }
        ServerPlayer mieux = null;
        double best = Double.MAX_VALUE;
        for (UUID id : agresseurs.keySet()) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.level() != sl || !Ponts.cible(p, this)) continue;
            double d2 = p.distanceToSqr(centre);
            if (d2 > portee * portee * 1.5 || d2 >= best) continue;
            if (!aVue(sl, p)) continue;
            best = d2;
            mieux = p;
        }
        if (mieux != null) {
            if (!mieux.getUUID().equals(cibleId)) {
                cibleId = mieux.getUUID();
                cibleDepuis = now;
                prochaineRafale = Math.max(prochaineRafale, now + 20);   // le temps de tourner la tourelle
            }
            cibleVue = true;
        } else {
            // garde l'agresseur caché tant qu'il est en mémoire (il le chasse), sans tirer
            cibleVue = false;
            if (actuelle == null) cibleId = null;
        }
    }

    private static boolean arme(ServerPlayer p) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
        return id != null && id.getNamespace().equals("tacz");
    }

    private static boolean vise(ServerPlayer p, Vec3 centre) {
        Vec3 d = centre.subtract(p.getEyePosition());
        double l = d.length();
        return l > 1e-3 && p.getViewVector(1f).dot(d.scale(1 / l)) > Math.cos(Math.toRadians(7.0));
    }

    private Vec3 bouche() {
        return versMonde(BOUCHE);
    }

    private boolean aVue(ServerLevel sl, Entity e) {
        Vec3 de = bouche();
        for (double f : new double[]{0.9, 0.5}) {
            Vec3 vers = e.position().add(0, e.getBbHeight() * f, 0);
            BlockHitResult r = sl.clip(new ClipContext(de, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (r.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    // ================================================================================================ tir
    private void tirer(ServerLevel sl, @Nullable ServerPlayer cible, long now) {
        boolean enRafale = false;
        if (cible != null && cibleVue && phase == Phase.PATROUILLE && tourellePeutViser(cible)) {
            if (rafale > 0) {
                enRafale = true;
                if (now >= prochainTir) {
                    balle(sl, cible);
                    rafale--;
                    prochainTir = now + AirstrikeConfig.HELICO_CADENCE.get();
                    if (rafale == 0) {
                        int a = AirstrikeConfig.HELICO_PAUSE_MIN.get(), b = Math.max(a, AirstrikeConfig.HELICO_PAUSE_MAX.get());
                        prochaineRafale = now + a + random.nextInt(b - a + 1);
                    }
                }
            } else if (now >= prochaineRafale) {
                rafale = AirstrikeConfig.HELICO_RAFALE.get();
                prochainTir = now;
                enRafale = true;
                sl.playSound(null, getX(), getY() + 0.5, getZ(), ModSounds.HELICO_TIR.get(), SoundSource.HOSTILE, 5.0f,
                        0.95f + random.nextFloat() * 0.1f);
            }
        } else {
            rafale = 0;
        }
        entityData.set(TIR, enRafale);
        entityData.set(CIBLE, cible != null ? cible.getId() : -1);
    }

    /** La tourelle du menton atteint-elle la cible (lacet et tangage dans ses limites) ? */
    private boolean tourellePeutViser(Entity c) {
        Vec3 t = versMonde(TOURELLE);
        Vec3 d = new Vec3(c.getX() - t.x, c.getY() + c.getBbHeight() * 0.6 - t.y, c.getZ() - t.z);
        double b = Math.toRadians(getYRot() - 180.0);
        double lx = d.x * Math.cos(b) + d.z * Math.sin(b), lz = -d.x * Math.sin(b) + d.z * Math.cos(b);
        double lacet = Math.toDegrees(Math.atan2(-lx, -lz));
        double tangage = Math.toDegrees(Math.atan2(d.y, Math.sqrt(lx * lx + lz * lz)));
        return Math.abs(lacet) <= LACET_MAX && tangage >= TANGAGE_MIN && tangage <= TANGAGE_MAX + 5;
    }

    static DamageSource source(ServerLevel sl, Entity helico) {
        return new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DEGATS), helico, helico);
    }

    /** Une balle : rayon de la bouche vers la cible, dispersion, premier bloc ou première créature touchée. */
    private void balle(ServerLevel sl, ServerPlayer cible) {
        Vec3 de = bouche();
        Vec3 vers = cible.position().add(0, cible.getBbHeight() * 0.55, 0);
        Vec3 dir = vers.subtract(de).normalize();
        double disp = Math.toRadians(AirstrikeConfig.HELICO_DISPERSION.get());
        Vec3 ref = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 e1 = dir.cross(ref).normalize(), e2 = dir.cross(e1).normalize();
        dir = dir.add(e1.scale(random.nextGaussian() * disp * 0.6)).add(e2.scale(random.nextGaussian() * disp * 0.6)).normalize();
        double portee = AirstrikeConfig.HELICO_PORTEE_TIR.get() * 1.3;
        Vec3 fin = de.add(dir.scale(portee));
        BlockHitResult bloc = sl.clip(new ClipContext(de, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean solide = bloc.getType() != HitResult.Type.MISS;
        if (solide) fin = bloc.getLocation();
        EntityHitResult touche = ProjectileUtil.getEntityHitResult(sl, this, de, fin, new AABB(de, fin).inflate(1.0),
                e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !(e instanceof ServerPlayer sp && sp.isCreative()));
        if (touche != null && touche.getEntity() instanceof LivingEntity le) {
            fin = touche.getLocation();
            // pas d'invulnérabilité entre deux balles (comme les balles de TaCZ) ; dégâts « projectile » : armures comprises
            le.invulnerableTime = 0;
            le.hurt(source(sl, this), (float) (double) AirstrikeConfig.HELICO_DEGATS.get());
            Network.sendImpact(sl, new ImpactPacket(ImpactPacket.HELICO_BALLE, fin.x, fin.y, fin.z, 0.5f));
        } else {
            // power : 1 = poussière sur un bloc, 0 = balle perdue (traçante seule)
            Network.sendImpact(sl, new ImpactPacket(ImpactPacket.HELICO_BALLE, fin.x, fin.y, fin.z, solide ? 1f : 0f));
        }
    }

    // ================================================================================================ dégâts
    @Override
    public boolean hurt(DamageSource source, float n) {
        if (level().isClientSide || isRemoved() || phase == Phase.CHUTE || n <= 0 || isInvulnerableTo(source)) return false;
        Entity direct = source.getDirectEntity();
        boolean balle = source.is(DamageTypeTags.IS_PROJECTILE) || direct instanceof Projectile
                || (direct != null && direct.getClass().getName().startsWith("com.tacz."));
        boolean boum = source.is(DamageTypeTags.IS_EXPLOSION);
        if (!balle && !boum) return false;   // mêlée, chute, feu... : rien
        if (!(level() instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (source.getEntity() instanceof ServerPlayer p && Ponts.cible(p, this)) {
            agresseurs.put(p.getUUID(), now + AirstrikeConfig.HELICO_MEMOIRE.get() * 20L);
            if (cibleId == null) {
                cibleId = p.getUUID();
                cibleDepuis = now;
                prochaineRafale = Math.max(prochaineRafale, now + 20);
            }
        }
        vieRestante -= n;
        entityData.set(VIE, Math.max(0f, vieRestante / vieMax));
        if (vieRestante <= 0) mourir(sl, source.getEntity() instanceof ServerPlayer sp ? sp : null);
        return true;
    }

    /** Abattu (ou /helico abattre) : il part en chute vers un point qui n'est pas dans l'eau. */
    void mourir(ServerLevel sl, @Nullable ServerPlayer auteur) {
        if (phase == Phase.CHUTE) return;
        phase = Phase.CHUTE;
        entityData.set(ETAT, CHUTE);
        entityData.set(TIR, false);
        entityData.set(CIBLE, -1);
        entityData.set(VIE, 0f);
        rafale = 0;
        tueur = auteur != null ? auteur.getUUID() : null;
        chuteDepart = position();
        chuteImpact = Helicos.pointDeChute(sl, this);
        double dist = Math.sqrt(chuteDepart.distanceToSqr(chuteImpact));
        chuteDuree = Mth.clamp((int) (dist / 0.65), 50, 170);
        chuteT = 0;
        sl.playSound(null, getX(), getY(), getZ(), ModSounds.HELICO_CHUTE.get(), SoundSource.HOSTILE, 6.0f, 1.0f);
        Helicos.abattu(this, auteur);
    }

    private void tickChute(ServerLevel sl) {
        chuteT++;
        double t = Math.min(1.0, chuteT / (double) chuteDuree);
        double x = Mth.lerp(t, chuteDepart.x, chuteImpact.x);
        double z = Mth.lerp(t, chuteDepart.z, chuteImpact.z);
        double y = chuteDepart.y + (chuteImpact.y - chuteDepart.y) * Math.pow(t, 1.7);
        Vec3 avant = position();
        Vec3 n = new Vec3(x, y, z);
        setYRot(getYRot() + 4f + 10f * (float) t);
        setDeltaMovement(n.subtract(avant));
        setPos(n.x, n.y, n.z);
        double s = sol(sl, x, z);
        boolean touche = t >= 1.0 || (!Double.isNaN(s) && y <= s + 0.6 && t > 0.15)
                || (t > 0.15 && !sl.getBlockState(BlockPos.containing(n)).getCollisionShape(sl, BlockPos.containing(n)).isEmpty());
        if (touche) impact(sl, Double.isNaN(s) ? y : s);
    }

    private void impact(ServerLevel sl, double solY) {
        Vec3 p = new Vec3(getX(), solY, getZ());
        Network.sendImpact(sl, new ImpactPacket(ImpactPacket.HEAVY, p.x, p.y + 0.5, p.z, 2.0f));
        float puissance = (float) (double) AirstrikeConfig.HELICO_PUISSANCE_CRASH.get();
        // explosion qui blesse sans casser de bloc (ni constructions de CRac)
        if (puissance > 0) sl.explode(this, p.x, p.y + 1.0, p.z, puissance, false, Level.ExplosionInteraction.NONE);
        ServerPlayer auteur = tueur == null ? null : sl.getServer().getPlayerList().getPlayer(tueur);
        Helicos.crash(sl, p, getYRot(), auteur);
        discard();
    }
}

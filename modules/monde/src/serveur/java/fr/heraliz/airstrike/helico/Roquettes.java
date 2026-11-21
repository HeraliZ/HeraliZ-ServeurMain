package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.api.HelicoApi;
import fr.heraliz.airstrike.entity.HelicopterServeur;
import fr.heraliz.airstrike.network.HelicoRoquettePacket;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Roquettes de l'hélicoptère, côté serveur (19/11, demande du propriétaire : « en plus des rafales, des missiles comme dans
 * Rust, qui explosent et font plus mal mais moins souvent ; quand il attaque aux missiles, il faut se mettre à couvert dans
 * un bâtiment »). Lancées par {@link HelicopterServeur} contre sa cible (toujours un de ses agresseurs), elles volent en
 * ligne droite, tick par tick (un seul rayon de blocs par roquette et par tick), et explosent au premier bloc ou à l'eau,
 * au passage de la cible, ou au bout de {@link #VIE_MAX} ticks.
 * <p>Explosion : seulement les agresseurs de l'hélicoptère, en survie et debout, à moins de {@code roquettesRayon} blocs ;
 * dégâts du centre au bord multipliés par la part visible du corps ({@code Explosion.getSeenPercent}, le calcul des
 * explosions de Minecraft : murs et toits protègent), un petit recul ; aucun bloc cassé, aucun feu. L'effet (boule de feu,
 * fumée, bruit) part en {@link ImpactPacket} à tous ceux qui le voient.</p>
 * <p>Jamais de chargement : une roquette qui sort de la zone chargée disparaît. Une roquette finit son vol même si
 * l'hélicoptère est abattu entre-temps. Sans roquette en vol, {@link #tick} sort tout de suite (appelé par
 * {@link Veille}).</p>
 */
public final class Roquettes {
    private Roquettes() {
    }

    public static final ResourceKey<DamageType> DEGATS = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(AirstrikeMod.MODID, "helico_roquette"));
    /** Durée de vol au plus (ticks) : 200 blocs à la vitesse par défaut. */
    static final int VIE_MAX = 100;
    /** Roquettes en vol au plus (toutes les salves de tous les hélicoptères). */
    private static final int EN_VOL_MAX = 32;
    /** Marge autour de la boîte de la cible : la roquette qui passe si près explose (blocs). */
    private static final double FUSEE_CIBLE = 0.4;
    /** L'explosion part un peu en deçà du point touché (blocs) : les rayons de la part visible ne partent pas du bloc. */
    private static final double RECUL_IMPACT = 0.3;

    private static final class Roquette {
        final int id;
        final ServerLevel level;
        final HelicopterServeur tireur;
        /** Les agresseurs du pilote (sa table, lue à l'explosion) : les seuls que l'explosion peut toucher. */
        final Map<UUID, Long> agresseurs;
        @Nullable
        final ServerPlayer cible;
        final double vx, vy, vz;
        final float degats, rayon;
        double x, y, z;
        int age;

        Roquette(int id, ServerLevel level, HelicopterServeur tireur, Map<UUID, Long> agresseurs, @Nullable ServerPlayer cible,
                 Vec3 de, double vx, double vy, double vz, float degats, float rayon) {
            this.id = id;
            this.level = level;
            this.tireur = tireur;
            this.agresseurs = agresseurs;
            this.cible = cible;
            this.x = de.x;
            this.y = de.y;
            this.z = de.z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.degats = degats;
            this.rayon = rayon;
        }
    }

    private static final List<Roquette> EN_VOL = new ArrayList<>();
    private static int numeros;

    public static boolean vide() {
        return EN_VOL.isEmpty();
    }

    /**
     * Lance une roquette de {@code de} vers {@code vers} (point déjà anticipé et dispersé par le pilote) : son de départ à
     * l'hélicoptère, paquet aux joueurs proches (traînée chez eux). {@code agresseurs} : la table du pilote.
     */
    public static void lancer(ServerLevel level, HelicopterServeur tireur, Vec3 de, Vec3 vers, HelicoApi.Reglages r,
                              Map<UUID, Long> agresseurs, @Nullable ServerPlayer cible) {
        double dx = vers.x - de.x, dy = vers.y - de.y, dz = vers.z - de.z;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-3 || EN_VOL.size() >= EN_VOL_MAX) return;
        double k = Math.max(0.5, r.roquettesVitesse) / l;
        Roquette q = new Roquette(++numeros, level, tireur, agresseurs, cible, de, dx * k, dy * k, dz * k, r.roquettesDegats, r.roquettesRayon);
        EN_VOL.add(q);
        level.playSound(null, de.x, de.y, de.z, ModSounds.NAPALM_WHOOSH.get(), SoundSource.HOSTILE, 2.5f, 1.35f + level.random.nextFloat() * 0.25f);
        Network.sendNear(level, de.x, de.y, de.z, HelicopterServeur.PORTEE_TRACANTES,
                new HelicoRoquettePacket(HelicoRoquettePacket.LANCEE, q.id, de.x, de.y, de.z, q.vx, q.vy, q.vz));
    }

    /** Chaque tick (Veille) : les roquettes en vol avancent ; sans roquette, rien. */
    public static void tick() {
        if (EN_VOL.isEmpty()) return;
        for (int i = EN_VOL.size() - 1; i >= 0; i--) {
            if (avancer(EN_VOL.get(i))) EN_VOL.remove(i);
        }
    }

    /** Arrêt du serveur : plus rien en vol. */
    public static void vider() {
        EN_VOL.clear();
    }

    /** Un tick de vol ; vrai si la roquette est finie (explosée ou perdue). */
    private static boolean avancer(Roquette q) {
        ServerLevel level = q.level;
        Vec3 de = new Vec3(q.x, q.y, q.z), vers = new Vec3(q.x + q.vx, q.y + q.vy, q.z + q.vz);
        // 21/11 (audit du code) : en plus, tronçons COMPLETS du trajet de ce tick (Troncons du noyau) ; isLoaded répond « oui »
        // pendant la lecture d'un tronçon (bord de la vue d'un joueur qui avance, ticket tout juste posé) et le clip qui suit
        // faisait alors attendre cette lecture par le fil du serveur
        if (!level.isLoaded(BlockPos.containing(vers))
                || !fr.heraliz.noyau.api.Troncons.zone(level, Mth.floor(de.x), Mth.floor(de.z), Mth.floor(vers.x), Mth.floor(vers.z))) {
            fin(q);   // sortie de la zone chargée : jamais de chargement pour une roquette
            return true;
        }
        BlockHitResult bh = level.clip(new ClipContext(de, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, q.tireur));
        Vec3 arret = bh.getType() == HitResult.Type.MISS ? null : bh.getLocation();
        ServerPlayer c = q.cible;
        if (c != null && !c.isRemoved() && c.isAlive() && c.level() == level) {
            Optional<Vec3> t = c.getBoundingBox().inflate(FUSEE_CIBLE).clip(de, arret != null ? arret : vers);
            if (t.isPresent()) arret = t.get();
        }
        if (arret != null) {
            double v = Math.sqrt(q.vx * q.vx + q.vy * q.vy + q.vz * q.vz);
            double recul = v > 1e-6 ? RECUL_IMPACT / v : 0;
            exploser(q, arret.x - q.vx * recul, arret.y - q.vy * recul, arret.z - q.vz * recul);
            return true;
        }
        q.x = vers.x;
        q.y = vers.y;
        q.z = vers.z;
        if (++q.age >= VIE_MAX) {
            exploser(q, q.x, q.y, q.z);
            return true;
        }
        return false;
    }

    /** Fin de la roquette chez les joueurs proches (ils cessent de la dessiner). */
    private static void fin(Roquette q) {
        Network.sendNear(q.level, q.x, q.y, q.z, HelicopterServeur.PORTEE_TRACANTES,
                new HelicoRoquettePacket(HelicoRoquettePacket.FINIE, q.id, q.x, q.y, q.z, 0, 0, 0));
    }

    private static void exploser(Roquette q, double x, double y, double z) {
        ServerLevel level = q.level;
        q.x = x;
        q.y = y;
        q.z = z;
        fin(q);
        Network.sendImpact(level, new ImpactPacket(ImpactPacket.SMALL, x, y, z, 1f));
        if (q.degats <= 0 || q.rayon <= 0) return;
        Vec3 centre = new Vec3(x, y, z);
        DamageSource src = null;
        for (ServerPlayer p : level.players()) {
            // seulement ses agresseurs (règle du propriétaire), en survie, debout
            if (!p.isAlive() || p.isSpectator() || p.isCreative() || !q.agresseurs.containsKey(p.getUUID()) || aTerre(p)) continue;
            double dx = p.getX() - x, dy = p.getY() + p.getBbHeight() * 0.5 - y, dz = p.getZ() - z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d >= q.rayon) continue;
            float vu = Explosion.getSeenPercent(centre, p);
            if (vu <= 0f) continue;
            float proche = (float) (1.0 - d / q.rayon);
            float degats = q.degats * proche * vu;
            if (degats < 0.25f) continue;
            if (src == null) src = source(level, q.tireur);
            p.invulnerableTime = 0;   // chaque roquette d'une salve compte
            p.hurt(src, degats);
            // recul loin du centre (Minecraft n'en donne pas aux dégâts d'explosion : c'est l'explosion qui pousse)
            double k = 0.55 * proche * vu;
            if (d > 1e-3 && k > 0.02) {
                p.push(dx / d * k, 0.25 * k + Math.max(0, dy / d) * 0.5 * k, dz / d * k);
                p.hurtMarked = true;
            }
        }
    }

    private static DamageSource source(ServerLevel level, HelicopterServeur tireur) {
        Optional<Holder.Reference<DamageType>> h = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(DEGATS);
        return h.isPresent() ? new DamageSource(h.get(), tireur) : level.damageSources().explosion(tireur, tireur);
    }

    private static boolean aTerre(ServerPlayer p) {
        try {
            return fr.heraliz.noyau.api.ATerre.aTerre(p);
        } catch (LinkageError e) {
            return false;
        }
    }
}

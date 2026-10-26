package fr.heraliz.airstrike.strike;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.entity.EffectCloudEntity;
import fr.heraliz.airstrike.entity.EffectCloudEntityServeur;
import fr.heraliz.airstrike.entity.SupplyCrateEntity;
import fr.heraliz.airstrike.entity.SupplyCrateEntityServeur;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server side consequences of every impact. */
public final class StrikeEffects {
    public static final ResourceKey<DamageType> BOMB_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("airstrike", "bomb"));
    public static final ResourceKey<DamageType> STRAFE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("airstrike", "strafe"));

    private StrikeEffects() {
    }

    @Nullable
    private static ServerPlayer caller(ServerLevel level, Strike strike) {
        return strike.callerUuid == null ? null : level.getServer().getPlayerList().getPlayer(strike.callerUuid);
    }

    private static DamageSource damage(ServerLevel level, ResourceKey<DamageType> key, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), null, attacker);
    }

    /**
     * 26/10 (règle du propriétaire : une frappe ne fait aucun dégât aux bases) : jamais de bloc cassé ni de feu posé,
     * quels que soient les réglages ({@code destroyBlocks}, {@code napalmPlacesFire}) ; joueurs à l'abri dans une base
     * épargnés ({@link AbriFrappes}, appelé par l'événement d'explosion pendant {@code level.explode}).
     */
    private static void explode(ServerLevel level, @Nullable ServerPlayer caller, Vec3 p, float power, boolean fire, boolean blocks) {
        if (power <= 0) return;
        AbriFrappes.enCours = true;
        try {
            level.explode(null, damage(level, BOMB_DAMAGE, caller), null, p.x, p.y + 0.2, p.z, power, false, Level.ExplosionInteraction.NONE);
        } finally {
            AbriFrappes.enCours = false;
        }
    }

    public static void release(ServerLevel level, Strike strike, Drop drop) {
        if (drop.kind == OrdnanceKind.CRATE) {
            Vec3 vel = new Vec3(strike.path.dx * strike.path.speed, 0, strike.path.dz * strike.path.speed);
            float yaw = (float) FlightPath.yawOf(strike.path.dx, strike.path.dz);
            SupplyCrateEntity caisse = SupplyCrateEntityServeur.create(level, new Vec3(drop.x0, drop.y0, drop.z0), vel, yaw);
            // largage d'un événement (25/10) : la caisse le sait, et le prévient à sa première ouverture
            if (strike.evenement != null && caisse instanceof SupplyCrateEntityServeur s) s.evenement(strike.evenement);
            level.addFreshEntity(caisse);
        }
    }

    /** Infectés attirés au plus par impact (25/10, banc de charge : chaque attiré calcule un chemin tout de suite). */
    private static final int ATTIRES_MAX = 12;

    /** DayZ style: the noise draws the infected towards the impact. */
    private static void attractMonsters(ServerLevel level, Vec3 p) {
        int r = AirstrikeConfig.NOISE_ATTRACTS_MONSTERS.get();
        if (r <= 0) return;
        AABB box = new AABB(p.x - r, p.y - 24, p.z - r, p.x + r, p.y + 24, p.z + r);
        int n = 0;
        // 25/10 : jusqu'à 330 ms de gel mesurés quand une frappe attirait d'un coup tous les monstres du secteur ; ceux qui
        // marchent déjà vers quelque chose (impact précédent compris) gardent leur chemin
        for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.getTarget() == null && m.isAlive())) {
            if (mob.distanceToSqr(p) > (double) r * r || !mob.getNavigation().isDone()) continue;
            mob.getNavigation().moveTo(p.x + (level.random.nextDouble() - 0.5) * 6, p.y, p.z + (level.random.nextDouble() - 0.5) * 6, 1.15);
            if (++n >= ATTIRES_MAX) break;
        }
    }

    public static void impact(ServerLevel level, Strike strike, Drop d) {
        Vec3 p = d.impact();
        ServerPlayer caller = caller(level, strike);
        if (d.kind != OrdnanceKind.CLUSTER && d.kind != OrdnanceKind.SMOKE_CAN && d.kind != OrdnanceKind.GAS_CAN
                && (d.kind != OrdnanceKind.BULLET || level.random.nextInt(8) == 0)) {
            attractMonsters(level, p);
        }
        switch (d.kind) {
            case BOMB -> {
                explode(level, caller, p, AirstrikeConfig.BOMB_POWER.get().floatValue(), false, true);
                Network.sendImpact(level, new ImpactPacket(ImpactPacket.BOMB, p.x, p.y, p.z, AirstrikeConfig.BOMB_POWER.get().floatValue()));
            }
            case GBU -> {
                explode(level, caller, p, AirstrikeConfig.PRECISION_POWER.get().floatValue(), false, true);
                Network.sendImpact(level, new ImpactPacket(ImpactPacket.HEAVY, p.x, p.y, p.z, AirstrikeConfig.PRECISION_POWER.get().floatValue()));
            }
            case CLUSTER -> {
                level.playSound(null, p.x, p.y, p.z, ModSounds.CANISTER_POP.get(), SoundSource.NEUTRAL, 4.0f, 1.0f);
                Network.sendImpact(level, new ImpactPacket(ImpactPacket.POP, p.x, p.y, p.z, 1.0f));
            }
            case BOMBLET -> {
                explode(level, caller, p, AirstrikeConfig.BOMBLET_POWER.get().floatValue(), false, true);
                Network.sendImpact(level, new ImpactPacket(ImpactPacket.SMALL, p.x, p.y, p.z, AirstrikeConfig.BOMBLET_POWER.get().floatValue()));
            }
            case NAPALM -> {
                explode(level, caller, p, 1.6f, true, false);
                level.addFreshEntity(EffectCloudEntityServeur.create(level, EffectCloudEntity.NAPALM, p, 6.0f,
                        AirstrikeConfig.NAPALM_SECONDS.get() * 20, 0xFF6A00));
                level.playSound(null, p.x, p.y, p.z, ModSounds.NAPALM_WHOOSH.get(), SoundSource.NEUTRAL, 5.0f, 0.9f);
                Network.sendImpact(level, new ImpactPacket(ImpactPacket.NAPALM, p.x, p.y, p.z, 2.0f));
            }
            case SMOKE_CAN -> {
                level.addFreshEntity(EffectCloudEntityServeur.create(level, EffectCloudEntity.SMOKE, p, 6.5f,
                        AirstrikeConfig.SMOKE_SECONDS.get() * 20, 0xDDDDDD));
                Network.sendImpact(level, new ImpactPacket(ImpactPacket.CANISTER, p.x, p.y, p.z, 1.0f));
            }
            case GAS_CAN -> {
                level.addFreshEntity(EffectCloudEntityServeur.create(level, EffectCloudEntity.GAS, p, 7.5f,
                        AirstrikeConfig.GAS_SECONDS.get() * 20, 0xA9B83E));
                Network.sendImpact(level, new ImpactPacket(ImpactPacket.CANISTER, p.x, p.y, p.z, 1.0f));
            }
            case BULLET -> {
                strafeHit(level, caller, p);
                Network.sendImpact(level, new ImpactPacket(ImpactPacket.BULLET, p.x, p.y, p.z, 1.0f));
            }
            default -> {
            }
        }
    }

    private static void strafeHit(ServerLevel level, @Nullable ServerPlayer caller, Vec3 p) {
        float dmg = AirstrikeConfig.GUN_DAMAGE.get().floatValue();
        if (dmg <= 0) return;
        double r = 2.2;
        AABB box = new AABB(p.x - r, p.y - 1, p.z - r, p.x + r, p.y + 2.5, p.z + r);
        DamageSource src = damage(level, STRAFE_DAMAGE, caller);
        // l'obus arrive du ciel : il touche ce qu'il voit depuis le dessus du point d'impact, jamais sous un toit d'une base
        Vec3 haut = new Vec3(p.x, p.y + 6, p.z);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double dist = Math.sqrt(e.distanceToSqr(p.x, e.getY(), p.z));
            if (dist > r || AbriFrappes.abrite(level, e) || !AbriFrappes.vue(level, haut, e.getEyePosition(), e)) continue;
            float f = (float) (1.0 - dist / r * 0.6);
            e.invulnerableTime = 0;
            e.hurt(src, dmg * f);
        }
    }

}

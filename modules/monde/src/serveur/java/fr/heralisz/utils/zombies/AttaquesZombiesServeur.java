package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.zombies.AttaquesZombies.Genre;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cote serveur des attaques animees (voir {@link AttaquesZombies}).
 * <ul>
 * <li>Le coup « au contact » de leur IA (ModMeleeAttackGoal) est annule : il lance seulement un geste (evenement
 * {@link AttaquesZombies#EVT_FRAPPE} vers les joueurs). A l'impact du geste ({@code armement} ticks plus tard), la cible
 * est touchee par le vrai coup du zombie ({@code doHurtTarget} : degats, recul) seulement si elle est encore a portee et
 * en vue. On peut donc esquiver en reculant. La Brute s'arrete pendant qu'elle arme.</li>
 * <li>Crawler : le petit saut de Minecraft (LeapAtTargetGoal, sans degats) est retire ; a 3,5 a 8 blocs d'une cible en
 * vue, il s'accroupit ({@link AttaquesZombies#EVT_BOND}), puis bondit vers elle ; s'il la percute en vol, il la mord
 * (meme coup que sa frappe). Un bond toutes les 3 a 5 s au plus.</li>
 * </ul>
 * Rien n'est sauvegarde : un zombie recharge repart sans geste en cours.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class AttaquesZombiesServeur {
    private AttaquesZombiesServeur() {
    }

    private static final double PORTEE_MARGE = 1.0;
    private static final double BOND_MIN = 3.5, BOND_MAX = 8.0;

    private static final class Etat {
        int debutFrappe = -1;
        LivingEntity cibleFrappe;
        int prochaineFrappe;
        int debutBond = -1;
        boolean enVol, mordu;
        int prochainBond = 40;
    }

    private static final Map<Mob, Etat> ETATS = new WeakHashMap<>();
    /** Vrai pendant notre propre coup : LivingAttackEvent le laisse passer. */
    private static boolean nosCoups;

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m)) return;
        if (AttaquesZombies.genre(m) == Genre.CRAWLER) m.goalSelector.removeAllGoals(g -> g instanceof LeapAtTargetGoal);
    }

    @SubscribeEvent
    public static void onAttaque(LivingAttackEvent e) {
        if (nosCoups || e.getEntity().level().isClientSide()) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof Mob m) || src.getDirectEntity() != m || !src.is(DamageTypes.MOB_ATTACK)) return;
        Genre g = AttaquesZombies.genre(m);
        if (g == null || (g == Genre.SURVIE && !ZombiesConfig.ESQUIVE_SURVIE.get())) return;
        e.setCanceled(true);
        Etat s = ETATS.computeIfAbsent(m, k -> new Etat());
        if (s.debutFrappe >= 0 || s.enVol || s.debutBond >= 0 || m.tickCount < s.prochaineFrappe) return;
        s.debutFrappe = m.tickCount;
        s.cibleFrappe = e.getEntity();
        m.level().broadcastEntityEvent(m, AttaquesZombies.EVT_FRAPPE);
        if (g == Genre.BRUTE) m.playSound(SoundEvents.RAVAGER_ROAR, 1.2f, 0.7f);
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Mob m) || m.level().isClientSide() || !m.isAlive()) return;
        Genre g = AttaquesZombies.genre(m);
        if (g == null) return;
        Etat s = ETATS.computeIfAbsent(m, k -> new Etat());
        if (s.debutFrappe >= 0) frappe(m, g, s);
        if (g == Genre.CRAWLER) bond(m, s);
    }

    private static void frappe(Mob m, Genre g, Etat s) {
        int t = m.tickCount - s.debutFrappe;
        LivingEntity c = s.cibleFrappe;
        if (g == Genre.BRUTE && t < g.armement) {
            m.getNavigation().stop();
            if (c != null) m.getLookControl().setLookAt(c, 30f, 30f);
        }
        if (t < g.armement) return;
        s.debutFrappe = -1;
        s.cibleFrappe = null;
        s.prochaineFrappe = m.tickCount + g.recharge - g.armement;
        if (c == null || !c.isAlive() || c.level() != m.level() || !aPortee(m, c) || !m.hasLineOfSight(c)) {
            if (g == Genre.BRUTE) impactSol(m);
            return;
        }
        toucher(m, c);
        if (g == Genre.BRUTE) impactSol(m);
    }

    private static boolean aPortee(Mob m, LivingEntity c) {
        double r = m.getBbWidth() * 2.0 + c.getBbWidth() + PORTEE_MARGE;
        return m.distanceToSqr(c.getX(), c.getY(), c.getZ()) <= r * r;
    }

    private static void toucher(Mob m, LivingEntity c) {
        nosCoups = true;
        try {
            m.doHurtTarget(c);
        } finally {
            nosCoups = false;
        }
    }

    /** Coup de la Brute dans le sol : poussiere et bruit sourd, qu'elle touche ou non. */
    private static void impactSol(Mob m) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        Vec3 av = Vec3.directionFromRotation(0, m.getYRot()).scale(m.getBbWidth() * 0.9);
        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, m.getX() + av.x, m.getY() + 0.1, m.getZ() + av.z, 10, 0.6, 0.05, 0.6, 0.01);
        sl.sendParticles(ParticleTypes.CLOUD, m.getX() + av.x, m.getY() + 0.1, m.getZ() + av.z, 14, 0.8, 0.05, 0.8, 0.05);
        sl.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.0f, 0.6f);
    }

    private static void bond(Mob m, Etat s) {
        LivingEntity c = m.getTarget();
        if (s.debutBond >= 0) {
            int t = m.tickCount - s.debutBond;
            if (t < AttaquesZombies.BOND_PREPARATION) {
                m.getNavigation().stop();
                if (c != null) m.getLookControl().setLookAt(c, 30f, 30f);
                return;
            }
            if (t == AttaquesZombies.BOND_PREPARATION) {
                if (c == null || !c.isAlive()) {
                    s.debutBond = -1;
                    return;
                }
                Vec3 cible = c.position().add(c.getDeltaMovement().scale(4));
                Vec3 d = cible.subtract(m.position());
                double dist = Math.sqrt(d.x * d.x + d.z * d.z);
                double v = Mth.clamp(dist * 0.17, 0.6, 1.25);
                m.setDeltaMovement(d.x / Math.max(0.01, dist) * v, 0.42 + Math.min(0.25, Math.max(0, d.y) * 0.1), d.z / Math.max(0.01, dist) * v);
                m.hasImpulse = true;
                m.setOnGround(false);
                s.enVol = true;
                s.mordu = false;
                m.playSound(SoundEvents.ZOMBIE_AMBIENT, 1.0f, 1.5f);
                return;
            }
            if (s.enVol && !s.mordu && c != null && c.isAlive() && m.getBoundingBox().inflate(0.45).intersects(c.getBoundingBox())) {
                s.mordu = true;
                toucher(m, c);
            }
            if ((m.onGround() || m.isInWater()) && t > AttaquesZombies.BOND_PREPARATION + 3 || t > 60) {
                s.debutBond = -1;
                s.enVol = false;
                s.prochainBond = m.tickCount + 60 + m.getRandom().nextInt(40);
                s.prochaineFrappe = m.tickCount + 8;
            }
            return;
        }
        if (c == null || !c.isAlive() || s.debutFrappe >= 0 || m.tickCount < s.prochainBond || !m.onGround() || m.isInWater()) return;
        double d = m.distanceTo(c);
        if (d < BOND_MIN || d > BOND_MAX || Math.abs(c.getY() - m.getY()) > 3 || !m.hasLineOfSight(c)) return;
        if (m.getRandom().nextInt(8) != 0) return;
        s.debutBond = m.tickCount;
        m.level().broadcastEntityEvent(m, AttaquesZombies.EVT_BOND);
    }
}

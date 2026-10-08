package com.Spartan.check.combat;

import com.Spartan.check.CombatCheck;
import com.Spartan.config.SpartanConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Détection d'attaques hors du champ de vision (killaura), côté serveur.
 *
 * Un joueur frappe ce qu'il regarde. On calcule l'angle entre la direction du
 * regard et la cible, moins la taille angulaire de la hitbox (frapper le bord
 * d'une grosse entité proche est légitime).
 *
 * Évaluation DIFFÉRÉE d'un tick : le client envoie son coup avant la rotation
 * du même tick, donc au moment du coup le serveur ne connaît que l'ancienne
 * direction du regard. On recalcule au tick suivant avec la direction à jour et
 * on garde l'écart le plus favorable au joueur : un coup donné en tournant vite
 * la souris n'est pas signalé.
 */
public class KillAuraCheck extends CombatCheck {
    private static final int MAX_PENDING = 8;
    private static final int STREAK_TO_FLAG = 3;

    private record Pending(Vec3 center, double radius, double offsetAtHit, long gameTime, String targetType) {
    }

    private final Map<UUID, List<Pending>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> streak = new ConcurrentHashMap<>();

    public KillAuraCheck() {
        super("KillAura");
    }

    @Override
    public void onAttack(ServerPlayer p, Entity target) {
        if (!SpartanConfig.CHECK_KILLAURA.get() || p.isSpectator()) {
            return;
        }
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        double radius = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) / 2.0;
        double offset = offset(p, center, radius);
        if (Double.isNaN(offset)) {
            return; // collé à la cible : l'angle n'a pas de sens
        }
        List<Pending> list = pending.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        if (list.size() < MAX_PENDING) {
            list.add(new Pending(center, radius, offset, p.level().getGameTime(), target.getType().getDescriptionId()));
        }
    }

    @Override
    public void tick(ServerPlayer p) {
        List<Pending> list = pending.get(p.getUUID());
        if (list == null || list.isEmpty()) {
            return;
        }
        long now = p.level().getGameTime();
        double max = SpartanConfig.KILLAURA_MAX_ANGLE.get();
        List<Pending> done = new ArrayList<>();
        for (Pending h : list) {
            if (now <= h.gameTime()) {
                continue; // attendre la rotation du tick suivant
            }
            done.add(h);
            double later = offset(p, h.center(), h.radius());
            double best = Double.isNaN(later) ? h.offsetAtHit() : Math.min(h.offsetAtHit(), later);
            if (best > max) {
                // Un coup isolé hors champ peut venir d'un à-coup réseau : on attend une série.
                int n = streak.merge(p.getUUID(), 1, Integer::sum);
                if (n >= STREAK_TO_FLAG) {
                    flag(p, String.format("%d coups d'affilée hors du regard (dernier à %.0f°, max %.0f°, cible %s)",
                            n, best, max, h.targetType()));
                }
            } else {
                streak.computeIfPresent(p.getUUID(), (k, v) -> v > 1 ? v - 1 : null);
            }
        }
        list.removeAll(done);
    }

    /** Angle (degrés) entre le regard et la hitbox, ou NaN si l'œil est dans la cible. */
    private static double offset(ServerPlayer p, Vec3 center, double radius) {
        Vec3 toTarget = center.subtract(p.getEyePosition());
        double distance = toTarget.length();
        if (distance < 0.5) {
            return Double.NaN;
        }
        double dot = Math.max(-1.0, Math.min(1.0, p.getLookAngle().dot(toTarget.normalize())));
        double angle = Math.toDegrees(Math.acos(dot));
        double halfSize = Math.toDegrees(Math.atan(radius / distance));
        return angle - halfSize;
    }

    @Override
    public void clear(UUID uuid) {
        pending.remove(uuid);
        streak.remove(uuid);
    }
}

package fr.heraliz.grades.serveur;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Délai « sans bouger » des téléportations (/tpa, /warp, Grades 2.4.1, 23/10, demande du propriétaire) : seul un
 * déplacement voulu par le joueur annule. Poussé par un autre joueur ou une créature (contact) ou repoussé par un coup :
 * le point de départ suit le joueur au lieu d'annuler. Fil serveur seulement.
 */
final class Immobile {
    static final double PAS = 0.09;

    private Immobile() {
    }

    /** Un autre corps touche le joueur, ou il vient d'être repoussé (recul d'un coup). */
    static boolean pousse(ServerPlayer p) {
        if (p.hurtTime > 0) return true;
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(0.35))) {
            if (e.isPushable() && !e.isSpectator()) return true;
        }
        return false;
    }

    /**
     * Nouveau point de départ, ou {@code null} si le joueur a bougé de lui-même (départ annulé).
     */
    static Vec3 suivre(ServerPlayer p, Vec3 depart) {
        if (p.position().distanceToSqr(depart) <= PAS) return depart;
        return pousse(p) ? p.position() : null;
    }
}

package com.Spartan.check.movement.model;

/**
 * Joueur vanilla 1.20.1 simulé tick par tick (LivingEntity.aiStep + travel), sur un
 * sol plat (y = sol) avec un plafond optionnel : sert à vérifier que les enveloppes
 * ne signalent JAMAIS un client honnête, et que les triches les dépassent.
 */
final class VanillaWalker {
    double x;
    double y;
    double z;
    double vx;
    double vy;
    double vz;
    boolean onGround = true;
    double yaw; // radians, 0 = +z
    double friction = 0.6;
    double floor = 0;
    double ceiling = Double.POSITIVE_INFINITY;
    double height = 1.8;
    double gravity = 0.08;
    double jumpVelocity = 0.42;
    boolean slimeFloor;
    int noJumpDelay;

    /** Résultat d'un tick : ce que le client envoie, plus son état de départ. */
    record Step(double dx, double dy, double dz, boolean groundStart, boolean groundEnd) {
        double horizontal() {
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    Step tick(double forward, double strafe, boolean sprint, boolean jump, boolean usingItem) {
        boolean groundStart = onGround;
        if (Math.abs(vx) < 0.003) {
            vx = 0;
        }
        if (Math.abs(vy) < 0.003) {
            vy = 0;
        }
        if (Math.abs(vz) < 0.003) {
            vz = 0;
        }
        if (noJumpDelay > 0) {
            noJumpDelay--;
        }
        double fwd = forward;
        double str = strafe;
        if (usingItem) {
            fwd *= 0.2;
            str *= 0.2;
        }
        if (jump && onGround && noJumpDelay == 0) {
            vy = jumpVelocity;
            if (sprint) {
                vx += -Math.sin(yaw) * 0.2;
                vz += Math.cos(yaw) * 0.2;
            }
            noJumpDelay = 10;
        }
        fwd *= 0.98;
        str *= 0.98;
        double speed = sprint ? 0.13 : 0.1;
        double accel = onGround ? speed * (0.21600002 / (friction * friction * friction)) : (sprint ? 0.026 : 0.02);
        double len2 = fwd * fwd + str * str;
        if (len2 >= 1.0E-7) {
            double ix = str;
            double iz = fwd;
            if (len2 > 1) {
                double l = Math.sqrt(len2);
                ix /= l;
                iz /= l;
            }
            ix *= accel;
            iz *= accel;
            double s = Math.sin(yaw);
            double c = Math.cos(yaw);
            vx += ix * c - iz * s;
            vz += iz * c + ix * s;
        }
        // Déplacement avec collisions sol / plafond.
        double ox = x;
        double oy = y;
        double oz = z;
        x += vx;
        z += vz;
        double ny = y + vy;
        boolean landed = false;
        if (ny <= floor) {
            ny = floor;
            landed = true;
        } else if (ny + height > ceiling) {
            ny = ceiling - height;
        }
        boolean hitCeiling = ny + height >= ceiling - 1e-9 && vy > 0;
        y = ny;
        double fallVy = vy;
        if (landed) {
            vy = slimeFloor && fallVy < 0 ? -fallVy : 0;
        } else if (hitCeiling) {
            vy = 0;
        }
        onGround = landed;
        double f3 = groundStart ? friction * 0.91 : 0.91;
        vx *= f3;
        vz *= f3;
        vy = (vy - gravity) * 0.98;
        return new Step(x - ox, y - oy, z - oz, groundStart, onGround);
    }
}

package com.Spartan.check.combat;

import com.Spartan.check.CombatCheck;
import com.Spartan.config.SpartanConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Détection de portée d'attaque anormale (reach), côté serveur.
 *
 * On mesure la distance entre l'œil de l'attaquant et le point le plus proche
 * de la hitbox de la cible, au moment où le serveur reçoit le coup. En survie
 * vanilla, cette distance ne dépasse pas ~3 blocs.
 *
 * Une marge est ajoutée selon le ping, l'interpolation du client et la vitesse
 * réelle de la cible : avec de la latence, la cible a pu bouger entre ce que le
 * joueur voyait et l'arrivée du coup sur le serveur.
 * La portée de base est celle du joueur (attribut Forge entity_reach), donc les
 * armes longues des mods sont prises en compte.
 */
public class ReachCheck extends CombatCheck {
    private static final double CLIENT_INTERPOLATION_S = 0.15;
    private static final double MAX_LAG_BONUS = 3.0; // plafond de la marge
    private static final double VANILLA_REACH = 3.0;

    public ReachCheck() {
        super("Reach");
    }

    @Override
    public boolean runsOnSecondaryTargets() {
        return true; // la portée a un sens pour chaque cible
    }

    @Override
    public void onAttack(ServerPlayer p, Entity target) {
        if (!SpartanConfig.CHECK_REACH.get() || p.isCreative() || p.isSpectator()) {
            return;
        }
        Vec3 eye = p.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 closest = new Vec3(
                Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
        double dist = eye.distanceTo(closest);

        // Portée réelle du joueur (attribut Forge) : les armes longues de mods
        // (lances, hallebardes…) l'augmentent légitimement. La config donne la
        // portée de référence vanilla + marge ; on garde la même marge au-dessus
        // de la portée réelle.
        double margin = SpartanConfig.REACH_DISTANCE.get() - VANILLA_REACH;
        // Le client voit la cible avec du retard : ping + interpolation (~0,15 s).
        // Pendant ce temps la cible a pu bouger à SA vitesse réelle (monture comprise).
        Entity root = target.getRootVehicle();
        double targetSpeed = Math.hypot(root.getX() - root.xo, root.getZ() - root.zo) * 20.0; // blocs/s
        double delay = p.latency / 1000.0 + CLIENT_INTERPOLATION_S;
        double lagBonus = Math.min(MAX_LAG_BONUS, 0.3 + delay * targetSpeed);
        double limit = Math.max(p.getEntityReach(), VANILLA_REACH) + margin + lagBonus;

        if (dist > limit) {
            flag(p, String.format("frappe à %.2f blocs (limite %.2f, ping %d ms, cible %s)",
                    dist, limit, p.latency, target.getType().getDescriptionId()));
        }
    }
}

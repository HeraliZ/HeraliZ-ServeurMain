package fr.heraliz.banc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Lieu de vie d'un bot : monde, centre et rayon de ses déplacements, et la zone de groupe ({@link Zones}) dont il fait
 * partie (-1 : aucune, par exemple dans le tutoriel des bases ou après un {@code /bots lancer} tapé à la main).
 */
record Place(ServerLevel niveau, Vec3 centre, int rayon, int zone) {
    Place(ServerLevel niveau, Vec3 centre, int rayon) {
        this(niveau, centre, rayon, -1);
    }
}

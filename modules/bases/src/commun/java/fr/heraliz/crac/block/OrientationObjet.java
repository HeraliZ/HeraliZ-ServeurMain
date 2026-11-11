package fr.heraliz.crac.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Sens d'un objet posé collé aux murs (coffres, caisse en métal, armoire) : sa face avant tournée vers le joueur, au quart
 * de tour près, selon le sens où il regarde au moment de la pose. Le sens posé est gardé dans l'état du bloc (FACING).
 * <p>HeraliZ (11/11, demande du propriétaire : « on tourne en fonction de l'orientation de son corps uniquement ») : la
 * touche R qui ajoutait des quarts de tour (28/10, avec son aide sous le viseur et son paquet) est retirée ; le sens ne
 * vient plus que du joueur. Jamais tiré de la fondation visée ni de ses voisines.
 */
public final class OrientationObjet {
    private OrientationObjet() {
    }

    /** Sens de pose : face avant vers le joueur. */
    public static Direction face(BlockPlaceContext ctx) {
        return ctx.getHorizontalDirection().getOpposite();
    }
}

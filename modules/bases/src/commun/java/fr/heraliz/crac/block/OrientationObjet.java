package fr.heraliz.crac.block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Sens d'un objet posé collé aux murs (coffres, caisse en métal, armoire), HeraliZ, 28/10. Avant, il n'était que le regard
 * du joueur arrondi au quart de tour : près d'une fondation triangle (pièces de biais, joueur qui regarde de biais), le
 * coffre prenait un sens inattendu, de travers par rapport à la pièce, sans moyen de le changer. Le sens est maintenant
 * séparé de la place : face vers le joueur (comme avant), plus 0 à 3 quarts de tour choisis avec R (la touche « tourner »
 * du plan), objet en main. Jamais tiré de la fondation visée ni de ses voisines. Le choix vit chez le joueur
 * ({@link #client}) et au serveur ({@code C2SRotationObjet}) ; le sens posé est gardé dans l'état du bloc (FACING).
 */
public final class OrientationObjet {
    private static volatile int client;
    private static final Map<UUID, Integer> SERVEUR = new ConcurrentHashMap<>();

    private OrientationObjet() {
    }

    /** Quarts de tour choisis par ce joueur (0 à 3). */
    public static int quarts(Player p) {
        if (p == null) {
            return 0;
        }
        return p.level().isClientSide() ? client : SERVEUR.getOrDefault(p.getUUID(), 0);
    }

    /** Sens de pose : face avant vers le joueur, tournée de ses quarts de tour (sens des aiguilles d'une montre). */
    public static Direction face(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection().getOpposite();
        for (int i = quarts(ctx.getPlayer()); i > 0; i--) {
            f = f.getClockWise();
        }
        return f;
    }

    /** Chez le joueur : le choix actuel. */
    public static int client() {
        return client;
    }

    public static void client(int quarts) {
        client = quarts & 3;
    }

    /** Au serveur : le choix reçu d'un joueur. */
    public static void serveur(UUID joueur, int quarts) {
        if ((quarts & 3) == 0) {
            SERVEUR.remove(joueur);
        } else {
            SERVEUR.put(joueur, quarts & 3);
        }
    }

    public static void oublier(UUID joueur) {
        SERVEUR.remove(joueur);
    }
}

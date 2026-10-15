package fr.heraliz.loot;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * API des salles à carte d'accès, appelée par HeraliZ Bunker (côté serveur, par réflexion : ne jamais renommer cette
 * classe ni changer ces signatures, CLAUDE.md §5). Les conteneurs de loot posés dans la boîte d'une salle forment sa
 * zone « salles_carte/&lt;id&gt; » ({@link RotationLoot}) : elle ne se remplit jamais d'elle-même, seulement par
 * {@link #recharger}, que Bunker appelle quand la salle est vide depuis le délai de sa couleur. Tout est gardé avec le
 * monde (heraliz_loot_rotation.dat de la dimension).
 */
public final class SallesLoot {
    private SallesLoot() {}

    /**
     * Déclare (ou redessine) la salle id : boîte de min à max, blocs compris. Ses conteneurs quittent leur zone et restent
     * vides jusqu'à la prochaine recharge. Même boîte qu'avant : rien ne change. -> nombre de conteneurs de la salle.
     */
    public static int declarer(ServerLevel l, String id, String nom, BlockPos min, BlockPos max) {
        return RotationLoot.get(l).declarerSalle(l, id, nom, min, max);
    }

    /** Retire la salle id : ses conteneurs retournent dans la rotation habituelle. -> faux si elle était inconnue. */
    public static boolean retirer(ServerLevel l, String id) {
        return RotationLoot.get(l).retirerSalle(l, id);
    }

    /** Remplit à neuf tous les conteneurs de la salle id. -> nombre de conteneurs remplis, -1 si la salle est inconnue. */
    public static int recharger(ServerLevel l, String id) {
        return RotationLoot.get(l).rechargerSalle(l, id);
    }

    /** {conteneurs, conteneurs remplis, remplis déjà ouverts} de la salle id ; null si Loot ne la connaît pas. */
    @Nullable
    public static int[] etat(ServerLevel l, String id) {
        return RotationLoot.get(l).etatSalle(l, id);
    }
}

package fr.heraliz.carte.serveur;

import fr.heralisz.utils.inspecter.BaseCrac;
import fr.heraliz.carte.CarteNet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Repère de la base du clan sur la carte du monde (04/11, demande du propriétaire : « un petit logo du claim block sur la
 * carte, pour pouvoir y retourner à pied si on n'a pas de home »). À l'ouverture de la carte, le joueur demande où est
 * l'armoire (Claim Block) de la base de son clan ; la réponse vient de CRac ({@link BaseCrac}, par réflexion). Rien n'est
 * calculé en dehors de ces demandes ; au plus une par seconde et par joueur.
 */
final class BaseCarte {
    private static final Map<UUID, Integer> DERNIERE = new HashMap<>();

    private BaseCarte() {
    }

    static void demande(ServerPlayer p) {
        int t = p.server.getTickCount();
        Integer d = DERNIERE.get(p.getUUID());
        if (d != null && t - d < 20 && t >= d) return;
        DERNIERE.put(p.getUUID(), t);
        Object[] b = BaseCrac.base(p.server, p.getUUID());
        CarteNet.BaseS2C r = CarteNet.BaseS2C.AUCUNE;
        if (b != null && b[0] instanceof ServerLevel l && b[1] instanceof BlockPos pos) {
            r = new CarteNet.BaseS2C(true, l.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
        CarteNet.versJoueur(p, r);
    }

    static void oublier(UUID u) {
        DERNIERE.remove(u);
    }
}

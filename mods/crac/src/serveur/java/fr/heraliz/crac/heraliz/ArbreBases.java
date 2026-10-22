package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Effets de l'arbre d'améliorations des clans (HeraliZ Clans 3.6.0) sur les bases de CRac :
 * <ul>
 * <li>branche « base » : la zone de l'armoire passe au palier suivant (rayon 20, 23, 26, 29 blocs : claims de niveau 1 à
 * 4) ; jamais réduite (une base agrandie avant avec du métal le reste) ; refusée tant qu'un bâtiment d'un autre groupe est
 * dans la zone agrandie ou que la base est sous raid (réessayé toutes les 5 s) ;</li>
 * <li>branche « tp » : délai entre deux /home (10, 8, 6, 4 min) ({@link #delaiHomeMs}).</li>
 * </ul>
 * Avec la saison, l'agrandissement au métal renforcé de l'armoire est coupé ({@link #agrandissementParArbre}).
 */
public final class ArbreBases {
    private ArbreBases() {
    }

    /** Zones déjà signalées comme bloquées (une ligne de journal par cas). */
    private static final Set<String> SIGNALES = new HashSet<>();

    /** La taille des bases des clans vient de l'arbre (et plus du métal renforcé). */
    public static boolean agrandissementParArbre() {
        return BasesClans.actif() && SaisonPont.present();
    }

    static void appliquer(MinecraftServer server, BasesClans d) {
        if (!agrandissementParArbre()) {
            return;
        }
        for (Map.Entry<UUID, UUID> e : BasesClans.groupesDesClans(server).entrySet()) {
            int voulu = 1 + Math.max(0, Math.min(3, SaisonPont.palier(e.getKey(), "base")));
            for (ServerLevel level : server.getAllLevels()) {
                Claim c = ClaimData.get(level).getByGroup(e.getValue());
                if (c == null || c.tier() >= voulu) {
                    continue;
                }
                String probleme = ClaimManager.upgradeProblem(level, c, voulu);
                String cle = c.groupId() + "|" + voulu + "|" + probleme;
                if (probleme != null) {
                    if (SIGNALES.add(cle)) {
                        Crac.LOGGER.info("[CRac] base du clan {} : agrandissement au niveau {} en attente ({})", e.getKey(), voulu, probleme);
                    }
                    continue;
                }
                c.setTier(voulu);
                ClaimData.get(level).markDirty();
                BlockState st = level.getBlockState(c.anchor());
                if (st.getBlock() instanceof ClaimBlock) {
                    level.setBlock(c.anchor(), st.setValue(ClaimBlock.TIER, Math.min(3, voulu)), 3);
                }
                C2SRequestNearbyClaimsTraitement.pushAll(level);
                Crac.LOGGER.info("[CRac] base du clan {} agrandie (arbre) : niveau {}, rayon {}", e.getKey(), voulu, c.radius());
            }
        }
    }

    /** Délai entre deux /home du joueur : celui de l'arbre de son clan (10 - 2 x palier min), sinon {@code defaut}. */
    public static long delaiHomeMs(UUID joueur, long defaut) {
        if (!agrandissementParArbre()) {
            return defaut;
        }
        @Nullable UUID clan = PriseCoeur.clanDe(joueur);
        if (clan == null) {
            return defaut;
        }
        int p = Math.max(0, Math.min(3, SaisonPont.palier(clan, "tp")));
        return (10L - 2L * p) * 60_000L;
    }
}

package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.Crac;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Lignes du journal d'activite du clan (onglet Apercu) et de son historique (onglet Historique, 11/11) pour la base et le
 * coeur : armoire posee ou retiree, coeur pose ou repris. Par {@code ClansApi.journal} de HeraliZ Clans (textes dans les
 * fichiers de langue de CRac : {@code crac.journal.*}) ; groupe de CRac sans clan (cabane du tutoriel) : rien.
 */
public final class JournalClan {
    private JournalClan() {
    }

    /** Ligne pour le clan du groupe {@code groupe} de CRac (rien si ce groupe n'est pas celui d'un clan). */
    public static void groupe(MinecraftServer server, @Nullable UUID groupe, String cle, String... args) {
        clan(BasesClans.clanDuGroupe(server, groupe), cle, args);
    }

    /** Ligne pour le clan {@code clan}. */
    public static void clan(@Nullable UUID clan, String cle, String... args) {
        if (clan == null) {
            return;
        }
        try {
            fr.heralisz.clans.api.ClansApi.journal(clan, cle, args);
        } catch (LinkageError | RuntimeException ex) {
            Crac.LOGGER.warn("[CRac] journal du clan {} : {}", clan, ex.toString());
        }
    }
}

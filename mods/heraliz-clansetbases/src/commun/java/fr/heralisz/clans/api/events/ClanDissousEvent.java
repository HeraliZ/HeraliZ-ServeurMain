package fr.heralisz.clans.api.events;

import net.minecraftforge.eventbus.api.Event;

import java.util.Set;
import java.util.UUID;

/**
 * Poste sur {@code MinecraftForge.EVENT_BUS} (fil serveur) quand un clan vient d'etre dissous (par son chef ou par
 * /clan admin supprimer). Le clan n'existe deja plus : {@code ClansApi.infos(clanId)} renvoie vide. {@link #membres} =
 * les membres au moment de la dissolution (aucun {@link MembreQuitteEvent} n'est poste pour eux).
 */
public class ClanDissousEvent extends Event {
    public final UUID clanId;
    public final String tag;
    public final Set<UUID> membres;

    public ClanDissousEvent(UUID clanId, String tag, Set<UUID> membres) {
        this.clanId = clanId;
        this.tag = tag;
        this.membres = Set.copyOf(membres);
    }
}

package fr.heralisz.clans.api.events;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Poste sur {@code MinecraftForge.EVENT_BUS} (fil serveur) quand un joueur vient de sortir d'un clan qui continue
 * d'exister : depart volontaire ({@link #exclu} faux), exclusion ({@link #exclu} vrai) ou deplacement force par
 * /clan admin vers un autre clan (faux). Le joueur n'est deja plus membre. Pas poste a la dissolution (voir
 * {@link ClanDissousEvent}).
 */
public class MembreQuitteEvent extends Event {
    public final UUID clanId;
    public final UUID joueur;
    public final boolean exclu;

    public MembreQuitteEvent(UUID clanId, UUID joueur, boolean exclu) {
        this.clanId = clanId;
        this.joueur = joueur;
        this.exclu = exclu;
    }
}

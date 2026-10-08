package fr.heralisz.clans.territory.api;

import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Evenements postes sur {@code MinecraftForge.EVENT_BUS} par le module territoires (thread serveur). Le module
 * « regles de jeu » s'y abonne pour les annonces (chat, toasts, sons), l'XP, les stats, les quetes et le journal du
 * clan. Le module territoires ne fait que mettre a jour son etat et poster.
 */
public final class TerritoireEvents {
    private TerritoireEvents() {
    }

    /** Une zone vient d'etre capturee par un clan. */
    public static class Capture extends Event {
        public final UUID clanId;
        public final String zoneId;
        public final String nomZone;
        /** Ancien proprietaire (null si la zone etait libre). */
        @Nullable
        public final UUID ancienClan;
        /** Membres presents dans la zone au moment de la capture (stats « captures »). */
        public final Collection<UUID> capteurs;

        public Capture(UUID clanId, String zoneId, String nomZone, @Nullable UUID ancienClan, Collection<UUID> capteurs) {
            this.clanId = clanId;
            this.zoneId = zoneId;
            this.nomZone = nomZone;
            this.ancienClan = ancienClan;
            this.capteurs = capteurs == null ? List.of() : List.copyOf(capteurs);
        }
    }

    /** Un clan vient de perdre une zone (capture adverse, reset admin, dissolution). */
    public static class Perdu extends Event {
        public final UUID clanId;
        public final String zoneId;
        public final String nomZone;

        public Perdu(UUID clanId, String zoneId, String nomZone) {
            this.clanId = clanId;
            this.zoneId = zoneId;
            this.nomZone = nomZone;
        }
    }

    /** Un signal de ravitaillement vient d'etre lance sur une zone (annonce a tout le serveur a faire). */
    public static class Ravitaillement extends Event {
        public final UUID clanId;
        public final String zoneId;
        public final String nomZone;
        public final UUID joueur;

        public Ravitaillement(UUID clanId, String zoneId, String nomZone, UUID joueur) {
            this.clanId = clanId;
            this.zoneId = zoneId;
            this.nomZone = nomZone;
            this.joueur = joueur;
        }
    }

    /** Toutes les minutes, pour chaque clan qui tient au moins une zone (quete TENIR_ZONE_MIN). */
    public static class TenueMinute extends Event {
        public final UUID clanId;
        public final int zonesTenues;

        public TenueMinute(UUID clanId, int zonesTenues) {
            this.clanId = clanId;
            this.zonesTenues = zonesTenues;
        }
    }
}

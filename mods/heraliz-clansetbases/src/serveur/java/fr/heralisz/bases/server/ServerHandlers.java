package fr.heralisz.bases.server;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.network.Packets;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Messages client -> serveur : cadence (une action toutes les 200 ms par joueur) puis la regle correspondante dans
 * {@link BaseService} (qui reverifie tout : le client n'est jamais cru). Apres une action, l'etat est renvoye.
 */
public final class ServerHandlers {
    private ServerHandlers() {
    }

    private static final long CADENCE_MS = 200;
    private static final Map<UUID, Long> DERNIERE = new HashMap<>();

    static boolean cadence(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long last = DERNIERE.get(p.getUUID());
        if (last != null && now - last < CADENCE_MS) {
            BaseService.refus(p, "msg.heralisz_bases.trop_rapide");
            return false;
        }
        DERNIERE.put(p.getUUID(), now);
        return true;
    }

    static void oublier(UUID joueur) {
        DERNIERE.remove(joueur);
    }

    public static void action(ServerPlayer p, Packets.ActionC2S m) {
        if (BasesStore.get() == null) return;
        // les demandes de lecture ne comptent pas dans la cadence (l'ecran peut en enchainer deux)
        boolean lecture = switch (m.genre()) {
            case ETAT, PLAN, FERMER -> true;
            default -> false;
        };
        if (!lecture && !cadence(p)) return;
        try {
            switch (m.genre()) {
                case OUVRIR -> ouvrir(p, "piller".equalsIgnoreCase(m.arg()) ? Packets.OuvrirS2C.Ecran.PILLER : Packets.OuvrirS2C.Ecran.BASE);
                case FERMER -> {
                    // rien a liberer cote serveur pour l'instant (pas d'envoi lent)
                }
                case ETAT -> Sync.etat(p);
                case PLAN -> Sync.plan(p);
                case PILLABLES -> Sync.pillables(p);
                case CREER -> {
                    BaseService.creer(p);
                    Sync.etat(p);
                }
                case ENTRER -> BaseService.entrer(p, m.arg().isEmpty() ? null : m.arg());
                case SORTIR -> BaseService.sortir(p);
                case DEBLOQUER -> {
                    BaseService.debloquer(p, m.arg());
                    Sync.etat(p);
                }
                case PILLER -> BasesApi.pillage().piller(p, m.arg().isEmpty() ? null : m.arg());
                case ANNULER -> Teleports.annuler(p, "");
            }
        } catch (RuntimeException ex) {
            HeraliszBases.LOG.error("[bases] action {} de {} en erreur", m.genre(), p.getGameProfile().getName(), ex);
            BaseService.erreur(p, "msg.heralisz_bases.erreur_interne");
        }
    }

    /** Ouvre un ecran : etat + plan (BASE) ou liste (PILLER), puis l'ordre d'ouverture. */
    public static void ouvrir(ServerPlayer p, Packets.OuvrirS2C.Ecran ecran) {
        if (ecran == Packets.OuvrirS2C.Ecran.PILLER) {
            Sync.pillables(p);
        } else {
            Sync.plan(p);
            Sync.etat(p);
        }
        BasesNet.toPlayer(p, new Packets.OuvrirS2C(ecran));
    }
}

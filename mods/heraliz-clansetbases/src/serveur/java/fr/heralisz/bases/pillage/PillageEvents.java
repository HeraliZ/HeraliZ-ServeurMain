package fr.heralisz.bases.pillage;

import fr.heralisz.bases.api.BasesApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Evenements Forge du pillage : chaque seconde, avertissement des pillards {@code avertissement_s} avant la fin de la
 * fenetre (toast + chat) puis renvoi a leur position d'origine quand elle ferme ; mort d'un pillard (pas de retour) ;
 * reconnexion ; pillages interrompus par un arret clos au demarrage.
 */
public final class PillageEvents {
    private PillageEvents() {
    }

    private static int seconde;
    /** Pillards deja prevenus pour la fin de fenetre {@link #finAvertie}. */
    private static final Set<UUID> AVERTIS = new HashSet<>();
    private static long finAvertie;

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        Pillages.cloreOrphelins();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        Pillages.vider();
        AVERTIS.clear();
        finAvertie = 0;
        seconde = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++seconde < 20) return;
        seconde = 0;
        if (Pillages.sessions().isEmpty()) return;
        MinecraftServer server = e.getServer();
        if (!BasesApi.fenetreOuverte()) {
            Pillages.finDeFenetre(server);
            AVERTIS.clear();
            return;
        }
        long fin = BasesApi.finFenetreMs();
        if (fin <= 0) return; // fenetre forcee : pas d'heure de fin
        if (fin != finAvertie) {
            finAvertie = fin;
            AVERTIS.clear();
        }
        long reste = fin - System.currentTimeMillis();
        if (reste > PillageConfig.AVERTISSEMENT_S.get() * 1000L) return;
        int minutes = (int) Math.max(1, (reste + 59_999) / 60_000);
        List<UUID> nouveaux = null;
        for (Pillages.Session s : Pillages.sessions()) {
            if (!AVERTIS.add(s.joueur)) continue;
            if (nouveaux == null) nouveaux = new ArrayList<>();
            nouveaux.add(s.joueur);
        }
        if (nouveaux != null) Pillages.avertir(server, minutes, nouveaux);
    }

    /** Mort d'un pillard : plus de retour ce jour-la (mort normale : corps et stuff restent dans la base). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMort(LivingDeathEvent e) {
        if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) Pillages.mort(p);
    }

    @SubscribeEvent
    public static void onConnexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Pillages.reconnexion(p);
    }
}

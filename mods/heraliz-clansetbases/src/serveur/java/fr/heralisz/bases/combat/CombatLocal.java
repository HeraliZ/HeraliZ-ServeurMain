package fr.heralisz.bases.combat;

import fr.heralisz.bases.BasesConfig;
import fr.heralisz.bases.HeraliszBases;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tag de combat LOCAL des bases, en attendant le moteur de matchs (MATCHS_SPEC §1.4) : un joueur est tague
 * {@code combat_tag_s} secondes (15 par defaut) apres avoir inflige ou subi des degats d'un AUTRE joueur (l'auteur reel :
 * projectiles et explosifs compris, via {@code DamageSource#getEntity}). Sert a refuser l'entree dans une base (et le
 * pillage), la sortie, et annule un compte a rebours en cours.
 * <p>Trois sources, un joueur est tague si l'une d'elles le dit (le plus long reste l'emporte) :
 * <ul>
 *   <li>ce tag local ;</li>
 *   <li>le nouveau systeme de combat de HeraliZ Matchs : cle {@link #CLE_COMBAT} ({@code long}, fin du tag en epoch ms)
 *   dans {@code persistentData} du joueur (RETOURS du 01/10 §3) ;</li>
 *   <li>le pont reflexif vers {@code fr.heralisz.matchs.moteur.api.CombatTag.restantMs(UUID)} s'il existe (tolerant :
 *   absent ou en erreur = ignore).</li>
 * </ul>
 * Horloge : {@link System#currentTimeMillis()} ; aucune allocation a la lecture. Fil serveur uniquement.
 */
public final class CombatLocal {
    private CombatLocal() {
    }

    /** Cle {@code persistentData} (long, epoch ms de fin du tag) ecrite par le systeme de combat de HeraliZ Matchs. */
    public static final String CLE_COMBAT = "heralisz:combat_jusqua";

    private static final Map<UUID, Long> FIN = new HashMap<>();
    private static boolean pontEssaye;
    private static Method pontRestant;
    private static int purge;

    /** Vrai si le joueur est tague en combat (local, cle de HeraliZ Matchs ou pont). */
    public static boolean tague(ServerPlayer p) {
        return p != null && restantMs(p) > 0;
    }

    /** Temps restant du tag d'un joueur en ligne (ms, 0 si non tague) : le plus long des trois tags. */
    public static long restantMs(ServerPlayer p) {
        if (p == null) return 0;
        long r = restantMs(p.getUUID());
        // getLong : 0 si la cle manque (et tolere un int ecrit a la place d'un long)
        long jusqua = p.getPersistentData().getLong(CLE_COMBAT);
        if (jusqua > 0) r = Math.max(r, jusqua - System.currentTimeMillis());
        return Math.max(0, r);
    }

    /** Temps restant du tag (ms, 0 si non tague), le plus long du tag local et du pont (sans la cle du joueur). */
    public static long restantMs(UUID joueur) {
        Long fin = FIN.get(joueur);
        long local = fin == null ? 0 : Math.max(0, fin - System.currentTimeMillis());
        return Math.max(local, pont(joueur));
    }

    /** Tague un joueur pour une duree pleine (utilisable par le module pillage). */
    public static void marquer(ServerPlayer p) {
        if (p != null) FIN.put(p.getUUID(), System.currentTimeMillis() + BasesConfig.COMBAT_TAG_S.get() * 1000L);
    }

    public static void effacer(UUID joueur) {
        FIN.remove(joueur);
    }

    private static long pont(UUID joueur) {
        if (!pontEssaye) {
            pontEssaye = true;
            try {
                Class<?> c = Class.forName("fr.heralisz.matchs.moteur.api.CombatTag");
                pontRestant = c.getMethod("restantMs", UUID.class);
                HeraliszBases.LOG.info("[bases] tag de combat : celui de HeraliZ Matchs est aussi consulte");
            } catch (ReflectiveOperationException | LinkageError ex) {
                pontRestant = null;
            }
        }
        if (pontRestant == null) return 0;
        try {
            Object r = pontRestant.invoke(null, joueur);
            return r instanceof Long l ? l : 0;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszBases.LOG.warn("[bases] pont vers CombatTag (matchs) coupe : {}", ex.toString());
            pontRestant = null;
            return 0;
        }
    }

    // ------------------------------------------------------------------ evenements
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent e) {
        if (e.isCanceled() || e.getAmount() <= 0 || !(e.getEntity() instanceof ServerPlayer victime)) return;
        Entity auteur = e.getSource().getEntity();
        if (auteur instanceof ServerPlayer attaquant && attaquant != victime) {
            marquer(attaquant);
            marquer(victime);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) effacer(p.getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++purge < 200) return;
        purge = 0;
        long now = System.currentTimeMillis();
        FIN.values().removeIf(fin -> fin <= now);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        FIN.clear();
        pontEssaye = false;
        pontRestant = null;
    }
}

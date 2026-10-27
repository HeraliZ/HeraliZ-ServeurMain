package fr.heraliz.airstrike.client.render;

import fr.heraliz.airstrike.item.DesignatorItem;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Horloge des animations des jumelles chez le joueur (27/10, « l'animation de rechargement saccade »).
 * <p>
 * Avant, la recharge (et la reprogrammation) se lisait sur le temps du monde du client plus le partialTick. Ce temps
 * avance d'un tick par tick du jeu et le serveur le recale d'un coup chaque seconde (ClientboundSetTimePacket ; même
 * défaut déjà corrigé pour le train, voir {@code TrainClient}) : dès que le serveur tient moins de 20 ticks par seconde ou
 * que le réseau hésite, l'animation de 2,3 s sautait en avant ou reculait d'un tick ou deux, une ou deux fois. Et quand la
 * cartouche arrivait du serveur avant que l'horloge du client ait rattrapé l'heure de départ, les jumelles restaient au
 * repos un instant puis partaient d'un coup.
 * <p>
 * Ici, la première image où une recharge est vue (heure de départ du serveur, {@link DesignatorItem#reloadStart}), son
 * départ est noté sur l'horloge réelle du joueur ({@code System.nanoTime}, relevée une fois par image) ; l'animation
 * avance ensuite d'un pas régulier, sans jamais reculer. Même durée ({@link DesignatorItem#RELOAD_TICKS} à 20 ticks par
 * seconde), mêmes étapes. Une recharge vue en cours de route (joueur qui arrive, jumelles reprises en main) part de là
 * où en est le serveur. Le serveur ne change pas : il décide toujours seul de la fin de la recharge.
 * <p>
 * Repères par heure de départ : deux joueurs qui rechargent au même tick ont la même animation, ce qui est juste.
 */
public final class HorlogeJumelles {
    private HorlogeJumelles() {
    }

    private static final long NS_TICK = 50_000_000L;
    /**
     * Écart (ticks) entre l'heure du client et le départ du serveur encore pris pour « elle commence maintenant » : l'heure
     * du client peut avoir quelques ticks d'avance sur celle du serveur entre deux recalages (serveur en retard).
     */
    private static final double TOLERANCE = 10.0;
    /** Départ plus loin que ça dans le futur du client : son horloge n'est pas encore calée (arrivée sur le serveur). */
    private static final double AVANCE_MAX = 40.0;
    /** Un repère est oublié tant de temps après son départ (l'animation est finie depuis longtemps). */
    private static final long OUBLI_NS = 30_000_000_000L;
    private static final long ORIGINE = System.nanoTime();

    private static final Long2LongOpenHashMap RECHARGES = new Long2LongOpenHashMap();
    private static final Long2LongOpenHashMap REPROGRAMMATIONS = new Long2LongOpenHashMap();

    static {
        RECHARGES.defaultReturnValue(Long.MIN_VALUE);
        REPROGRAMMATIONS.defaultReturnValue(Long.MIN_VALUE);
    }

    /** Heure de l'image en cours (nanosecondes), relevée au début de chaque image : la même pour tous les dessins. */
    private static long imageNs = ORIGINE;

    /** Début d'une image (RenderTickEvent START, {@code ClientEvents}). */
    public static void image() {
        imageNs = System.nanoTime();
    }

    /** Temps continu (ticks depuis le lancement du jeu) pour les clignotements et pulsations de l'écran et du voyant. */
    public static double temps() {
        return (imageNs - ORIGINE) / (double) NS_TICK;
    }

    /** 0..1 : avancée de la recharge des jumelles, 1 au repos. */
    public static float recharge(ItemStack stack) {
        return avancee(RECHARGES, DesignatorItem.reloadStart(stack), DesignatorItem.RELOAD_TICKS);
    }

    /** 0..1 : avancée de la reprogrammation de la cartouche de la baie, 1 au repos. */
    public static float reprogrammation(ItemStack stack) {
        return avancee(REPROGRAMMATIONS, DesignatorItem.reprogramStart(stack), DesignatorItem.REPROGRAM_TICKS);
    }

    private static float avancee(Long2LongOpenHashMap vus, long depart, int duree) {
        if (depart == Long.MIN_VALUE) return 1f;
        long maintenant = imageNs;
        long debut = vus.get(depart);
        if (debut == Long.MIN_VALUE) {
            // première image où ce départ est vu : on le place sur l'horloge réelle
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return 1f;
            double ecart = mc.level.getGameTime() + mc.getFrameTime() - depart;
            if (ecart >= duree || ecart < -AVANCE_MAX) return 1f;   // finie depuis longtemps, ou horloge pas encore calée
            if (ecart < TOLERANCE) ecart = 0;                         // elle commence : depuis le début, sans saut
            debut = maintenant - (long) (ecart * NS_TICK);
            oublier(vus, maintenant);
            vus.put(depart, debut);
        }
        double p = (maintenant - debut) / (double) (duree * NS_TICK);
        return p >= 1.0 ? 1f : (float) Math.max(0.0, p);
    }

    /** Retire les repères des animations finies depuis longtemps (appelé seulement quand un départ nouveau arrive). */
    private static void oublier(Long2LongOpenHashMap vus, long maintenant) {
        if (vus.isEmpty()) return;
        for (var it = vus.long2LongEntrySet().iterator(); it.hasNext(); ) {
            if (maintenant - it.next().getLongValue() > OUBLI_NS) it.remove();
        }
    }
}

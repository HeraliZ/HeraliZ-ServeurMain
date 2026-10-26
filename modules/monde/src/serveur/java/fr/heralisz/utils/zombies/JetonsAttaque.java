package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * « Jetons d'attaque » (25/10, combat juste) : au plus {@code attaquants_max} créatures différentes peuvent toucher un même
 * joueur par un coup au corps à corps dans une fenêtre de {@code fenetre_ticks} ; les autres attendent leur tour (leur coup
 * ne passe pas, elles restent autour). Plus de mort en une seconde sous dix coups à la fois quand une horde rattrape un
 * joueur. Ne compte que les vrais coups (priorité basse, coups déjà annulés ignorés : le geste des zombies esquivables
 * n'est compté qu'à l'impact). Les balles, explosions et autres dégâts ne sont pas concernés. Serveur seulement.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class JetonsAttaque {
    private JetonsAttaque() {
    }

    /** Par joueur : identifiants des attaquants récents et heure (tick de jeu) de leur dernier coup. */
    private static final Map<UUID, int[][]> RECENTS = new HashMap<>();
    private static long refuses;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void coup(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        int max = ZombiesConfig.JETONS.get();
        if (max <= 0) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof Mob m) || src.getDirectEntity() != m || !src.is(DamageTypes.MOB_ATTACK)) return;
        int maintenant = (int) p.level().getGameTime(), fenetre = ZombiesConfig.JETONS_FENETRE.get();
        int[][] t = RECENTS.get(p.getUUID());
        if (t == null || t.length != max) {
            t = new int[max][2];
            for (int[] x : t) x[1] = Integer.MIN_VALUE / 2;
            RECENTS.put(p.getUUID(), t);
        }
        int libre = -1;
        for (int i = 0; i < t.length; i++) {
            boolean actif = maintenant - t[i][1] < fenetre;
            if (actif && t[i][0] == m.getId()) {   // déjà parmi les attaquants : son coup passe
                t[i][1] = maintenant;
                return;
            }
            if (!actif && libre < 0) libre = i;
        }
        if (libre < 0) {   // déjà assez d'attaquants : celui-ci attend son tour
            e.setCanceled(true);
            refuses++;
            return;
        }
        t[libre][0] = m.getId();
        t[libre][1] = maintenant;
    }

    @SubscribeEvent
    public static void depart(PlayerEvent.PlayerLoggedOutEvent e) {
        RECENTS.remove(e.getEntity().getUUID());
    }

    /** Coups refusés depuis le démarrage (pour /zombies stats). */
    static long refuses() {
        return refuses;
    }
}

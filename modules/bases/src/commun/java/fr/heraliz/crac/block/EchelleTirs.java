package fr.heraliz.crac.block;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.heraliz.Txt;
import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Pas de tir sur une échelle de raid (demande du propriétaire du 03/11) : le tir d'une arme de TaCZ
 * ({@code GunShootEvent}, annulable, émis chez le joueur ET au serveur) est refusé à qui est sur une échelle
 * ({@link EchelleBlock}) : dans sa case et pas au sol. Chez le joueur, le tir n'est pas montré (pas de faux tir) et une
 * ligne le dit ; au serveur, il n'a pas lieu. Le reste est permis sur l'échelle (plan de construction, poser une autre
 * échelle, se soigner, coups de crosse...). Lu par son nom : CRac ne dépend pas de TaCZ ; sans TaCZ, rien. Rien par tick :
 * seulement quand une arme tire.
 */
public final class EchelleTirs {
    private EchelleTirs() {
    }

    /** Branché à la construction du mod (les deux côtés). */
    @SuppressWarnings("unchecked")
    public static void brancher() {
        try {
            Class<Event> c = (Class<Event>) Class.forName("com.tacz.guns.api.event.common.GunShootEvent").asSubclass(Event.class);
            Method tireur = c.getMethod("getShooter");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, c, e -> {
                try {
                    if (tireur.invoke(e) instanceof LivingEntity qui && surEchelle(qui)) {
                        e.setCanceled(true);
                        if (qui instanceof Player p && p.level().isClientSide()) {
                            p.displayClientMessage(Txt.red("crac.echelle.tir"), true);
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // évènement de TaCZ inattendu : on laisse passer
                }
            });
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            Crac.LOGGER.info("[CRac] échelles : TaCZ absent, rien à refuser au tir ({})", e.toString());
        }
    }

    /** Sur une échelle de raid : ses pieds dans une case d'échelle, sans toucher le sol (accroché, en train de grimper). */
    public static boolean surEchelle(LivingEntity e) {
        return !e.onGround() && !e.isSpectator() && e.getFeetBlockState().getBlock() instanceof EchelleBlock;
    }
}

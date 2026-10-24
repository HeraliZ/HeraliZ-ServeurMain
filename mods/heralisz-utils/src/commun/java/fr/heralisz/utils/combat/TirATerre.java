package fr.heralisz.utils.combat;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Pas de tir ni de coup de crosse TaCZ pour un joueur à terre (Réanimation) : {@code GunShootEvent} et
 * {@code GunMeleeEvent} de TaCZ, annulables, émis chez le joueur ET sur le serveur, refusés (déplacé de HeraliZ Matchs
 * le 24/10, retrait des matchs). Branché des deux côtés à la construction du mod ; sans TaCZ ou sans Réanimation : rien.
 * État « à terre » lu par réflexion ({@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)}, les deux côtés).
 */
public final class TirATerre {
    private static boolean cherche;
    @Nullable
    private static Method aTerre;

    private TirATerre() {
    }

    public static void enregistrer() {
        ModList ml = ModList.get();
        if (ml == null || !ml.isLoaded("tacz") || !ml.isLoaded("reanimation")) return;
        ecouter("com.tacz.guns.api.event.common.GunShootEvent");
        ecouter("com.tacz.guns.api.event.common.GunMeleeEvent");
    }

    /** Vrai si l'entité est à terre au sens de Réanimation (faux si le mod est absent). */
    public static boolean aTerre(@Nullable Entity e) {
        if (!cherche) {
            cherche = true;
            try {
                aTerre = Class.forName("fr.heraliz.reanimation.core.Downed").getMethod("isDowned", Entity.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                aTerre = null;
            }
        }
        if (aTerre == null || e == null) return false;
        try {
            return Boolean.TRUE.equals(aTerre.invoke(null, e));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event> void ecouter(String classe) {
        try {
            Class<T> c = (Class<T>) Class.forName(classe).asSubclass(Event.class);
            Method tireur = c.getMethod("getShooter");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, c, e -> {
                try {
                    if (tireur.invoke(e) instanceof Player p && aTerre(p)) e.setCanceled(true);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // évènement TaCZ inattendu : on laisse passer
                }
            });
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            HeraliszUtils.LOG.warn("[combat] TaCZ : {} introuvable, tir à terre non bloqué ({})", classe, e.toString());
        }
    }
}

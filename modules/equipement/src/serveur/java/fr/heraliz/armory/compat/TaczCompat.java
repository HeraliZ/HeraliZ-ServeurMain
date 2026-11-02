package fr.heraliz.armory.compat;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tirs a la tete de TaCZ (facultatif) : l'evenement EntityHurtByGunEvent.Pre arrive juste avant les degats de la balle ;
 * on note la cible et l'instant, ProtectionEvents ne compte alors que les pieces de tete (comme HeralisZ Armor).
 * Cette classe n'est chargee que si TaCZ est present.
 * <p>Audit du 02/11, point 27 : la note vaut pour UNE balle. Chaque balle qui touche ailleurs qu'a la tete l'efface, et
 * le premier coup qui la lit la consomme. Avant, elle valait pour toute la victime pendant tout le tick : un seul plomb a
 * la tete d'une decharge de fusil a pompe, et tous les autres plombs (et les balles des autres tireurs ce tick-la)
 * ignoraient le gilet.</p>
 */
public final class TaczCompat {
    private TaczCompat() {}

    private static final Map<Entity, Long> HEADSHOTS = new WeakHashMap<>();

    /** A appeler une fois au demarrage du mod. */
    public static void register() {
        if (ModList.get() != null && ModList.get().isLoaded("tacz")) Impl.register();
    }

    /**
     * Vrai si le coup en cours est la balle TaCZ qui vient de toucher cette entite a la tete (meme tick) ; la note est
     * consommee : un seul coup la lit.
     */
    public static boolean headshot(Entity victim) {
        Long t = HEADSHOTS.remove(victim);
        return t != null && t == victim.level().getGameTime();
    }

    private static final class Impl {
        static void register() {
            MinecraftForge.EVENT_BUS.addListener(Impl::onGunHit);
        }

        static void onGunHit(EntityHurtByGunEvent.Pre event) {
            Entity hurt = event.getHurtEntity();
            if (hurt == null || hurt.level().isClientSide) return;
            // balle a la tete : notee pour ce coup ; ailleurs : la note d'une balle d'avant (meme tick) ne compte plus
            if (event.isHeadShot()) HEADSHOTS.put(hurt, hurt.level().getGameTime());
            else HEADSHOTS.remove(hurt);
        }
    }
}

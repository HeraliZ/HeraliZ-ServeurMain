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
 */
public final class TaczCompat {
    private TaczCompat() {}

    private static final Map<Entity, Long> HEADSHOTS = new WeakHashMap<>();

    /** A appeler une fois au demarrage du mod. */
    public static void register() {
        if (ModList.get() != null && ModList.get().isLoaded("tacz")) Impl.register();
    }

    /** Vrai si cette entite vient d'etre touchee a la tete par une balle TaCZ (meme tick). */
    public static boolean headshot(Entity victim) {
        Long t = HEADSHOTS.get(victim);
        return t != null && t == victim.level().getGameTime();
    }

    private static final class Impl {
        static void register() {
            MinecraftForge.EVENT_BUS.addListener(Impl::onGunHit);
        }

        static void onGunHit(EntityHurtByGunEvent.Pre event) {
            Entity hurt = event.getHurtEntity();
            if (hurt != null && !hurt.level().isClientSide && event.isHeadShot()) HEADSHOTS.put(hurt, hurt.level().getGameTime());
        }
    }
}

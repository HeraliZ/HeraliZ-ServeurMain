package fr.heraliz.armory.compat;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.damagesource.DamageSource;
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
 * <p>03/11 (tableau des fusils à pompe) : TaCZ inflige chaque balle en DEUX coups (la part normale, puis la part
 * « perce-armure » de l'arme, 25 à 33 % pour les fusils à pompe), avec la même balle comme source. La note était consommée
 * par le premier : la part perce-armure d'un plomb à la tête comptait le gilet au lieu du casque. La note retient
 * maintenant la balle (son numéro d'entité) : les deux coups de CETTE balle la lisent, aucun autre.</p>
 */
public final class TaczCompat {
    private TaczCompat() {}

    /** Victime -> balle qui l'a touchée à la tête (numéro d'entité) et tick. */
    private static final Map<Entity, long[]> HEADSHOTS = new WeakHashMap<>();

    /** A appeler une fois au demarrage du mod. */
    public static void register() {
        if (ModList.get() != null && ModList.get().isLoaded("tacz")) Impl.register();
    }

    /**
     * Vrai si le coup en cours vient de la balle TaCZ qui vient de toucher cette entite a la tete (meme balle, meme tick) :
     * ses deux coups (part normale et part perce-armure) la lisent, aucun autre coup.
     */
    public static boolean headshot(Entity victim, DamageSource source) {
        long[] n = HEADSHOTS.get(victim);
        if (n == null) return false;
        Entity balle = source.getDirectEntity();
        if (n[1] != victim.level().getGameTime()) {
            HEADSHOTS.remove(victim);
            return false;
        }
        return balle != null && balle.getId() == n[0];
    }

    private static final class Impl {
        static void register() {
            MinecraftForge.EVENT_BUS.addListener(Impl::onGunHit);
        }

        static void onGunHit(EntityHurtByGunEvent.Pre event) {
            Entity hurt = event.getHurtEntity();
            if (hurt == null || hurt.level().isClientSide) return;
            // balle a la tete : notee pour ses deux coups ; ailleurs : la note d'une balle d'avant ne compte plus
            if (event.isHeadShot() && event.getBullet() != null) {
                HEADSHOTS.put(hurt, new long[]{event.getBullet().getId(), hurt.level().getGameTime()});
            } else {
                HEADSHOTS.remove(hurt);
            }
        }
    }
}

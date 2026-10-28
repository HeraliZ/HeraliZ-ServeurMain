package fr.heraliz.grades.serveur;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Arme TaCZ mise en vente à l'hôtel des ventes : déchargée d'abord, comme le « Décharger » de HeraliZ Tactique
 * ({@code DechargerServeur}) : la balle de la chambre rejoint le chargeur, puis {@code dropAllAmmo} de TaCZ rend tout le
 * chargeur à l'inventaire (au sol s'il est plein). Les munitions sont déplacées, jamais copiées. TaCZ est lu par
 * réflexion (API publique de TaCZ, noms stables) : sans lui, rien n'est fait.
 */
final class HdvTacz {
    private static final Logger LOG = LogUtils.getLogger();
    /** Résultat : rien à faire (pas une arme, ou rien à rendre), déchargée, ou refusée (rechargement en cours). */
    static final int RIEN = 0, DECHARGEE = 1, REFUS_RECHARGE = 2;

    private static boolean cherche;
    private static Class<?> iGun;
    private static Method useInventoryAmmo, useDummyAmmo, getCurrentAmmoCount, hasBulletInBarrel, setBulletInBarrel, setCurrentAmmoCount,
            dropAllAmmo, fromLivingEntity, getSynReloadState, getStateType, isReloading;

    private HdvTacz() {
    }

    private static boolean pret() {
        if (!cherche) {
            cherche = true;
            try {
                iGun = Class.forName("com.tacz.guns.api.item.IGun");
                useInventoryAmmo = iGun.getMethod("useInventoryAmmo", ItemStack.class);
                useDummyAmmo = iGun.getMethod("useDummyAmmo", ItemStack.class);
                getCurrentAmmoCount = iGun.getMethod("getCurrentAmmoCount", ItemStack.class);
                hasBulletInBarrel = iGun.getMethod("hasBulletInBarrel", ItemStack.class);
                setBulletInBarrel = iGun.getMethod("setBulletInBarrel", ItemStack.class, boolean.class);
                setCurrentAmmoCount = iGun.getMethod("setCurrentAmmoCount", ItemStack.class, int.class);
                dropAllAmmo = iGun.getMethod("dropAllAmmo", Player.class, ItemStack.class);
                Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
                fromLivingEntity = op.getMethod("fromLivingEntity", LivingEntity.class);
                getSynReloadState = op.getMethod("getSynReloadState");
                getStateType = getSynReloadState.getReturnType().getMethod("getStateType");
                isReloading = getStateType.getReturnType().getMethod("isReloading");
            } catch (Throwable t) {
                iGun = null;
                LOG.warn("[grades] hôtel des ventes : TaCZ absent ou différent ({}), armes mises en vente sans les décharger", t.toString());
            }
        }
        return iGun != null;
    }

    static int decharger(ServerPlayer p, int slot) {
        ItemStack st = p.getInventory().getItem(slot);
        if (st.isEmpty() || !pret() || !iGun.isInstance(st.getItem())) return RIEN;
        Object gun = st.getItem();
        try {
            if (slot == p.getInventory().selected) {
                Object etat = getStateType.invoke(getSynReloadState.invoke(fromLivingEntity.invoke(null, p)));
                if ((Boolean) isReloading.invoke(etat)) return REFUS_RECHARGE;
            }
            if ((Boolean) useInventoryAmmo.invoke(gun, st) || (Boolean) useDummyAmmo.invoke(gun, st)) return RIEN;
            boolean chambre = (Boolean) hasBulletInBarrel.invoke(gun, st);
            int balles = (Integer) getCurrentAmmoCount.invoke(gun, st) + (chambre ? 1 : 0);
            if (balles <= 0) return RIEN;
            if (chambre) {
                setBulletInBarrel.invoke(gun, st, false);
                setCurrentAmmoCount.invoke(gun, st, balles);
            }
            // munitions inconnues de TaCZ (carburant, énergie) : rien n'est rendu, l'arme les garde (rien de copié)
            dropAllAmmo.invoke(gun, p, st);
            p.getInventory().setChanged();
            return DECHARGEE;
        } catch (Throwable t) {
            LOG.warn("[grades] hôtel des ventes : arme non déchargée ({})", t.toString());
            return RIEN;
        }
    }
}

package fr.heraliz.tactique.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.init.ModCreativeTabs;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Icônes des onglets d'armes de TaCZ : TaCZ les fixe sur des armes de son pack par défaut (pistolet-mitrailleur
 * tacz:hk_mp5a5, précision tacz:ai_awp) qui n'existent pas dans nos gunpacks, et l'onglet n'a alors aucune icône. On
 * montre à la place la première arme de ce type présente (ordre des identifiants). Appelé par IconeOngletMixin.
 */
public final class IconesOnglets {
    private static final Map<CreativeModeTab, ItemStack> CACHE = new IdentityHashMap<>();

    private IconesOnglets() {
    }

    /** Icône de remplacement, ou null pour garder celle de l'onglet. */
    public static ItemStack remplacer(CreativeModeTab tab, ItemStack icone) {
        String type = tab == ModCreativeTabs.GUN_SMG_TAB.get() ? "smg" : tab == ModCreativeTabs.GUN_SNIPER_TAB.get() ? "sniper" : null;
        if (type == null || existe(icone)) {
            return null;
        }
        ItemStack deja = CACHE.get(tab);
        if (deja != null && existe(deja)) {
            return deja;
        }
        ItemStack nouvelle = TimelessAPI.getAllCommonGunIndex().stream()
                .filter(e -> type.equals(e.getValue().getType()))
                .min(Comparator.comparing((Map.Entry<ResourceLocation, CommonGunIndex> e) -> e.getKey().toString()))
                .map(e -> {
                    GunData d = e.getValue().getGunData();
                    return GunItemBuilder.create().setId(e.getKey()).setFireMode(d.getFireModeSet().get(0)).build();
                })
                .orElse(null);
        if (nouvelle != null) {
            CACHE.put(tab, nouvelle);
        }
        return nouvelle;
    }

    private static boolean existe(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation id = gun == null ? null : gun.getGunId(stack);
        return id != null && TimelessAPI.getCommonGunIndex(id).isPresent();
    }
}

package fr.heraliz.tactique.onglets;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Couteaux TaCZ (armes des gunpacks dont la fiche technique est arkaniaz:knife_data : couteaux CT / T, karambits,
 * papillons, M9 de heralisz_skins…). TaCZ les range dans l'onglet « Arme lourde » (type « rpg ») : on les en retire et
 * on les met dans le groupe Mêlée de l'onglet HeraliZ · Tactique (demande du propriétaire, 11/10).
 */
public final class Couteaux {
    public static final ResourceLocation KNIFE_DATA = new ResourceLocation("arkaniaz", "knife_data");

    private Couteaux() {
    }

    public static boolean estCouteau(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return false;
        }
        ResourceLocation id = gun.getGunId(stack);
        return id != null && TimelessAPI.getCommonGunIndex(id).map(i -> KNIFE_DATA.equals(i.getPojo().getData())).orElse(false);
    }

    /** Un exemplaire de chaque couteau, dans l'ordre des identifiants, construit comme TaCZ le fait pour ses onglets. */
    public static List<ItemStack> tous() {
        List<ItemStack> out = new ArrayList<>();
        TimelessAPI.getAllCommonGunIndex().stream()
                .filter(e -> KNIFE_DATA.equals(e.getValue().getPojo().getData()))
                .sorted(Comparator.comparing((Map.Entry<ResourceLocation, CommonGunIndex> e) -> e.getKey().toString()))
                .forEach(e -> {
                    GunData data = e.getValue().getGunData();
                    out.add(GunItemBuilder.create().setId(e.getKey()).setFireMode(data.getFireModeSet().get(0))
                            .setAmmoCount(data.getAmmoAmount()).setHeatData(data.hasHeatData()).setAmmoInBarrel(true).build());
                });
        return out;
    }
}

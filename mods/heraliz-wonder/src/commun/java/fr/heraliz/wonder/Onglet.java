package fr.heraliz.wonder;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Onglet créatif « Wonder Weapons » : toutes les armes du gunpack {@code heraliz_wonder}, construites comme TaCZ construit
 * les siennes (chargeur plein, premier mode de tir). Elles sont retirées des onglets de TaCZ (pistolets, fusils à pompe...)
 * pour n'être que dans celui-ci. Commun : l'onglet est un registre ; aucun code d'affichage.
 */
public final class Onglet {
    private static final DeferredRegister<CreativeModeTab> ONGLETS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HeralizWonder.MODID);

    public static final RegistryObject<CreativeModeTab> WONDER = ONGLETS.register("wonder_weapons", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.heraliz_wonder"))
            .icon(() -> arme(new ResourceLocation(Armes.ID_RAYON)))
            .displayItems((params, out) -> out.acceptAll(armes()))
            .build());

    private Onglet() {
    }

    public static void brancher(IEventBus mod) {
        ONGLETS.register(mod);
        mod.addListener(Onglet::surContenu);
    }

    /** Une arme du gunpack, comme dans les onglets de TaCZ ; pile vide si l'arme est inconnue. */
    private static ItemStack arme(ResourceLocation id) {
        return TimelessAPI.getCommonGunIndex(id).map(index -> construire(id, index)).orElse(ItemStack.EMPTY);
    }

    private static ItemStack construire(ResourceLocation id, CommonGunIndex index) {
        GunData d = index.getGunData();
        return GunItemBuilder.create().setId(id).setFireMode(d.getFireModeSet().get(0)).setAmmoCount(d.getAmmoAmount())
                .setHeatData(d.hasHeatData()).setAmmoInBarrel(true).build();
    }

    /** Toutes les Wonder Weapons connues de TaCZ, triées par id. */
    private static List<ItemStack> armes() {
        List<ItemStack> liste = new ArrayList<>();
        TimelessAPI.getAllCommonGunIndex().stream()
                .filter(e -> HeralizWonder.MODID.equals(e.getKey().getNamespace()))
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(e -> liste.add(construire(e.getKey(), e.getValue())));
        return liste;
    }

    private static boolean estWonder(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getString(Armes.NBT_GUN_ID).startsWith(HeralizWonder.MODID + ":");
    }

    /** Retire les Wonder Weapons des autres onglets. */
    private static void surContenu(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(WONDER.getKey())) return;
        List<ItemStack> retirees = new ArrayList<>();
        for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> e : event.getEntries()) {
            if (estWonder(e.getKey())) retirees.add(e.getKey());
        }
        retirees.forEach(s -> event.getEntries().remove(s));
    }
}

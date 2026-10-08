package fr.heraliz.vehicules;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Objets de Limitless Vehicle qu'on ne garde pas (repris de Fuse : RemovedContent + FuseTabCleanup). Le moteur les déclare
 * dans son code (les retirer le casserait) : ils sont cachés de ses onglets, leurs recettes sont absentes du jar réduit par
 * Fuse, et ils sont effacés s'ils apparaissent quand même (/give, vieux mondes, coffres). Pareil pour les véhicules
 * retirés. Ce sont surtout des objets à risque sur un serveur : boîte à figurines (capture n'importe quelle entité),
 * imprimante 3D (fabrique tous les véhicules), télécommande de drone, munitions d'armes de véhicules.
 * <p>Jar fusionné (docs/METHODE_CLIENT_SERVEUR.md §5) : ici, la liste et le filtre des onglets créatifs (là où l'onglet
 * est construit, comme à l'origine) ; l'effacement dans les inventaires et au sol est dans {@code ContenuRetireServeur}
 * (partie serveur).
 */
public final class ContenuRetire {
    static final Set<String> OBJETS = Set.of(
            "ywzj_vehicle:uav_controller", "ywzj_vehicle:modding_tool", "ywzj_vehicle:decoration_item",
            "ywzj_vehicle:figure_box", "ywzj_vehicle:machine_max_block",
            "ywzj_vehicle:ammo_creative", "ywzj_vehicle:ammo_machine_gun", "ywzj_vehicle:ammo_auto_cannon",
            "ywzj_vehicle:ammo_grenade", "ywzj_vehicle:ammo_artillery", "ywzj_vehicle:ammo_rocket",
            "ywzj_vehicle:ammo_aerial_bomb", "ywzj_vehicle:ammo_missile", "ywzj_vehicle:ammo_decoy_flare");

    private ContenuRetire() {
    }

    public static boolean estRetire(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("ywzj_vehicle")) return false;
        if (id.getPath().equals("vehicle_spawn_item")) {
            return stack.getTag() == null || !Contenu.VEHICULES.contains(stack.getTag().getString("YwzjVehicleId"));
        }
        return OBJETS.contains(id.toString());
    }

    static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("ywzj_vehicle")) return;
        List<ItemStack> retires = new ArrayList<>();
        for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> e : event.getEntries()) {
            if (estRetire(e.getKey())) retires.add(e.getKey());
        }
        retires.forEach(s -> event.getEntries().remove(s));
    }
}

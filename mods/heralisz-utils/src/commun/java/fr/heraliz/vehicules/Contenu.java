package fr.heraliz.vehicules;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.ywzj.vehicle.all.AllItems;

import java.util.List;

/** Les véhicules gardés (ceux du pack réduit par Fuse) et les objets qui vont avec. */
public final class Contenu {
    /** Identifiants Limitless Vehicle des véhicules du serveur. */
    public static final List<String> VEHICULES = List.of(
            "ywzj_vehicle:atv_mrzr4", "ywzj_vehicle:motorcycle", "ywzj_vehicle:dump_truck", "ywzj_vehicle:hiace");
    /** Nom court (commande /vehicules donner) -> identifiant. */
    public static final List<String> NOMS = List.of("buggy", "moto", "camion", "fourgon");

    // (onglet heraliz_vehicules:vehicules retiré : groupe « vehicules » de l'onglet commun HeraliZ · Utils, voir HeralizVehicules)

    private Contenu() {
    }

    /** Objet qui pose le véhicule (clic droit sur un bloc). */
    public static ItemStack vehicule(String id) {
        return AllItems.VEHICLE_SPAWN_ITEM.get().createInstance(new ResourceLocation(id));
    }

    /** Jerrican d'essence, chalumeau de réparation, parachute dorsal. */
    public static List<ItemStack> outils() {
        return List.of(new ItemStack(AllItems.FUEL_TANK.get()), new ItemStack(AllItems.REPAIR_TOOL.get()),
                new ItemStack(AllItems.PARACHUTE_PACK.get()));
    }

    /** Identifiant complet depuis un nom court (« moto ») ou un id (« motorcycle », « ywzj_vehicle:motorcycle »), null sinon. */
    public static String resoudre(String nom) {
        int i = NOMS.indexOf(nom);
        if (i >= 0) return VEHICULES.get(i);
        String id = nom.contains(":") ? nom : "ywzj_vehicle:" + nom;
        return VEHICULES.contains(id) ? id : null;
    }
}

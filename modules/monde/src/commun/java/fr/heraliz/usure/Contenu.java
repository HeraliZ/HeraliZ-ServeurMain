package fr.heraliz.usure;

import fr.heralisz.utils.pont.Usine;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Les trois kits de reparation, leur onglet creatif et l'atelier de reparation (menu ouvert au clic droit). */
public final class Contenu {
    private Contenu() {
    }

    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizUsure.MODID);

    /**
     * Usine des kits (docs/METHODE_CLIENT_SERVEUR.md §5.3) : KitReparationServeur (la reparation) des que la partie serveur
     * est dans le jar (branchee par serveur.PartieServeur, avant les registres), sinon le kit commun (affichage).
     */
    public static final Usine<KitReparation.Params, KitReparation> KIT_USINE = new Usine<>(KitReparation::new);

    public static final RegistryObject<Item> KIT_FORTUNE = ITEMS.register("kit_fortune", () -> KIT_USINE.apply(new KitReparation.Params(0, Rarity.COMMON)));
    public static final RegistryObject<Item> KIT_STANDARD = ITEMS.register("kit_standard", () -> KIT_USINE.apply(new KitReparation.Params(1, Rarity.UNCOMMON)));
    public static final RegistryObject<Item> KIT_ARMURIER = ITEMS.register("kit_armurier", () -> KIT_USINE.apply(new KitReparation.Params(2, Rarity.RARE)));

    public static final List<RegistryObject<Item>> KITS = List.of(KIT_FORTUNE, KIT_STANDARD, KIT_ARMURIER);

    /** Atelier de réparation (clic droit avec un kit en main, 26/10). */
    static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, HeralizUsure.MODID);
    public static final RegistryObject<MenuType<MenuReparation>> MENU_REPARATION = MENUS.register("reparation",
            () -> IForgeMenuType.create(MenuReparation::new));

    // (onglet heraliz_usure:usure retiré : groupe « usure » de l'onglet commun HeraliZ · Utils, voir HeralizUsure)

    static void enregistrer(IEventBus modBus) {
        ITEMS.register(modBus);
        MENUS.register(modBus);
    }
}

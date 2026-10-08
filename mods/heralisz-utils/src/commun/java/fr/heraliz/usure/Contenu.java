package fr.heraliz.usure;

import fr.heralisz.utils.pont.Usine;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Les trois kits de reparation et leur onglet creatif (ni caisses ni recettes : loot et craft seront refaits). */
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

    // (onglet heraliz_usure:usure retiré : groupe « usure » de l'onglet commun HeraliZ · Utils, voir HeralizUsure)

    static void enregistrer(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}

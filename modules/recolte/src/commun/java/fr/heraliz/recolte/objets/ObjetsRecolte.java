package fr.heraliz.recolte.objets;

import fr.heraliz.recolte.HeralizRecolte;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Objets de HeraliZ Récolte. Identifiants FIGÉS (gardés dans les inventaires, les coffres, le loot et les échanges) :
 * {@code heraliz_recolte:buche}, {@code pierre_brute}, {@code minerai_fer}, {@code minerai_rare}, {@code hache},
 * {@code pioche}, {@code hache_pioche}. Raretés (couleur du nom, HeraliZ Loot, {@code outils/loot_armes.py}) : bûche et
 * pierre brute Commun, minerai de fer Peu commun, minerai rare Rare, hache et pioche Rare, hache-pioche Légendaire.
 * Icônes et modèles en main : écrits par {@code modules/recolte/outils/recolte_blockbench.py}, jamais à la main.
 */
public final class ObjetsRecolte {
    private ObjetsRecolte() {
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizRecolte.MODID);

    public static final RegistryObject<Item> BUCHE = ITEMS.register("buche", () -> new Matiere("buche", 32));
    public static final RegistryObject<Item> PIERRE_BRUTE = ITEMS.register("pierre_brute", () -> new Matiere("pierre_brute", 32));
    public static final RegistryObject<Item> MINERAI_FER = ITEMS.register("minerai_fer", () -> new Matiere("minerai_fer", 32));
    public static final RegistryObject<Item> MINERAI_RARE = ITEMS.register("minerai_rare", () -> new Matiere("minerai_rare", 16));

    public static final RegistryObject<OutilRecolte> HACHE = ITEMS.register("hache", () -> new OutilRecolte("hache", OutilRecolte.Genre.HACHE, 400));
    public static final RegistryObject<OutilRecolte> PIOCHE = ITEMS.register("pioche", () -> new OutilRecolte("pioche", OutilRecolte.Genre.PIOCHE, 400));
    public static final RegistryObject<OutilRecolte> HACHE_PIOCHE = ITEMS.register("hache_pioche",
            () -> new OutilRecolte("hache_pioche", OutilRecolte.Genre.HACHE_PIOCHE, 1000));

    public static void enregistrer(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(ObjetsRecolte::onglets);
    }

    /** Onglets créatifs de Minecraft (staff en créatif) : matières dans « Ingrédients », outils dans « Outils ». */
    private static void onglets(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            e.accept(BUCHE);
            e.accept(PIERRE_BRUTE);
            e.accept(MINERAI_FER);
            e.accept(MINERAI_RARE);
        } else if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(HACHE);
            e.accept(PIOCHE);
            e.accept(HACHE_PIOCHE);
        }
    }
}

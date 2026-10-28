package fr.heraliz.crac.registry;

import fr.heraliz.crac.block.ObjetCoffre;
import fr.heraliz.crac.item.BuildingHammerItem;
import fr.heraliz.crac.item.BuildingPlanItem;
import fr.heraliz.crac.item.C4Item;
import fr.heraliz.crac.item.ClaimBlockItem;
import fr.heraliz.crac.item.ExplosiveChargeItem;
import fr.heraliz.crac.item.GlassWindowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "crac");
    public static final RegistryObject<BuildingHammerItem> BUILDING_HAMMER = ITEMS.register("building_hammer", BuildingHammerItem::new);
    public static final RegistryObject<Item> NAIL = ITEMS.register("nail", () -> new Item(new Properties()));
    public static final RegistryObject<Item> SHEET_METAL_PANEL = ITEMS.register("sheet_metal_panel", () -> new Item(new Properties()));
    public static final RegistryObject<Item> REINFORCED_METAL_PANEL = ITEMS.register("reinforced_metal_panel", () -> new Item(new Properties()));
    public static final RegistryObject<Item> ARMORED_METAL_PANEL = ITEMS.register("armored_metal_panel", () -> new Item(new Properties()));
    public static final RegistryObject<BuildingPlanItem> BUILDING_PLAN = ITEMS.register("building_plan", BuildingPlanItem::new);
    public static final RegistryObject<C4Item> C4_ITEM = ITEMS.register("c4", C4Item::new);
    public static final RegistryObject<ExplosiveChargeItem> EXPLOSIVE_CHARGE = ITEMS.register("explosive_charge", ExplosiveChargeItem::new);
    public static final RegistryObject<GlassWindowItem> GLASS_WINDOW = ITEMS.register("glass_window", GlassWindowItem::new);
    public static final RegistryObject<GlassWindowItem> METAL_WINDOW = ITEMS.register("metal_window", GlassWindowItem::new);
    public static final RegistryObject<BlockItem> METAL_CRATE = ITEMS.register(
        "metal_crate", () -> new ObjetCoffre(ModBlocks.METAL_CRATE.get(), new Properties(), 1, 18, "1")
    );
    /** Coffres par rareté (27/10) ; un 2 de haut se pose dans une case libre avec la case du dessus libre. */
    public static final RegistryObject<BlockItem> MALLE_CHANTIER = ITEMS.register(
        "malle_chantier", () -> new ObjetCoffre(ModBlocks.MALLE_CHANTIER.get(), new Properties().stacksTo(16), 1, 45, "large")
    );
    public static final RegistryObject<BlockItem> CASIER_METALLIQUE = ITEMS.register(
        "casier_metallique", () -> new ObjetCoffre(ModBlocks.CASIER_METALLIQUE.get(), new Properties().stacksTo(16), 2, 54, "haut")
    );
    public static final RegistryObject<BlockItem> CAISSE_ARMES = ITEMS.register(
        "caisse_armes", () -> new ObjetCoffre(ModBlocks.CAISSE_ARMES.get(), new Properties().stacksTo(16), 1, 66, "large")
    );
    public static final RegistryObject<BlockItem> COFFRE_FORT = ITEMS.register(
        "coffre_fort", () -> new ObjetCoffre(ModBlocks.COFFRE_FORT.get(), new Properties().stacksTo(16), 2, 78, "haut")
    );
    public static final RegistryObject<ClaimBlockItem> CLAIM_BLOCK = ITEMS.register(
        "claim_block", () -> new ClaimBlockItem(ModBlocks.CLAIM_BLOCK.get(), new Properties().stacksTo(16))
    );

    public static final RegistryObject<BlockItem> COEUR_CLAN = ITEMS.register(
        "coeur_clan", () -> new BlockItem(ModBlocks.COEUR_CLAN.get(), new Properties().stacksTo(1).fireResistant())
    );

    private ModItems() {
    }
}

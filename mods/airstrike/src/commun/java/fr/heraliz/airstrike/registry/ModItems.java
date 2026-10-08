package fr.heraliz.airstrike.registry;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.item.DesignatorItem;
import fr.heraliz.airstrike.item.PayloadItem;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AirstrikeMod.MODID);

    public static final RegistryObject<DesignatorItem> DESIGNATOR = ITEMS.register("designator",
            () -> new DesignatorItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final Map<StrikeType, RegistryObject<PayloadItem>> PAYLOADS = new EnumMap<>(StrikeType.class);

    static {
        for (StrikeType type : StrikeType.values()) {
            PAYLOADS.put(type, ITEMS.register("payload_" + type.id,
                    () -> new PayloadItem(type, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))));
        }
    }

    public static final RegistryObject<BlockItem> SUPPLY_CRATE = ITEMS.register("supply_crate",
            () -> new BlockItem(ModBlocks.SUPPLY_CRATE.get(), new Item.Properties()));

    public static PayloadItem payload(StrikeType type) {
        return PAYLOADS.get(type).get();
    }

    private ModItems() {
    }
}

package fr.heraliz.airstrike.registry;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.helico.PieceHelico;
import fr.heraliz.airstrike.helico.PieceHelicoItem;
import fr.heraliz.airstrike.item.CartridgeItem;
import fr.heraliz.airstrike.item.DesignatorItem;
import fr.heraliz.airstrike.item.PayloadItem;
import fr.heraliz.airstrike.strike.CartridgeTier;
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

    /** Strike cartridges, one per level. */
    public static final Map<CartridgeTier, RegistryObject<CartridgeItem>> CARTRIDGES = new EnumMap<>(CartridgeTier.class);

    static {
        for (CartridgeTier tier : CartridgeTier.values()) {
            Rarity rarity = switch (tier) {
                case LIGHT -> Rarity.UNCOMMON;
                case HEAVY -> Rarity.RARE;
                case SPECIAL -> Rarity.EPIC;
            };
            CARTRIDGES.put(tier, ITEMS.register("cartridge_" + tier.id,
                    () -> new CartridgeItem(tier, new Item.Properties().stacksTo(16).rarity(rarity))));
        }
    }

    /** Old single strike payloads: kept for the stacks already in the worlds (turned into cartridges). */
    public static final Map<StrikeType, RegistryObject<PayloadItem>> PAYLOADS = new EnumMap<>(StrikeType.class);

    static {
        for (StrikeType type : StrikeType.values()) {
            PAYLOADS.put(type, ITEMS.register("payload_" + type.id,
                    () -> new PayloadItem(type, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))));
        }
    }

    public static final RegistryObject<BlockItem> SUPPLY_CRATE = ITEMS.register("supply_crate",
            () -> new BlockItem(ModBlocks.SUPPLY_CRATE.get(), new Item.Properties()));

    /** Pièces d'hélicoptère récupérées sur l'épave (revendues à Mila). */
    public static final Map<PieceHelico, RegistryObject<PieceHelicoItem>> PIECES_HELICO = new EnumMap<>(PieceHelico.class);

    static {
        for (PieceHelico p : PieceHelico.values()) {
            PIECES_HELICO.put(p, ITEMS.register(p.objet(), () -> new PieceHelicoItem(p, new Item.Properties().stacksTo(16))));
        }
    }

    public static PieceHelicoItem piece(PieceHelico p) {
        return PIECES_HELICO.get(p).get();
    }

    public static PayloadItem payload(StrikeType type) {
        return PAYLOADS.get(type).get();
    }

    public static CartridgeItem cartridge(CartridgeTier tier) {
        return CARTRIDGES.get(tier).get();
    }

    /** Cartridge drawn in the bay of the binoculars for a strike (the supply drop, without a level: Heavy). */
    public static CartridgeItem cartridgeFor(StrikeType type) {
        return cartridge(type.tier != null ? type.tier : CartridgeTier.HEAVY);
    }

    private ModItems() {
    }
}

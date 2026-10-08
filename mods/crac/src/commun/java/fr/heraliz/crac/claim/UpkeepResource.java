package fr.heraliz.crac.claim;

import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Upkeep slots of a Claim Block. Each building tier is paid in one of these resources. */
public enum UpkeepResource {
    WOOD("Wood"),
    STONE("Stone"),
    METAL("Metal"),
    ARMORED("Armored");

    private static final UpkeepResource[] VALUES = values();
    public static final int COUNT = VALUES.length;

    public final String displayName;

    UpkeepResource(String displayName) {
        this.displayName = displayName;
    }

    public static UpkeepResource of(Tier tier) {
        return switch (tier) {
            case TWIG, WOOD -> WOOD;
            case STONE -> STONE;
            case METAL -> METAL;
            case ARMORED -> ARMORED;
        };
    }

    public static UpkeepResource bySlot(int slot) {
        return VALUES[Math.max(0, Math.min(VALUES.length - 1, slot))];
    }

    public int slot() {
        return this.ordinal();
    }

    /** The item this resource is paid with now (config), AIR when its id does not resolve. */
    public Item item() {
        return Config.upkeepItem(this);
    }

    /** Whether the configured item exists; an unknown id means no upkeep for this resource. */
    public boolean isPayable() {
        return this.item() != Items.AIR;
    }

    /** Items consumed per piece of this resource over {@code upkeepHours}. */
    public int perPiece() {
        return Config.upkeepPerPiece(this);
    }
}

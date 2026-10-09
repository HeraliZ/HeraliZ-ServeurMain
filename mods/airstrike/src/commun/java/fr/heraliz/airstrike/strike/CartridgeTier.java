package fr.heraliz.airstrike.strike;

import java.util.ArrayList;
import java.util.List;

/**
 * Cartridge levels. The binoculars take one cartridge of the level of the chosen strike; the strike itself is chosen
 * in the wheel when loading. Colours: the rarities of the server loot (uncommon, rare, legendary).
 */
public enum CartridgeTier {
    LIGHT("light", 0x5CBF4A, 1),
    HEAVY("heavy", 0x3F8CFF, 2),
    SPECIAL("special", 0xFF9B1F, 3);

    private static final CartridgeTier[] VALUES = values();

    public final String id;
    /** Colour of the level (cartridge band, wheel, names). */
    public final int color;
    /** Chevrons printed on the cartridge. */
    public final int chevrons;

    CartridgeTier(String id, int color, int chevrons) {
        this.id = id;
        this.color = color;
        this.chevrons = chevrons;
    }

    public String translationKey() {
        return "tier.airstrike." + id;
    }

    /** Strikes of this level, in wheel order. */
    public List<StrikeType> strikes() {
        List<StrikeType> out = new ArrayList<>();
        for (StrikeType t : StrikeType.WHEEL) {
            if (t.tier == this) out.add(t);
        }
        return out;
    }

    public static CartridgeTier byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : LIGHT;
    }
}

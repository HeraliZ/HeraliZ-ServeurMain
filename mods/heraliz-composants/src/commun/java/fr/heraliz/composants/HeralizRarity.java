package fr.heraliz.composants;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Rarity;

/** Code couleur HeraliZ (enum Rarity etendu par Forge), du plus courant au plus rare. */
public final class HeralizRarity {
    public static final Rarity COMMON = Rarity.create("HERALIZ_COMMON", ChatFormatting.GRAY);
    public static final Rarity UNCOMMON = Rarity.create("HERALIZ_UNCOMMON", ChatFormatting.GREEN);
    public static final Rarity RARE = Rarity.create("HERALIZ_RARE", ChatFormatting.BLUE);
    public static final Rarity EPIC = Rarity.create("HERALIZ_EPIC", ChatFormatting.LIGHT_PURPLE);
    public static final Rarity LEGENDARY = Rarity.create("HERALIZ_LEGENDARY", ChatFormatting.GOLD);
    public static final Rarity MYTHIC = Rarity.create("HERALIZ_MYTHIC", ChatFormatting.RED);

    private HeralizRarity() {}
}

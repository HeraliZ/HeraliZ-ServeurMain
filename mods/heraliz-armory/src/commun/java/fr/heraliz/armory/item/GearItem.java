package fr.heraliz.armory.item;

import fr.heraliz.armory.armor.GearDef;
import fr.heraliz.armory.armor.Guard;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Objet repris de HeralisZ Armor (0.9.0) : ghillie, brassard, masque a gaz (Curios) ou objet event (slot de tete).
 * Memes noms colores par rarete et meme infobulle qu'avant (format de l'Armurerie : « Protection : » et cinq lignes a
 * pictogrammes) ; la protection passe par le calcul commun (armor.Protection).
 */
public interface GearItem extends ProtectiveItem {
    GearDef gear();

    @Override
    default Guard guard() {
        return gear();
    }

    @Override
    @Nullable
    default EquipmentSlot wornArmorSlot() {
        return gear().armor() ? EquipmentSlot.HEAD : null;
    }

    @Override
    @Nullable
    default String wornCurioSlot() {
        return gear().curio();
    }

    /** Couleur du nom selon la rarete (couleurs de HeralisZ Armor). */
    static Style rarityStyle(String rarity) {
        int rgb = switch (rarity) {
            case "uncommon" -> 0x55FF55;
            case "rare" -> 0x5599FF;
            case "epic" -> 0xC060FF;
            case "legendary" -> 0xFFAA00;
            case "unique" -> 0xFF3B3B;
            case "event" -> 0xFF77CC;
            default -> 0xFFFFFF;
        };
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
    }

    /** Pictogramme de rarete du serveur (police des rangs du pack de ressources), vide pour l'evenementiel. */
    private static String glyph(String rarity) {
        return switch (rarity) {
            case "common" -> " ";
            case "uncommon" -> " ";
            case "rare" -> " ";
            case "epic" -> " ";
            case "legendary" -> " ";
            case "unique" -> " ";
            default -> "";
        };
    }

    static MutableComponent rarityLine(String rarity) {
        return Component.literal(glyph(rarity)).append(Component.translatable("rarity.heraliz_armory." + rarity).withStyle(rarityStyle(rarity)));
    }

    /** Infobulle : rarete, emplacement, protection (s'il y en a une), zone, bonus. */
    static void appendTooltip(GearDef g, List<Component> lines) {
        lines.add(rarityLine(g.rarity()));
        if (g.armor()) {
            lines.add(Component.translatable("tooltip.heraliz_armory.slot", Component.translatable("tooltip.heraliz_armory.slot." + g.slot()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (g.bullet() > 0) lines.add(PieceItem.protection(g.bullet()));
        lines.add(Component.translatable(g.protectsHead() ? "tooltip.heraliz_armory.zone.head" : "tooltip.heraliz_armory.zone.body")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (g.bonus() != null) {
            lines.add(Component.literal("✦ ").append(Component.translatable("bonus.heraliz_armory." + g.bonus())).withStyle(ChatFormatting.AQUA));
        }
    }
}

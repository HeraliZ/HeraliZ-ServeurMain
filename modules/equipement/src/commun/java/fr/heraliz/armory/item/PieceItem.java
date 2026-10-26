package fr.heraliz.armory.item;

import fr.heraliz.armory.armor.PieceDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Commun aux pieces (armure vanilla ou Curios) : definition + infobulle. 0.15.0 : rarete, ensemble, emplacement, une
 * seule ligne « Protection : x % », zone protegee (plus de pictogrammes par type de degat).
 */
public interface PieceItem extends ProtectiveItem {
    PieceDef def();

    @Override
    default fr.heraliz.armory.armor.Guard guard() {
        return def();
    }

    @Override
    @org.jetbrains.annotations.Nullable
    default net.minecraft.world.entity.EquipmentSlot wornArmorSlot() {
        return def().equipmentSlot();
    }

    @Override
    @org.jetbrains.annotations.Nullable
    default String wornCurioSlot() {
        return def().curioSlot();
    }

    /** Nom de la touche des lunettes de vision nocturne (renseigne par le client ; « ? » ailleurs). */
    Holder NVG_KEY = new Holder();

    final class Holder {
        public Supplier<Component> key = () -> Component.literal("?");
    }

    static String pct(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** « Protection : 12 % » (une seule protection depuis la 0.15.0), grisee si nulle. */
    static MutableComponent protection(float value) {
        return Component.translatable("tooltip.heraliz_armory.protection_valeur", pct(value))
                .withStyle(value > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
    }

    static void appendTooltip(PieceDef def, List<Component> tooltip) {
        tooltip.add(GearItem.rarityLine(rarity(def.tier())));
        tooltip.add(Component.translatable("tooltip.heraliz_armory.set", Component.translatable("tooltip.heraliz_armory.set." + def.set()))
                .withStyle(ChatFormatting.GRAY));
        // emplacement : seulement pour les pieces d'armure (Curios affiche deja « Emplacement : … » pour les siennes)
        if (def.curioSlot() == null) {
            tooltip.add(Component.translatable("tooltip.heraliz_armory.slot", Component.translatable("tooltip.heraliz_armory.slot." + def.piece()))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(protection(def.bullet()));
        tooltip.add(Component.translatable(def.protectsHead() ? "tooltip.heraliz_armory.zone.head" : "tooltip.heraliz_armory.zone.body")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (def.gas()) tooltip.add(Component.translatable("tooltip.heraliz_armory.gas").withStyle(ChatFormatting.AQUA));
        if (def.nvg()) tooltip.add(Component.translatable("tooltip.heraliz_armory.nvg", NVG_KEY.key.get()).withStyle(ChatFormatting.AQUA));
        // le poids (vitesse) est affiche par le jeu sous « Porte sur la tete : » / « Porte en gilet : » (attributs)
    }

    /** Rarete d'apres le palier des pieces (0.15.0 : palier = rang de rarete, 1 commun ... 6 unique). */
    static String rarity(int tier) {
        return switch (tier) {
            case 2 -> "uncommon";
            case 3 -> "rare";
            case 4 -> "epic";
            case 5 -> "legendary";
            case 6 -> "unique";
            default -> "common";
        };
    }

    /** Couleur du nom selon la rarete (memes couleurs que les objets de l'Armurerie et HeraliZ Loot). */
    static Style nameStyle(int tier) {
        return GearItem.rarityStyle(rarity(tier));
    }

    static boolean is(ItemStack stack, String piece) {
        return stack.getItem() instanceof PieceItem p && p.def().piece().equals(piece);
    }
}

package fr.heraliz.armory.item;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.armor.SetDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Commun aux pieces (armure vanilla ou Curios) : definition + infobulle. 0.8.2 : meme presentation que les armures
 * HeralisZ Armor (emplacement, « Protection : » et cinq lignes a pictogrammes, zone protegee), pour que les deux mods se
 * lisent pareil dans l'inventaire.
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

    /** Pictogrammes des protections (police heraliz_armory:icons, copie de celle de HeralisZ Armor). */
    String BULLET = "", EXPLOSION = "", MELEE = "", BITE = "", RADIATION = "";
    Style ICON_FONT = Style.EMPTY.withFont(HeralizArmory.id("icons")).withColor(ChatFormatting.WHITE);

    /** Nom de la touche des lunettes de vision nocturne (renseigne par le client ; « ? » ailleurs). */
    Holder NVG_KEY = new Holder();

    final class Holder {
        public Supplier<Component> key = () -> Component.literal("?");
    }

    static String pct(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** « [icone] Balles 8 % » : valeur doree, grisee si nulle. */
    static MutableComponent stat(String glyph, String key, float value) {
        return Component.literal(" ").append(Component.literal(glyph).withStyle(ICON_FONT)).append(Component.literal(" "))
                .append(Component.translatable("stat.heraliz_armory." + key).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" " + pct(value) + " %").withStyle(value > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
    }

    static void appendTooltip(PieceDef def, List<Component> tooltip) {
        int level = SetDef.levelOf(def.set());
        tooltip.add(Component.translatable("tooltip.heraliz_armory.set", Component.translatable("tooltip.heraliz_armory.set." + def.set()), level)
                .withStyle(ChatFormatting.GOLD));
        // emplacement : seulement pour les pieces d'armure (Curios affiche deja « Emplacement : … » pour les siennes),
        // comme les armures HeralisZ Armor
        if (def.curioSlot() == null) {
            tooltip.add(Component.translatable("tooltip.heraliz_armory.slot", Component.translatable("tooltip.heraliz_armory.slot." + def.piece()))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.heraliz_armory.protection").withStyle(ChatFormatting.GRAY));
        tooltip.add(stat(BULLET, "bullet", def.bullet()));
        tooltip.add(stat(EXPLOSION, "explosion", def.explosion()));
        tooltip.add(stat(MELEE, "melee", def.melee()));
        tooltip.add(stat(BITE, "bite", def.bite()));
        tooltip.add(stat(RADIATION, "radiation", def.radiation()));
        tooltip.add(Component.translatable(def.protectsHead() ? "tooltip.heraliz_armory.zone.head" : "tooltip.heraliz_armory.zone.body")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (def.gas()) tooltip.add(Component.translatable("tooltip.heraliz_armory.gas").withStyle(ChatFormatting.AQUA));
        if (def.nvg()) tooltip.add(Component.translatable("tooltip.heraliz_armory.nvg", NVG_KEY.key.get()).withStyle(ChatFormatting.AQUA));
        // le poids (vitesse) est affiche par le jeu sous « Porte sur la tete : » / « Porte en gilet : » (attributs)
    }

    /**
     * Couleur du nom selon le palier, avec les couleurs de rarete de HeralisZ Armor (commun blanc, peu commun vert, rare
     * bleu, epique violet, legendaire orange) : les deux mods se lisent pareil dans l'inventaire.
     */
    static Style nameStyle(int tier) {
        int rgb = switch (tier) {
            case 2 -> 0x55FF55;
            case 3 -> 0x5599FF;
            case 4 -> 0xC060FF;
            case 5, 6 -> 0xFFAA00;
            default -> 0xFFFFFF;
        };
        return Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb));
    }

    /** Palier d'un ensemble d'apres son niveau NV (meme decoupage que tools/kit.py). */
    static int tierOfLevel(int level) {
        return level <= 2 ? 1 : level <= 7 ? 2 : level <= 12 ? 3 : level <= 17 ? 4 : level <= 20 ? 5 : 6;
    }

    static boolean is(ItemStack stack, String piece) {
        return stack.getItem() instanceof PieceItem p && p.def().piece().equals(piece);
    }
}

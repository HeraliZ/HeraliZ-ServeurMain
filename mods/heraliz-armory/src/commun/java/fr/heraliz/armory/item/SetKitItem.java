package fr.heraliz.armory.item;

import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.armor.SetDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * Tenue complete d'un ensemble (kit_<ensemble>) : clic droit = enfile toutes les pieces d'un coup (slots vanilla + Curios).
 * Ce qui etait porte dans ces slots part dans l'inventaire (ou au sol s'il est plein). Accroupi : les pieces vont dans
 * l'inventaire au lieu d'etre enfilees. La tenue est consommee (sauf en creatif).
 * 0.9.0 : refusee (rien n'est consomme) tant qu'une combinaison est portee, sauf accroupi.
 */
public class SetKitItem extends Item {
    private final SetDef set;

    public SetKitItem(SetDef set, Properties properties) {
        super(properties.stacksTo(16));
        this.set = set;
    }

    /** Pour l'usine ModItems.KIT_USINE (proprietes par defaut, comme l'enregistrement d'origine). */
    public SetKitItem(SetDef set) {
        this(set, new Properties());
    }

    public SetDef set() {
        return set;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack kit = player.getItemInHand(hand);
        List<PieceDef> pieces = set.pieces();
        if (pieces.isEmpty()) return InteractionResultHolder.pass(kit);
        boolean intoInventory = player.isSecondaryUseActive();
        // 0.9.0 : une combinaison se porte seule, la tenue ne s'enfile pas par-dessus (accroupi : pieces dans l'inventaire)
        if (!intoInventory && fr.heraliz.armory.suit.SuitRules.wearsSuit(player)) {
            fr.heraliz.armory.suit.SuitRules.tell(player, true);
            return InteractionResultHolder.fail(kit);
        }
        if (level.isClientSide) return InteractionResultHolder.success(kit);
        return enfiler(level, player, hand, kit, pieces, intoInventory);
    }

    /**
     * Enfile la tenue (monde serveur) : pieces dans leurs slots, objets remplaces rendus, tenue consommee. Neutre ici
     * (rien ne change) : la partie serveur (SetKitItemServeur) reprend le code d'origine.
     */
    protected InteractionResultHolder<ItemStack> enfiler(Level level, Player player, InteractionHand hand, ItemStack kit,
                                                        List<PieceDef> pieces, boolean intoInventory) {
        return InteractionResultHolder.pass(kit);
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(PieceItem.nameStyle(PieceItem.tierOfLevel(set.level())));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heraliz_armory.level", set.level()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.heraliz_armory.family",
                Component.translatable("tooltip.heraliz_armory.family." + set.family())).withStyle(set.familyColor()));
        float[] total = fr.heraliz.armory.armor.Protection.setBullet(set.pieces());
        tooltip.add(Component.translatable("tooltip.heraliz_armory.kit.total", PieceItem.pct(total[0]), PieceItem.pct(total[1]))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.heraliz_armory.kit").withStyle(ChatFormatting.GRAY));
        for (PieceDef def : set.pieces()) {
            tooltip.add(Component.literal(" · ").append(Component.translatable("item.heraliz_armory." + def.itemId())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

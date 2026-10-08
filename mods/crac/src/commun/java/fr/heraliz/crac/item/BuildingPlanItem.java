package fr.heraliz.crac.item;

import fr.heraliz.crac.pont.Crochet;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class BuildingPlanItem extends Item {
    public BuildingPlanItem() {
        super(new Properties().stacksTo(1));
    }

    /** Rendu en main / dans l'inventaire : branché par la partie client (client/PartieClient). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("crac.tip.plan").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}

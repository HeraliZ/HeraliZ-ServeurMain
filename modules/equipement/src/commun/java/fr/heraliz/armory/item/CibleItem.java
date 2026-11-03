package fr.heraliz.armory.item;

import fr.heraliz.armory.cible.CibleEssai;
import fr.heraliz.armory.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pose une cible d'essai (03/11), tournée vers le joueur par pas de 45 degrés, comme le mannequin. Seulement par le staff
 * et le créatif, où ils veulent (décidé par le serveur : CibleItemServeur, CiblesServeur.posePermise).
 * Jamais dans le loot, les fabrications ni chez les marchands : donnée par le staff ({@code /give}).
 */
public class CibleItem extends Item {
    public CibleItem(Properties properties) {
        super(properties.stacksTo(4));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() == Direction.DOWN) return InteractionResult.FAIL;
        Level level = ctx.getLevel();
        BlockPos pos = new BlockPlaceContext(ctx).getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        Vec3 v = Vec3.atBottomCenterOf(pos);
        EntityType<CibleEssai> type = ModEntities.CIBLE.get();
        AABB box = type.getDimensions().makeBoundingBox(v.x(), v.y(), v.z());
        if (!level.noCollision(null, box) || !level.getEntities(null, box).isEmpty()) return InteractionResult.FAIL;
        if (level instanceof ServerLevel sl && !poser(ctx, sl, pos, stack)) return InteractionResult.FAIL;
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Pose la cible (monde serveur) ; faux si elle est refusée. Neutre ici ; partie serveur : CibleItemServeur. */
    protected boolean poser(UseOnContext ctx, ServerLevel sl, BlockPos pos, ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heraliz_armory.cible_essai").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.heraliz_armory.cible_essai.pose").withStyle(ChatFormatting.DARK_GRAY));
    }
}

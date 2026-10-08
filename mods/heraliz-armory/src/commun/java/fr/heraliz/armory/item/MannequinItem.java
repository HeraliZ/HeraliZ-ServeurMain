package fr.heraliz.armory.item;

import fr.heraliz.armory.entity.Mannequin;
import fr.heraliz.armory.registry.ModEntities;
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

/** Pose un mannequin d'armurerie (comme un porte-armure, oriente vers le joueur par pas de 45 degres). */
public class MannequinItem extends Item {
    public MannequinItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() == Direction.DOWN) return InteractionResult.FAIL;
        Level level = ctx.getLevel();
        BlockPos pos = new BlockPlaceContext(ctx).getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        Vec3 v = Vec3.atBottomCenterOf(pos);
        EntityType<Mannequin> type = ModEntities.MANNEQUIN.get();
        AABB box = type.getDimensions().makeBoundingBox(v.x(), v.y(), v.z());
        if (!level.noCollision(null, box) || !level.getEntities(null, box).isEmpty()) return InteractionResult.FAIL;
        if (level instanceof ServerLevel sl && !poser(ctx, sl, pos, stack)) return InteractionResult.FAIL;
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Pose le mannequin dans le monde serveur ; faux si la creation echoue. Neutre ici (rien n'est pose) : la partie
     * serveur (MannequinItemServeur) reprend le code d'origine.
     */
    protected boolean poser(UseOnContext ctx, ServerLevel sl, BlockPos pos, ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heraliz_armory.mannequin").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}

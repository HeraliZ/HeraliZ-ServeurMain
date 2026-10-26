package fr.heraliz.crac.item;

import fr.heraliz.crac.pont.Crochet;
import java.util.List;
import java.util.function.Consumer;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.entity.C4Entity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class C4Item extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public C4Item() {
        super(new Properties().stacksTo(16));
    }

    /** Rendu en main / dans l'inventaire : branché par la partie client (client/PartieClient). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.5F);
        if (!level.isClientSide) {
            C4Entity entity = new C4Entity(level, player);
            entity.setItem(stack);
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.4F, 0.0F);
            level.addFreshEntity(entity);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        // the server config is only there once in a world (search trees can ask for tooltips before)
        boolean loaded = Config.SPEC.isLoaded();
        tooltip.add((loaded ? Component.translatable("crac.tip.c4_fuse", Config.c4FuseSeconds.get()) : Component.translatable("crac.tip.c4")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("crac.tip.c4_target").withStyle(ChatFormatting.RED));
        if (loaded) {
            tooltip.add(Component.translatable("crac.tip.damage", Config.c4Damage.get()).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void registerControllers(ControllerRegistrar c) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}

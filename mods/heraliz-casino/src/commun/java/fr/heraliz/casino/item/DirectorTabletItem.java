package fr.heraliz.casino.item;

import fr.heraliz.casino.pont.Crochet;
import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachinePartBlock;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Tablette du Directeur : reglage des probabilites et des mises de chaque machine en jeu. Reservee aux operateurs.
 * Clic droit sur une machine : ses reglages ; clic droit dans le vide : toutes les machines.
 */
public class DirectorTabletItem extends Item {
    /** Ouverture de l'ecran des reglages (machine visee, ou null : toutes) : branche par la partie client. */
    public static final Crochet<MachineType> ECRAN_CLIENT = new Crochet<>();

    /** Coin de la zone du casino (0 = A, 1 = B) choisi par un operateur : traitement de la partie serveur (ZoneCommand). */
    @FunctionalInterface
    public interface Coin {
        void poser(net.minecraft.server.level.ServerPlayer p, int which, BlockPos pos);
    }

    /** Branche par la partie serveur ; neutre sans elle. */
    public static volatile Coin COIN_SERVEUR;

    public DirectorTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        // accroupi : on delimite la zone du casino (clic droit = coin B ; le coin A se pose au clic gauche)
        if (p.isShiftKeyDown()) {
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) corner(sp, 1, ctx.getClickedPos());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        MachineType type = machineAt(level, ctx.getClickedPos());
        if (type == null) return InteractionResult.PASS;
        open(level, p, type);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        open(level, p, null);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }

    @Nullable
    private static MachineType machineAt(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof MachineBlock mb) return mb.type;
        if (st.getBlock() instanceof MachinePartBlock) {
            BlockState ms = level.getBlockState(MachinePartBlock.masterPos(pos, st));
            if (ms.getBlock() instanceof MachineBlock mb) return mb.type;
        }
        return null;
    }

    private static void open(Level level, Player p, @Nullable MachineType type) {
        if (!p.hasPermissions(2)) {
            if (!level.isClientSide) p.displayClientMessage(Component.translatable("message.heralizcasino.admin_only").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (level.isClientSide) ECRAN_CLIENT.accept(type);
    }

    /** Coin de la zone du casino choisi avec la tablette (operateurs seulement). */
    public static void corner(net.minecraft.server.level.ServerPlayer p, int which, BlockPos pos) {
        if (!p.hasPermissions(2)) {
            p.displayClientMessage(Component.translatable("message.heralizcasino.admin_only").withStyle(ChatFormatting.RED), true);
            return;
        }
        Coin c = COIN_SERVEUR;
        if (c != null) c.poser(p, which, pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heralizcasino.tablet.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.heralizcasino.tablet.zone").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.heralizcasino.tablet.op").withStyle(ChatFormatting.DARK_RED));
    }
}

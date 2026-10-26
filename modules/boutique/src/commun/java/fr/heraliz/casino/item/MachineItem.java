package fr.heraliz.casino.item;

import fr.heraliz.casino.pont.Crochet;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Objet servant a poser une machine (rendu 3D dans l'inventaire).
 */
public class MachineItem extends BlockItem {
    public final MachineType type;

    public MachineItem(Block block, MachineType type, Properties properties) {
        super(block, properties);
        this.type = type;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(s -> s.withColor(type.glow).withBold(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(type.description().withStyle(ChatFormatting.GRAY));
        int[] size = size();
        tooltip.add(Component.translatable("tooltip.heralizcasino.machine.size", size[0], size[1], size[2]).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Largeur, profondeur, hauteur (blocs). */
    private int[] size() {
        int minX = 0, maxX = 0, minZ = 0, maxZ = 0, maxY = 0;
        for (int[] c : type.footprint.cells) {
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[2]);
            maxZ = Math.max(maxZ, c[2]);
            maxY = Math.max(maxY, c[1]);
        }
        return new int[]{maxX - minX + 1, maxZ - minZ + 1, maxY + 1};
    }

    /** Rendu 3D dans l'inventaire : branche par la partie client (Forge l'appelle a la creation de l'objet). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }
}

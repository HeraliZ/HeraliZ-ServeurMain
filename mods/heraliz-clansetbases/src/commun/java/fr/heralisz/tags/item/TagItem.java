package fr.heralisz.tags.item;

import fr.heralisz.tags.catalogue.TagDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Objet « tag » : clic droit = debloque le tag pour le joueur et consomme un exemplaire. Deja possede : message,
 * rien de consomme. Le nom affiche vient du catalogue (tags.json), la couleur du nom de la rarete.
 */
public class TagItem extends Item {
    /**
     * Deblocage du tag pour le joueur (TagService.debloquer, branche par la partie serveur du mod) ; vrai si le tag vient
     * d'etre debloque (un exemplaire est alors consomme). Sans partie serveur (jar -client) : rien n'est debloque.
     */
    public static volatile BiPredicate<ServerPlayer, String> DEBLOQUER = (p, id) -> false;

    private final TagDef def;

    public TagItem(TagDef def, Properties props) {
        super(props);
        this.def = def;
    }

    public TagDef def() {
        return def;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.heralisz_tags.tag", def.nom(fr.heralisz.tags.util.Langue.francais()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lignes, TooltipFlag flag) {
        lignes.add(Component.translatable(def.rarete().cle()).withStyle(s -> s.withColor(def.rarete().couleur)));
        lignes.add(Component.translatable("item.heralisz_tags.tag.aide").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (DEBLOQUER.test(sp, def.id())) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}

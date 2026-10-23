package fr.heraliz.grades.bon;

import fr.heraliz.grades.HeralizGrades;
import fr.heraliz.grades.pont.Crochet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

/**
 * L'objet « Bon » ({@code heraliz_grades:bon}) : même aspect que le document « Bonus de loot », nom, couleur, rareté et
 * effets écrits par l'éditeur des op ({@link Bon}). Clic droit : le serveur vérifie la signature et donne ce que dit le
 * bon (partie serveur, {@link #UTILISER}). Aussi l'effet « Bonus de loot » montré dans l'inventaire, sans particules.
 */
public final class ObjetBon extends Item {
    public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizGrades.MODID);
    public static final DeferredRegister<MobEffect> EFFETS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, HeralizGrades.MODID);
    public static final RegistryObject<Item> BON = OBJETS.register("bon", ObjetBon::new);
    public static final RegistryObject<MobEffect> BONUS_LOOT = EFFETS.register("bonus_loot", EffetBonusLoot::new);

    /** Un bon utilisé (clic droit), côté serveur. */
    public record Utilisation(ServerPlayer joueur, InteractionHand main) {
    }

    /** Clic droit côté serveur : branché par la partie serveur. */
    public static final Crochet<Utilisation> UTILISER = new Crochet<>();

    private ObjetBon() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public Component getName(ItemStack st) {
        Bon.Donnees d = Bon.lire(st);
        if (d == null || d.nom().isBlank()) return super.getName(st);
        int c = d.couleur() & 0xFFFFFF;
        return Component.literal(d.nom()).withStyle(s -> s.withColor(TextColor.fromRgb(c)));
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        Bon.Donnees d = Bon.lire(st);
        if (d == null) {
            lignes.add(Component.translatableWithFallback("item.heraliz_grades.bon.vierge", "Bon vierge : réglé par le staff.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (Bon.Effet e : d.effets()) lignes.add(Component.literal("• ").append(Bon.texteEffet(e)).withStyle(ChatFormatting.GRAY));
        lignes.add(Component.translatableWithFallback("item.heraliz_grades.bon.utiliser", "Clic droit pour l'utiliser")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player joueur, InteractionHand main) {
        ItemStack st = joueur.getItemInHand(main);
        if (joueur instanceof ServerPlayer p) UTILISER.accept(new Utilisation(p, main));
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
    }
}

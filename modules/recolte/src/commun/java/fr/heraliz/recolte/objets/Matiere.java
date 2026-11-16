package fr.heraliz.recolte.objets;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Une matière brute récoltée (bûche, pierre brute, minerai de fer, minerai rare) : de la place dans l'inventaire (piles de
 * 32, minerai rare 16), reste sur le corps à la mort, échangée au spawn par le Contremaître contre les matériaux des bases.
 * Jamais revendue (ni Mila, ni le recycleur) : la récolte fait des matériaux, pas des hérals. Sa rareté (couleur du nom)
 * vient de {@code raretes.json} de HeraliZ Loot.
 */
public class Matiere extends Item {
    private final String id;

    public Matiere(String id, int pile) {
        super(new Item.Properties().stacksTo(pile));
        this.id = id;
    }

    /** Identifiant dans le mod ({@code buche}, {@code pierre_brute}...). */
    public String id() {
        return id;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        lignes.add(Component.translatable("heraliz_recolte.info." + id).withStyle(ChatFormatting.GRAY));
    }
}

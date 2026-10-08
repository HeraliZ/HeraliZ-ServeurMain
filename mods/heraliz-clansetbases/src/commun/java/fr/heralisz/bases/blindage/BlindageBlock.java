package fr.heralisz.bases.blindage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Bloc de blindage de niveau 1 a 8 (BASES_SPEC §5) : niveau N = N explosions de C4 pour le casser (table
 * {@link Protection}, coups dans {@link CoupsData}). Insensible aux autres explosions (retire de la liste detruite par
 * {@link Blindage}, resistance 1200 pour l'ombre portee des rayons). Un membre le retire a la pioche (il se rend) ; un
 * pillard ne le casse jamais a la main.
 */
public class BlindageBlock extends Block {
    /** Couleur du liseré par niveau (meme table que tools/bases/gen_blindage.py). */
    public static final int[] COULEURS = {0x6E7C8A, 0x5FD08A, 0x4FC3F7, 0x6FA8FF, 0xB28DFF, 0xE8C36A, 0xFF9F43, 0xFF5A5A};

    public final int niveau;

    public BlindageBlock(int niveau, Properties p) {
        super(p);
        this.niveau = niveau;
    }

    public static int couleur(int niveau) {
        return COULEURS[Math.max(1, Math.min(COULEURS.length, niveau)) - 1];
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lignes, TooltipFlag flag) {
        lignes.add(Component.translatable("tooltip.heralisz_bases.blindage.niveau", niveau).withStyle(s -> s.withColor(couleur(niveau))));
        lignes.add(Component.translatable(niveau > 1 ? "tooltip.heralisz_bases.blindage.c4" : "tooltip.heralisz_bases.blindage.c4_un", niveau)
                .withStyle(s -> s.withColor(0x8F95A0)));
    }
}

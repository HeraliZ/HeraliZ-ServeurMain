package fr.heraliz.wonder.sliquifier.serveur;

import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.sliquifier.Sliquifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;

/**
 * Nom du Sliquifier passe au Pack-a-Punch du mode zombies : « ★ Sliquidator » (nom maison : dans CoD, le Sliquifier n'a
 * pas de version Pack-a-Punch connue). Meme presentation que les autres Wonder Weapons ({@code NomsPap} : etoiles et
 * couleur du niveau) ; verification une fois par seconde et par joueur, sans allocation si rien a faire.
 */
public final class SliqPap {
    private SliqPap() {
    }

    private static final String NBT_NOM = "heraliz_wonder:nom_pap";
    private static final int[] COULEURS = {0xE59BFF, 0xC77DFF, 0xFF5FD2};

    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || (p.tickCount + p.getId() + 7) % 20 != 0) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) renommer(inv.getItem(i));
    }

    static void renommer(ItemStack s) {
        if (!Sliquifier.est(s)) return;
        int n = Armes.pap(s);
        CompoundTag t = s.getTag();
        if (t == null) return;
        if (n <= 0) {
            t.remove(NBT_NOM);
            return;
        }
        if (t.getInt(NBT_NOM) == n && s.hasCustomHoverName()) return;
        int coul = COULEURS[Math.min(COULEURS.length, n) - 1];
        MutableComponent nom = Component.empty()
                .append(Component.literal("★".repeat(n) + " ").withStyle(st -> st.withFont(Style.DEFAULT_FONT)))
                .append(Component.translatable("heraliz_wonder.gun.sliquifier.pap"))
                .withStyle(st -> st.withColor(coul).withItalic(false).withBold(true));
        s.setHoverName(nom);
        t.putInt(NBT_NOM, n);
    }
}

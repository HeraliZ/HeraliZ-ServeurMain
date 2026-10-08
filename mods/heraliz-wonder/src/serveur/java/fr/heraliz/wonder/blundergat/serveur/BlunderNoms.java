package fr.heraliz.wonder.blundergat.serveur;

import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.blundergat.BlunderArmes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;

/**
 * Noms CoD au Pack-a-Punch : « ★ The Sweeper » (Blundergat), « ★ Vitriolic Withering » (Acid Gat), dans la presentation du
 * mode zombies (etoiles = niveau, couleur du niveau), comme {@link fr.heraliz.wonder.serveur.NomsPap} pour les autres
 * Wonder Weapons. La marque retient l'arme ET le niveau : une arme convertie a l'etabli (Blundergat -> Acid Gat) est
 * renommee. Verification une fois par seconde et par joueur, sans allocation si rien a faire.
 */
public final class BlunderNoms {
    private BlunderNoms() {
    }

    private static final String NBT_NOM = "heraliz_wonder:nom_pap_bg";
    private static final int[] COULEURS = {0xE59BFF, 0xC77DFF, 0xFF5FD2};

    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 20 != 7) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) renommer(inv.getItem(i));
    }

    public static void renommer(ItemStack s) {
        byte type = BlunderArmes.type(s);
        if (type == BlunderArmes.AUCUNE) return;
        CompoundTag t = s.getTag();
        if (t == null) return;
        int n = Armes.pap(s);
        if (n <= 0) {
            if (t.contains(NBT_NOM)) {
                t.remove(NBT_NOM);
                s.resetHoverName();
            }
            return;
        }
        int marque = type * 10 + n;
        if (t.getInt(NBT_NOM) == marque && s.hasCustomHoverName()) return;
        String cle = type == BlunderArmes.ACIDGAT ? "heraliz_wonder.gun.acidgat.pap" : "heraliz_wonder.gun.blundergat.pap";
        int coul = COULEURS[Math.min(COULEURS.length, n) - 1];
        MutableComponent nom = Component.empty()
                .append(Component.literal("★".repeat(n) + " ").withStyle(st -> st.withFont(Style.DEFAULT_FONT)))
                .append(Component.translatable(cle))
                .withStyle(st -> st.withColor(coul).withItalic(false).withBold(true));
        s.setHoverName(nom);
        t.putInt(NBT_NOM, marque);
    }
}

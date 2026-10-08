package fr.heraliz.wonder.serveur;

import fr.heraliz.wonder.Armes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;

/**
 * Noms CoD des Wonder Weapons passees au Pack-a-Punch du mode zombies : « Porter's X2 Ray Gun », « Mk II-Z »,
 * « Wunderwaffe DG-3 JZ », « Zeus Cannon » (Thundergun, 1.0.2). Le mode zombies nomme l'arme « ★ <nom d'origine> » (etoiles = niveau, couleur du niveau) et
 * garde le nom d'origine a part pour le niveau suivant ; on remplace seulement le nom par celui de la version amelioree,
 * dans la meme presentation. Verification une fois par seconde et par joueur (inventaire, sans allocation si rien a faire).
 */
public final class NomsPap {
    private NomsPap() {
    }

    /** Niveau pour lequel le nom a ete pose (le mode zombies refait le nom a chaque passage). */
    private static final String NBT_NOM = "heraliz_wonder:nom_pap";
    /** Couleurs des niveaux du mode zombies ({@code PackAPunch.COULEURS}) : lavande, violet, magenta. */
    private static final int[] COULEURS = {0xE59BFF, 0xC77DFF, 0xFF5FD2};

    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 20 != 0) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) renommer(inv.getItem(i));
    }

    static void renommer(ItemStack s) {
        byte type = Armes.type(s);
        if (type == Armes.AUCUNE) return;
        int n = Armes.pap(s);
        CompoundTag t = s.getTag();
        if (t == null) return;
        if (n <= 0) {
            t.remove(NBT_NOM);
            return;
        }
        if (t.getInt(NBT_NOM) == n && s.hasCustomHoverName()) return;
        String cle = switch (type) {
            case Armes.RAYON -> "heraliz_wonder.gun.raygun_bo6.pap";
            case Armes.MK2 -> "heraliz_wonder.gun.ray_gun_mk2.pap";
            case Armes.TONNERRE -> "heraliz_wonder.gun.thundergun.pap";
            case Armes.HURLEMENT -> "heraliz_wonder.gun.winters_howl.pap";
            case Armes.PARALYSEUR -> "heraliz_wonder.gun.paralyzer.pap";
            default -> "heraliz_wonder.gun.wonder_waffle.pap";
        };
        int coul = COULEURS[Math.min(COULEURS.length, n) - 1];
        MutableComponent nom = Component.empty()
                .append(Component.literal("★".repeat(n) + " ").withStyle(st -> st.withFont(Style.DEFAULT_FONT)))
                .append(Component.translatable(cle))
                .withStyle(st -> st.withColor(coul).withItalic(false).withBold(true));
        s.setHoverName(nom);
        t.putInt(NBT_NOM, n);
    }
}

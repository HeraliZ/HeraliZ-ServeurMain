package fr.heraliz.armory.nvg;

import fr.heraliz.armory.item.PieceItem;
import net.minecraft.world.item.ItemStack;

/**
 * Lunettes de vision nocturne articulees : l'etat (baissees / relevees) est stocke sur le casque (NBT "NvgDown"), donc
 * synchronise a tous les joueurs (animation visible) et conserve sur un mannequin. Baissees : effet Vision nocturne
 * entretenu tant que le casque est porte ; relevees ou casque retire : l'effet du mod est retire. En service, les
 * objectifs luisent en vert et l'appareil gresille (visible et audible a quelques blocs : voir NvgClient).
 */
public final class NightVision {
    private NightVision() {}

    public static final String TAG = "NvgDown";
    // logique serveur (bascule, effet entretenu) : NightVisionServeur

    public static boolean hasNvg(ItemStack s) {
        return s.getItem() instanceof PieceItem p && p.def().nvg();
    }

    public static boolean isDown(ItemStack s) {
        return hasNvg(s) && s.hasTag() && s.getTag().getBoolean(TAG);
    }
}

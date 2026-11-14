package fr.heraliz.noyau.client.kit;

import com.mojang.datafixers.util.Pair;
import fr.heraliz.noyau.client.mixin.CaseAcces;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Place des cases d'un menu dans nos écrans en verre, seulement chez le joueur. Le serveur garde le même menu, les mêmes
 * cases aux mêmes numéros et les mêmes règles (anti-duplication comprise) : un clic, un Maj + clic, un glisser, une touche
 * 1 à 9 ou un double clic ne transportent que le numéro de la case, jamais sa place à l'écran.
 * <ul>
 *     <li>{@link #placer} : nouvelle place d'une case (coin haut gauche de l'objet, depuis le coin du panneau) ;</li>
 *     <li>{@link #cacher} : case remplacée par une case cachée (même conteneur, même numéro, inactive : ni dessinée, ni
 *     survolée, ni cliquée, ni prise dans un glisser) ; ce qui y arrive du serveur y est toujours écrit.</li>
 * </ul>
 * Un menu qui refait ses cases (Curios, à chaque changement de ses cases) remet les siennes : l'écran recommence alors sa
 * disposition (voir {@link #signature}).
 */
public final class CasesEcran {
    private CasesEcran() {
    }

    /** Place des cases cachées : loin hors du panneau. */
    public static final int LOIN = -10000;

    /** Pose la case en (x, y) (coin haut gauche de l'objet, unités d'interface depuis le coin du panneau). */
    public static void placer(Slot s, int x, int y) {
        if (s.x == x && s.y == y) return;
        CaseAcces a = (CaseAcces) s;
        a.heraliz$poserX(x);
        a.heraliz$poserY(y);
    }

    /** Cache la case {@code i} du menu ; rend la case cachée. */
    public static Slot cacher(AbstractContainerMenu menu, int i) {
        Slot s = menu.slots.get(i);
        if (s instanceof Cachee) return s;
        Cachee c = new Cachee(s);
        menu.slots.set(i, c);
        return c;
    }

    /** Vrai pour une case cachée par {@link #cacher}. */
    public static boolean cachee(Slot s) {
        return s instanceof Cachee;
    }

    /**
     * Repère des cases d'un menu : nombre et première case. Il change quand le menu refait ses cases (l'écran doit alors
     * refaire sa disposition). Sans allocation.
     */
    public static long signature(AbstractContainerMenu menu) {
        int n = menu.slots.size();
        return n == 0 ? 0L : ((long) n << 32) ^ (System.identityHashCode(menu.slots.get(0)) & 0xFFFFFFFFL);
    }

    /** Case cachée : même conteneur, même numéro, inactive et sans dépôt. */
    public static final class Cachee extends Slot {
        private final Slot origine;

        Cachee(Slot origine) {
            super(origine.container, origine.getContainerSlot(), LOIN, LOIN);
            this.origine = origine;
            this.index = origine.index;
        }

        public Slot origine() {
            return origine;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player joueur) {
            return origine.mayPickup(joueur);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return null;
        }
    }
}

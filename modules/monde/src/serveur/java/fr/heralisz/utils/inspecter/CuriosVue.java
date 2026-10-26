package fr.heralisz.utils.inspecter;

import fr.heralisz.utils.HeraliszUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Emplacements Curios d'un joueur (masque, gilet, gants, genouillère, sac, ghillie, brassard) pour /inspecter. */
final class CuriosVue {
    private CuriosVue() {
    }

    /** Une case par emplacement Curios (hors cosmétiques), les vrais emplacements ; liste vide sans Curios. */
    static List<Vue.Case> cases(Player p) {
        List<Vue.Case> l = new ArrayList<>();
        if (ModList.get() == null || !ModList.get().isLoaded("curios")) {
            return l;
        }
        try {
            Api.cases(p, l);
        } catch (LinkageError | RuntimeException e) {
            HeraliszUtils.LOG.warn("[inspecter] emplacements Curios illisibles : {}", e.toString());
        }
        return l;
    }

    private static final class Api {
        static void cases(Player p, List<Vue.Case> l) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.getCurios().forEach((id, sh) -> {
                var stacks = sh.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    final int k = i;
                    l.add(new Vue.Case() {
                        @Override
                        public ItemStack get() {
                            return stacks.getStackInSlot(k);
                        }

                        @Override
                        public void set(ItemStack s) {
                            stacks.setStackInSlot(k, s);
                        }

                        @Override
                        public boolean accepte(ItemStack s) {
                            return stacks.isItemValid(k, s);
                        }
                    });
                }
            }));
        }
    }
}

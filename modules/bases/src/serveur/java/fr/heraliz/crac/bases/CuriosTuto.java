package fr.heraliz.crac.bases;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Emplacements Curios d'un joueur (sac porté compris : HeraliZ Backpack le range dans l'emplacement « back ») mis de côté
 * pendant le tutoriel des bases ({@link InventaireTuto}). Jamais cité sans que Curios soit chargé
 * ({@link InventaireTuto#curios()}). Chaque objet est gardé avec sa place (emplacement, case, cosmétique) et revient à la
 * même place ; une place qui n'existe plus ou qui est prise : l'objet revient avec le reste (inventaire, sinon mis de
 * côté), jamais perdu.
 */
final class CuriosTuto {
    private CuriosTuto() {
    }

    private static Optional<ICuriosItemHandler> inv(ServerPlayer p) {
        return CuriosApi.getCuriosInventory(p).resolve();
    }

    /** Les objets des emplacements Curios, sans rien changer : {Id, Case, Cosm, Objet}. */
    static ListTag lister(ServerPlayer p) {
        ListTag out = new ListTag();
        Optional<ICuriosItemHandler> o = inv(p);
        if (o.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, ICurioStacksHandler> e : o.get().getCurios().entrySet()) {
            lister(e.getKey(), e.getValue().getStacks(), false, out);
            lister(e.getKey(), e.getValue().getCosmeticStacks(), true, out);
        }
        return out;
    }

    private static void lister(String id, IDynamicStackHandler pile, boolean cosmetique, ListTag out) {
        for (int i = 0; i < pile.getSlots(); i++) {
            ItemStack s = pile.getStackInSlot(i);
            if (!s.isEmpty()) {
                CompoundTag t = new CompoundTag();
                t.putString("Id", id);
                t.putInt("Case", i);
                t.putBoolean("Cosm", cosmetique);
                t.put("Objet", s.save(new CompoundTag()));
                out.add(t);
            }
        }
    }

    /** Vide les emplacements Curios (après l'écriture du dépôt qui les garde). */
    static void vider(ServerPlayer p) {
        Optional<ICuriosItemHandler> o = inv(p);
        if (o.isEmpty()) {
            return;
        }
        for (ICurioStacksHandler h : o.get().getCurios().values()) {
            vider(h.getStacks());
            vider(h.getCosmeticStacks());
        }
    }

    private static void vider(IDynamicStackHandler pile) {
        for (int i = 0; i < pile.getSlots(); i++) {
            if (!pile.getStackInSlot(i).isEmpty()) {
                pile.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** Remet chaque objet à sa place ; ceux qui n'y tiennent pas sont ajoutés à {@code reste}. */
    static void remettre(ServerPlayer p, ListTag l, List<ItemStack> reste) {
        Optional<ICuriosItemHandler> o = inv(p);
        for (int k = 0; k < l.size(); k++) {
            CompoundTag t = l.getCompound(k);
            ItemStack s = ItemStack.of(t.getCompound("Objet"));
            if (s.isEmpty()) {
                continue;
            }
            ICurioStacksHandler h = o.isEmpty() ? null : o.get().getCurios().get(t.getString("Id"));
            IDynamicStackHandler pile = h == null ? null : t.getBoolean("Cosm") ? h.getCosmeticStacks() : h.getStacks();
            int i = t.getInt("Case");
            if (pile != null && i >= 0 && i < pile.getSlots() && pile.getStackInSlot(i).isEmpty()) {
                pile.setStackInSlot(i, s);
            } else {
                reste.add(s);
            }
        }
    }
}

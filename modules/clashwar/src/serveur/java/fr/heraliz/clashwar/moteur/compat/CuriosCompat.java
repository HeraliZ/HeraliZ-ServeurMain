package fr.heraliz.clashwar.moteur.compat;

import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Acces direct a l'API Curios (compilee contre 5.14.1, n'utilise que ce qui existe deja en 5.9.1, la version du
 * serveur). Ne jamais referencer cette classe sans passer par {@link Compat} (qui verifie que Curios est charge).
 */
final class CuriosCompat {
    private CuriosCompat() {
    }

    private static Optional<ICuriosItemHandler> inv(ServerPlayer p) {
        return CuriosApi.getCuriosInventory(p).resolve();
    }

    static ListTag sauver(ServerPlayer p) {
        return inv(p).map(i -> i.saveInventory(false)).orElse(null);
    }

    static int vider(ServerPlayer p) {
        Optional<ICuriosItemHandler> o = inv(p);
        if (o.isEmpty()) return 0;
        int n = 0;
        for (ICurioStacksHandler h : o.get().getCurios().values()) {
            n += viderPile(h.getStacks());
            n += viderPile(h.getCosmeticStacks());
        }
        return n;
    }

    private static int viderPile(IDynamicStackHandler s) {
        int n = 0;
        for (int i = 0; i < s.getSlots(); i++) {
            if (!s.getStackInSlot(i).isEmpty()) {
                s.setStackInSlot(i, ItemStack.EMPTY);
                n++;
            }
        }
        return n;
    }

    static void charger(ServerPlayer p, ListTag t) {
        Optional<ICuriosItemHandler> o = inv(p);
        if (o.isEmpty()) return;
        // piege connu : loadInventory ne vide pas les emplacements absents de l'instantane -> vider d'abord
        vider(p);
        o.get().loadInventory(t);
    }

    static boolean equiper(ServerPlayer p, ItemStack pile) {
        Optional<ICuriosItemHandler> o = inv(p);
        if (o.isEmpty()) return false;
        for (String id : CuriosApi.getItemStackSlots(pile, p).keySet()) {
            Optional<ICurioStacksHandler> h = o.get().getStacksHandler(id);
            if (h.isEmpty()) continue;
            IDynamicStackHandler s = h.get().getStacks();
            for (int i = 0; i < s.getSlots(); i++) {
                if (s.getStackInSlot(i).isEmpty()) {
                    s.setStackInSlot(i, pile);
                    return true;
                }
            }
        }
        return false;
    }

    static int purger(ServerPlayer p, Predicate<ItemStack> filtre) {
        Optional<ICuriosItemHandler> o = inv(p);
        if (o.isEmpty()) return 0;
        int n = 0;
        for (ICurioStacksHandler h : o.get().getCurios().values()) {
            n += purgerPile(h.getStacks(), filtre);
            n += purgerPile(h.getCosmeticStacks(), filtre);
        }
        return n;
    }

    private static int purgerPile(IDynamicStackHandler s, Predicate<ItemStack> filtre) {
        int n = 0;
        for (int i = 0; i < s.getSlots(); i++) {
            ItemStack st = s.getStackInSlot(i);
            if (!st.isEmpty() && filtre.test(st)) {
                s.setStackInSlot(i, ItemStack.EMPTY);
                n++;
            }
        }
        return n;
    }

    static List<ItemStack> pieces(ServerPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        inv(p).ifPresent(i -> {
            for (ICurioStacksHandler h : i.getCurios().values()) {
                for (int k = 0; k < h.getStacks().getSlots(); k++) {
                    ItemStack st = h.getStacks().getStackInSlot(k);
                    if (!st.isEmpty()) l.add(st);
                }
                for (int k = 0; k < h.getCosmeticStacks().getSlots(); k++) {
                    ItemStack st = h.getCosmeticStacks().getStackInSlot(k);
                    if (!st.isEmpty()) l.add(st);
                }
            }
        });
        return l;
    }

    static boolean poser(ServerPlayer p, String slot, int index, ItemStack pile) {
        Optional<ICuriosItemHandler> o = inv(p);
        if (o.isEmpty()) return false;
        Optional<ICurioStacksHandler> h = o.get().getStacksHandler(slot);
        if (h.isEmpty() || index >= h.get().getStacks().getSlots()) return false;
        h.get().getStacks().setStackInSlot(index, pile);
        return true;
    }

    static List<String> emplacements(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        inv(p).ifPresent(i -> {
            for (Map.Entry<String, ICurioStacksHandler> e : i.getCurios().entrySet()) {
                l.add(e.getKey() + "x" + e.getValue().getSlots());
            }
        });
        return l;
    }
}

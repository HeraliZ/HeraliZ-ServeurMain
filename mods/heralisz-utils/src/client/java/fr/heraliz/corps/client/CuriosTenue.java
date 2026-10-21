package fr.heraliz.corps.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.HashMap;
import java.util.Map;

/** Emplacements Curios du mannequin d'un corps ({@link TenueCorps}) ; charge seulement si Curios est la. */
final class CuriosTenue {
    private CuriosTenue() {
    }

    /** Vide tous ses emplacements puis y range la tenue ({@code Id} + objet). Vrai si au moins un objet a ete pose. */
    static boolean remplir(Player j, ListTag curios) {
        boolean[] pose = {false};
        CuriosApi.getCuriosInventory(j).ifPresent(inv -> {
            for (ICurioStacksHandler h : inv.getCurios().values()) {
                for (int i = 0; i < h.getStacks().getSlots(); i++) h.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                for (int i = 0; i < h.getCosmeticStacks().getSlots(); i++) h.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
            Map<String, Integer> rang = new HashMap<>();
            for (int k = 0; k < curios.size(); k++) {
                CompoundTag o = curios.getCompound(k);
                String id = o.getString("Id");
                ItemStack s = ItemStack.of(o);
                if (s.isEmpty()) continue;
                inv.getStacksHandler(id).ifPresent(h -> {
                    int i = rang.getOrDefault(id, 0);
                    if (i < h.getStacks().getSlots()) {
                        h.getStacks().setStackInSlot(i, s);
                        rang.put(id, i + 1);
                        pose[0] = true;
                    }
                });
            }
        });
        return pose[0];
    }
}

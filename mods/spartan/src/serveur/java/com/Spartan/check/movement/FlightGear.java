package com.Spartan.check.movement;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Équipement de vol légitime, porté (armure), tenu en main ou dans un slot Curios.
 *
 * - flyHoverItems (identifiants exacts : jetpacks…) : peut voler sur place et
 *   monter → Fly, Vertical et NoFall ignorent le joueur, Speed aussi en l'air.
 * - flyExemptKeywords (mots-clés : parachute, planeur…) : ralentit seulement la
 *   chute → Fly tolère une descente très lente, mais ni vol stationnaire ni montée.
 *
 * Un parachute DÉPLOYÉ sous forme d'entité chevauchée est déjà exempté (monture).
 */
public final class FlightGear {
    private static Method curiosInventory;   // CuriosApi.getCuriosInventory(LivingEntity)
    private static Method equippedCurios;    // ICuriosItemHandler.getEquippedCurios()
    private static boolean curiosResolved;

    private FlightGear() {
    }

    public static boolean hovers(ServerPlayer p) {
        List<String> ids = SpartanConfig.getFlyHoverItems();
        return !ids.isEmpty() && anyEquipped(p, id -> ids.contains(id));
    }

    public static boolean slowsFall(ServerPlayer p) {
        List<String> keywords = SpartanConfig.getFlyExemptKeywords();
        return !keywords.isEmpty() && anyEquipped(p, id -> keywords.stream().anyMatch(id::contains));
    }

    private interface IdTest {
        boolean test(String id);
    }

    private static boolean anyEquipped(ServerPlayer p, IdTest test) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (matches(p.getItemBySlot(slot), test)) {
                return true;
            }
        }
        for (ItemStack stack : curios(p)) {
            if (matches(stack, test)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ItemStack stack, IdTest test) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && test.test(key.toString().toLowerCase(Locale.ROOT));
    }

    /** Objets équipés dans les slots Curios (dépendance optionnelle, par réflexion). */
    private static List<ItemStack> curios(ServerPlayer p) {
        if (!resolveCurios()) {
            return List.of();
        }
        try {
            Object lazy = curiosInventory.invoke(null, p);
            if (!(lazy instanceof LazyOptional<?> opt)) {
                return List.of();
            }
            Optional<?> handler = opt.resolve();
            if (handler.isEmpty()) {
                return List.of();
            }
            Object items = equippedCurios.invoke(handler.get());
            if (!(items instanceof IItemHandler h)) {
                return List.of();
            }
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < h.getSlots(); i++) {
                out.add(h.getStackInSlot(i));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    private static synchronized boolean resolveCurios() {
        if (curiosResolved) {
            return curiosInventory != null;
        }
        curiosResolved = true;
        if (!ModList.get().isLoaded("curios")) {
            return false;
        }
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            curiosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            equippedCurios = handler.getMethod("getEquippedCurios");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            curiosInventory = null;
            Spartan.LOGGER.warn("[Spartan] Curios présent mais API non reconnue : slots Curios ignorés ({})", e.toString());
            return false;
        }
    }
}

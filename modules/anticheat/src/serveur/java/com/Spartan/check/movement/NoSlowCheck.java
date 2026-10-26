package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.check.movement.model.ExcessWindow;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * NoSlow : vanilla réduit l'entrée du joueur à 20 % quand il utilise un objet
 * (manger, boire, se bander, bander un arc, lever un bouclier…). Un client qui
 * garde sa vitesse court en se soignant.
 *
 * Vérifié uniquement quand l'utilisation est CERTAINE malgré le ping : commencée
 * côté serveur depuis plus d'un aller-retour, pas sur le point de finir, pas
 * relâchée dans le tick (voir MovementEngine). L'élan d'avant l'utilisation
 * retombe selon la physique de vanilla (sauts et reculs compris).
 */
public class NoSlowCheck extends MovementCheck {
    private final Map<UUID, ExcessWindow> windows = new ConcurrentHashMap<>();

    public NoSlowCheck() {
        super("NoSlow");
    }

    /** Objet qui ne ralentit pas dans ce modpack (liste de la config). */
    public static boolean ignored(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && SpartanConfig.getNoSlowIgnoredItems().contains(id.toString());
    }

    @Override
    public void onSample(ServerPlayer p, MoveSample s) {
        if (!SpartanConfig.CHECK_NOSLOW.get()) {
            return;
        }
        ExcessWindow w = windows.computeIfAbsent(p.getUUID(), k -> new ExcessWindow());
        MotionTracker.Result m = s.motion();
        if (m == null || m.exempt() || !m.slowed()) {
            w.clear();
            return;
        }
        // Seule la part due à l'absence de ralentissement (le reste relève de Speed).
        double excess = Math.max(0, m.excessSlow() - m.excessH());
        double over = w.add(excess, s.ticks(), SpartanConfig.NOSLOW_TOLERANCE.get());
        if (over > 0) {
            ResourceLocation item = ForgeRegistries.ITEMS.getKey(p.getUseItem().getItem());
            flag(p, String.format("garde sa vitesse en utilisant %s : %.1f blocs/s (maximum %.1f), %.2f bloc(s) de trop en ~1 s",
                    item == null ? "un objet" : item.toString(), s.horizontal() / m.ticks() * 20,
                    m.allowedSlow() / m.ticks() * 20, over));
        }
    }

    @Override
    public void clear(UUID uuid) {
        windows.remove(uuid);
    }
}

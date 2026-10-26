package fr.heraliz.armory.client;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Plus moyen de cacher un objet Curios porté (22/10, choix du propriétaire) : le bouton est retiré des cases
 * (« render_toggle » à false dans les slots), et un objet caché avant reste dessiné : chez le joueur, toutes les cases
 * Curios de chaque joueur visible sont remises « affichées » une fois par seconde (CuriosLayer lit ce drapeau).
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class AffichageCurios {
    private AffichageCurios() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() % 20 != 0) return;
        try {
            for (Player p : mc.level.players()) {
                CuriosApi.getCuriosInventory(p).ifPresent(inv -> inv.getCurios().values().forEach(h -> {
                    NonNullList<Boolean> r = h.getRenders();
                    for (int i = 0; i < r.size(); i++) if (!r.get(i)) r.set(i, true);
                }));
            }
        } catch (RuntimeException | LinkageError e) {
            // Curios absent ou changé : rien à faire
        }
    }
}

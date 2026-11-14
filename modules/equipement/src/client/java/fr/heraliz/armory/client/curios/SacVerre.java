package fr.heraliz.armory.client.curios;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Écran du sac à dos en verre (14/11) : à l'ouverture de l'écran de HeraliZ Backpack, le même menu passe dans
 * {@link EcranSac}. Reconnu par le nom de sa classe (sans le mod, rien ne se passe) ; le code du mod n'est pas touché.
 * Une erreur coupe l'échange pour la session (journal) : l'écran du mod revient.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class SacVerre {
    private SacVerre() {
    }

    private static final String ECRAN_DU_MOD = "com.mrcrayfish.backpacked.client.gui.screen.inventory.BackpackScreen";
    private static boolean enPanne;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void ouverture(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (enPanne || e.isCanceled() || s == null || !ECRAN_DU_MOD.equals(s.getClass().getName())) return;
        try {
            e.setNewScreen(EcranSac.de((AbstractContainerScreen<?>) s));
        } catch (LinkageError | RuntimeException ex) {
            enPanne = true;
            HeralizArmory.LOGGER.error("[armurerie] écran du sac en verre en panne : écran du mod pour cette session", ex);
        }
    }

    /** Erreur de dessin de l'écran en verre : l'écran du mod pour le reste de la session. */
    static void panne() {
        enPanne = true;
    }
}

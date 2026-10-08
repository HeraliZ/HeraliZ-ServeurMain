package fr.heralisz.economie.client;

import fr.heralisz.economie.HeraliszEconomie;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Point d'entree client (bus MOD) : enregistrement du calque HUD du solde. L'etat client est dans
 * {@link ClientSolde}, le dessin dans {@link SoldeHud}, la deconnexion dans {@link ClientEvents}.
 */
@Mod.EventBusSubscriber(modid = HeraliszEconomie.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EconomieClient {
    private EconomieClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent e) {
        HeraliszEconomie.LOG.info("[economie] client pret (HUD du solde)");
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("solde", (gui, g, partialTick, largeur, hauteur) -> SoldeHud.render(g, largeur, hauteur));
    }
}

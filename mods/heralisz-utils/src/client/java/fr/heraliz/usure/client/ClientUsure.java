package fr.heraliz.usure.client;

import fr.heraliz.usure.HeralizUsure;
import fr.heraliz.usure.client.ecran.EcranConfig;
import fr.heraliz.usure.client.ecran.EcranReglages;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Mise en place cote client : petit indicateur de l'arme en main en bas a gauche (HUD), infobulles, tirs du joueur local,
 * ecrans. Plus aucune barre sur l'icone des armes (inventaires, barre d'objets, barre DayZ) : l'etat se lit dans
 * l'indicateur et dans l'infobulle.
 */
public final class ClientUsure {
    private ClientUsure() {
    }

    public static void init(IEventBus modBus) {
        HeralizUsure.reglesClient(ReglesClient::get);
        modBus.addListener(ClientUsure::calques);
        IEventBus forge = MinecraftForge.EVENT_BUS;
        ClientTirs.enregistrer(forge);
        forge.addListener((ItemTooltipEvent e) -> Infobulle.onTooltip(e));
        forge.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ReglesClient.set(null));
        forge.addListener((TagsUpdatedEvent e) -> ReglesClient.get().viderCache());
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new EcranReglages(parent)));
    }

    /** Indicateur de l'etat de l'arme en main, en bas a gauche ({@link HudUsure}). */
    private static void calques(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("etat_arme", HudUsure::render);
    }

    /** Regles recues du serveur ; {@code ouvrirEcran} : reponse a {@code /usure config}. */
    public static void recevoirRegles(Regles r, boolean ouvrirEcran) {
        ReglesClient.set(r);
        if (ouvrirEcran) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new EcranConfig(r.copie()));
        }
    }
}

package fr.heraliz.usure.client;

import com.tacz.guns.api.item.IGun;
import fr.heraliz.usure.HeralizUsure;
import fr.heraliz.usure.client.ecran.EcranConfig;
import fr.heraliz.usure.client.ecran.EcranReglages;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

/** Mise en place cote client : barre sur les objets, HUD, infobulles, touches et tirs du joueur local, ecrans. */
public final class ClientUsure {
    private ClientUsure() {
    }

    public static void init(IEventBus modBus) {
        HeralizUsure.reglesClient(ReglesClient::get);
        modBus.addListener(ClientUsure::decorations);
        modBus.addListener(ClientUsure::calques);
        IEventBus forge = MinecraftForge.EVENT_BUS;
        ClientTirs.enregistrer(forge);
        forge.addListener((ItemTooltipEvent e) -> Infobulle.onTooltip(e));
        forge.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ReglesClient.set(null));
        forge.addListener((TagsUpdatedEvent e) -> ReglesClient.get().viderCache());
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new EcranReglages(parent)));
    }

    /** La barre d'usure sur toutes les armes TaCZ (objet « modern_kinetic_gun » et autres armes enregistrees). */
    private static void decorations(RegisterItemDecorationsEvent e) {
        int n = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof IGun) {
                e.register(item, BarreUsure.INSTANCE);
                n++;
            }
        }
        HeralizUsure.LOG.info("[usure] barre d'usure posee sur {} objet(s) d'arme", n);
    }

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

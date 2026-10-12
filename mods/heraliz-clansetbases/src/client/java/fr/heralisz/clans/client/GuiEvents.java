package fr.heralisz.clans.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.hud.ClanHud;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Evenements client (bus Forge) : derriere nos ecrans, le HUD et la main du joueur sont caches (seul le monde
 * assombri reste visible) ; la touche K ouvre l'ecran ; l'etat est vide a la deconnexion.
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, value = Dist.CLIENT)
public final class GuiEvents {
    private GuiEvents() {
    }

    /** Vrai si l'ecran courant est un des notres (ClanScreen, ClanCreateScreen, AtelierScreen, FlagPanelScreen). */
    public static boolean ours() {
        return ClansClient.ecranANous(Minecraft.getInstance().screen);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (ours()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (ours()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (ClansKeys.OUVRIR.consumeClick()) ClansClient.ouvrirDepuisTouche();
    }

    /**
     * Envois groupes des permissions modifiees a l'ecran (ils doivent partir meme si l'ecran vient d'etre ferme) :
     * une verification de liste vide quand rien n'attend.
     */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) PermsLocales.tick();
    }

    /** Deconnexion : etat, toasts et textures de bannieres oublies (elles se recreent a la demande). */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientClans.vider();
        PermsLocales.vider();
        ClanHud.vider();
        BannerArt.vider();
        fr.heralisz.clans.api.CarteApi.vider();
    }
}

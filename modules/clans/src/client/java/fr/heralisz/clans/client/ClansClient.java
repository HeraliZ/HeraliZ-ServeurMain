package fr.heralisz.clans.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.AtelierScreen;
import fr.heralisz.clans.client.gui.ClanCreateScreen;
import fr.heralisz.clans.client.gui.ClanScreen;
import fr.heralisz.clans.client.gui.FlagPanelScreen;
import fr.heralisz.clans.client.gui.PermissionsScreen;
import fr.heralisz.clans.client.gui.hud.ClanHud;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.ZoneVue;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Point d'entree client : enregistrement de la touche, ouverture des ecrans par nom, toasts du HUD. Les evenements de
 * jeu (touche, HUD masque) sont dans {@link GuiEvents} ; les rendus des blocs et entites de territoire sont enregistres
 * par {@code fr.heralisz.clans.territory.client.TerritoireClient}.
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClansClient {
    private ClansClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent e) {
        HeraliszClans.LOG.info("[clans] client pret (touche K = clan)");
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(ClansKeys.OUVRIR);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent e) {
        // atlas des formes relu sur le fil d'arriere-plan du rechargement, puis textures de bannieres videes
        e.registerReloadListener(fr.heralisz.clans.client.banniere.AtlasSdf.RECHARGEMENT);
    }

    // ------------------------------------------------------------------ ecrans
    /**
     * Ouvre un ecran par nom : "clan" (principal, ou sans clan : invitations / fondation), "creation" (fondation),
     * "banniere" (atelier de banniere depuis Parametres), "atelier" (atelier depuis l'ecran de fondation ouvert, sinon
     * comme "banniere"), "drapeau" (panneau du drapeau, avec {@link ClientClans#panneauDrapeau()}), "permissions" (onglet
     * Permissions), "permissions_joueurs" / "permissions_base" (editeur par joueur autonome, voir {@link ClansUi}).
     */
    public static void ouvrir(String ecran) {
        Minecraft mc = Minecraft.getInstance();
        switch (ecran) {
            case "clan" -> {
                if (!(mc.screen instanceof ClanScreen)) {
                    Ui.son(Sons.UI_OUVRIR, 0.7f);
                    mc.setScreen(new ClanScreen());
                }
            }
            case "creation" -> {
                Ui.son(Sons.UI_OUVRIR, 0.7f);
                mc.setScreen(ClanCreateScreen.pourFondation(mc.screen));
            }
            case "banniere" -> {
                Ui.son(Sons.UI_OUVRIR, 0.7f);
                mc.setScreen(AtelierScreen.pourParametres(mc.screen));
            }
            case "atelier" -> {
                // depuis la fondation : l'atelier lui rendra la banniere ; sinon, comme « banniere » (Parametres)
                Ui.son(Sons.UI_OUVRIR, 0.7f);
                if (mc.screen instanceof ClanCreateScreen cs) mc.setScreen(AtelierScreen.pourCreation(cs, cs.banniere()));
                else mc.setScreen(AtelierScreen.pourParametres(mc.screen));
            }
            case "drapeau" -> {
                ZoneVue z = ClientClans.panneauDrapeau();
                if (z == null) {
                    HeraliszClans.LOG.warn("[clans] panneau du drapeau demande sans donnees de zone");
                    return;
                }
                Ui.son(Sons.UI_OUVRIR, 0.7f);
                mc.setScreen(new FlagPanelScreen(z));
            }
            case "permissions" -> {
                // onglet Permissions de l'ecran du clan (reglages par grade et par joueur)
                Ui.son(Sons.UI_OUVRIR, 0.7f);
                mc.setScreen(new ClanScreen(ClanScreen.Onglet.PERMISSIONS));
            }
            case "permissions_joueurs", "permissions_base" -> {
                if (!ClansUi.ouvrirPermissionsJoueurs(ecran.equals("permissions_base"))) {
                    HeraliszClans.LOG.warn("[clans] editeur des permissions demande sans clan");
                }
            }
            default -> HeraliszClans.LOG.warn("[clans] ecran inconnu : {}", ecran);
        }
    }

    /** Touche K : demande l'etat au serveur et ouvre l'ecran tout de suite (il se met a jour a la reponse). */
    public static void ouvrirDepuisTouche() {
        if (Minecraft.getInstance().getConnection() != null) ClansNet.toServer(new Packets.SimpleC2S(Packets.SimpleC2S.Genre.OUVRIR, "clan"));
        ouvrir("clan");
    }

    /** Toast HUD (pile de 3, 4 a 6 s, fondu). */
    public static void toast(Packets.NoticeS2C notice) {
        ClanHud.toast(notice);
    }

    /** Vrai si l'ecran courant est l'un des notres (HUD et main masques derriere). */
    public static boolean ecranANous(Screen s) {
        return s instanceof ClanScreen || s instanceof ClanCreateScreen || s instanceof AtelierScreen || s instanceof FlagPanelScreen
                || s instanceof PermissionsScreen;
    }
}

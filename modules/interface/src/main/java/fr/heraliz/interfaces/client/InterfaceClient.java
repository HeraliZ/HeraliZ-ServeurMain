package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.InterfaceConfig;
import fr.heraliz.interfaces.kit.Flou;
import fr.heraliz.interfaces.kit.Ui;
import com.mojang.realmsclient.RealmsMainScreen;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Branchements du client. Le menu Echap vanilla est remplace par {@link EcranPause} seulement quand :
 * <ul>
 *     <li>l'ecran qui s'ouvre est EXACTEMENT {@link PauseScreen} (un mod qui ouvre sa propre sous-classe garde la
 *     sienne) et qu'aucun autre mod n'a deja annule ou remplace l'ouverture (priorite basse, evenement annule ignore) ;</li>
 *     <li>c'est le vrai menu (titre « menu.game ») et non la pause sans menu de F3+Echap (titre « menu.paused ») ;</li>
 *     <li>un monde est charge, le menu est actif dans la config et notre ecran n'a pas plante dans cette session
 *     (sinon : menu vanilla jusqu'au redemarrage, voir {@link #secours}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class InterfaceClient {
    private InterfaceClient() {
    }

    private static boolean enPanne, titreEnPanne;

    /**
     * Ecran titre HeraliZ ({@link EcranTitre}) a la place de celui de Minecraft, de la liste des serveurs, des mondes solo
     * et de Realms : on ne joue que sur HeraliZ. Config « ecran_titre.actif = false » : ecrans vanilla (essais en solo).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void ecranTitre(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s == null || titreEnPanne || !InterfaceConfig.oui(InterfaceConfig.TITRE_ACTIF, true)) return;
        if (s.getClass() == TitleScreen.class || s instanceof JoinMultiplayerScreen || s instanceof SelectWorldScreen
                || s instanceof RealmsMainScreen) {
            e.setNewScreen(new EcranTitre());
        }
    }

    /**
     * Notre ecran titre a plante (construction ou rendu) : journal, ecran titre de Minecraft au tick suivant et pour le
     * reste de la session (jamais de jeu bloque au demarrage).
     */
    public static void titreEnPanne(Throwable t) {
        if (titreEnPanne) return;
        titreEnPanne = true;
        HeralizInterface.LOG.error("[interface] ecran titre HeraliZ en panne, retour a celui de Minecraft pour cette session", t);
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new TitleScreen()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void ouverture(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s == null || s.getClass() != PauseScreen.class || enPanne) return;
        if (!InterfaceConfig.oui(InterfaceConfig.MENU_ACTIF, true) || !menuComplet(s)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        e.setNewScreen(new EcranPause());
    }

    private static boolean graphismesEnPanne;

    /**
     * Ecran Video ouvert depuis les Options (celui de Minecraft ou celui d'Embeddium) : remplace par {@link EcranGraphismes}
     * (les trois profils en haut) ; son bouton « Reglages detailles » rouvre l'ecran d'origine. Ouvert d'ailleurs (depuis
     * notre ecran, ou par un autre mod), l'ecran Video n'est pas touche.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void video(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s == null || graphismesEnPanne || !(e.getCurrentScreen() instanceof OptionsScreen options) || !estVideo(s)) return;
        e.setNewScreen(new EcranGraphismes(options, s));
    }

    private static boolean estVideo(Screen s) {
        if (s instanceof VideoSettingsScreen) return true;
        String n = s.getClass().getName();
        return n.equals("me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI")
                || n.equals("org.embeddedt.embeddium.gui.EmbeddiumVideoOptionsScreen");
    }

    /** L'ecran Graphismes a plante : journal, ecran Video d'origine pour le reste de la session. */
    static void graphismesEnPanne(Throwable t) {
        if (graphismesEnPanne) return;
        graphismesEnPanne = true;
        HeralizInterface.LOG.error("[interface] ecran Graphismes en panne, ecran Video d'origine pour cette session", t);
    }

    /** Vrai menu de pause (avec boutons) : titre « menu.game » ; F3+Echap ouvre la pause sans menu (« menu.paused »). */
    public static boolean menuComplet(Screen s) {
        return s.getTitle().getContents() instanceof TranslatableContents tc && "menu.game".equals(tc.getKey());
    }

    /** Boutons du menu Echap vanilla retires (serveur multi) : Succes, Statistiques, Ouvrir au LAN. */
    private static final Set<String> RETIRES = Set.of("gui.advancements", "gui.stats", "menu.shareToLan");

    /** Menu Echap vanilla (secours, voir {@link #secours}) : sans les boutons de {@link #RETIRES}. */
    @SubscribeEvent
    public static void menuVanilla(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof PauseScreen)) return;
        for (GuiEventListener l : e.getListenersList()) {
            if (l instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents tc
                    && RETIRES.contains(tc.getKey())) {
                w.visible = false;
                w.active = false;
            }
        }
    }

    /** Contour du bloc vise (blocs, decors, props) : cache hors du mode creatif, pour l'immersion. */
    @SubscribeEvent
    public static void contourBloc(RenderHighlightEvent.Block e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isCreative()) e.setCanceled(true);
    }

    /**
     * Vue a la 1re personne en survie (facon DayZ) : la touche F5 ne marche qu'en creatif et en spectateur (staff) ; en
     * survie, elle ramene seulement a la 1re personne si la vue avait change. Les cinematiques des autres mods (execution,
     * badge, scenes) changent la camera elles-memes et la remettent apres : elles ne sont pas concernees. Pendant une emote
     * (HeraliZ Emote, {@code fr.heraliz.emote.client.CameraEmote.actif()} par reflexion), F5 marche : le joueur se voit
     * danser ; l'emote le remet a la 1re personne a la fin (et le serveur lui interdit les armes 10 s).
     */
    @SubscribeEvent
    public static void vue(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;
        if (enEmote()) return;
        boolean appui = false;
        while (mc.options.keyTogglePerspective.consumeClick()) {
            appui = true;
        }
        if (appui && mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static boolean emoteCherchee;
    private static java.lang.reflect.Method emoteActive;

    /** Vrai si le joueur joue une emote de HeraliZ Emote (faux sans ce mod). */
    static boolean enEmote() {
        if (!emoteCherchee) {
            emoteCherchee = true;
            try {
                emoteActive = Class.forName("fr.heraliz.emote.client.CameraEmote").getMethod("actif");
            } catch (ReflectiveOperationException | LinkageError ex) {
                emoteActive = null;
            }
        }
        if (emoteActive == null) return false;
        try {
            return Boolean.TRUE.equals(emoteActive.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean reglagesPoses;

    /** Premier tick du jeu : tutoriel de Minecraft coupé, réglages HeraliZ une fois par installation ({@link ReglagesDefaut}). */
    @SubscribeEvent
    public static void reglages(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || reglagesPoses) return;
        reglagesPoses = true;
        try {
            ReglagesDefaut.appliquer(Minecraft.getInstance());
        } catch (Throwable t) {
            HeralizInterface.LOG.warn("[interface] réglages HeraliZ non posés", t);
        }
    }

    /** Distance de vue des joueurs plafonnée (choix du propriétaire, 24/10 : 8 tronçons, comme le serveur). */
    static final int VUE_MAX = 8;

    /**
     * Une fois par seconde : distance de vue plafonnée à {@link #VUE_MAX} ; boîtes des entités (F3+B) seulement en créatif et
     * en spectateur (choix du propriétaire, 24/10 : en survie elles montrent les joueurs et les zombies à travers le décor).
     */
    @SubscribeEvent
    public static void reglagesSurveilles(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.player == null || mc.gameMode == null) return;
        if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes() && !mc.player.isCreative() && !mc.player.isSpectator()) {
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
        }
        if (mc.player.tickCount % 20 == 0 && mc.options.renderDistance().get() > VUE_MAX) {
            mc.options.renderDistance().set(VUE_MAX);
            mc.options.save();
        }
    }

    private static boolean touchesPosees;

    /**
     * Touches par défaut du serveur ({@link Touches}), à chaque lancement : au premier tick sans écran de chargement
     * (Forge relit les touches des mods dans options.txt à la fin du chargement) et en priorité la plus basse, après les
     * autres mods qui rangent leurs touches au même tick (véhicules de HeraliZ Utils).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void touches(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || touchesPosees) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.options == null) return;
        touchesPosees = true;
        try {
            Touches.appliquer(mc);
        } catch (Throwable t) {
            HeralizInterface.LOG.warn("[interface] touches HeraliZ non posées", t);
        }
    }

    /** Notre ecran a plante : journal, menu vanilla a la place et pour le reste de la session. */
    public static void secours(Throwable t) {
        enPanne = true;
        HeralizInterface.LOG.error("[interface] menu Echap HeraliZ en panne, retour au menu vanilla pour cette session", t);
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new PauseScreen(true));
    }

    /**
     * Rechargement des ressources (F3+T, pack de ressources, mipmaps changees) : programmes du flou et glyphes recrees au
     * besoin ; filtrage anisotrope de la grande texture des blocs repose ({@link Qualite#anisotrope()}). Les ecouteurs
     * des mods passent apres celui des modeles de Minecraft : la grande texture est deja refaite.
     */
    @Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Ressources {
        private Ressources() {
        }

        @SubscribeEvent
        public static void rechargement(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) rm -> {
                Flou.liberer();
                Ui.viderCache();
                Qualite.anisotrope();
            });
        }
    }
}

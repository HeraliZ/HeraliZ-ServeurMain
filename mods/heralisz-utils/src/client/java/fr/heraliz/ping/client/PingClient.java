package fr.heraliz.ping.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.heraliz.ping.HeralizPing;
import fr.heraliz.ping.PingType;
import fr.heraliz.ping.client.hud.Atlas;
import fr.heraliz.ping.client.hud.Lot;
import fr.heraliz.ping.client.hud.RenduPings;
import fr.heraliz.ping.client.hud.Roue;
import fr.heraliz.ping.net.Paquets;
import fr.heraliz.ping.net.PingNet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

/**
 * Cote client : enregistrements (touches, calques du HUD, shader, atlas, ecran de reglages) et evenements du jeu
 * (touche, matrices de la vue, curseur de la roue, clics pendant la roue, deconnexion). Les actions de ping sont ici :
 * {@link #pingRapide()} (appui bref) et {@link #ping(int)} (roue).
 */
public final class PingClient {
    private PingClient() {
    }

    /** Ecart minimal entre deux envois (le serveur limite de toute facon : on evite juste les paquets inutiles). */
    private static final long ECART_MS = 120;
    private static long dernierEnvoi;

    /** Construction du mod, client seulement : bouton « Config » de la liste des mods. */
    public static void construire() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new EcranReglages(parent)));
    }

    // ------------------------------------------------------------------ actions
    /** Appui bref : retire mon ping sous le viseur s'il y en a un, sinon ping « automatique » sur la cible. */
    public static void pingRapide() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        float cx = mc.getWindow().getGuiScaledWidth() * 0.5f, cy = mc.getWindow().getGuiScaledHeight() * 0.5f;
        int mien = PingsClient.monPingSousViseur(cx, cy, 14f);
        if (mien >= 0) {
            PingNet.versServeur(new Paquets.AnnulerC2S(mien));
            return;
        }
        envoyer(mc, PingType.AUTO);
    }

    /** Ping d'un type choisi dans la roue. */
    public static void ping(int type) {
        envoyer(Minecraft.getInstance(), type);
    }

    private static void envoyer(Minecraft mc, int type) {
        if (!PingsClient.actif || mc.player == null || mc.getConnection() == null) return;
        if (!PingNet.CANAL.isRemotePresent(mc.getConnection().getConnection())) return; // serveur sans le mod : rien a envoyer
        long maintenant = Util.getMillis();
        if (maintenant - dernierEnvoi < ECART_MS) return;
        Visee.Cible c = Visee.viser(mc, PingsClient.portee, mc.getFrameTime());
        if (c == null) return;
        dernierEnvoi = maintenant;
        PingNet.versServeur(new Paquets.PingC2S(type, c.entite(), c.bloc(), c.x(), c.y(), c.z()));
    }

    /** Banc d'essai : oublie l'ecart minimal entre deux envois. */
    public static void sansEcart() {
        dernierEnvoi = 0;
    }

    // ------------------------------------------------------------------ bus du mod
    @Mod.EventBusSubscriber(modid = HeralizPing.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Mod_ {
        private Mod_() {
        }

        @SubscribeEvent
        public static void touches(RegisterKeyMappingsEvent e) {
            e.register(PingTouches.PING);
            e.register(PingTouches.REGLAGES);
        }

        @SubscribeEvent
        public static void calques(RegisterGuiOverlaysEvent e) {
            // marqueurs sous la barre d'objets et le chat ; roue au-dessus de tout
            e.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "pings", (gui, g, pt, l, h) -> RenduPings.dessiner(g.pose().last().pose(), pt, l, h));
            e.registerAboveAll("roue", (gui, g, pt, l, h) -> Roue.dessiner(g.pose().last().pose(), l, h));
        }

        @SubscribeEvent
        public static void shaders(RegisterShadersEvent e) {
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(), HeralizPing.id("hud"), DefaultVertexFormat.POSITION_COLOR_TEX),
                        s -> Lot.shaderHud = s);
            } catch (IOException ex) {
                Lot.shaderHud = null;
                HeralizPing.LOG.warn("[ping] shader du HUD non charge, repli sur celui du jeu", ex);
            }
        }

        @SubscribeEvent
        public static void ressources(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) Atlas::charger);
        }
    }

    // ------------------------------------------------------------------ bus du jeu
    @Mod.EventBusSubscriber(modid = HeralizPing.MODID, value = Dist.CLIENT)
    public static final class Jeu {
        private Jeu() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) PingTouches.tick(Minecraft.getInstance());
        }

        /** Chaque image, avant le rendu : direction du curseur de la roue. */
        @SubscribeEvent
        public static void image(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.START && Roue.ouverte()) Roue.suivreCurseur();
        }

        /**
         * Fin du rendu du monde : matrices pour projeter les pings a l'ecran. AFTER_WEATHER (dernier stade qui donne la
         * pile de VUE : a AFTER_LEVEL, Forge passe la pile de projection, les marqueurs seraient figes et inverses).
         */
        @SubscribeEvent
        public static void monde(RenderLevelStageEvent e) {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
                RenduPings.capturer(e.getPoseStack().last().pose(), e.getProjectionMatrix(), e.getCamera());
            }
        }

        /** Roue ouverte : clic gauche = valider, clic droit = annuler, autres clics ignores (pas de tir ni de coup). */
        @SubscribeEvent
        public static void clic(InputEvent.MouseButton.Pre e) {
            if (!Roue.ouverte() || PingTouches.estBoutonPing(e.getButton())) return;
            if (e.getAction() != GLFW.GLFW_PRESS) return; // les relachements passent (une touche ne reste pas enfoncee)
            e.setCanceled(true);
            if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) PingTouches.valider();
            else if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) PingTouches.annuler();
        }

        @SubscribeEvent
        public static void molette(InputEvent.MouseScrollingEvent e) {
            if (Roue.ouverte()) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
            Roue.fermer();
            PingsClient.vider();
            PingsClient.portee = 300;
            PingsClient.actif = true;
            RenduPings.oublierMatrices();
        }

        /** Reapparition, changement de dimension : les pings de l'ancien monde partent. */
        @SubscribeEvent
        public static void clone(ClientPlayerNetworkEvent.Clone e) {
            if (e.getOldPlayer().level() != e.getNewPlayer().level()) PingsClient.vider();
        }
    }
}

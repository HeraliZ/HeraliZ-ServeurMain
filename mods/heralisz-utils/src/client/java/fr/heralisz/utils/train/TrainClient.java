package fr.heralisz.utils.train;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le train côté client : état annoncé par le serveur (actif, décalage de l'horaire), temps de l'horaire (temps du
 * monde + fraction de l'image), et rendu à trois étapes de l'image : la voie avec les blocs « cutout » (même état que
 * les chunks), la rame après les entités (opaque, lumière de la cabine), les vitres après les blocs translucides.
 */
@OnlyIn(Dist.CLIENT)
public final class TrainClient {
    static boolean actif;
    static long decalage;
    static ShaderInstance shTrain;
    /** Temps de l'horaire au début du tick client en cours : les voyageurs sont placés à cet instant, et la rame est
     *  dessinée à tTick - (1 - fraction) / 20, l'instant où le moteur interpole leur position (aucun décalage). */
    static double tTick;

    private TrainClient() {}

    /** Paquets du canal heralisz_utils:train reçus du serveur (branché par client.PartieClient). */
    public static void recevoir(Object m) {
        if (m instanceof Train.Annonce a) {
            if (!a.reseau().equals(Reseau.nom())) {                       // autre réseau : voie et rame à recalculer
                Reseau.choisir(a.reseau());
                Minecraft.getInstance().execute(() -> {
                    VoieRendu.vider();
                    RameRendu.vider();
                });
            }
            annonce(a.actif(), a.decalage());
        } else if (m instanceof Train.Recadrer r) {
            recadrage(r.vehicule(), r.lx(), r.lz());
        }
    }

    static void annonce(boolean a, long d) {
        actif = a;
        decalage = d;
        HeraliszUtils.LOG.info("Train : {} (décalage {} s)", a ? "en service" : "absent", d / 20);
    }

    public static boolean actif() {
        return actif && Horaire.get() != null && Minecraft.getInstance().level != null;
    }

    /** Secondes de l'horaire pour l'image en cours. */
    public static double temps(float partial) {
        return tTick - (1.0 - partial) / 20.0;
    }

    static boolean estMoi(net.minecraft.world.entity.Entity e) {
        return e == Minecraft.getInstance().player;
    }

    /** Entrées du joueur à bord : {gauche, avant, vitesse en blocs par tick}. */
    static float[] entree() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.screen != null) return new float[]{0, 0, 0};
        double v = mc.options.keySprint.isDown() ? Voyageurs.COURSE : Voyageurs.MARCHE;
        if (p.input.shiftKeyDown) v *= 0.35;
        return new float[]{p.input.leftImpulse, p.input.forwardImpulse, (float) v};
    }

    static void envoyer(int v, float x, float z, boolean assis) {
        Train.CANAL.sendToServer(new Train.Marche(v, x, z, assis));
    }

    private static long derniereDescente;

    /** Demande au serveur de descendre du train (Maj) ; une fois par demi-seconde au plus. En marche : seulement le
     *  message (le serveur refuse aussi). */
    static void descendre() {
        long t = System.currentTimeMillis();
        if (t - derniereDescente < 500) return;
        derniereDescente = t;
        Horaire h = Horaire.get();
        Minecraft mc0 = Minecraft.getInstance();
        int rame = mc0.player != null && mc0.player.getVehicle() instanceof Voyageur vy ? Horaire.rame(vy.vehicule()) : 0;
        if (h != null && h.etatRame(tTick, rame).aQuai() < 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_utils.train.en_marche"), false);
            }
            return;
        }
        Train.CANAL.sendToServer(new Train.Descente());
    }

    /** Rappel des touches dans la barre d'action : à bord (Maj descend, devant une banquette : s'asseoir) ou assis. */
    static void aide(boolean assis) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var saut = mc.options.keyJump.getTranslatedKeyMessage();
        var maj = mc.options.keyShift.getTranslatedKeyMessage();
        mc.gui.setOverlayMessage(assis ? net.minecraft.network.chat.Component.translatable("msg.heralisz_utils.train.assis", saut, maj)
                : net.minecraft.network.chat.Component.translatable("msg.heralisz_utils.train.a_bord", maj), false);
    }

    /** Touches : saut (se lever), accroupi (s'asseoir). */
    static boolean saut() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.options.keyJump.isDown();
    }

    static boolean accroupi() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.options.keyShift.isDown();
    }

    static void recadrage(int veh, float x, float z) {
        for (Voyageur v : Voyageurs.CLIENT) {
            if (estMoi(v.getFirstPassenger())) {
                v.pv = veh;
                v.px = x;
                v.pz = z;
            }
        }
    }

    /** Bus MOD : shader de la rame. */
    @Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void rendus(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(Train.VOYAGEUR.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
            e.registerBlockEntityRenderer(Train.TABLEAU_BE.get(), TableauRenderer::new);
        }

        @SubscribeEvent
        public static void shaders(RegisterShadersEvent e) {
            PlateauRendu.vider();                                     // l'atlas des blocs a pu changer
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(HeraliszUtils.MODID, "train"),
                        DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL), s -> shTrain = s);
            } catch (Exception ex) {
                HeraliszUtils.LOG.error("Shader du train illisible : pas de rendu du train", ex);
                shTrain = null;
            }
        }
    }

    /** Bus FORGE : rendu, déconnexion. */
    @Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Evenements {
        private Evenements() {}

        @SubscribeEvent
        public static void rendu(RenderLevelStageEvent e) {
            RenderLevelStageEvent.Stage st = e.getStage();
            Vec3 cam = e.getCamera().getPosition();
            // plateau de la cinématique et épave du refuge : même sans train en service sur ce monde
            if (shTrain != null && (st == RenderLevelStageEvent.Stage.AFTER_ENTITIES || st == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)) {
                boolean vitres = st == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
                long d0 = System.nanoTime();
                try {
                    if (vitres) {                                      // sang et lambeaux collés dehors sur les vitres
                        fr.heralisz.utils.cine.CineActeurs.decors(e.getPoseStack(), cam, e.getPartialTick());
                    }
                    PlateauRendu.dessiner(e.getPoseStack().last().pose(), e.getProjectionMatrix(), cam, e.getPartialTick(), vitres);
                    if (!vitres) {                                     // goules et corps du joueur
                        fr.heralisz.utils.cine.CineActeurs.opaque(e.getPoseStack(), cam, e.getPartialTick());
                    }
                    PlateauRendu.dessinerEpave(e.getPoseStack().last().pose(), e.getProjectionMatrix(), cam, vitres);
                    fr.heralisz.utils.cine.Cinematique.rendus(System.nanoTime() - d0);
                } catch (Exception ex) {
                    HeraliszUtils.LOG.error("Plateau de la cinématique en panne", ex);
                    PlateauRendu.visible = false;
                }
            }
            if (!actif()) return;
            try {
                if (st == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                    VoieRendu.dessiner(e.getPoseStack(), e.getProjectionMatrix(), cam, e.getFrustum());
                } else if (st == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                    RameRendu.dessiner(e.getPoseStack(), e.getProjectionMatrix(), cam, e.getFrustum(), e.getPartialTick(), false);
                } else if (st == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                    RameRendu.dessiner(e.getPoseStack(), e.getProjectionMatrix(), cam, e.getFrustum(), e.getPartialTick(), true);
                }
            } catch (Exception ex) {
                HeraliszUtils.LOG.error("Rendu du train en panne : désactivé jusqu'à la prochaine connexion", ex);
                actif = false;
            }
        }

        /** Nom unique dans le paquet : le bus d'événements de Forge nomme ses adaptateurs d'après paquet + classe simple +
         *  méthode + événement ; un autre « Evenements.tick(ClientTickEvent) » du paquet (TrainSonsClient) prenait sa place
         *  et l'horaire du client restait figé à 0 (train immobile à l'écran). */
        @SubscribeEvent
        public static void horaire(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
            Minecraft mc = Minecraft.getInstance();
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.START && mc.level != null) {
                tTick = (mc.level.getGameTime() + decalage) / 20.0;
            }
        }

        @SubscribeEvent
        public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
            actif = false;
            decalage = 0;
            fr.heralisz.utils.cine.CineDecor.oublier();                // le décor de la cinématique de ce serveur
            Minecraft.getInstance().execute(() -> {
                VoieRendu.vider();
                RameRendu.vider();
                PlateauRendu.vider();
            });
        }
    }
}

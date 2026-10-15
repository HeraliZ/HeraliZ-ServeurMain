package fr.heralisz.matchs.duels.killcam.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.dev.MatchsHarness;
import fr.heralisz.matchs.duels.killcam.Clip;
import fr.heralisz.matchs.duels.killcam.Killcam;
import fr.heralisz.matchs.duels.killcam.PaquetsKillcam;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cote client de la killcam : reception d'un clip, lecture ({@link Lecteur}), camera, rendu des joueurs factices et des
 * traceurs, bandeau ({@link KillcamHud}), touche pour passer, retour propre de la camera et du HUD.
 * <p>Pendant une lecture : la camera est une entite factice placee dans les yeux du tueur, du debut a la fin (son
 * modele n'est pas dessine ; mode premiere personne force ; a la place de la main du joueur, l'arme du tueur en
 * premiere personne, {@link ArmeEnMain}) ; les vrais joueurs (et les entites du clip) sont masques, seuls les faux
 * joueurs sont dessines ; tous les calques de HUD sont masques sauf le bandeau ; les clics d'attaque/utilisation sont
 * ignores. A la fin (ou au passage, ou sur ordre du serveur) : camera d'avant (ou le joueur), mode de camera d'avant,
 * rotation du joueur rendue s'il n'a pas ete teleporte, HUD rendu, voile de retour de {@link KillcamHud#RETOUR_MS}.
 * <p>Les HUD des autres modules qui ne passent pas par un calque Forge peuvent tester {@link #actif()}.
 */
@Mod.EventBusSubscriber(modid = HeraliszMatchs.MODID, value = Dist.CLIENT)
public final class KillcamClient {
    private KillcamClient() {
    }

    /** Identifiant du calque de HUD du bandeau. */
    public static final ResourceLocation CALQUE = HeraliszMatchs.id("killcam");

    /** Contexte de touche : active seulement pendant une killcam, n'entre en conflit avec aucune autre touche. */
    private static final IKeyConflictContext CONTEXTE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return lecteur != null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext autre) {
            return false;
        }
    };

    /** Touche « passer la killcam » (espace par defaut, reglable dans les controles). */
    public static final KeyMapping CLE_PASSER = new KeyMapping("key.heralisz_matchs.killcam_passer", CONTEXTE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, "key.categories.heralisz_matchs");

    @Nullable
    private static Lecteur lecteur;
    private static long retourMs;
    /** Etat a rendre a la fin. */
    @Nullable
    private static Entity cameraARendre;
    private static CameraType typeAvant = CameraType.FIRST_PERSON;
    private static ParticleStatus particulesAvant = ParticleStatus.ALL;
    private static float lacetAvant, tangageAvant;
    private static double xAvant, yAvant, zAvant;
    /** Entites du clip (bots) a masquer pendant la lecture. */
    private static final Set<UUID> MASQUES = new HashSet<>();

    // ------------------------------------------------------------------ etat
    /** Vrai pendant une killcam (les HUD des autres modules peuvent se masquer). */
    public static boolean actif() {
        return lecteur != null;
    }

    @Nullable
    static Lecteur lecteur() {
        return lecteur;
    }

    static long retourMs() {
        return retourMs;
    }

    // ------------------------------------------------------------------ reception
    /** Clip recu du serveur : lance la lecture (remplace une lecture en cours). */
    public static void recevoir(Clip clip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || clip.participants.isEmpty() || clip.images < 2) {
            prevenirServeur(false);
            return;
        }
        boolean remplace = lecteur != null;
        if (remplace) terminer(false, true, false);
        Lecteur l;
        try {
            l = new Lecteur(clip, mc.level, mc.player.getPose());
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[killcam] clip illisible", ex);
            prevenirServeur(false);
            return;
        }
        // etat a rendre (une lecture remplacee a deja tout rendu)
        cameraARendre = mc.getCameraEntity();
        typeAvant = mc.options.getCameraType();
        particulesAvant = mc.options.particles().get();
        lacetAvant = mc.player.getYRot();
        tangageAvant = mc.player.getXRot();
        xAvant = mc.player.getX();
        yAvant = mc.player.getY();
        zAvant = mc.player.getZ();
        MASQUES.clear();
        for (Clip.Participant p : clip.participants) MASQUES.add(p.uuid);
        KillcamHud.preparer(clip);
        lecteur = l;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        // les particules du monde reel (effets du joueur masque, tirs en cours ailleurs) n'ont rien a faire dans le rejeu
        mc.options.particles().set(ParticleStatus.MINIMAL);
        mc.setCameraEntity(l.camera);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(Killcam.SON_DEBUT.get(), 1f, 0.9f));
        Clip.Participant t = clip.participants.get(clip.tueur), v = clip.participants.get(clip.victime);
        HeraliszMatchs.LOG.info("[killcam] lecture : {} images (debut {}), {} participants, {} tirs, {} objets, tueur {} -> victime {}, "
                        + "{} m, raison {}, sons TaCZ {}", clip.images, l.debut, clip.participants.size(), clip.tirs.size(), clip.objets.size(),
                t.nom, v.nom, String.format(java.util.Locale.ROOT, "%.1f", clip.distance), clip.raison, SonsTir.tacz() ? "oui" : "non");
    }

    /** Ordre du serveur : arret immediat. */
    public static void arreterDepuisServeur() {
        if (lecteur != null) terminer(false, false, true);
    }

    /** Passe la killcam (touche ou banc). */
    public static void passer() {
        if (lecteur == null) return;
        terminer(true, true, true);
        // la touche (espace = saut) ne doit pas agir une fois la camera rendue
        KeyMapping.releaseAll();
    }

    /**
     * Fin de lecture : camera, mode de camera, rotation du joueur et HUD rendus.
     *
     * @param prevenir envoyer {@code FinC2S} au serveur
     * @param son      jouer le son de retour
     */
    private static void terminer(boolean passee, boolean prevenir, boolean son) {
        Lecteur l = lecteur;
        if (l == null) return;
        lecteur = null;
        MASQUES.clear();
        Minecraft mc = Minecraft.getInstance();
        Entity rendre = cameraARendre;
        if (rendre == null || rendre.isRemoved() || rendre.level() != mc.level || rendre == l.camera) rendre = mc.player;
        if (rendre != null) mc.setCameraEntity(rendre);
        mc.options.setCameraType(typeAvant);
        mc.options.particles().set(particulesAvant);
        if (mc.player != null) {
            double dx = mc.player.getX() - xAvant, dy = mc.player.getY() - yAvant, dz = mc.player.getZ() - zAvant;
            // la souris a tourne le joueur pendant la killcam : on rend sa visee, sauf s'il a ete deplace (reapparition)
            if (dx * dx + dy * dy + dz * dz < 2.25) {
                mc.player.setYRot(lacetAvant);
                mc.player.setXRot(tangageAvant);
                mc.player.yRotO = lacetAvant;
                mc.player.xRotO = tangageAvant;
                mc.player.setYHeadRot(lacetAvant);
            }
        }
        cameraARendre = null;
        l.liberer();
        retourMs = System.currentTimeMillis();
        if (son) mc.getSoundManager().play(SimpleSoundInstance.forUI(Killcam.SON_FIN.get(), 1f, 0.7f));
        if (prevenir) prevenirServeur(passee);
        HeraliszMatchs.LOG.info("[killcam] fin ({}) : camera rendue a {}, mode {}, particules {}", passee ? "passee" : prevenir ? "finie" : "interrompue",
                mc.getCameraEntity() == mc.player ? "joueur" : String.valueOf(mc.getCameraEntity()), mc.options.getCameraType(),
                mc.options.particles().get());
    }

    private static void prevenirServeur(boolean passee) {
        if (Minecraft.getInstance().getConnection() != null) MatchsNet.versServeur(new PaquetsKillcam.FinC2S(passee));
    }

    // ------------------------------------------------------------------ evenements
    @SubscribeEvent
    public static void avantImage(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || lecteur == null) return;
        Minecraft mc = Minecraft.getInstance();
        Lecteur l = lecteur;
        if (mc.level != l.niveau || mc.player == null) {
            terminer(false, false, false);
            return;
        }
        l.avancer();
        if (l.etat == Lecteur.Etat.FINI) {
            terminer(false, true, true);
            return;
        }
        // la camera reste la notre (un paquet de camera du serveur est garde pour la fin)
        Entity cam = mc.getCameraEntity();
        if (cam != l.camera) {
            if (cam != null && !(cam instanceof FauxJoueur)) cameraARendre = cam;
            mc.setCameraEntity(l.camera);
        }
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || lecteur == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lecteur.niveau || mc.player == null) terminer(false, false, false);
    }

    @SubscribeEvent
    public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        if (lecteur != null) terminer(false, false, false);
        retourMs = 0;
    }

    @SubscribeEvent
    public static void rendu(RenderLevelStageEvent e) {
        Lecteur l = lecteur;
        if (l == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
            MultiBufferSource.BufferSource tampons = mc.renderBuffers().bufferSource();
            Vec3 c = e.getCamera().getPosition();
            PoseStack pose = e.getPoseStack();
            for (int k = 0; k < l.faux.length; k++) {
                FauxJoueur f = l.faux[k];
                if (!f.visible) continue;
                // fin vue par les yeux du tueur : la camera est dans sa tete
                if (k == l.clip.tueur && l.tueurMasque()) continue;
                float pt = l.partiel(k);
                double x = l.pos(k, 0), y = l.pos(k, 1), z = l.pos(k, 2);
                try {
                    d.render(f, x - c.x, y - c.y, z - c.z, 0f, pt, pose, tampons, d.getPackedLightCoords(f, pt));
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[killcam] rendu du faux joueur {} impossible, masque", f.getGameProfile().getName(), ex);
                    f.visible = false;
                }
            }
            tampons.endBatch();
        } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            l.traceurs.dessiner(e.getPoseStack(), e.getCamera(), mc.renderBuffers().bufferSource(), l.horloge);
        } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && l.etat != Lecteur.Etat.FINI) {
            // arme du tueur en premiere personne, dessinee ici et non a l'evenement de la main : le joueur qui regarde
            // est le plus souvent mort, donc spectateur, et Minecraft ne dessine jamais la main d'un spectateur (ni
            // l'evenement RenderHandEvent) : jusqu'a Matchs 0.9.0, on ne voyait aucune arme
            ArmeEnMain.dessiner(l, e.getPartialTick());
        }
    }

    /** Zoom de la visee du tueur (champ de vision du monde seulement, pas celui de la main). */
    @SubscribeEvent
    public static void champ(ViewportEvent.ComputeFov e) {
        Lecteur l = lecteur;
        if (l == null || !e.usedConfiguredFov()) return;
        float v = l.visee();
        if (v > 0.001f) e.setFOV(e.getFOV() * (1.0 - 0.2 * v));
    }

    /** Pendant la lecture : vrais joueurs et entites du clip masques (seuls les faux joueurs sont dessines). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void masquer(RenderLivingEvent.Pre<?, ?> e) {
        if (lecteur == null || e.getEntity() instanceof FauxJoueur) return;
        if (e.getEntity() instanceof Player || MASQUES.contains(e.getEntity().getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void etiquette(RenderNameTagEvent e) {
        if (e.getEntity() instanceof FauxJoueur) e.setResult(Event.Result.DENY);
    }

    /**
     * Main du joueur qui regarde : jamais pendant la killcam (annule en premier : sinon TaCZ dessinerait SON arme). L'arme
     * du tueur est dessinee a part ({@link #rendu}, etape AFTER_LEVEL), meme quand le joueur est spectateur.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void main(RenderHandEvent e) {
        if (lecteur != null) e.setCanceled(true);
    }

    /** Tous les calques de HUD sauf le bandeau sont masques pendant la lecture. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void calques(RenderGuiOverlayEvent.Pre e) {
        if (lecteur != null && !CALQUE.equals(e.getOverlay().id())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void touche(InputEvent.Key e) {
        if (lecteur == null || e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        if (CLE_PASSER.isActiveAndMatches(InputConstants.getKey(e.getKey(), e.getScanCode()))) passer();
    }

    @SubscribeEvent
    public static void bouton(InputEvent.MouseButton.Pre e) {
        if (lecteur == null || e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        if (CLE_PASSER.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(e.getButton()))) {
            passer();
            e.setCanceled(true);
        }
    }

    /** Ni attaque, ni utilisation, ni choix de bloc pendant la lecture. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (lecteur == null) return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    /** Enregistrements du bus du mod : touche et calque de HUD. */
    @Mod.EventBusSubscriber(modid = HeraliszMatchs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Enregistrements {
        private Enregistrements() {
        }

        @SubscribeEvent
        public static void touches(RegisterKeyMappingsEvent e) {
            e.register(CLE_PASSER);
        }

        @SubscribeEvent
        public static void calques(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll(CALQUE.getPath(), KillcamHud::dessiner);
        }
    }

    // ------------------------------------------------------------------ banc d'essai
    /**
     * Etapes du banc : {"kcPasser": true} (comme la touche), {"kcEtat": "etiquette"} (journal : lecture, temps,
     * camera, mode, faux joueurs visibles, traceurs, calques).
     */
    public static void etapesBanc(MatchsHarness.Registre r) {
        r.ajouter("kcPasser", (mc, valeur, etape) -> passer());
        r.ajouter("kcEtat", (mc, valeur, etape) -> {
            Lecteur l = lecteur;
            String quoi = valeur.isJsonPrimitive() ? valeur.getAsString() : "";
            if (l == null) {
                HeraliszMatchs.LOG.info("[killcam] etat {} : inactive ; camera {} ; mode {} ; hud {}", quoi,
                        mc.getCameraEntity() == mc.player ? "joueur" : String.valueOf(mc.getCameraEntity()), mc.options.getCameraType(),
                        mc.options.hideGui ? "cache" : "visible");
                return;
            }
            int visibles = 0;
            for (FauxJoueur f : l.faux) if (f.visible) visibles++;
            HeraliszMatchs.LOG.info("[killcam] etat {} : {} temps {}/{} horloge {} ; camera {} {} {} (lacet {}, tangage {}) ; mode {} ; "
                            + "{} faux joueurs visibles ; {} traceurs ; victime deathTime {}", quoi, l.etat,
                    String.format(java.util.Locale.ROOT, "%.2f", l.temps), l.derniere, String.format(java.util.Locale.ROOT, "%.2f", l.horloge),
                    String.format(java.util.Locale.ROOT, "%.2f", l.camera.getX()), String.format(java.util.Locale.ROOT, "%.2f", l.camera.getEyeY()),
                    String.format(java.util.Locale.ROOT, "%.2f", l.camera.getZ()), String.format(java.util.Locale.ROOT, "%.1f", l.camera.getYRot()),
                    String.format(java.util.Locale.ROOT, "%.1f", l.camera.getXRot()), mc.options.getCameraType(), visibles, l.traceurs.actifs(),
                    l.faux[l.clip.victime].deathTime);
        });
    }
}

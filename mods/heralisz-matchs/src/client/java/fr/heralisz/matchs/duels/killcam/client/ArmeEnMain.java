package fr.heralisz.matchs.duels.killcam.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Arme du tueur en premiere personne pendant la killcam (vue par ses yeux, comme il voyait).
 * <p>Dessinee a la fin du rendu du monde (etape AFTER_LEVEL, appelee par {@link KillcamClient}) avec la projection de la
 * main de Minecraft (champ de 70°, profondeur effacee), et PAS a l'evenement de la main : le joueur qui regarde est le
 * plus souvent mort, donc spectateur, et Minecraft ne dessine jamais la main d'un spectateur (Matchs 0.7.0 a 0.9.0 :
 * « on ne voit ni arme ni rien »).
 * <p>Arme de TaCZ : son propre rendu en premiere personne ({@code AnimateGeoItemRenderer.tryInit} puis
 * {@code renderFirstPerson}, appeles par reflexion : TaCZ n'est pas une dependance de compilation de Matchs ; verifies
 * sur TaCZ 1.1.8 : le rendu des armes, {@code GunItemRendererWrapper}, en herite). Visee du tueur (drapeau
 * {@code Clip.VISE}) : TaCZ place l'arme d'apres l'avancement de visee du joueur local
 * ({@code LocalPlayerDataHolder.clientAimingProgress} et {@code oldAimingProgress}, champs publics) ; on y met celui du
 * tueur le temps de ce rendu, puis on remet les valeurs d'avant. Chaque tir du tueur declenche l'animation de tir de
 * l'arme ({@code triggerAnimation(objet, "shoot")}) et un petit recul. Autre objet (ou TaCZ en panne) : rendu de
 * Minecraft dans la main droite, recentre en visee. Une erreur du rendu de TaCZ le coupe jusqu'au redemarrage du jeu.
 */
final class ArmeEnMain {
    private static final String RENDU_TACZ = "com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer";
    /** Champ de vision de la main (celui de Minecraft pour la main : 70°, sans le reglage du joueur). */
    private static final double CHAMP_MAIN = 70.0;

    private static boolean taczCherche, taczEnPanne, viseeCherchee, viseeEnPanne;
    private static Class<?> classeTacz;
    private static Method preparer, premierePersonne, declencher;
    @Nullable
    private static Method operateurLocal, donnees;
    @Nullable
    private static Field progres, progresAvant;
    /** Lecture en cours et tirs du tueur deja animes. */
    @Nullable
    private static Lecteur lecteurVu;
    private static int tirsAnimes;

    private ArmeEnMain() {
    }

    /** Dessine l'arme du tueur par-dessus le monde (fil de rendu, fin du rendu du monde). */
    static void dessiner(Lecteur l, float partiel) {
        ItemStack objet = l.objetTueur();
        if (l != lecteurVu) {
            lecteurVu = l;
            tirsAnimes = l.tirsTueur();
        }
        if (objet == null || objet.isEmpty()) {
            tirsAnimes = l.tirsTueur();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        if (joueur == null) return;
        Matrix4f projectionAvant = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting triAvant = RenderSystem.getVertexSorting();
        PoseStack vue = RenderSystem.getModelViewStack();
        vue.pushPose();
        vue.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(mc.gameRenderer.getProjectionMatrix(CHAMP_MAIN), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        MultiBufferSource.BufferSource tampons = mc.renderBuffers().bufferSource();
        try {
            int lumiere = mc.getEntityRenderDispatcher().getPackedLightCoords(l.camera, partiel);
            dessinerArme(mc, joueur, l, objet, new PoseStack(), tampons, lumiere, partiel);
            tampons.endBatch();
        } catch (RuntimeException ex) {
            tampons.endBatch();
            HeraliszMatchs.LOG.debug("[killcam] arme en premiere personne non dessinee", ex);
        } finally {
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            vue.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projectionAvant, triAvant);
        }
    }

    private static void dessinerArme(Minecraft mc, LocalPlayer joueur, Lecteur l, ItemStack objet, PoseStack pose, MultiBufferSource tampons,
                                     int lumiere, float partiel) {
        float vise = l.visee(), recul = l.reculTir();
        boolean nouveauTir = l.tirsTueur() > tirsAnimes;
        tirsAnimes = l.tirsTueur();
        BlockEntityWithoutLevelRenderer rendu = IClientItemExtensions.of(objet).getCustomRenderer();
        if (rendu != null && estTacz(rendu)) {
            pose.pushPose();
            Object donneesJoueur = null;
            float avant = 0f, avantVieux = 0f;
            try {
                // petit recul de l'arme a chaque tir (en plus de l'animation de tir de TaCZ)
                pose.translate(0, 0, 0.035f * recul);
                pose.mulPose(Axis.XP.rotationDegrees(1.6f * recul));
                donneesJoueur = donneesVisee(joueur);
                if (donneesJoueur != null) {
                    avant = progres.getFloat(donneesJoueur);
                    avantVieux = progresAvant.getFloat(null);
                    progres.setFloat(donneesJoueur, vise);
                    progresAvant.setFloat(null, vise);
                }
                preparer.invoke(rendu, objet, joueur, partiel);
                if (nouveauTir) declencher.invoke(rendu, objet, "shoot");
                premierePersonne.invoke(rendu, joueur, objet, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, tampons, lumiere, partiel);
                return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                taczEnPanne = true;
                HeraliszMatchs.LOG.warn("[killcam] arme de TaCZ en premiere personne impossible, rendu ordinaire jusqu'au redemarrage", ex);
            } finally {
                if (donneesJoueur != null) {
                    try {
                        progres.setFloat(donneesJoueur, avant);
                        progresAvant.setFloat(null, avantVieux);
                    } catch (ReflectiveOperationException | RuntimeException ex) {
                        viseeEnPanne = true;
                    }
                }
                pose.popPose();
            }
        }
        // objet ordinaire : place comme Minecraft tient un objet dans la main droite (sans balancement), recentre en visee
        pose.pushPose();
        try {
            pose.translate(Mth.lerp(vise, 0.56F, 0.0F), Mth.lerp(vise, -0.52F, -0.30F), Mth.lerp(vise, -0.72F, -0.62F) + 0.05F * recul);
            pose.mulPose(Axis.XP.rotationDegrees(4f * recul));
            mc.getEntityRenderDispatcher().getItemInHandRenderer()
                    .renderItem(joueur, objet, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, pose, tampons, lumiere);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.debug("[killcam] objet en main non dessine", ex);
        } finally {
            pose.popPose();
        }
    }

    /** Donnees de visee de TaCZ du joueur local ({@code LocalPlayerDataHolder}), ou null (introuvables : arme a la hanche). */
    @Nullable
    private static Object donneesVisee(LocalPlayer joueur) {
        if (viseeEnPanne) return null;
        try {
            if (!viseeCherchee) {
                viseeCherchee = true;
                ClassLoader cl = classeTacz.getClassLoader();
                Class<?> operateur = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator", false, cl);
                Class<?> donneesClasse = Class.forName("com.tacz.guns.client.gameplay.LocalPlayerDataHolder", false, cl);
                operateurLocal = operateur.getMethod("fromLocalPlayer", LocalPlayer.class);
                donnees = operateur.getMethod("getDataHolder");
                progres = donneesClasse.getField("clientAimingProgress");
                progresAvant = donneesClasse.getField("oldAimingProgress");
            }
            if (operateurLocal == null || donnees == null || progres == null || progresAvant == null) return null;
            Object op = operateurLocal.invoke(null, joueur);
            return op == null ? null : donnees.invoke(op);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            viseeEnPanne = true;
            HeraliszMatchs.LOG.info("[killcam] visee de TaCZ illisible : arme du tueur toujours a la hanche ({})", ex.toString());
            return null;
        }
    }

    /** Vrai si ce rendu est celui des armes de TaCZ et que ses methodes sont trouvees. */
    private static boolean estTacz(BlockEntityWithoutLevelRenderer rendu) {
        if (taczEnPanne) return false;
        if (!taczCherche) {
            taczCherche = true;
            try {
                classeTacz = Class.forName(RENDU_TACZ, false, rendu.getClass().getClassLoader());
                preparer = classeTacz.getMethod("tryInit", ItemStack.class, Player.class, float.class);
                declencher = classeTacz.getMethod("triggerAnimation", ItemStack.class, String.class);
                premierePersonne = classeTacz.getMethod("renderFirstPerson", LocalPlayer.class, ItemStack.class,
                        ItemDisplayContext.class, PoseStack.class, MultiBufferSource.class, int.class, float.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                taczEnPanne = true;
                HeraliszMatchs.LOG.info("[killcam] rendu des armes de TaCZ introuvable : armes dessinees comme des objets ordinaires");
                return false;
            }
        }
        return classeTacz.isInstance(rendu);
    }
}

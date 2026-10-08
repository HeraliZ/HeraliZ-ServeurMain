package fr.heralisz.tags.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.TagsConfig;
import fr.heralisz.tags.client.ClientTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rendu des tags poses (§2.3, TAGS_V2_SPEC §3) a AFTER_TRANSLUCENT_BLOCKS.
 * <ul>
 *   <li>Shader {@link PeintureShader} ({@code heralisz_tags:peinture}, format POSITION_COLOR_TEX_LIGHTMAP) : couleur
 *       × lightmap ({@code texelFetch(Sampler2, UV2 / 16)}) et brouillard comme {@code rendertype_text}, donc la
 *       lumiere cuite par le DecalBaker (uv2) est reellement visible (noir, torche, nuit) ; en plus « peint sur le
 *       mur » : porosite hachee sur floor(position monde × 16) (uniforme {@code Origine} = origine monde de la cellule
 *       des sommets, modulo 1024, televersee par lot / par tag), assombrissement leger, une seule lecture de texture, rejet a
 *       alpha &lt; 0,004 (le fondu va jusqu'au bout). Repli sur {@code rendertype_text} si le shader n'a pas charge
 *       (rendu v1 : rejet a alpha &lt; 0,1).</li>
 *   <li>Une seule passe par image : samplers (atlas mipmappe en unite 0, lightmap en unite 1), projection, brouillard
 *       et IViewRotMat poses puis {@code apply()} une fois. Les tags a alpha plein sont fondus par cellule de
 *       32 blocs en {@link DecalLot} (un appel de dessin par cellule, reconstruit seulement quand l'ensemble change) ;
 *       les tags en apparition / fondu sont dessines un par un (ModelViewMat + ColorModulator televerses, draw).
 *       Fin de passe : {@code unbind}, {@code clear()} et etat GL retabli. Pas de {@code drawWithShader} : il refait
 *       12 samplers, ~15 uniformes, apply() et clear() par appel (et alloue 24 objets).</li>
 *   <li>Alpha : apparition 0,25 s, plein jusqu'a duree−5 s, fondu jusqu'a duree, puis le tampon est ferme (purge).</li>
 * </ul>
 * Aucune allocation par image : liste indexee, matrice et enveloppe Integer de la lightmap reutilisees, bornes
 * precalculees a la cuisson ; les lots n'allouent qu'a leur reconstruction (evenement, pas image).
 */
@Mod.EventBusSubscriber(modid = HeraliszTags.MODID, value = Dist.CLIENT)
public final class DecalRenderer {
    private DecalRenderer() {
    }

    public static final double DISTANCE_RENDU = 48.0;
    private static final double DISTANCE_RENDU_2 = DISTANCE_RENDU * DISTANCE_RENDU;
    private static final Matrix4f MODELE = new Matrix4f();
    private static final Map<Long, DecalLot> LOTS_PAR_CLE = new HashMap<>();
    private static final ArrayList<DecalLot> LOTS = new ArrayList<>();
    private static int versionLots = -1;
    private static long dernierePurgeMs;
    /** Id GL de la lightmap et son enveloppe (setSampler prend un Object : on ne re-boxe que si l'id change). */
    private static int lightmapId = -1;
    private static Integer lightmapObj;
    /** Shader de la derniere image et son uniforme Origine (recherche seulement quand le shader change). */
    private static ShaderInstance shaderCourant;
    private static Uniform origine;
    private static boolean repliSignale;
    /** Derniere origine televersee (evite les envois redondants entre tags d'une meme cellule). */
    private static float origX = Float.NaN, origY, origZ;
    /** Compteurs de la derniere image, nombre d'images rendues et temps CPU cumule de la passe (banc d'essai). */
    public static int dessines, testes, lotsDessines, lotsTotal;
    public static long images, reconstructions, passeNs;
    /** Banc d'essai : forcer le rendu v1 ({@code rendertype_text}) pour comparer le cout du shader de peinture. */
    public static boolean forcerTexte;
    /** Banc d'essai : chronometrer la passe cote GPU (requetes GL_TIME_ELAPSED) ; cumul et nombre de mesures lues. */
    public static boolean mesureGpu;
    public static long gpuNs, gpuMesures;
    /** Plus petit temps GPU lu depuis la derniere remise a zero (le banc la remet a Long.MAX_VALUE a chaque mesure) :
     *  le cout sans concurrence d'autres programmes sur la carte graphique. */
    public static long gpuMinNs = Long.MAX_VALUE;
    private static final int[] REQUETES = new int[8];
    private static int requeteSuivante, requetesEnVol;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        images++;
        boolean gpu = mesureGpu && debutGpu();
        long debut = System.nanoTime();
        passe(e);
        passeNs += System.nanoTime() - debut;
        if (gpu) org.lwjgl.opengl.GL15.glEndQuery(org.lwjgl.opengl.GL33.GL_TIME_ELAPSED);
    }

    /**
     * Requetes de temps GPU en anneau (8) : lit sans attendre celles qui sont pretes, puis en ouvre une. Rend false si
     * l'anneau est plein (on saute la mesure plutot que de bloquer le pilote). Dev seulement (banc d'essai).
     */
    private static boolean debutGpu() {
        if (REQUETES[0] == 0) {
            for (int i = 0; i < REQUETES.length; i++) REQUETES[i] = org.lwjgl.opengl.GL15.glGenQueries();
        }
        while (requetesEnVol > 0) {
            int plusVieille = REQUETES[(requeteSuivante - requetesEnVol + REQUETES.length) % REQUETES.length];
            if (org.lwjgl.opengl.GL15.glGetQueryObjecti(plusVieille, org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE) == 0) break;
            long ns = org.lwjgl.opengl.GL33.glGetQueryObjecti64(plusVieille, org.lwjgl.opengl.GL15.GL_QUERY_RESULT);
            gpuNs += ns;
            if (ns < gpuMinNs) gpuMinNs = ns;
            gpuMesures++;
            requetesEnVol--;
        }
        if (requetesEnVol == REQUETES.length) return false;
        org.lwjgl.opengl.GL15.glBeginQuery(org.lwjgl.opengl.GL33.GL_TIME_ELAPSED, REQUETES[requeteSuivante]);
        requeteSuivante = (requeteSuivante + 1) % REQUETES.length;
        requetesEnVol++;
        return true;
    }

    private static void passe(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float duree = TagsConfig.DUREE_S.get();
        long now = System.currentTimeMillis();
        if (now - dernierePurgeMs > 500) {
            dernierePurgeMs = now;
            ClientTags.purger(duree);
        }
        List<ClientTags.TagActif> actifs = ClientTags.liste();
        dessines = 0;
        lotsDessines = 0;
        testes = actifs.size();
        if (actifs.isEmpty()) {
            if (!LOTS.isEmpty()) viderLots();
            return;
        }
        ShaderInstance shader = forcerTexte ? null : PeintureShader.obtenir();
        if (shader == null) {
            if (!repliSignale && !forcerTexte) {
                repliSignale = true;
                HeraliszTags.LOGGER.warn("[HeraliZ Tags] shader de peinture absent : decalques dessines avec rendertype_text");
            }
            shader = GameRenderer.getRendertypeTextShader();
            if (shader == null) return;
        }
        if (shader != shaderCourant) {
            shaderCourant = shader;
            origine = shader.getUniform(PeintureShader.ORIGINE);
        }

        // ---- alpha de chaque tag ; un tag qui entre ou sort de la phase « alpha plein » change les lots
        boolean rebatir = ClientTags.version() != versionLots;
        for (int i = 0, n = actifs.size(); i < n; i++) {
            ClientTags.TagActif t = actifs.get(i);
            if (!(t.rendu instanceof Decal d)) continue;
            d.alpha = alpha(t.age(now), duree);
            boolean plein = d.alpha >= 0.999f;
            if (plein != d.dansLot) {
                d.dansLot = plein;
                rebatir = true;
            }
        }
        if (rebatir) {
            reconstruireLots(actifs);
            versionLots = ClientTags.version();
        }

        Vec3 cam = e.getCamera().getPosition();
        Frustum frustum = e.getFrustum();
        PoseStack pose = e.getPoseStack();
        Matrix4f vue = pose.last().pose();

        // ---- une fois par image : textures, uniformes communs, etat GL, apply()
        AbstractTexture atlas = AtlasTexture.obtenir(mc);
        LightTexture lumiere = mc.gameRenderer.lightTexture();
        lumiere.turnOnLightLayer();
        int idLumiere = RenderSystem.getShaderTexture(2);
        if (idLumiere != lightmapId) {
            lightmapId = idLumiere;
            lightmapObj = idLumiere;
        }
        shader.setSampler("Sampler0", atlas);
        shader.setSampler("Sampler2", lightmapObj);
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(e.getProjectionMatrix());
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        Uniform modele = shader.MODEL_VIEW_MATRIX, couleur = shader.COLOR_MODULATOR;
        if (modele != null) modele.set(vue);
        if (couleur != null) couleur.set(1f, 1f, 1f, 1f);
        if (origine != null) origine.set(0f, 0f, 0f);
        origX = Float.NaN;
        float alphaCourant = 1f;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.polygonOffset(-1f, -2f);
        RenderSystem.enablePolygonOffset();
        shader.apply();
        boolean lie = false;

        // ---- lots (tags a alpha plein) : un dessin par cellule visible
        for (int i = 0, n = LOTS.size(); i < n; i++) {
            DecalLot lot = LOTS.get(i);
            if (lot.triangles == 0 || lot.bornes == null) continue;
            if (lot.bornes.distanceToSqr(cam) > DISTANCE_RENDU_2) continue;
            if (!frustum.isVisible(lot.bornes)) continue;
            MODELE.set(vue).translate((float) (lot.ox - cam.x), (float) (lot.oy - cam.y), (float) (lot.oz - cam.z));
            if (modele != null) {
                modele.set(MODELE);
                modele.upload();
            }
            televerserOrigine(lot.ox, lot.oy, lot.oz);
            lot.dessiner();
            lie = true;
            lotsDessines++;
            dessines += lot.decals.size();
        }

        // ---- tags en apparition / fondu : un dessin chacun, avec leur alpha
        for (int i = 0, n = actifs.size(); i < n; i++) {
            ClientTags.TagActif t = actifs.get(i);
            if (!(t.rendu instanceof Decal d) || d.dansLot || d.tampon == null) continue;
            if (d.alpha <= 0.002f) continue;
            double dx = d.x - cam.x, dy = d.y - cam.y, dz = d.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > DISTANCE_RENDU_2) continue;
            if (!frustum.isVisible(d.bornes)) continue;
            MODELE.set(vue).translate((float) (d.ox - cam.x), (float) (d.oy - cam.y), (float) (d.oz - cam.z));
            if (modele != null) {
                modele.set(MODELE);
                modele.upload();
            }
            televerserOrigine(d.ox, d.oy, d.oz);
            if (couleur != null && d.alpha != alphaCourant) {
                alphaCourant = d.alpha;
                couleur.set(1f, 1f, 1f, alphaCourant);
                couleur.upload();
            }
            d.tampon.bind();
            lie = true;
            d.tampon.draw();
            dessines++;
        }

        // ---- fin de passe : programme et textures delies, etat GL retabli
        if (lie) VertexBuffer.unbind();
        shader.clear();
        RenderSystem.disablePolygonOffset();
        RenderSystem.polygonOffset(0f, 0f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        lumiere.turnOffLightLayer();
    }

    /**
     * Origine monde des sommets suivants pour le shader de peinture : celle de la cellule de 32 blocs, ramenee modulo
     * 1024 (multiple de 1/16 conserve : la porosite reste alignee sur les pixels des blocs, et les flottants restent
     * precis loin du centre du monde). Televersee seulement si elle change ; rien en repli rendertype_text.
     */
    private static void televerserOrigine(double ox, double oy, double oz) {
        if (origine == null) return;
        float x = (float) Math.floorMod((long) Math.floor(ox), 1024L);
        float y = (float) Math.floorMod((long) Math.floor(oy), 1024L);
        float z = (float) Math.floorMod((long) Math.floor(oz), 1024L);
        if (x == origX && y == origY && z == origZ) return;
        origX = x;
        origY = y;
        origZ = z;
        origine.set(x, y, z);
        origine.upload();
    }

    /** Alpha d'un tag selon son age : apparition 0,25 s, fondu sur les 5 dernieres secondes. */
    public static float alpha(float ageS, float dureeS) {
        if (ageS < 0) return 0;
        if (ageS < 0.25f) return ageS / 0.25f;
        float fondu = dureeS - 5f;
        if (ageS <= fondu) return 1f;
        if (ageS >= dureeS) return 0f;
        return 1f - (ageS - fondu) / 5f;
    }

    /**
     * Repartit les tags a alpha plein par cellule et reconstruit chaque lot (thread de rendu). Appele seulement quand
     * l'ensemble change : les cellules devenues vides sont fermees.
     */
    private static void reconstruireLots(List<ClientTags.TagActif> actifs) {
        reconstructions++;
        for (int i = 0; i < LOTS.size(); i++) LOTS.get(i).decals.clear();
        for (int i = 0, n = actifs.size(); i < n; i++) {
            if (!(actifs.get(i).rendu instanceof Decal d) || !d.dansLot || d.sommets == null) continue;
            DecalLot lot = LOTS_PAR_CLE.get(d.cle);
            if (lot == null) {
                lot = new DecalLot(d.cle, d.ox, d.oy, d.oz);
                LOTS_PAR_CLE.put(d.cle, lot);
                LOTS.add(lot);
            }
            lot.decals.add(d);
        }
        for (int i = LOTS.size() - 1; i >= 0; i--) {
            DecalLot lot = LOTS.get(i);
            if (lot.decals.isEmpty()) {
                LOTS_PAR_CLE.remove(lot.cle);
                LOTS.remove(i);
                lot.close();
            } else {
                lot.reconstruire();
            }
        }
        lotsTotal = LOTS.size();
    }

    private static void viderLots() {
        for (int i = 0; i < LOTS.size(); i++) LOTS.get(i).close();
        LOTS.clear();
        LOTS_PAR_CLE.clear();
        lotsTotal = 0;
        versionLots = -1;
    }

    /**
     * Rechargement des ressources : les tampons sont recuits pour refleter d'eventuels changements de formes /
     * lumiere (l'atlas mipmappe est recharge par le TextureManager lui-meme). Un tag dont la cuisson echoue est
     * simplement sans rendu : il ne casse ni le rechargement ni les autres. A appeler sur le thread de rendu.
     */
    public static void rechargement() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        List<ClientTags.TagActif> actifs = ClientTags.liste();
        for (int i = 0; i < actifs.size(); i++) {
            ClientTags.TagActif t = actifs.get(i);
            if (t.rendu instanceof Decal d) d.close();
            t.rendu = null;
            try {
                t.rendu = DecalBaker.cuire(mc.level, t.paquet);
            } catch (Exception ex) {
                HeraliszTags.LOGGER.warn("[HeraliZ Tags] recuisson du tag #{} impossible", t.id(), ex);
            }
        }
        ClientTags.modifie();
    }
}

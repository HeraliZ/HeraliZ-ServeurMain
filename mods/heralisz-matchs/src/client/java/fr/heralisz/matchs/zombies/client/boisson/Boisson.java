package fr.heralisz.matchs.zombies.client.boisson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.client.rendu.Geo;
import fr.heralisz.matchs.zombies.client.rendu.ModeleZb;
import fr.heralisz.matchs.zombies.client.rendu.ModelesZb;
import fr.heralisz.matchs.zombies.contenu.ZbSons;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

/**
 * Animation de boisson en 1re personne a l'achat d'un atout (ZOMBIES_V2_SPEC §3), declenchee par l'evenement du jeu
 * {@code EvenementsHud.atout} pour le joueur local : l'arme disparait, la main droite remonte avec la bouteille de
 * l'atout (modele {@code bouteille}, texture {@code bouteille_<id>}), le pouce fait sauter la capsule, la bouteille est
 * portee a la bouche (trois gorgees), puis jetee hors champ ; sons de decapsulage, gorgees, verre et rot (1,6 s).
 * Pendant ce temps les clics sont ignores (pas de tir ni de visee). Le son « atout bu » du serveur est etouffe pour le
 * buveur (voir {@link #etoufferSonServeur}). Dessin sans allocation (matrices et vecteurs reutilises).
 */
@Mod.EventBusSubscriber(modid = HeraliszMatchs.MODID, value = Dist.CLIENT)
public final class Boisson {
    private Boisson() {
    }

    public static final int DUREE_MS = 1600;
    private static final long JAMAIS = Long.MIN_VALUE / 4;
    /** Echelle de la bouteille (modele de 13 px) en 1re personne. */
    private static final float ECHELLE = 0.55F;
    /** Echelle du bras : 1 px de peau = 0,04 bloc. */
    private static final float BRAS = 0.040F;
    /** Point de prise (pixels du modele de bouteille) et main : a droite de la bouteille, a mi-hauteur. */
    private static final float PRISE_Y = 4.5F, MAIN_X = 2.1F, MAIN_Y = 4.2F, MAIN_Z = 0.4F;
    /** Epaule (repere de la camera, hors champ en bas a droite). */
    private static final float EPAULE_X = 0.66F, EPAULE_Y = -1.12F, EPAULE_Z = -0.10F;
    /** Instants des sons (ms) : capsule, gorgees, verre, rot. */
    private static final int T_CAPSULE = 300, T_GORGEE = 540, T_VERRE = 1400, T_ROT = 1470;

    /** Cles : temps (ms), position de la prise (x, y, z), bascule (degres, haut vers la camera), lacet, roulis. */
    private static final float[][] CLES = {
            {0, 0.40F, -0.98F, -0.62F, -5, -22, 12},
            {230, 0.28F, -0.30F, -0.60F, -8, -26, 6},
            {330, 0.27F, -0.27F, -0.59F, -2, -24, 4},
            {520, 0.12F, -0.18F, -0.42F, 80, -12, 0},
            {690, 0.04F, -0.12F, -0.33F, 138, -6, 0},
            {1110, 0.03F, -0.10F, -0.32F, 146, -4, 0},
            {1290, 0.27F, -0.34F, -0.55F, 30, -18, -8},
            {1430, 0.80F, -0.86F, -0.52F, -55, -40, -75},
            {1600, 1.20F, -1.30F, -0.40F, -140, -60, -130},
    };

    private static TypeAtout type = TypeAtout.BLINDAGE;
    private static long debut = JAMAIS;
    private static int sonsJoues;
    private static final ModelesZb.Tex[] TEX = new ModelesZb.Tex[TypeAtout.values().length];

    private static final Matrix4f M = new Matrix4f(), MB = new Matrix4f(), MC = new Matrix4f();
    private static final Matrix3f N3 = new Matrix3f();
    private static final Vector4f V = new Vector4f();
    private static final Vector3f H = new Vector3f(), D = new Vector3f(), X = new Vector3f(), Z = new Vector3f(), C0 = new Vector3f();
    private static final float[] K = new float[6];

    // ------------------------------------------------------------------ etat
    /** Lance l'animation pour cet atout (appele par le HUD a l'evenement du jeu). */
    public static void boire(TypeAtout t) {
        type = t == null ? TypeAtout.BLINDAGE : t;
        debut = Util.getMillis();
        sonsJoues = 0;
    }

    /** Vrai pendant l'animation. */
    public static boolean actif() {
        long dt = Util.getMillis() - debut;
        return dt >= 0 && dt < DUREE_MS;
    }

    /** Millisecondes ecoulees depuis le debut (pour le banc). */
    public static long ecoule() {
        return Util.getMillis() - debut;
    }

    /** Fige l'animation a un instant donne (banc d'essai : captures a des instants precis). */
    public static void figer(TypeAtout t, int ms) {
        type = t == null ? TypeAtout.BLINDAGE : t;
        debut = Util.getMillis() - Mth.clamp(ms, 0, DUREE_MS - 1);
        sonsJoues = 0xFF;
        fige = true;
        figeMs = Mth.clamp(ms, 0, DUREE_MS - 1);
    }

    private static boolean fige;
    private static int figeMs;

    /** Fin du gel du banc. */
    public static void liberer() {
        fige = false;
        debut = JAMAIS;
    }

    private static int instant() {
        if (fige) {
            debut = Util.getMillis() - figeMs;
            return figeMs;
        }
        return (int) (Util.getMillis() - debut);
    }

    /**
     * Le son « atout bu » joue par le serveur a la position du buveur fait doublon avec les sons de l'animation : il est
     * etouffe chez le buveur (rend vrai si le son doit etre coupe).
     */
    public static boolean etoufferSonServeur(ResourceLocation son) {
        return actif() && son != null && HeraliszMatchs.MODID.equals(son.getNamespace()) && "zb_atout_bu".equals(son.getPath());
    }

    // ------------------------------------------------------------------ evenements
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mains(RenderHandEvent e) {
        if (!actif() && !fige) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        try {
            dessiner(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), instant());
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] animation de boisson : {}", ex.toString());
            debut = JAMAIS;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void souris(InputEvent.MouseButton.Pre e) {
        if (actif() && e.getAction() == GLFW.GLFW_PRESS) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (!actif()) return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !actif() || fige) return;
        Minecraft mc = Minecraft.getInstance();
        // un bouton tenu avant le debut ne doit pas tirer pendant la boisson (TaCZ tire tant que sa touche est enfoncee)
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        for (KeyMapping k : mc.options.keyMappings) {
            // saveString() = nom de la touche (« key.mouse.left »...) : methode vanilla, reobfusquee proprement
            if (k.getCategory().contains("tacz") && k.isDown() && k.saveString().startsWith("key.mouse")) {
                k.setDown(false);
            }
        }
        int t = instant();
        jouer(t, 0, T_CAPSULE, ZbSons.BOISSON_DECAPSULE.get(), 0.9F, 1.0F);
        jouer(t, 1, T_GORGEE, ZbSons.BOISSON_GORGEE.get(), 0.9F, 1.0F);
        jouer(t, 2, T_VERRE, ZbSons.BOISSON_VERRE.get(), 0.55F, 1.0F);
        jouer(t, 3, T_ROT, ZbSons.BOISSON_ROT.get(), 0.75F, 0.95F + (float) Math.random() * 0.1F);
    }

    private static void jouer(int t, int bit, int quand, SoundEvent son, float vol, float hauteur) {
        if (t < quand || (sonsJoues & (1 << bit)) != 0) return;
        sonsJoues |= 1 << bit;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(son.getLocation(), SoundSource.PLAYERS, vol, hauteur,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    // ------------------------------------------------------------------ dessin
    private static ModelesZb.Tex tex(TypeAtout t) {
        ModelesZb.Tex x = TEX[t.ordinal()];
        if (x == null) {
            x = ModelesZb.tex(new ResourceLocation(HeraliszMatchs.MODID, "textures/entity/zombies/v2/bouteille_" + t.id() + ".png"));
            TEX[t.ordinal()] = x;
        }
        return x;
    }

    /** Interpole les cles a l'instant t (lissage par morceaux). */
    private static void cles(int t) {
        int i = 0;
        while (i < CLES.length - 2 && t > CLES[i + 1][0]) i++;
        float[] a = CLES[i], b = CLES[i + 1];
        float u = Mth.clamp((t - a[0]) / (b[0] - a[0]), 0, 1);
        u = u * u * (3 - 2 * u);
        for (int k = 0; k < 6; k++) K[k] = Mth.lerp(u, a[k + 1], b[k + 1]);
        // trois gorgees : la bouteille bat legerement contre les levres
        if (t > 690 && t < 1110) {
            float g = (t - 690) / 420F;
            K[1] += 0.010F * Mth.sin(g * Mth.PI * 6);
            K[3] += 3F * Mth.sin(g * Mth.PI * 6);
        }
    }

    /** Matrice bouteille (pixels du modele) -> repere de la camera, a l'instant t. */
    private static Matrix4f matrice(Matrix4f m, int t) {
        cles(t);
        return m.identity().translate(K[0], K[1], K[2]).rotateY(K[4] * Mth.DEG_TO_RAD).rotateZ(K[5] * Mth.DEG_TO_RAD)
                .rotateX(K[3] * Mth.DEG_TO_RAD).scale(ECHELLE / 16F).translate(0, -PRISE_Y, 0);
    }

    static void dessiner(PoseStack ps, MultiBufferSource buf, int lumiere, int t) {
        Minecraft mc = Minecraft.getInstance();
        ModeleZb m = ModelesZb.get("bouteille");
        ModelesZb.Tex tx = tex(type);
        matrice(MB, t);
        // --- bouteille (rotation propre sans echelle pour les normales)
        ps.pushPose();
        cles(t);
        ps.translate(K[0], K[1], K[2]);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(K[4]));
        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(K[5]));
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(K[3]));
        ps.scale(ECHELLE, ECHELLE, ECHELLE);
        ps.translate(0, -PRISE_Y / 16F, 0);
        if (m != null) {
            VertexConsumer vb = buf.getBuffer(tx.base());
            ModeleZb.dessiner(m.groupe("verre"), ModeleZb.BASE, ps, vb, lumiere, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            ModeleZb.dessiner(m.groupe("etiquette"), ModeleZb.BASE, ps, vb, lumiere, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            if (t < T_CAPSULE) ModeleZb.dessiner(m.groupe("bouchon"), ModeleZb.BASE, ps, vb, lumiere, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            // reflet de la couleur de l'atout dans le verre (lueur douce)
            VertexConsumer vl = buf.getBuffer(tx.lueur());
            ModeleZb.dessiner(m.groupe("etiquette"), ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(0.25F, 0xFFFFFF));
        }
        ps.popPose();
        // --- capsule qui saute (trajectoire balistique depuis le haut de la bouteille au moment du decapsulage)
        if (m != null && t >= T_CAPSULE && t < T_CAPSULE + 520) {
            matrice(MC, T_CAPSULE);
            V.set(0, 12.6F, 0, 1).mul(MC);
            float s = (t - T_CAPSULE) / 1000F;
            float cx = V.x() + 0.55F * s, cy = V.y() + 1.6F * s - 4.5F * s * s, cz = V.z() + 0.25F * s;
            ps.pushPose();
            ps.translate(cx, cy, cz);
            ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-900 * s));
            ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(500 * s));
            ps.scale(ECHELLE, ECHELLE, ECHELLE);
            ps.translate(0, -12.55F / 16F, 0);
            ModeleZb.dessiner(m.groupe("bouchon"), ModeleZb.BASE, ps, buf.getBuffer(tx.base()), lumiere, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            ps.popPose();
        }
        // --- bras : de la main (contre la bouteille) a l'epaule hors champ
        if (mc.player != null) bras(ps, buf, lumiere, mc.player);
    }

    private static void bras(PoseStack ps, MultiBufferSource buf, int lumiere, AbstractClientPlayer p) {
        V.set(MAIN_X, MAIN_Y, MAIN_Z, 1).mul(MB);
        H.set(V.x(), V.y(), V.z());
        D.set(EPAULE_X, EPAULE_Y, EPAULE_Z).sub(H);
        float longueur = D.length();
        if (longueur < 1e-3F) return;
        D.normalize();
        // repere du bras : Y = vers l'epaule, Z = vers la camera (dessus de l'avant-bras), X = perpendiculaire
        C0.set(0, 0, 1);
        X.set(D).cross(C0);
        if (X.lengthSquared() < 1e-6F) X.set(1, 0, 0);
        X.normalize();
        Z.set(X).cross(D).normalize();
        M.set(X.x(), X.y(), X.z(), 0, D.x(), D.y(), D.z(), 0, Z.x(), Z.y(), Z.z(), 0, H.x(), H.y(), H.z(), 1);
        ps.pushPose();
        ps.mulPoseMatrix(M);
        ps.last().normal().mul(N3.set(M));
        ps.scale(BRAS, BRAS, BRAS);
        boolean fin = "slim".equals(p.getModelName());
        float l = fin ? 1.5F : 2F;
        float lp = longueur / BRAS + 1.5F;
        ResourceLocation peau = p.getSkinTextureLocation();
        float[] uv = fin ? UV_FIN : UV_LARGE, uvm = fin ? UV_FIN_MANCHE : UV_LARGE_MANCHE;
        Geo.pave(ps, buf.getBuffer(RenderType.entitySolid(peau)), -l, -1.5F, -2F, l, lp, 2F, uv, lumiere, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        Geo.pave(ps, buf.getBuffer(RenderType.entityTranslucent(peau)), -l - 0.25F, -1.25F, -2.25F, l + 0.25F, lp, 2.25F, uvm, lumiere,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        ps.popPose();
    }

    /** UV du bras droit sur la peau 64 x 64 (bas = main, haut = epaule, nord, sud, ouest, est), larges et fins. */
    private static final float[] UV_LARGE = bras(4, 0), UV_LARGE_MANCHE = bras(4, 16), UV_FIN = bras(3, 0), UV_FIN_MANCHE = bras(3, 16);

    private static float[] bras(int w, int dy) {
        // patron du bras droit : dessus (44,16), dessous (44+w,16), cote exterieur (40,20), avant (44,20),
        // cote interieur (44+w,20), arriere (48+w,20) ; la main est en bas de la texture
        float[] dessous = Geo.uv(44 + w, 16 + dy, w, 4, 64, 64), dessus = Geo.uv(44, 16 + dy, w, 4, 64, 64);
        float[] avant = Geo.uv(44, 20 + dy, w, 12, 64, 64), arriere = Geo.uv(48 + w, 20 + dy, w, 12, 64, 64);
        float[] ext = Geo.uv(40, 20 + dy, 4, 12, 64, 64), inte = Geo.uv(44 + w, 20 + dy, 4, 12, 64, 64);
        // le pave du bras va de la main (y bas) a l'epaule (y haut) : la face « haut » est l'epaule (dessus du patron)
        return Geo.faces(dessous, dessus, arriere, avant, inte, ext);
    }
}

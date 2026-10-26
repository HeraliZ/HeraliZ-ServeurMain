package fr.heralisz.clans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.ClanSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Petit losange vert au-dessus de la tête des membres de son clan (25/10, demande des propriétaires), vu seulement par
 * les membres du même clan : la liste vient du snapshot du clan déjà envoyé par le serveur (aucun paquet en plus,
 * aucune position d'un joueur que le jeu ne dessine pas déjà).
 * <p>Dessiné à la fin du rendu de chaque joueur que le jeu dessine ({@link RenderPlayerEvent.Post}), comme un objet du
 * monde : test de profondeur normal, donc jamais à travers les murs. Caché avec F1, jamais au-dessus de soi, ni en match
 * (ClashWar : l'équipe a son propre affichage), ni sur un joueur invisible, spectateur ou mort, ni pendant une
 * cinématique (le rendu des joueurs y est annulé avant). Rien d'alloué par image : un test dans un ensemble de quelques
 * UUID, puis 5 quadrilatères.
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, value = Dist.CLIENT)
public final class IconeClan {
    private IconeClan() {
    }

    /** Hauteur du centre du losange au-dessus de la boîte du joueur (le nom est à +0,5, haut de 0,25). */
    private static final float HAUTEUR = 0.72f;
    /** Échelle des « pixels » d'interface dans le monde (celle des noms de Minecraft). */
    private static final float ECHELLE = 0.025f;
    /** Demi-diagonales du losange (contour, intérieur), en pixels : 9 px de large, soit 0,22 bloc. */
    private static final float R_EXT = 4.5f, R_INT = 3.1f;
    /** Couleurs : vert doux et contour sombre, un peu transparents (discret). */
    private static final int VERT = 0x5BE38A, CONTOUR = 0x0F2A18;
    private static final int A_VERT = 220, A_CONTOUR = 170;

    /** Membres de son clan, soi exclu (fil de rendu). */
    private static final Set<UUID> MEMBRES = new HashSet<>();
    /** Recalculé à chaque tick client : il y a quelqu'un à marquer et on n'est pas en match. */
    private static boolean actif;

    @Nullable
    private static final Method EN_MATCH = methodeEnMatch();

    @Nullable
    private static Method methodeEnMatch() {
        try {
            return Class.forName("fr.heraliz.noyau.api.EtatClient").getMethod("enMatch");
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** Nouveau snapshot du clan (ou null : plus de clan). Appelé par {@link ClientClans}. */
    static void majMembres(@Nullable ClanSnapshot s) {
        MEMBRES.clear();
        if (s == null) return;
        for (ClanSnapshot.MembreVue m : s.membres()) {
            if (!m.uuid().equals(s.moi())) MEMBRES.add(m.uuid());
        }
    }

    static void vider() {
        MEMBRES.clear();
        actif = false;
    }

    private static boolean enMatch(LocalPlayer moi) {
        if ("heraliz_clashwar".equals(moi.level().dimension().location().getNamespace())) return true;
        if (EN_MATCH == null) return false;
        try {
            return Boolean.TRUE.equals(EN_MATCH.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        LocalPlayer moi = Minecraft.getInstance().player;
        actif = moi != null && !MEMBRES.isEmpty() && !ClientClans.estFaux() && !enMatch(moi);
    }

    @SubscribeEvent
    public static void rendu(RenderPlayerEvent.Post e) {
        if (!actif) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Player j = e.getEntity();
        LocalPlayer moi = mc.player;
        if (moi == null || j == moi || !MEMBRES.contains(j.getUUID())) return;
        if (j.isSpectator() || !j.isAlive() || j.isInvisibleTo(moi)) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(0.0, j.getBbHeight() + HAUTEUR, 0.0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-ECHELLE, -ECHELLE, ECHELLE);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.debugQuads());
        // contour : 4 bandes entre le grand et le petit losange (aucune face superposée : pas de scintillement)
        bande(vc, m, 0, -R_EXT, R_EXT, 0, 0, -R_INT, R_INT, 0);
        bande(vc, m, R_EXT, 0, 0, R_EXT, R_INT, 0, 0, R_INT);
        bande(vc, m, 0, R_EXT, -R_EXT, 0, 0, R_INT, -R_INT, 0);
        bande(vc, m, -R_EXT, 0, 0, -R_EXT, -R_INT, 0, 0, -R_INT);
        // intérieur vert
        sommet(vc, m, 0, -R_INT, VERT, A_VERT);
        sommet(vc, m, -R_INT, 0, VERT, A_VERT);
        sommet(vc, m, 0, R_INT, VERT, A_VERT);
        sommet(vc, m, R_INT, 0, VERT, A_VERT);
        ps.popPose();
    }

    /** Quadrilatère du contour : deux sommets extérieurs (a, b) puis les deux intérieurs correspondants (b', a'). */
    private static void bande(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float aix, float aiy, float bix, float biy) {
        sommet(vc, m, ax, ay, CONTOUR, A_CONTOUR);
        sommet(vc, m, bx, by, CONTOUR, A_CONTOUR);
        sommet(vc, m, bix, biy, CONTOUR, A_CONTOUR);
        sommet(vc, m, aix, aiy, CONTOUR, A_CONTOUR);
    }

    private static void sommet(VertexConsumer vc, Matrix4f m, float x, float y, int rgb, int a) {
        vc.vertex(m, x, y, 0f).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a).endVertex();
    }
}

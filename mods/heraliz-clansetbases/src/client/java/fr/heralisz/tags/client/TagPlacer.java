package fr.heralisz.tags.client;

import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.network.Packets;
import fr.heralisz.tags.network.TagsNet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pose cote client : rayon depuis les yeux (portee annoncee par le serveur, sinon celle de la config) jusqu'a la face
 * touchee, puis TagC2S. Le serveur reverifie tout. Sert aussi au banc d'essai : rayon par un point de l'ecran, pose a
 * un point donne, et pose locale (sans serveur) pour les scenes de performance.
 */
public final class TagPlacer {
    private TagPlacer() {
    }

    private static long prochainIdLocal = -1;
    private static long dernierMessageMs;

    /** Face visee par le joueur (portee du serveur), ou null. */
    public static BlockHitResult viser() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;
        HitResult hr = p.pick(ClientTags.portee(), 1f, false);
        return hr.getType() == HitResult.Type.BLOCK ? (BlockHitResult) hr : null;
    }

    /**
     * Face visee par un rayon passant par un point de l'ecran (coordonnees d'interface), ou null. Utilise le champ
     * de vision des options et la rotation de la camera.
     */
    public static BlockHitResult viserEcran(double sx, double sy) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;
        double w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double fovY = Math.toRadians(mc.options.fov().get());
        double tanY = Math.tan(fovY / 2), tanX = tanY * (w / h);
        float nx = (float) ((sx / w * 2 - 1) * tanX), ny = (float) ((1 - sy / h * 2) * tanY);
        Camera cam = mc.gameRenderer.getMainCamera();
        Quaternionf rot = new Quaternionf(cam.rotation());
        Vector3f dir = new Vector3f(nx, ny, -1f).normalize().rotate(rot);
        Vec3 depart = cam.getPosition();
        double portee = ClientTags.portee();
        Vec3 fin = depart.add(dir.x * portee, dir.y * portee, dir.z * portee);
        BlockHitResult hr = mc.level.clip(new ClipContext(depart, fin, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hr.getType() == HitResult.Type.BLOCK ? hr : null;
    }

    /** Tague avec ce tag la face visee ; vrai si une demande est partie au serveur. Messages discrets sinon. */
    public static boolean taguer(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (id == null || id.isEmpty() || !TagCatalog.existe(id)) {
            message(mc, "msg.heralisz_tags.roue_vide");
            return false;
        }
        if (!ClientTags.possede(id)) {
            message(mc, "msg.heralisz_tags.pas_possede");
            return false;
        }
        // a bord du train, ses parois ne sont pas des blocs : le rayon passerait au travers (« trop loin » ou un mur du
        // dehors). Le serveur refuse aussi (Refus.TRAIN).
        if (fr.heralisz.tags.util.Vehicule.dansLeTrain(mc.player)) {
            message(mc, "msg.heralisz_tags.train");
            return false;
        }
        BlockHitResult hr = viser();
        if (hr == null) {
            message(mc, "msg.heralisz_tags.trop_loin");
            return false;
        }
        return taguer(id, hr);
    }

    /** Envoie la demande de pose pour une face deja determinee. */
    public static boolean taguer(String id, BlockHitResult hr) {
        if (hr == null || id == null || id.isEmpty()) return false;
        TagsNet.toServer(new Packets.TagC2S(id, hr.getLocation(), hr.getDirection()));
        return true;
    }

    /**
     * Pose locale (banc d'essai) : fabrique un TagS2C a id negatif et le traite comme s'il venait du serveur
     * (cuisson + rendu), sans particules ni son.
     */
    public static ClientTags.TagActif local(String id, Vec3 point, Direction face, float angle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Packets.TagS2C p = new Packets.TagS2C(prochainIdLocal--, mc.player.getUUID(), id, point, face, angle, mc.player.getYRot(), System.currentTimeMillis());
        return ClientHandlers.tag(p);
    }

    private static void message(Minecraft mc, String cle) {
        long now = System.currentTimeMillis();
        if (now - dernierMessageMs < 800) return;
        dernierMessageMs = now;
        mc.player.displayClientMessage(Component.translatable(cle), true);
    }
}

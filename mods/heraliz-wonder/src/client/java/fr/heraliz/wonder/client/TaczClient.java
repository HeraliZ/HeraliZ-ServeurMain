package fr.heraliz.wonder.client;

import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.client.fx.Fx;
import fr.heraliz.wonder.net.Regle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;
import org.joml.Vector3f;

/**
 * Branchements TaCZ cote client (seule classe client qui voit TaCZ) :
 * <ul>
 *   <li>balle de Pistolet a rayons ou de Mk II qui arrive dans le monde du client : suivie par {@link Fx} (elle est
 *   dessinee en rayon d'energie ; TaCZ ne dessine rien pour elle : pas de trainee, pas de modele). Forge lit l'id d'arme
 *   de la balle apres l'avoir ajoutee au monde : elle est reconnue a la fin du tick ;</li>
 *   <li>hors partie de zombies, le tir d'une Wonder Weapon est refuse tout de suite (comme sur le serveur), sans
 *   animation ni son pour rien ; un tir de Thundergun accepte lance tout de suite son onde de choc (prediction) ;</li>
 *   <li>position du canon en vue a la premiere personne (calculee par TaCZ a chaque image).</li>
 * </ul>
 */
public final class TaczClient {
    private TaczClient() {
    }

    private static long dernierRefus;
    private static final Vector3f TMP = new Vector3f();

    static void brancher(IEventBus f) {
        f.addListener(EventPriority.HIGH, false, GunShootEvent.class, TaczClient::avantTir);
        f.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, TaczClient::balle);
        // avant Fx.tick (priorite normale) : les balles arrivees pendant ce tick sont reconnues avant le dessin
        f.addListener(EventPriority.HIGH, false, TickEvent.ClientTickEvent.class, TaczClient::tick);
    }

    // balles TaCZ arrivees chez le client, en attente de leurs donnees : Forge ajoute l'entite au monde AVANT de lire ses
    // donnees d'apparition (id d'arme, tireur), on la reconnait donc au tick suivant (reserve fixe, sans allocation)
    private static final int ATTENTE = 64;
    private static final EntityKineticBullet[] A_BALLE = new EntityKineticBullet[ATTENTE];
    private static final double[] A_X = new double[ATTENTE], A_Y = new double[ATTENTE], A_Z = new double[ATTENTE];
    private static int nAttente;

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || nAttente == 0) return;
        for (int i = 0; i < nAttente; i++) {
            EntityKineticBullet b = A_BALLE[i];
            A_BALLE[i] = null;
            if (b == null) continue;
            byte type = Armes.type(b.getGunId());
            if (type != Armes.RAYON && type != Armes.MK2) continue;
            LivingEntity tireur = b.getOwner() instanceof LivingEntity le ? le : null;
            // un eclair de Mk II a souvent deja touche (et ete retire) quand on le reconnait : son trajet suffit
            if (type == Armes.MK2) traitMk2(b, tireur, A_X[i], A_Y[i], A_Z[i]);
            else if (!b.isRemoved()) Fx.balle(b, type, Armes.papTenue(tireur, type), tireur, A_X[i], A_Y[i], A_Z[i]);
        }
        nAttente = 0;
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || !(e.getShooter() instanceof LocalPlayer p)) return;
        byte type = Armes.type(e.getGunItemStack());
        if (type == Armes.AUCUNE) return;
        if (peutTirer(p)) {
            // Thundergun : l'onde part tout de suite chez le tireur (le serveur decide des creatures soufflees)
            if (type == Armes.TONNERRE) fr.heraliz.wonder.client.fx.FxVent.tirLocal(p, e.getGunItemStack());
            return;
        }
        e.setCanceled(true);
        long now = System.currentTimeMillis();
        if (now - dernierRefus > 1500) {
            dernierRefus = now;
            p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
        }
    }

    private static boolean peutTirer(LocalPlayer p) {
        return p.isCreative() || p.isSpectator() || Regle.clientTirHorsZombies || Regles.dansDimensionZombies(p.level());
    }

    private static void balle(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof EntityKineticBullet b) || nAttente >= ATTENTE) return;
        A_BALLE[nAttente] = b;
        A_X[nAttente] = b.getX();
        A_Y[nAttente] = b.getY();
        A_Z[nAttente] = b.getZ();
        nAttente++;
    }

    /**
     * Eclair de Mk II (400 m/s : il a souvent deja touche quand le client le voit) : le trajet est retrace ici (blocs puis
     * creatures, 64 blocs au plus) et dessine d'un coup, comme le laser de CoD. Le serveur seul decide des degats.
     */
    private static void traitMk2(EntityKineticBullet b, LivingEntity tireur, double sx, double sy, double sz) {
        Vec3 v = b.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-8) return;
        Vec3 s = new Vec3(sx, sy, sz);
        Vec3 e = s.add(v.normalize().scale(64));
        BlockHitResult bh = b.level().clip(new ClipContext(s, e, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b));
        if (bh.getType() != HitResult.Type.MISS) e = bh.getLocation();
        Entity source = tireur != null ? tireur : b;
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(b.level(), source, s, e, new AABB(s, e).inflate(1.0),
                x -> x instanceof LivingEntity le && le.isAlive() && x != tireur && !x.isSpectator(), 0.3f);
        if (eh != null) e = eh.getLocation();
        Fx.trait(sx, sy, sz, e.x, e.y, e.z, Armes.papTenue(tireur, Armes.MK2), tireur);
    }

    /**
     * Decalage monde (oeil -> canon) de l'arme en premiere personne, d'apres TaCZ (espace de la camera, tourne comme la
     * camera). Rend faux si TaCZ ne l'a pas encore calcule.
     */
    public static boolean decalageCanon(Camera cam, float[] out) {
        Vector3f o = GunItemRendererWrapper.muzzleRenderOffset;
        if (o.x == 0 && o.y == 0 && o.z == 0) return false;
        TMP.set(o).rotateX(-cam.getXRot() * ((float) Math.PI / 180f)).rotateY(-(cam.getYRot() + 180f) * ((float) Math.PI / 180f));
        out[0] = TMP.x;
        out[1] = TMP.y;
        out[2] = TMP.z;
        return true;
    }

    /** Banc ({@code /wonderfx inspecter}) : inspection TaCZ de l'arme tenue par le joueur local. */
    static void inspecter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).inspect();
    }

    /** Vrai si le joueur local voit son arme en premiere personne. */
    public static boolean premierePersonne() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType().isFirstPerson();
    }
}

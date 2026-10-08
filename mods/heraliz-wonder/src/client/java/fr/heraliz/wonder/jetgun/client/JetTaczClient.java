package fr.heraliz.wonder.jetgun.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.jetgun.JetGun;
import fr.heraliz.wonder.net.Regle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;
import org.joml.Vector3f;

/**
 * Branchements TaCZ du Jet Gun côté client (seule classe client du Jet Gun qui voit TaCZ) :
 * <ul>
 *   <li>hors partie de zombies, le tir est refusé tout de suite (comme sur le serveur), sans animation ni son ;</li>
 *   <li>chaque tir vu (le sien : {@code GunFireEvent} du tireur local ; ceux des autres : relayés par TaCZ) entretient les
 *   effets du tireur ; chaque purge (rechargement) lance la vapeur ;</li>
 *   <li>position du canon à la première personne (calculée par TaCZ à chaque image) et échelle de la vue.</li>
 * </ul>
 */
public final class JetTaczClient {
    private JetTaczClient() {
    }

    private static long dernierRefus;
    private static final Vector3f TMP = new Vector3f();

    static void brancher(IEventBus f) {
        f.addListener(EventPriority.HIGH, false, GunShootEvent.class, JetTaczClient::avantTir);
        f.addListener(EventPriority.NORMAL, false, GunFireEvent.class, JetTaczClient::tir);
        f.addListener(EventPriority.NORMAL, false, GunReloadEvent.class, JetTaczClient::purge);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || !(e.getShooter() instanceof LocalPlayer p)) return;
        if (!JetGun.estJetGun(e.getGunItemStack())) return;
        if (p.isCreative() || p.isSpectator() || Regle.clientTirHorsZombies || Regles.dansDimensionZombies(p.level())) return;
        e.setCanceled(true);
        long now = System.currentTimeMillis();
        if (now - dernierRefus > 1500) {
            dernierRefus = now;
            p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
        }
    }

    private static void tir(GunFireEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || e.getShooter() == null || !JetGun.estJetGun(e.getGunItemStack())) return;
        JetFx.tir(e.getShooter());
    }

    private static void purge(GunReloadEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || e.getEntity() == null || !JetGun.estJetGun(e.getGunItemStack())) return;
        JetFx.purge(e.getEntity());
    }

    /** Décalage monde (œil -> bouche) de l'arme à la première personne, d'après TaCZ ; faux s'il n'est pas encore calculé. */
    static boolean decalageCanon(Camera cam, float[] out) {
        Vector3f o = GunItemRendererWrapper.muzzleRenderOffset;
        if (o.x == 0 && o.y == 0 && o.z == 0) return false;
        TMP.set(o).rotateX(-cam.getXRot() * ((float) Math.PI / 180f)).rotateY(-(cam.getYRot() + 180f) * ((float) Math.PI / 180f));
        out[0] = TMP.x;
        out[1] = TMP.y;
        out[2] = TMP.z;
        return true;
    }

    /** Échelle réelle de la vue (distance œil -> bouche mesurée / attendue), bornée. */
    static float echelleVue() {
        Vector3f o = GunItemRendererWrapper.muzzleRenderOffset;
        float l = o.length();
        if (l < 1e-4f) return 1f;
        return Math.max(0.4f, Math.min(2.5f, l / JetVue.LONG_BOUCHE));
    }

    static void tirer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) IClientPlayerGunOperator.fromLocalPlayer(mc.player).shoot();
    }

    static void inspecter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) IClientPlayerGunOperator.fromLocalPlayer(mc.player).inspect();
    }

    /** Banc : état de TaCZ et du Jet Gun chez le joueur local (journal). */
    static String etat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "pas de joueur";
        IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
        com.tacz.guns.api.entity.IGunOperator g = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(mc.player);
        net.minecraft.world.item.ItemStack s = mc.player.getMainHandItem();
        net.minecraft.nbt.CompoundTag t = s.getTag();
        return String.format(java.util.Locale.ROOT, "visee %.2f (%s), recharge %s, sortie %d, chaleur %.1f, verrou %s, munitions %d, emballement %.2f",
                op.getClientAimingProgress(1f), op.isAim(), g.getSynReloadState().getStateType(), g.getSynDrawCoolDown(),
                t == null ? 0f : t.getFloat("HeatAmount"), t != null && t.getBoolean("OverHeated"), t == null ? -1 : t.getInt("GunCurrentAmmoCount"),
                JetFx.spinLocal());
    }

    static void recharger() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) IClientPlayerGunOperator.fromLocalPlayer(mc.player).reload();
    }
}

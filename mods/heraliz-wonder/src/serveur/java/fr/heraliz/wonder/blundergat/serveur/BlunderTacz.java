package fr.heraliz.wonder.blundergat.serveur;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.blundergat.BlunderArmes;
import fr.heraliz.wonder.blundergat.net.Eclats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

import java.util.Optional;

/**
 * Branchements TaCZ cote serveur du Blundergat et de l'Acid Gat (seule classe serveur de ce paquet qui voit TaCZ ;
 * chargee seulement si TaCZ est la).
 * <ul>
 *   <li>hors partie de zombies, ces armes ne tirent pas (sauf creatif, ou reglage {@code general.tirHorsZombies}) ;</li>
 *   <li>Blundergat : la gerbe de 8 obus reste celle de TaCZ (vraies balles : tete, points, Pack-a-Punch du mode zombies) ;
 *   un obus ne blesse <b>jamais un joueur</b> ; sur un bloc il ne laisse ni trou ni bris de verre (impact remplace) ; chaque
 *   impact est releve pour les effets ({@link EclatsServeur}) ;</li>
 *   <li>Acid Gat : la balle TaCZ est annulee a son arrivee et remplacee par trois cartouches d'acide ({@link Cartouches}).</li>
 * </ul>
 */
public final class BlunderTacz {
    private BlunderTacz() {
    }

    public static void brancher(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, false, GunShootEvent.class, BlunderTacz::avantTir);
        bus.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, BlunderTacz::balle);
        bus.addListener(EventPriority.HIGHEST, false, EntityHurtByGunEvent.Pre.class, BlunderTacz::avantDegats);
        bus.addListener(EventPriority.NORMAL, false, EntityHurtByGunEvent.Post.class, BlunderTacz::touche);
        bus.addListener(EventPriority.NORMAL, false, EntityKillByGunEvent.class, BlunderTacz::tue);
        bus.addListener(EventPriority.HIGH, false, AmmoHitBlockEvent.class, BlunderTacz::bloc);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getShooter() instanceof ServerPlayer p)) return;
        if (BlunderArmes.type(e.getGunItemStack()) == BlunderArmes.AUCUNE || Regles.peutTirer(p)) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
    }

    /** Balle d'Acid Gat : annulee, remplacee par les cartouches d'acide. */
    private static void balle(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof EntityKineticBullet b)) return;
        if (BlunderArmes.type(b.getGunId()) != BlunderArmes.ACIDGAT) return;
        e.setCanceled(true);
        if (!(b.getOwner() instanceof LivingEntity tireur) || !(e.getLevel() instanceof ServerLevel niveau)) return;
        try {
            Cartouches.tirer(niveau, tireur, b.position(), tireur.getLookAngle(), BlunderArmes.papTenue(tireur, BlunderArmes.ACIDGAT));
        } catch (RuntimeException ex) {
            HeralizWonder.LOG.error("[wonder] tir d'Acid Gat", ex);
        }
    }

    /** Jamais un joueur : la balle d'un obus qui touche un joueur ne fait rien (meme hors partie, meme en creatif). */
    private static void avantDegats(EntityHurtByGunEvent.Pre e) {
        if (BlunderArmes.type(e.getGunId()) == BlunderArmes.AUCUNE) return;
        if (e.getHurtEntity() instanceof Player) e.setCanceled(true);
    }

    private static void touche(EntityHurtByGunEvent.Post e) {
        if (e.getLogicalSide() == LogicalSide.SERVER) surCreature(e.getBullet(), e.getHurtEntity(), e.getAttacker(), e.getGunId(), (byte) 0);
    }

    private static void tue(EntityKillByGunEvent e) {
        if (e.getLogicalSide() == LogicalSide.SERVER) surCreature(e.getBullet(), e.getKilledEntity(), e.getAttacker(), e.getGunId(), Eclats.MORT);
    }

    private static void surCreature(Entity balle, Entity touchee, LivingEntity tireur, net.minecraft.resources.ResourceLocation gun, byte drap) {
        if (BlunderArmes.type(gun) != BlunderArmes.BLUNDERGAT || touchee == null || !(touchee.level() instanceof ServerLevel niveau)) return;
        Vec3 p = pointTouche(balle, touchee);
        EclatsServeur.ajouter(niveau, tireur == null ? -1 : tireur.getId(), BlunderArmes.papTenue(tireur, BlunderArmes.BLUNDERGAT), p.x, p.y, p.z, -1, drap);
    }

    private static Vec3 pointTouche(Entity balle, Entity touchee) {
        if (balle != null) {
            Vec3 a = balle.position(), b = a.add(balle.getDeltaMovement());
            Optional<Vec3> o = touchee.getBoundingBox().inflate(0.3).clip(a, b);
            if (o.isPresent()) return o.get();
        }
        return new Vec3(touchee.getX(), touchee.getY() + touchee.getBbHeight() * 0.6, touchee.getZ());
    }

    /** Obus sur un bloc : ni trou de balle, ni etincelles de TaCZ, ni verre brise ; notre impact a la place. */
    private static void bloc(AmmoHitBlockEvent e) {
        EntityKineticBullet b = e.getAmmo();
        if (b == null || BlunderArmes.type(b.getGunId()) != BlunderArmes.BLUNDERGAT || !(e.getLevel() instanceof ServerLevel niveau)) return;
        e.setCanceled(true);
        b.discard();
        BlockHitResult h = e.getHitResult();
        Vec3 p = h.getLocation();
        LivingEntity tireur = b.getOwner() instanceof LivingEntity le ? le : null;
        double k = 0.04;
        EclatsServeur.ajouter(niveau, tireur == null ? -1 : tireur.getId(), BlunderArmes.papTenue(tireur, BlunderArmes.BLUNDERGAT),
                p.x + h.getDirection().getStepX() * k, p.y + h.getDirection().getStepY() * k, p.z + h.getDirection().getStepZ() * k,
                h.getDirection().get3DDataValue(), (byte) 0);
    }

    /**
     * Conversion Blundergat -> Acid Gat (le kit de l'etabli ; aussi {@code /blundergat acide}) : meme pile (niveau et
     * marqueurs de Pack-a-Punch, camo, accessoires, rarete conserves), nouvel id d'arme, chargeur plein, reserve du mode
     * zombies ramenee a 8 chargeurs d'Acid Gat au plus. Rend faux si la pile n'est pas un Blundergat.
     */
    public static boolean convertir(net.minecraft.world.item.ItemStack s) {
        if (BlunderArmes.type(s) != BlunderArmes.BLUNDERGAT) return false;
        com.tacz.guns.api.item.IGun g = com.tacz.guns.api.item.IGun.getIGunOrNull(s);
        if (g == null) return false;
        net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation(BlunderArmes.ID_ACIDGAT);
        g.setGunId(s, id);
        int chargeur = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(id)
                .map(i -> com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(s, i.getGunData())).orElse(3);
        g.setCurrentAmmoCount(s, chargeur);
        if (g.useDummyAmmo(s)) g.setDummyAmmoAmount(s, Math.min(g.getDummyAmmoAmount(s), chargeur * 8));
        BlunderNoms.renommer(s);
        return true;
    }
}

package fr.heraliz.wonder.serveur;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.WonderConfig;
import fr.heraliz.wonder.net.Impact;
import fr.heraliz.wonder.net.Regle;
import fr.heraliz.wonder.net.Reseau;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Optional;

/**
 * Branchements TaCZ cote serveur (seule classe serveur qui voit les classes de TaCZ ; chargee seulement si TaCZ est la).
 * <ul>
 *   <li>{@code GunShootEvent} : hors partie de zombies, une Wonder Weapon ne tire pas (message dans la barre d'action) ;</li>
 *   <li>arrivee d'une balle de Wunderwaffe : la balle est annulee et remplacee par la foudre en chaine ({@link Foudre}) ;
 *   les munitions, le son, l'animation et le recul restent ceux de TaCZ ;</li>
 *   <li>arrivee d'une balle de Thundergun (1.0.2) : annulee et remplacee par l'onde de choc en cone ({@link Tonnerre}) ;</li>
 *   <li>balle du Pistolet a rayons qui touche une creature ({@code EntityHurtByGunEvent.Post} / {@code EntityKillByGunEvent})
 *   ou un bloc ({@code AmmoHitBlockEvent}) : eclaboussure ({@link Eclaboussure}) ; le coup direct reste la balle TaCZ, donc
 *   le mode zombies compte la tete, les points et le Pack-a-Punch comme pour toute arme ;</li>
 *   <li>balle de Mk II : effet d'impact ; sur un bloc, ni trou de balle ni etincelles de TaCZ (c'est un rayon).</li>
 * </ul>
 */
public final class TaczServeur {
    private TaczServeur() {
    }

    public static void brancher(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, false, GunShootEvent.class, TaczServeur::avantTir);
        bus.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, TaczServeur::balle);
        bus.addListener(EventPriority.NORMAL, false, EntityHurtByGunEvent.Post.class, TaczServeur::touche);
        bus.addListener(EventPriority.NORMAL, false, EntityKillByGunEvent.class, TaczServeur::tue);
        bus.addListener(EventPriority.HIGH, false, AmmoHitBlockEvent.class, TaczServeur::bloc);
        bus.addListener(TaczServeur::connexion);
    }

    /** Envoie au joueur le reglage « tir hors zombies » (le client refuse le tir de la meme facon). */
    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Reseau.a(p, new Regle(WonderConfig.lire(WonderConfig.TIR_HORS_ZOMBIES)));
    }

    // ------------------------------------------------------------------ tir refuse hors zombies
    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getShooter() instanceof ServerPlayer p)) return;
        if (Armes.type(e.getGunItemStack()) == Armes.AUCUNE || Regles.peutTirer(p)) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ Wunderwaffe : la balle devient foudre
    private static void balle(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof EntityKineticBullet b)) return;
        byte type = Armes.type(b.getGunId());
        if (type != Armes.DG2 && type != Armes.TONNERRE) return;
        e.setCanceled(true);
        if (!(b.getOwner() instanceof LivingEntity tireur) || !(e.getLevel() instanceof ServerLevel niveau)) return;
        try {
            if (type == Armes.DG2) Foudre.tirer(niveau, tireur, b.position(), b.getDeltaMovement(), Armes.papTenue(tireur, Armes.DG2));
            else Tonnerre.tirer(niveau, tireur, b.position(), tireur.getLookAngle(), Armes.papTenue(tireur, Armes.TONNERRE));
        } catch (RuntimeException ex) {
            HeralizWonder.LOG.error("[wonder] tir de " + Armes.nom(type), ex);
        }
    }

    // ------------------------------------------------------------------ impacts sur les creatures
    private static void touche(EntityHurtByGunEvent.Post e) {
        if (e.getLogicalSide() == LogicalSide.SERVER) surCreature(e.getBullet(), e.getHurtEntity(), e.getAttacker(), Armes.type(e.getGunId()));
    }

    private static void tue(EntityKillByGunEvent e) {
        if (e.getLogicalSide() == LogicalSide.SERVER) surCreature(e.getBullet(), e.getKilledEntity(), e.getAttacker(), Armes.type(e.getGunId()));
    }

    private static void surCreature(Entity balle, Entity touchee, LivingEntity tireur, byte type) {
        // Winter's Howl et Paralyzer : leur code a eux (fr.heraliz.wonder.wintershowl / paralyzer) ; aucune balle a traiter ici
        if (type == Armes.AUCUNE || type == Armes.DG2 || type == Armes.TONNERRE || type == Armes.HURLEMENT || type == Armes.PARALYSEUR || touchee == null
                || !(touchee.level() instanceof ServerLevel niveau)) return;
        long t0 = System.nanoTime();
        Vec3 p = pointTouche(balle, touchee);
        int pap = Armes.papTenue(tireur, type);
        if (type == Armes.RAYON) {
            Eclaboussure.eclater(niveau, tireur, p.x, p.y, p.z, pap, touchee, null);
        } else {
            Reseau.autour(niveau, p.x, p.y, p.z, new Impact(Armes.MK2, (byte) pap, (float) p.x, (float) p.y, (float) p.z, (byte) -1, (byte) 0));
        }
        Perf.balle(System.nanoTime() - t0);
    }

    /** Point d'entree de la balle dans la boite de la creature (segment du tick), sinon son centre. */
    private static Vec3 pointTouche(Entity balle, Entity touchee) {
        if (balle != null) {
            Vec3 a = balle.position(), b = a.add(balle.getDeltaMovement());
            Optional<Vec3> o = touchee.getBoundingBox().inflate(0.3).clip(a, b);
            if (o.isPresent()) return o.get();
            if (touchee.getBoundingBox().inflate(0.3).contains(a)) return a;
        }
        return new Vec3(touchee.getX(), touchee.getY() + touchee.getBbHeight() * 0.6, touchee.getZ());
    }

    // ------------------------------------------------------------------ impacts sur les blocs
    private static void bloc(AmmoHitBlockEvent e) {
        EntityKineticBullet b = e.getAmmo();
        if (b == null) return;
        byte type = Armes.type(b.getGunId());
        if (type == Armes.AUCUNE || type == Armes.TONNERRE || type == Armes.HURLEMENT || type == Armes.PARALYSEUR || !(e.getLevel() instanceof ServerLevel niveau)) return;
        long t0 = System.nanoTime();
        // un rayon ne laisse ni trou de balle ni etincelles : on remplace l'impact de TaCZ par le notre
        e.setCanceled(true);
        b.discard();
        BlockHitResult h = e.getHitResult();
        Vec3 p = h.getLocation();
        double nx = h.getDirection().getStepX() * 0.05, ny = h.getDirection().getStepY() * 0.05, nz = h.getDirection().getStepZ() * 0.05;
        LivingEntity tireur = b.getOwner() instanceof LivingEntity le ? le : null;
        int pap = Armes.papTenue(tireur, type);
        if (type == Armes.RAYON) {
            Eclaboussure.eclater(niveau, tireur, p.x + nx, p.y + ny, p.z + nz, pap, null, h.getDirection());
        } else {
            Reseau.autour(niveau, p.x, p.y, p.z, new Impact(type, (byte) pap, (float) (p.x + nx), (float) (p.y + ny), (float) (p.z + nz),
                    (byte) h.getDirection().get3DDataValue(), (byte) 0));
        }
        Perf.balle(System.nanoTime() - t0);
    }

    /**
     * Banc ({@code /wonder pap}) : monte ou retire le chargeur grande capacite de TaCZ, comme le fait la machine
     * Pack-a-Punch du mode zombies (le modele du Thundergun montre alors les lueurs du Zeus Cannon).
     */
    public static void chargeurPap(net.minecraft.world.item.ItemStack s, boolean monter) {
        com.tacz.guns.api.item.IGun g = com.tacz.guns.api.item.IGun.getIGunOrNull(s);
        if (g == null) return;
        if (monter) {
            g.installAttachment(s, com.tacz.guns.api.item.builder.AttachmentItemBuilder.create()
                    .setId(new net.minecraft.resources.ResourceLocation("tacz", "extended_mag_1")).build());
        } else {
            g.unloadAttachment(s, com.tacz.guns.api.item.attachment.AttachmentType.EXTENDED_MAG);
        }
    }

    /** Rechargement de la config : le reglage est renvoye a tous. */
    public static void config(ModConfigEvent e) {
        if (e.getConfig().getSpec() != WonderConfig.SPEC) return;
        var s = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (s == null) return;
        Regle r = new Regle(WonderConfig.lire(WonderConfig.TIR_HORS_ZOMBIES));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) Reseau.a(p, r);
    }
}

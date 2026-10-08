package fr.heraliz.wonder.wintershowl;

import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Branchements TaCZ cote serveur du Winter's Howl (seule classe serveur du paquet qui voit TaCZ ; chargee seulement si
 * TaCZ est la) :
 * <ul>
 *   <li>arrivee d'une balle de Winter's Howl : la balle est annulee et remplacee par le souffle de givre ({@link Givre}) ;
 *   munitions, son, animation et recul restent ceux de TaCZ ;</li>
 *   <li>hors partie de zombies, l'arme ne tire pas (sauf creatif, reglage {@code tirHorsZombies}) ; {@code TaczServeur}
 *   le fait deja quand {@code Armes} connait l'arme : ici en secours, sans double message.</li>
 * </ul>
 */
public final class TaczGivre {
    private TaczGivre() {
    }

    static void brancher(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, TaczGivre::balle);
        bus.addListener(EventPriority.NORMAL, false, GunShootEvent.class, TaczGivre::avantTir);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getShooter() instanceof ServerPlayer p)) return;
        if (!WintersHowl.est(e.getGunItemStack()) || Regles.peutTirer(p)) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
    }

    private static void balle(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof EntityKineticBullet b)) return;
        if (b.getGunId() == null || !WintersHowl.CHEMIN.equals(b.getGunId().getPath()) || !HeralizWonder.MODID.equals(b.getGunId().getNamespace())) return;
        e.setCanceled(true);
        if (!(b.getOwner() instanceof LivingEntity tireur) || !(e.getLevel() instanceof ServerLevel niveau)) return;
        try {
            Givre.tirer(niveau, tireur, b.position(), tireur.getLookAngle(), WintersHowl.papTenu(tireur));
        } catch (RuntimeException ex) {
            HeralizWonder.LOG.error("[wonder] tir du Winter's Howl", ex);
        }
    }
}

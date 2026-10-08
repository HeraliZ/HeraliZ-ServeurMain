package fr.heraliz.wonder.sliquifier.serveur;

import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.sliquifier.Sliquifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Branchements TaCZ du Sliquifier cote serveur (seule classe serveur du paquet qui voit TaCZ ; chargee seulement si TaCZ
 * est la) :
 * <ul>
 *   <li>{@code GunShootEvent} : hors partie de zombies, le Sliquifier ne tire pas (sauf creatif ou reglage commun
 *   {@code tirHorsZombies}), message dans la barre d'action ;</li>
 *   <li>arrivee d'une balle de Sliquifier : la balle est annulee et remplacee par une boule de gelee
 *   ({@link SliqServeur#tirer}) ; munitions, son, animation et recul restent ceux de TaCZ.</li>
 * </ul>
 */
public final class SliqTacz {
    private SliqTacz() {
    }

    public static void brancher(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, false, GunShootEvent.class, SliqTacz::avantTir);
        bus.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, SliqTacz::balle);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getShooter() instanceof ServerPlayer p)) return;
        if (!Sliquifier.est(e.getGunItemStack()) || Regles.peutTirer(p)) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
    }

    private static void balle(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof EntityKineticBullet b) || !Sliquifier.est(b.getGunId())) return;
        e.setCanceled(true);
        if (!(b.getOwner() instanceof LivingEntity tireur) || !(e.getLevel() instanceof ServerLevel niveau)) return;
        try {
            SliqServeur.tirer(niveau, tireur, b.position(), b.getDeltaMovement(), Sliquifier.papTenu(tireur));
        } catch (RuntimeException ex) {
            HeralizWonder.LOG.error("[sliquifier] tir", ex);
        }
    }

    /** Banc ({@code /sliq pap}) : monte ou retire le chargeur grande capacite de TaCZ, comme la machine Pack-a-Punch. */
    public static void chargeurPap(ItemStack s, boolean monter) {
        com.tacz.guns.api.item.IGun g = com.tacz.guns.api.item.IGun.getIGunOrNull(s);
        if (g == null) return;
        if (monter) {
            g.installAttachment(s, com.tacz.guns.api.item.builder.AttachmentItemBuilder.create()
                    .setId(new net.minecraft.resources.ResourceLocation("tacz", "extended_mag_1")).build());
        } else {
            g.unloadAttachment(s, com.tacz.guns.api.item.attachment.AttachmentType.EXTENDED_MAG);
        }
    }
}

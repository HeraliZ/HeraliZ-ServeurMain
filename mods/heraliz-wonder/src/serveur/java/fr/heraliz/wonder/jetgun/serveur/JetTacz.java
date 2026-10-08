package fr.heraliz.wonder.jetgun.serveur;

import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.jetgun.JetGun;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Branchements TaCZ du Jet Gun côté serveur (chargée seulement si TaCZ est là) :
 * <ul>
 *   <li>{@code GunShootEvent} : hors partie de zombies le Jet Gun ne tire pas (sauf créatif ou réglage
 *   {@code tirHorsZombies}, comme les autres Wonder Weapons) ;</li>
 *   <li>arrivée d'une balle de Jet Gun : annulée (elle n'existe jamais chez les clients) ; elle entretient l'aspiration du
 *   tireur ({@link Aspiration#impulsion}). La chaleur, le verrou de surchauffe et la purge sont gérés par TaCZ et le script
 *   Lua du gunpack.</li>
 * </ul>
 */
public final class JetTacz {
    private JetTacz() {
    }

    private static final ResourceLocation ID = new ResourceLocation(JetGun.ID);

    public static void brancher(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, false, GunShootEvent.class, JetTacz::avantTir);
        bus.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, JetTacz::balle);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getShooter() instanceof ServerPlayer p)) return;
        if (!JetGun.estJetGun(e.getGunItemStack()) || Regles.peutTirer(p)) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
    }

    private static void balle(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof EntityKineticBullet b)) return;
        if (!ID.equals(b.getGunId())) return;
        e.setCanceled(true);
        if (b.getOwner() instanceof LivingEntity tireur && e.getLevel() instanceof ServerLevel niveau) {
            Aspiration.impulsion(niveau, tireur, JetGun.tientJetGun(tireur) ? JetGun.pap(tireur.getMainHandItem()) : 0);
        }
    }
}

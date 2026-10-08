package fr.heraliz.wonder.blundergat.client;

import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.blundergat.BlunderArmes;
import fr.heraliz.wonder.net.Regle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Branchements TaCZ cote client du Blundergat (seule classe client de ce paquet qui voit TaCZ) :
 * <ul>
 *   <li>hors partie de zombies, le tir est refuse tout de suite (comme sur le serveur), sans animation ni son pour rien ;
 *   un tir accepte du joueur local lance tout de suite la flamme de bouche et la secousse (prediction) ;</li>
 *   <li>chaque obus (balle TaCZ) qui arrive chez le client devient un obus traceur ({@link FxBlunder#obus}) : son vrai
 *   trajet est retrace (blocs puis creatures) et dessine a sa vraie vitesse. Forge lit l'id d'arme de la balle apres
 *   l'avoir ajoutee au monde : elle est reconnue en fin de tick.</li>
 * </ul>
 */
public final class BlunderTaczClient {
    private BlunderTaczClient() {
    }

    private static long dernierRefus;
    private static final int ATTENTE = 64;
    private static final EntityKineticBullet[] A_BALLE = new EntityKineticBullet[ATTENTE];
    private static final double[] A_X = new double[ATTENTE], A_Y = new double[ATTENTE], A_Z = new double[ATTENTE];
    private static int nAttente;

    static void brancher(IEventBus f) {
        f.addListener(EventPriority.HIGH, false, GunShootEvent.class, BlunderTaczClient::avantTir);
        f.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, BlunderTaczClient::balle);
        f.addListener(EventPriority.HIGH, false, TickEvent.ClientTickEvent.class, BlunderTaczClient::tick);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || !(e.getShooter() instanceof LocalPlayer p)) return;
        byte type = BlunderArmes.type(e.getGunItemStack());
        if (type == BlunderArmes.AUCUNE) return;
        if (p.isCreative() || p.isSpectator() || Regle.clientTirHorsZombies || Regles.dansDimensionZombies(p.level())) {
            FxBlunder.tirLocal(p, type, Armes.pap(e.getGunItemStack()));
            return;
        }
        e.setCanceled(true);
        long now = System.currentTimeMillis();
        if (now - dernierRefus > 1500) {
            dernierRefus = now;
            p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
        }
    }

    private static void balle(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof EntityKineticBullet b) || nAttente >= ATTENTE) return;
        A_BALLE[nAttente] = b;
        A_X[nAttente] = b.getX();
        A_Y[nAttente] = b.getY();
        A_Z[nAttente] = b.getZ();
        nAttente++;
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || nAttente == 0) return;
        for (int i = 0; i < nAttente; i++) {
            EntityKineticBullet b = A_BALLE[i];
            A_BALLE[i] = null;
            if (b == null || BlunderArmes.type(b.getGunId()) != BlunderArmes.BLUNDERGAT) continue;
            LivingEntity tireur = b.getOwner() instanceof LivingEntity le ? le : null;
            FxBlunder.obus(b, tireur, A_X[i], A_Y[i], A_Z[i], BlunderArmes.papTenue(tireur, BlunderArmes.BLUNDERGAT));
        }
        nAttente = 0;
    }
}

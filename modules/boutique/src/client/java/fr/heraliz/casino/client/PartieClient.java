package fr.heraliz.casino.client;

import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.MachinePartBlock;
import fr.heraliz.casino.block.entity.DecorBlockEntity;
import fr.heraliz.casino.client.zone.ZoneClient;
import fr.heraliz.casino.item.DecorItem;
import fr.heraliz.casino.item.DirectorTabletItem;
import fr.heraliz.casino.item.MachineItem;
import fr.heraliz.casino.net.AdminStatsPacket;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.DuelPrivatePacket;
import fr.heraliz.casino.net.WinPacket;
import fr.heraliz.casino.net.ZoneCutscenePacket;
import fr.heraliz.casino.net.ZoneDialogPacket;
import fr.heraliz.casino.pont.Partie;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de Heraliz Casino (docs/METHODE_CLIENT_SERVEUR.md) : branche les crochets du code commun sur
 * l'affichage (animations des machines et du decor, ecrans, rendus 3D des objets, paquets recus du serveur). Rendus,
 * HUD, sons, camera et cinematiques s'inscrivent seuls (@Mod.EventBusSubscriber, value = Dist.CLIENT).
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // animations, sons et effets des machines et du decor
        MachineBlockEntity.TICK_CLIENT = MachineClientTicker::tick;
        DecorBlockEntity.TICK_CLIENT = DecorClientTicker::tick;
        // ecrans de jeu et Tablette du Directeur
        MachineBlock.OUVRIR_CLIENT.brancher(ClientHooks::openMachine);
        DirectorTabletItem.ECRAN_CLIENT.brancher(ClientHooks::openAdmin);
        // rendu 3D des machines et du decor dans l'inventaire
        MachineItem.RENDU_CLIENT.brancher(c -> c.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ClientRenderers.itemRenderer();
            }
        }));
        DecorItem.RENDU_CLIENT.brancher(c -> c.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ClientRenderers.itemRenderer();
            }
        }));
        // parties invisibles des machines : ni particules de coup, ni de casse
        MachinePartBlock.EFFETS_CLIENT.brancher(c -> c.accept(new IClientBlockExtensions() {
            @Override
            public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
                return true;
            }

            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
                return true;
            }
        }));
        // paquets recus du serveur
        CasinoNet.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof WinPacket x) WinOverlay.show(x.amount(), x.multiplier(), x.machine());
            else if (m instanceof DuelPrivatePacket x) ClientHooks.duelPrivate(x.pos(), x.data());
            else if (m instanceof AdminStatsPacket x) ClientHooks.adminStats(x.json());
            else if (m instanceof ZoneDialogPacket x) ZoneClient.dialog(x);
            else if (m instanceof ZoneCutscenePacket x) ZoneClient.cutscene(x);
        });
    }
}

package fr.heraliz.crates.client;

import fr.heraliz.crates.block.CrateBlockEntity;
import fr.heraliz.crates.block.CratePartBlock;
import fr.heraliz.crates.client.gui.ClientPackets;
import fr.heraliz.crates.item.CrateBlockItem;
import fr.heraliz.crates.item.CrateKeyItem;
import fr.heraliz.crates.network.Packets;
import fr.heraliz.crates.pont.Partie;
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
 * Partie client de Heraliz Crates (docs/METHODE_CLIENT_SERVEUR.md §4) : effets et sons des caisses, rendus 3D des caisses
 * et des clés dans l'inventaire, interfaces d'ouverture et d'édition. Rendus et écrans s'inscrivent seuls.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        CrateBlockEntity.TICK_CLIENT = CrateClientTicker::tick;
        IClientItemExtensions rendu = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ClientRenderers.itemRenderer();
            }
        };
        CrateBlockItem.RENDU_CLIENT.brancher(c -> c.accept(rendu));
        CrateKeyItem.RENDU_CLIENT.brancher(c -> c.accept(rendu));
        CratePartBlock.RENDU_CLIENT.brancher(c -> c.accept(new IClientBlockExtensions() {
            @Override
            public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
                return true;
            }

            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
                return true;
            }
        }));
        Packets.RECEPTION_CLIENT.brancher(PartieClient::recevoir);
    }

    /** Corps d'origine des gestionnaires des paquets serveur -> client. */
    private static void recevoir(Object paquet) {
        if (paquet instanceof Packets.CaseScreen s) {
            ClientPackets.caseScreen(s);
        } else if (paquet instanceof Packets.CaseResult r) {
            ClientPackets.caseResult(r);
        } else if (paquet instanceof Packets.EditorOpen e) {
            ClientPackets.editorOpen(e);
        }
    }
}

package fr.heraliz.decors.serveur;

import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.bloc.Clignotement;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Relance les lampes qui clignotent d'un morceau de carte qu'on charge (1.30.3, repris du jar Décors de la nouvelle
 * carte) : leurs ticks programmés ne sont pas toujours gardés avec le monde. Le chargement note les lampes (seulement les
 * sections qui en contiennent), le tick du serveur suivant leur programme un tick.
 */
@Mod.EventBusSubscriber(modid = HeralizDecors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClignotementServeur {
    private static final ConcurrentLinkedQueue<Object[]> A_LANCER = new ConcurrentLinkedQueue<>();

    private ClignotementServeur() {}

    @SubscribeEvent
    public static void clignotementChunkCharge(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !(e.getChunk() instanceof LevelChunk c)) return;
        LevelChunkSection[] sections = c.getSections();
        int bx = c.getPos().getMinBlockX();
        int bz = c.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> Clignotement.clignote(st.getBlock()))) continue;
            int by = c.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (Clignotement.clignote(s.getBlockState(x, y, z).getBlock())) {
                            A_LANCER.add(new Object[]{l.dimension(), new BlockPos(bx + x, by + y, bz + z)});
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void clignotementTickServeur(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || A_LANCER.isEmpty()) return;
        MinecraftServer srv = e.getServer();
        Object[] q;
        while ((q = A_LANCER.poll()) != null) {
            ServerLevel l = srv.getLevel((ResourceKey<Level>) q[0]);
            BlockPos p = (BlockPos) q[1];
            if (l == null || !l.isLoaded(p)) continue;
            Block b = l.getBlockState(p).getBlock();
            if (Clignotement.clignote(b) && !l.getBlockTicks().hasScheduledTick(p, b)) {
                l.scheduleTick(p, b, 1);
            }
        }
    }
}

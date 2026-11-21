package fr.heraliz.decors.serveur;

import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.bloc.Clignotement;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lampes qui clignotent d'un morceau de carte qu'on charge (1.30.3, repris du jar Décors de la nouvelle carte).
 * <p>
 * 19/11 : elles ne basculent plus (lumière fixe, voir {@link Clignotement}) ; il ne reste qu'à rallumer une fois celles
 * qu'une sauvegarde a gardées dans la seconde noire : la lumière enregistrée avec le morceau de carte y est encore à 0, et
 * Minecraft ne la recalcule pas tout seul (lumière jugée correcte au chargement). Le chargement note les lampes (seulement
 * les sections qui en contiennent), le tick du serveur suivant fait recalculer la lumière des seules lampes encore
 * sombres : une fois par lampe, ensuite la bonne lumière est enregistrée.
 */
@Mod.EventBusSubscriber(modid = HeralizDecors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClignotementServeur {
    private static final ConcurrentLinkedQueue<Object[]> A_VOIR = new ConcurrentLinkedQueue<>();

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
                            A_VOIR.add(new Object[]{l.dimension(), new BlockPos(bx + x, by + y, bz + z)});
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void clignotementTickServeur(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || A_VOIR.isEmpty()) return;
        MinecraftServer srv = e.getServer();
        Object[] q;
        while ((q = A_VOIR.poll()) != null) {
            ServerLevel l = srv.getLevel((ResourceKey<Level>) q[0]);
            BlockPos p = (BlockPos) q[1];
            // 21/11 (audit) : tronçon complet seulement (isLoaded répond « oui » pendant sa lecture : getBlockState l'attendrait)
            if (l == null || !fr.heraliz.noyau.api.Troncons.charge(l, p)) continue;
            BlockState s = l.getBlockState(p);
            if (Clignotement.clignote(s.getBlock()) && l.getBrightness(LightLayer.BLOCK, p) < s.getLightEmission(l, p)) {
                l.getChunkSource().getLightEngine().checkBlock(p);
            }
        }
    }
}

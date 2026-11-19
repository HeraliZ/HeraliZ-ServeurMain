package fr.heraliz.casino.client;

import fr.heraliz.casino.block.DecorBlock;
import fr.heraliz.casino.block.FlickerDecorBlock;
import fr.heraliz.casino.block.entity.DecorBlockEntity;
import fr.heraliz.casino.client.render.DecorRenderer;
import fr.heraliz.casino.client.render.MachineRenderer;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.decor.DecorView;
import fr.heraliz.casino.config.ClientConfig;
import fr.heraliz.casino.decor.DecorType;
import fr.heraliz.noyau.client.Eclairage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Effets des elements de decor a chaque tick client (flammes, etincelles, gresillements), decrits par leur modele ;
 * recherche des poteaux voisins a relier par une chaine.
 */
public final class DecorClientTicker {
    private DecorClientTicker() {
    }

    private static final Random RANDOM = new Random();
    private static final MachineClientTicker.WorldFx FX = new MachineClientTicker.WorldFx();
    private static final DecorView VIEW = new DecorView();

    public static void tick(Level level, BlockPos pos, BlockState state, DecorBlockEntity be) {
        if (!(state.getBlock() instanceof DecorBlock block)) return;
        long gt = level.getGameTime();
        be.watch(gt);
        if (block.type.flicker()) gresiller(level, pos, state, be, gt);
        if (block.type.linked() && (be.clientLinksAt == Long.MIN_VALUE || gt - be.clientLinksAt >= 20)) {
            be.clientLinks = links(level, pos, block.type, be.unhooked);
            be.clientLinksAt = gt;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d2 = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (d2 > 32 * 32) {
            be.fxLastTick = gt;
            return;
        }
        MachineDesign<?> d = Designs.get(block.type.designId(be.mode));
        if (!d.isBuilt()) return;
        float yRot = MachineRenderer.yRot(state);
        FX.set(level, pos, yRot, (float) (double) ClientConfig.PARTICLES.get(), (float) (double) ClientConfig.MACHINE_VOLUME.get());
        DecorView v = DecorRenderer.fill(VIEW, be, state, yRot, gt, 0f);
        long from = be.fxLastTick == Long.MIN_VALUE ? gt : Math.max(be.fxLastTick + 1, gt - 3);
        float since = v.since, flickerSince = v.flickerSince;
        for (long k = from; k <= gt; k++) {
            Scene s = new Scene().set(k, 0f);
            s.seed = be.seed;
            // les ticks rattrapes voient l'etat a leur propre instant
            v.since = Math.max(0, since - (gt - k));
            v.flickerSince = Math.max(0, flickerSince - (gt - k));
            d.tickFxRaw(s, v, FX, RANDOM);
        }
        be.fxLastTick = gt;
    }

    /**
     * Reglette qui gresille (19/11, audit des images par seconde) : le client joue la suite d'a-coups que jouait le serveur
     * (FlickerDecorBlock.next, memes rafales et memes delais), sans rien changer au bloc ni a sa lumiere ; le modele la
     * suit comme avant (flicker, clientFlickerAt). Pendant une saute faible ou une coupure, la salle s'assombrit pres de
     * la reglette par la teinte de la lumiere (Eclairage : toute la lumiere de bloc a moins de 6 blocs, plus rien a 16),
     * au lieu de refaire l'eclairage du monde.
     */
    private static void gresiller(Level level, BlockPos pos, BlockState state, DecorBlockEntity be, long gt) {
        if (!state.getValue(DecorBlock.ON)) {
            if (be.clientFlicker != 0) {
                be.clientFlicker = 0;
                be.clientFlickerAt = gt;
            }
            be.flickerNext = Long.MIN_VALUE;
            return;
        }
        // premiere vue, ou heure du monde revenue en arriere : chaque reglette repart a son rythme
        if (be.flickerNext == Long.MIN_VALUE || be.flickerNext - gt > 1000) be.flickerNext = gt + 2 + (long) (be.seed * 60);
        if (gt >= be.flickerNext) {
            FlickerDecorBlock.Step st = FlickerDecorBlock.next(be.clientFlicker, Math.floorMod(be.mode, 3), be, level.random);
            if (st.level() != be.clientFlicker) {
                be.clientFlicker = st.level();
                be.clientFlickerAt = gt;
            }
            be.flickerNext = gt + Math.max(1, st.delay());
        }
        if (be.clientFlicker == 0) return;
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        double d = Math.sqrt(p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        float pres = d <= 6 ? 1f : (float) Math.max(0, 1 - (d - 6) / 10);
        if (pres > 0) Eclairage.demander(pres * (be.clientFlicker == 2 ? 1f : 0.5f));
    }

    /**
     * Poteaux a relier (chaine, cordon de velours : meme type) : le premier poteau allume trouve vers l'est et vers le
     * sud (chaque paire n'est dessinee qu'une fois, par le poteau le plus a l'ouest / au nord), sans bloc plein entre
     * les deux, et si la chaine n'est decrochee d'aucun des deux bouts.
     */
    private static BlockPos[] links(Level level, BlockPos pos, DecorType type, int unhooked) {
        List<BlockPos> out = new ArrayList<>(2);
        for (Direction d : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            if ((unhooked & DecorBlock.sideBit(d)) != 0) continue;
            BlockPos q = DecorBlock.chainPartner(level, pos, type, d);
            if (q == null || !level.getBlockState(q).getValue(DecorBlock.ON)) continue;
            if ((DecorBlock.unhooked(level, q) & DecorBlock.sideBit(d.getOpposite())) != 0) continue;
            out.add(q);
        }
        return out.toArray(new BlockPos[0]);
    }
}

package com.Spartan.esp;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Ce que chaque état de bloc cache, calculé une fois pour tous les états du jeu
 * (mods compris) sur le thread serveur, puis publié en table immuable lisible par
 * les threads de l'anti-ESP :
 * - bloc opaque plein (pierre, bois, béton, blocs pleins de mods) : cache tout ;
 * - bloc opaque partiel (dalle, escalier, couche de neige…) : sa forme d'occlusion ;
 * - blocs listés dans antiEsp.opaqueBlocks (portes…) : leur forme de collision,
 *   qui suit l'état ouvert / fermé ;
 * - tout le reste (air, verre, feuilles, vitres, barreaux, clôtures, fluides…) :
 *   transparent (absent de la table). Dans le doute, on montre.
 * Exception : la case de construction de CRac ({@code crac:structure}) dont la forme est dans son
 * entité de bloc ; la table n'en garde que la lecture de secours (drapeaux de l'état), la vraie forme
 * est lue à sa position par {@link StructuresCrac} (2.9.4).
 */
final class Occluders {
    private static final int MAX_BOXES = 16;
    private static volatile Reference2ObjectOpenHashMap<BlockState, double[]> table = new Reference2ObjectOpenHashMap<>();
    private static List<? extends String> configured;
    /** Bloc dont la forme se lit à la position (case de construction de CRac), null si absent. */
    private static volatile Block parPosition;
    private static boolean stale = true;

    private Occluders() {
    }

    /** Table courante (immuable une fois publiée) : null = transparent. */
    static Reference2ObjectOpenHashMap<BlockState, double[]> table() {
        return table;
    }

    /** Bloc dont l'occultation dépend de la position (lu par LevelVoxels sur tout thread). */
    static Block parPosition() {
        return parPosition;
    }

    /** Tags rechargés (datapacks) : la table sera reconstruite au prochain tick. */
    static void invalidate() {
        stale = true;
    }

    /** Thread serveur, chaque tick : reconstruit la table si la config ou les tags ont changé. */
    static void refresh() {
        List<? extends String> now = SpartanConfig.ESP_OPAQUE_BLOCKS.get();
        if (!stale && now.equals(configured)) {
            return;
        }
        stale = false;
        configured = List.copyOf(now);
        List<TagKey<Block>> tags = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        for (String raw : configured) {
            String s = raw.trim().toLowerCase(Locale.ROOT);
            ResourceLocation id = ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
            if (id == null) {
                continue;
            }
            if (s.startsWith("#")) {
                tags.add(TagKey.create(Registries.BLOCK, id));
            } else {
                ids.add(id);
            }
        }
        long start = System.nanoTime();
        Reference2ObjectOpenHashMap<BlockState, double[]> t = new Reference2ObjectOpenHashMap<>();
        int states = 0;
        Block dyn = null;
        for (Block b : ForgeRegistries.BLOCKS) {
            if (StructuresCrac.estCase(ForgeRegistries.BLOCKS.getKey(b))) {
                dyn = b;
            }
        }
        parPosition = dyn;
        for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
            states++;
            double[] occ = compute(s, tags, ids);
            if (occ != null) {
                t.put(s, occ);
            }
        }
        t.trim();
        table = t;
        Spartan.LOGGER.info("[Spartan] Anti-ESP : {} états de bloc, {} cachent la vue ({} ms).",
                states, t.size(), (System.nanoTime() - start) / 1_000_000);
    }

    private static double[] compute(BlockState s, List<TagKey<Block>> tags, Set<ResourceLocation> ids) {
        try {
            if (s.isAir()) {
                return null;
            }
            if (s.canOcclude()) {
                if (s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                    return Raycaster.FULL;
                }
                double[] b = boxes(s.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), MAX_BOXES);
                if (b != null) {
                    return b;
                }
            }
            if (isExtra(s, tags, ids)) {
                return boxes(s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), MAX_BOXES);
            }
        } catch (RuntimeException e) {
            // Bloc de mod qui exige un vrai monde pour calculer sa forme : transparent.
        }
        return null;
    }

    private static boolean isExtra(BlockState s, List<TagKey<Block>> tags, Set<ResourceLocation> ids) {
        for (TagKey<Block> tag : tags) {
            if (s.is(tag)) {
                return true;
            }
        }
        if (ids.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        return id != null && ids.contains(id);
    }

    /** Boîtes locales à plat (au plus {@code max} : au-delà, les dernières sont oubliées, on montre), null si vide. */
    static double[] boxes(VoxelShape shape, int max) {
        if (shape.isEmpty()) {
            return null;
        }
        List<AABB> list = shape.toAabbs();
        int n = Math.min(max, list.size());
        double[] out = new double[n * 6];
        for (int i = 0; i < n; i++) {
            AABB a = list.get(i);
            out[i * 6] = a.minX;
            out[i * 6 + 1] = a.minY;
            out[i * 6 + 2] = a.minZ;
            out[i * 6 + 3] = a.maxX;
            out[i * 6 + 4] = a.maxY;
            out[i * 6 + 5] = a.maxZ;
        }
        return out;
    }
}

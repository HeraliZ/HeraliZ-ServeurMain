package com.Spartan.esp;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Ce que chaque état de bloc cache, calculé une fois pour tous les états du jeu
 * (mods compris) sur un fil à part, puis publié en table immuable lisible par
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
 * <p>Fil à part (2.10.2) : le calcul prenait ~1 s sur le thread serveur au démarrage (rapport de
 * blocage). Tant que la table demandée n'est pas publiée ({@link #prete()} faux : démarrage, réglage
 * antiEsp.opaqueBlocks ou tags de blocs changés), l'anti-ESP ne cache personne
 * ({@link VisibilityEngine#tick}). Les formes sont lues sur un monde vide (EmptyBlockGetter), comme
 * le font déjà les fils de génération et de lumière de Minecraft : même table qu'avant.
 */
final class Occluders {
    private static final int MAX_BOXES = 16;
    private static volatile Reference2ObjectOpenHashMap<BlockState, double[]> table = new Reference2ObjectOpenHashMap<>();
    /**
     * États de la table qui cachent pour l'anti-ESP mais à travers lesquels un joueur PEUT voir, pour le suivi à travers
     * les murs (03/11, {@link SuiviMurs}) : blocs de opaqueBlocks (portes, trappes : beaucoup ont des vitres) et blocs
     * dessinés invisibles (RenderShape.INVISIBLE). L'anti-ESP n'en tient pas compte (il cache comme avant).
     */
    private static volatile ReferenceOpenHashSet<BlockState> voirAuTravers = new ReferenceOpenHashSet<>();
    private static List<? extends String> configured;
    /** Bloc dont la forme se lit à la position (case de construction de CRac), null si absent. */
    private static volatile Block parPosition;
    /** Écrit par l'évènement des tags (thread serveur, ou fil principal au chargement des données). */
    private static volatile boolean stale = true;
    /** Numéro de la dernière table demandée (thread serveur). */
    private static int demandee;
    /** Numéro de la table publiée (écrit par le fil de calcul après la table, lu par le thread serveur). */
    private static volatile int publiee = -1;
    /** Fil du calcul en cours, null si aucun (thread serveur). */
    private static Thread enCours;
    /** Calculs lancés pour la table demandée (thread serveur) : un échec est réessayé une fois, puis abandonné. */
    private static int essais;
    private static final int ESSAIS_MAX = 2;

    private Occluders() {
    }

    /** Table courante (immuable une fois publiée) : null = transparent. */
    static Reference2ObjectOpenHashMap<BlockState, double[]> table() {
        return table;
    }

    /** États qui cachent pour l'anti-ESP mais pas pour le suivi à travers les murs (immuable une fois publié). */
    static ReferenceOpenHashSet<BlockState> voirAuTravers() {
        return voirAuTravers;
    }

    /** Bloc dont l'occultation dépend de la position (lu par LevelVoxels sur tout thread). */
    static Block parPosition() {
        return parPosition;
    }

    /** Tags rechargés (datapacks) : la table sera reconstruite au prochain tick. */
    static void invalidate() {
        stale = true;
    }

    /** Thread serveur : vrai si la table publiée suit la config et les tags actuels (sinon : personne n'est caché). */
    static boolean prete() {
        return publiee == demandee;
    }

    /**
     * Thread serveur, chaque tick : si la config ou les tags ont changé, demande une nouvelle table, calculée sur un fil
     * à part (un seul à la fois ; une table demandée entre-temps est calculée à la suite). Jamais de calcul sur le
     * thread serveur : un échec est réessayé une fois, puis l'anti-ESP reste sans table (personne n'est caché) jusqu'au
     * prochain changement des réglages ou des tags.
     */
    static void refresh() {
        List<? extends String> now = SpartanConfig.ESP_OPAQUE_BLOCKS.get();
        if (stale || !now.equals(configured)) {
            stale = false;
            configured = List.copyOf(now);
            demandee++;
            essais = 0;
        }
        if (publiee == demandee || essais >= ESSAIS_MAX || enCours != null && enCours.isAlive()) {
            return;
        }
        int numero = demandee;
        int essai = ++essais;
        List<? extends String> config = configured;
        Thread t = new Thread(() -> {
            try {
                construire(config, numero);
            } catch (Throwable e) {
                Spartan.LOGGER.error(essai < ESSAIS_MAX
                        ? "[Spartan] Anti-ESP : calcul de la table des blocs qui cachent la vue échoué, nouvel essai."
                        : "[Spartan] Anti-ESP : table des blocs qui cachent la vue impossible à calculer ; personne n'est "
                        + "caché jusqu'au prochain changement des réglages ou des tags.", e);
            }
        }, "Spartan-AntiESP-table");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        enCours = t;
        t.start();
    }

    /** Fil de calcul : calcule puis publie la table numéro {@code numero}. */
    private static void construire(List<? extends String> config, int numero) {
        List<TagKey<Block>> tags = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        for (String raw : config) {
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
        ReferenceOpenHashSet<BlockState> vat = new ReferenceOpenHashSet<>();
        for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
            states++;
            double[] occ = compute(s, tags, ids);
            if (occ != null) {
                t.put(s, occ);
                if (vueAuTravers(s, tags, ids)) {
                    vat.add(s);
                }
            }
        }
        t.trim();
        vat.trim();
        // publication : la table et le bloc lu à sa position d'abord, le numéro en dernier (volatiles)
        parPosition = dyn;
        voirAuTravers = vat;
        table = t;
        publiee = numero;
        Spartan.LOGGER.info("[Spartan] Anti-ESP : {} états de bloc, {} cachent la vue ({} ms, fil à part).",
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

    /** Suivi à travers les murs : on peut voir à travers ce bloc (porte, trappe, bloc dessiné invisible). Dans le doute : oui. */
    private static boolean vueAuTravers(BlockState s, List<TagKey<Block>> tags, Set<ResourceLocation> ids) {
        try {
            return isExtra(s, tags, ids) || s.getRenderShape() == RenderShape.INVISIBLE;
        } catch (RuntimeException e) {
            return true;
        }
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

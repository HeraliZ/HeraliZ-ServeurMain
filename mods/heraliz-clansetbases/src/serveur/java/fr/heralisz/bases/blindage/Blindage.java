package fr.heralisz.bases.blindage;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.pillage.PillageConfig;
import fr.heralisz.bases.pillage.PillageNet;
import fr.heralisz.bases.registry.BasesRegistry;
import fr.heralisz.bases.registry.Sons;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Points de vie des blocs proteges (BASES_SPEC §5), cote serveur :
 * <ul>
 *   <li>{@code ExplosionEvent.Detonate} (apres le coeur, qui a deja retire la coque et tout ce qui est hors du volume
 *   constructible) : les blocs proteges sont RETIRES de la liste detruite ; si la source porte des coups
 *   ({@code protection.json} : C4 = 1 coup, rayon 2,5), chaque bloc protege dont le centre est a moins du rayon du
 *   centre de l'explosion recoit ses coups (une fois par explosion, porte = moitie basse, porte HBM = controleur) ;</li>
 *   <li>coups persistants ({@link CoupsData}, SavedData de la dimension) affiches comme des fissures
 *   ({@code ClientboundBlockDestructionPacket}, stade = coups x 10 / niveau), renvoyees quand un joueur charge le chunk et
 *   toutes les {@code fissures_rafraichir_s} secondes aux joueurs proches (le client les oublie apres 20 s) ;</li>
 *   <li>au N-ieme coup : le bloc casse (particules, son {@code blindage_rupture}, aucun objet rendu) ;</li>
 *   <li>clic droit accroupi main vide sur un bloc protege : infobulle « Blindage 4 · 2/4 » (paquet
 *   {@link PillageNet.InfoBlindageS2C}) sans utiliser le bloc.</li>
 * </ul>
 * Ou ces regles valent : les blocs de la table ({@code cfm:*}, portes HBM) seulement dans la dimension des bases, sauf
 * {@code blindage_partout} = true (la carte, qui en est pleine, n'est jamais touchee par defaut). Les anciens blocs de
 * blindage du mod (niveaux 1 a 8, qui valaient partout) ont ete retires en 2.3.0 : hors de la dimension des bases (et
 * sans {@code blindage_partout}), plus aucun bloc n'est protege.
 */
public final class Blindage {
    private Blindage() {
    }

    /** Source imposee pendant une explosion simulee (/base admin c4), null sinon. */
    @Nullable
    private static String sourceForcee;
    /** Blocs touches par la derniere explosion (retour de la commande admin). */
    private static int derniersTouches;
    private static int compteur;
    private static final Map<Class<?>, Method> CONTROLEUR = new HashMap<>();
    private static final LongArrayList PERIMES = new LongArrayList();

    /** Vrai si TOUTE la table de protection vaut dans ce niveau (dimension des bases, ou {@code blindage_partout}). */
    public static boolean actif(@Nullable Level level) {
        return level instanceof ServerLevel && (BasesApi.dansBunkers(level) || PillageConfig.BLINDAGE_PARTOUT.get());
    }

    /** Niveau de protection qui vaut ICI : toute la table si {@link #actif}, sinon aucun (0). */
    public static int niveauIci(Level level, BlockPos pos) {
        return actif(level) ? niveauEn(level, pos) : 0;
    }

    /** {@link #cle} pour les blocs qui comptent ICI (aucun hors de la dimension des bases, sauf {@code blindage_partout}). */
    @Nullable
    public static BlockPos cleIci(Level level, BlockPos pos, BlockState s) {
        return actif(level) ? cle(level, pos, s) : null;
    }

    // ------------------------------------------------------------------ resolution
    /**
     * Position « cle » d'un bloc protege : moitie basse d'une porte, controleur d'une piece de structure (porte HBM),
     * sinon la position elle-meme. Null si le bloc n'est pas protege.
     */
    @Nullable
    public static BlockPos cle(Level level, BlockPos pos, BlockState s) {
        if (Protection.partie(s)) {
            BlockPos c = controleur(level, pos);
            return c != null && Protection.niveau(level.getBlockState(c)) > 0 ? c.immutable() : null;
        }
        if (Protection.niveau(s) <= 0) return null;
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            BlockPos b = pos.below();
            if (level.getBlockState(b).is(s.getBlock())) return b;
        }
        return pos.immutable();
    }

    /** Niveau de protection du bloc a cette position (0 = aucun), en suivant portes et pieces de structure. */
    public static int niveauEn(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (Protection.partie(s)) {
            BlockPos c = controleur(level, pos);
            return c == null ? 0 : Protection.niveau(level.getBlockState(c));
        }
        return Protection.niveau(s);
    }

    /** Controleur d'une piece de structure (methode {@code getControllerPos()} de son BlockEntity, par reflexion). */
    @Nullable
    private static BlockPos controleur(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        try {
            Method m = CONTROLEUR.computeIfAbsent(be.getClass(), c -> {
                try {
                    return c.getMethod("getControllerPos");
                } catch (NoSuchMethodException ex) {
                    return null;
                }
            });
            return m == null ? null : m.invoke(be) instanceof BlockPos p ? p : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static String idBloc(BlockState s) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        return rl == null ? "?" : rl.toString();
    }

    /** Coups encaisses par le bloc cle (0 si aucun ou entree perimee). */
    public static int coups(ServerLevel level, BlockPos cle) {
        CoupsData.Coup c = CoupsData.de(level).get(cle.asLong());
        return c == null || !c.bloc.equals(idBloc(level.getBlockState(cle))) ? 0 : c.coups;
    }

    // ------------------------------------------------------------------ explosions
    /** Id de la source d'une explosion : ThrowableId (heralisz_tactical), id d'entite, ou null. */
    @Nullable
    static String sourceId(Explosion ex) {
        if (sourceForcee != null) return sourceForcee;
        Entity e = ex.getDirectSourceEntity();
        if (e == null) return null;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (type != null && type.getNamespace().equals("heralisz_tactical")) {
            try {
                CompoundTag t = new CompoundTag();
                e.saveWithoutId(t);
                String id = t.getCompound("Item").getCompound("tag").getString("ThrowableId");
                if (!id.isEmpty()) return id;
            } catch (RuntimeException ignore) {
                // entite illisible : id d'entite
            }
        }
        return type == null ? null : type.toString();
    }

    /**
     * Apres le coeur (HIGH) : blocs proteges retires de la liste, coups portes par la source, seulement la ou
     * {@link #actif} (ailleurs, aucun bloc protege : rien n'est change).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDetonate(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        derniersTouches = 0;
        // hors de la dimension des bases (sans blindage_partout), aucun bloc protege : l'explosion n'est pas touchee
        if (!actif(level)) return;
        e.getAffectedBlocks().removeIf(pos -> niveauEn(level, pos) > 0);
        String id = sourceId(e.getExplosion());
        Protection.Source src = Protection.source(id);
        if (src.coups() <= 0 || src.rayon() <= 0) return;
        Vec3 c = e.getExplosion().getPosition();
        int r = (int) Math.ceil(src.rayon());
        double r2 = (double) src.rayon() * src.rayon();
        boolean bunkers = BasesApi.dansBunkers(level);
        LongOpenHashSet vus = new LongOpenHashSet();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = (int) Math.floor(c.x), by = (int) Math.floor(c.y), bz = (int) Math.floor(c.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(bx + dx, by + dy, bz + dz);
                    double ex = m.getX() + 0.5 - c.x, ey = m.getY() + 0.5 - c.y, ez = m.getZ() + 0.5 - c.z;
                    if (ex * ex + ey * ey + ez * ez > r2) continue;
                    BlockState s = level.getBlockState(m);
                    if (s.isAir()) continue;
                    BlockPos k = cleIci(level, m, s);
                    if (k == null || !vus.add(k.asLong())) continue;
                    if (bunkers) {
                        Base b = BasesApi.baseA(level, k);
                        if (b == null || !BasesApi.constructible(b, k)) continue;
                    }
                    frapper(level, k, src.coups());
                    derniersTouches++;
                }
            }
        }
        if (derniersTouches > 0) {
            HeraliszBases.LOG.info("[bases] explosion {} en {} ({}) : {} bloc(s) protege(s) touche(s)", id, BlockPos.containing(c),
                    level.dimension().location(), derniersTouches);
        }
    }

    /** Ajoute des coups au bloc cle ; casse au niveau atteint. @return vrai si le bloc a cede */
    public static boolean frapper(ServerLevel level, BlockPos cle, int n) {
        BlockState s = level.getBlockState(cle);
        int niveau = Protection.niveau(s);
        if (niveau <= 0) return false;
        return fixer(level, cle, coups(level, cle) + n);
    }

    /** Fixe les coups du bloc cle (0 = intact) ; au niveau ou plus : rupture. @return vrai si le bloc a cede */
    public static boolean fixer(ServerLevel level, BlockPos cle, int total) {
        BlockState s = level.getBlockState(cle);
        int niveau = Protection.niveau(s);
        CoupsData d = CoupsData.de(level);
        long k = cle.asLong();
        if (niveau <= 0 || total <= 0) {
            d.retirer(k);
            fissure(level, cle, -1);
            return false;
        }
        if (total >= niveau) {
            d.retirer(k);
            fissure(level, cle, -1);
            rompre(level, cle, s);
            return true;
        }
        CoupsData.Coup c = d.get(k);
        String id = idBloc(s);
        if (c == null || !c.bloc.equals(id)) c = new CoupsData.Coup(id, total, System.currentTimeMillis());
        c.coups = total;
        c.le = System.currentTimeMillis();
        d.mettre(k, c);
        fissure(level, cle, stade(total, niveau));
        double x = cle.getX() + 0.5, y = cle.getY() + 0.5, z = cle.getZ() + 0.5;
        level.playSound(null, x, y, z, Sons.FISSURE.get(), SoundSource.BLOCKS, 1.4f, 0.9f + level.random.nextFloat() * 0.2f);
        level.sendParticles(BasesRegistry.POUSSIERE_COQUE.get(), x, y, z, 10, 0.45, 0.45, 0.45, 0.01);
        return false;
    }

    /** Stade de fissure 0..9 (10 = intact) : coups x 10 / niveau. */
    public static int stade(int coups, int niveau) {
        return Math.max(0, Math.min(9, coups * 10 / Math.max(1, niveau)));
    }

    private static void rompre(ServerLevel level, BlockPos cle, BlockState s) {
        double x = cle.getX() + 0.5, y = cle.getY() + 0.5, z = cle.getZ() + 0.5;
        level.playSound(null, x, y, z, Sons.RUPTURE.get(), SoundSource.BLOCKS, 2.0f, 0.9f + level.random.nextFloat() * 0.2f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), x, y, z, 40, 0.35, 0.35, 0.35, 0.15);
        level.sendParticles(BasesRegistry.POUSSIERE_COQUE.get(), x, y, z, 28, 0.5, 0.5, 0.5, 0.03);
        // aucun objet rendu : retrait direct (la moitie haute d'une porte suit, une porte HBM detruit ses pieces)
        level.setBlock(cle, Blocks.AIR.defaultBlockState(), 3);
        HeraliszBases.LOG.info("[bases] blindage {} rompu en {} {} {}", idBloc(s), cle.getX(), cle.getY(), cle.getZ());
    }

    // ------------------------------------------------------------------ fissures
    /** Identifiant de « casseur » du paquet de fissure (negatif : jamais celui d'une vraie entite), un par bloc. */
    private static int idFissure(long pos) {
        return -1 - (int) ((pos ^ (pos >>> 29)) & 0x3FFFFFFF);
    }

    /** Fissure d'un bloc a tous les joueurs a moins de 64 blocs ({@code stade} < 0 : effacee). */
    private static void fissure(ServerLevel level, BlockPos pos, int stade) {
        ClientboundBlockDestructionPacket pk = new ClientboundBlockDestructionPacket(idFissure(pos.asLong()), pos, stade);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(pos) < 64 * 64) p.connection.send(pk);
        }
    }

    /** Envoie a un joueur les fissures d'un chunk (perimees oubliees). */
    private static void envoyerChunk(ServerPlayer p, ServerLevel level, CoupsData d, long chunk) {
        LongSet s = d.duChunk(chunk);
        if (s == null || s.isEmpty()) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (LongIterator it = s.iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            m.set(k);
            if (!level.isLoaded(m)) continue;
            CoupsData.Coup c = d.get(k);
            BlockState st = level.getBlockState(m);
            int niveau = Protection.niveau(st);
            if (c == null || niveau <= 0 || !c.bloc.equals(idBloc(st))) {
                PERIMES.add(k);
                continue;
            }
            p.connection.send(new ClientboundBlockDestructionPacket(idFissure(k), m.immutable(), stade(c.coups, niveau)));
        }
    }

    private static void purgerPerimes(ServerLevel level, CoupsData d) {
        if (PERIMES.isEmpty()) return;
        for (int i = 0; i < PERIMES.size(); i++) {
            long k = PERIMES.getLong(i);
            d.retirer(k);
            fissure(level, BlockPos.of(k), -1);
        }
        PERIMES.clear();
    }

    @SubscribeEvent
    public static void onChunkVu(ChunkWatchEvent.Watch e) {
        ServerLevel level = e.getLevel();
        // tous les mondes : avec blindage_partout, un bloc peut etre fissure hors des bases (SavedData vide = rien a faire)
        CoupsData d = CoupsData.de(level);
        if (d.vide()) return;
        envoyerChunk(e.getPlayer(), level, d, e.getPos().toLong());
        purgerPerimes(level, d);
    }

    /** Renvoi periodique aux joueurs proches (le client efface une fissure non renouvelee au bout de 20 s). */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++compteur < PillageConfig.FISSURES_RAFRAICHIR_S.get() * 20) return;
        compteur = 0;
        MinecraftServer server = e.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            CoupsData d = CoupsData.de(level);
            if (d.vide()) continue;
            for (ServerPlayer p : level.players()) {
                int cx = p.getBlockX() >> 4, cz = p.getBlockZ() >> 4;
                for (int x = -3; x <= 3; x++)
                    for (int z = -3; z <= 3; z++) envoyerChunk(p, level, d, ChunkPos.asLong(cx + x, cz + z));
            }
            purgerPerimes(level, d);
        }
    }

    /** Un bloc protege casse a la main (membre) : ses coups sont oublies. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCasse(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockPos k = cleIci(level, e.getPos(), e.getState());
        if (k != null && CoupsData.de(level).get(k.asLong()) != null) {
            CoupsData.de(level).retirer(k.asLong());
            fissure(level, k, -1);
        }
    }

    // ------------------------------------------------------------------ infobulle
    /**
     * Clic droit accroupi, main vide, sur un bloc protege : infobulle (niveau, coups) sans utiliser le bloc. Priorite la
     * plus haute : l'evenement annule n'atteint ni les regles du coeur ni le bloc (porte, coffre).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClic(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (!p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()) return;
        ServerLevel level = (ServerLevel) e.getLevel();
        BlockState s = level.getBlockState(e.getPos());
        // hors du monde des bases (sans blindage_partout), rien ne repond (une porte cfm de la carte s'ouvre normalement)
        if (!actif(level)) return;
        BlockPos k = cle(level, e.getPos(), s);
        if (k == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        BlockState ks = level.getBlockState(k);
        int niveau = Protection.niveau(ks);
        BasesNet.toPlayer(p, new PillageNet.InfoBlindageS2C(k, idBloc(ks), niveau, coups(level, k)));
    }

    // ------------------------------------------------------------------ table
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        Protection.charger(e.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) envoyerTable(p);
    }

    public static void envoyerTable(ServerPlayer p) {
        BasesNet.toPlayer(p, new PillageNet.ProtectionS2C(Protection.brut(), Protection.partiesBrutes()));
    }

    // ------------------------------------------------------------------ admin
    /**
     * Explosion de C4 simulee (banc, admin) : vraie explosion du monde ({@code Level.explode}, rayon 2), traitee comme une
     * source {@code heralisz_grenade:c4} par tout le pipeline (coeur puis blindage). @return blocs proteges touches
     */
    public static int simulerC4(ServerLevel level, Vec3 centre) {
        sourceForcee = "heralisz_grenade:c4";
        derniersTouches = 0;
        try {
            level.explode(null, centre.x, centre.y, centre.z, 2.0f, Level.ExplosionInteraction.TNT);
        } finally {
            sourceForcee = null;
        }
        return derniersTouches;
    }
}

package fr.heraliz.crac.bases;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Les parcelles du monde du tutoriel des bases ({@code crac:tuto}, 28/10, demande du propriétaire).
 * <ul>
 *   <li>Monde plat et vide (data/crac/dimension/tuto.json : bedrock, terre, herbe ; ni arbres, ni plantes, ni structures,
 *   ni pluie ; aucune créature, {@link NatureBases}).</li>
 *   <li>Chaque joueur qui fait le tutoriel reçoit SA parcelle de {@link #TAILLE} × {@link #TAILLE} blocs (la première libre),
 *   gardée tant que dure son tutoriel (déconnexion comprise), libérée à la fin (cabane d'essai effacée avant). Les
 *   parcelles sont espacées de {@link #PAS} blocs (centre à centre) : deux joueurs en même temps ne se voient pas.</li>
 *   <li>Murs invisibles : un anneau de blocs barrière autour de la parcelle (posé une fois, à la première attribution,
 *   gardé ensuite) ; en plus, un joueur en survie ou en aventure sorti de sa parcelle (par-dessus le mur...) y est remis
 *   par une vraie téléportation (suivie par Spartan), jamais par un simple setPos.</li>
 *   <li>Bordure propre du monde (en Forge 1.20.1 chaque monde reçoit celle de la carte) : remise chaque seconde.</li>
 * </ul>
 * Données gardées avec le monde du tutoriel ({@code dimensions/crac/tuto/data/crac_tuto_parcelles.dat}).
 */
@EventBusSubscriber(modid = "crac")
public final class PlotsTuto extends SafeSavedData {
    private static final String ID = "crac_tuto_parcelles";
    /** Côté d'une parcelle (blocs). */
    public static final int TAILLE = 64;
    /** Écart entre les centres de deux parcelles voisines (blocs) : 192 blocs vides entre deux parcelles. */
    public static final int PAS = 256;
    /** Parcelles par ligne (et nombre de lignes) : 900 parcelles au plus. */
    private static final int COLONNES = 30;
    private static final int MAX = COLONNES * COLONNES;
    /** Hauteur des murs invisibles au-dessus du sol (au-delà, la téléportation de retour suffit). */
    private static final int HAUT_MURS = 64;
    /** Bordure du monde du tutoriel (couvre toutes les parcelles). */
    private static final int BORDURE = COLONNES * PAS + 512;

    /** Joueur -> numéro de sa parcelle. */
    private final Map<UUID, Integer> parcelles = new HashMap<>();
    /** Parcelles dont les murs sont déjà posés. */
    private final BitSet murs = new BitSet(MAX);
    private static int ticks;

    private PlotsTuto() {
    }

    // ---------------------------------------------------------------------------------------------- données

    @Nullable
    private static PlotsTuto get(MinecraftServer server) {
        ServerLevel l = server.getLevel(MondeBases.TUTO);
        if (l == null) {
            return null;
        }
        return l.getDataStorage().computeIfAbsent(PlotsTuto::load, () -> SafeSavedData.fresh(l, ID, new PlotsTuto()), ID);
    }

    private static PlotsTuto load(CompoundTag tag) {
        PlotsTuto d = new PlotsTuto();
        ListTag l = tag.getList("parcelles", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            try {
                CompoundTag c = l.getCompound(i);
                int n = c.getInt("n");
                if (n >= 0 && n < MAX) {
                    d.parcelles.put(c.getUUID("joueur"), n);
                }
            } catch (RuntimeException ex) {
                Crac.LOGGER.warn("[CRac] parcelle du tutoriel illisible ignorée ({})", ex.toString());
            }
        }
        for (int n : tag.getIntArray("murs")) {
            if (n >= 0 && n < MAX) {
                d.murs.set(n);
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        this.parcelles.forEach((u, n) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("joueur", u);
            c.putInt("n", n);
            l.add(c);
        });
        tag.put("parcelles", l);
        tag.putIntArray("murs", this.murs.stream().toArray());
        return tag;
    }

    // ---------------------------------------------------------------------------------------------- parcelles

    /** Centre (x, z) de la parcelle {@code n} : bloc du coin sud-est du carré central. */
    private static int centreX(int n) {
        return (n % COLONNES - COLONNES / 2) * PAS;
    }

    private static int centreZ(int n) {
        return (n / COLONNES - COLONNES / 2) * PAS;
    }

    /** Bloc minimal (x) de la parcelle ; la parcelle va de min à min + TAILLE - 1. */
    private static int minX(int n) {
        return centreX(n) - TAILLE / 2;
    }

    private static int minZ(int n) {
        return centreZ(n) - TAILLE / 2;
    }

    /** La parcelle du joueur, -1 s'il n'en a pas. */
    static int de(MinecraftServer server, UUID u) {
        PlotsTuto d = get(server);
        Integer n = d == null ? null : d.parcelles.get(u);
        return n == null ? -1 : n;
    }

    /** La parcelle du joueur ; s'il n'en a pas, la première libre lui est donnée (murs posés). -1 : monde absent ou plein. */
    static int attribuer(MinecraftServer server, UUID u) {
        PlotsTuto d = get(server);
        ServerLevel l = server.getLevel(MondeBases.TUTO);
        if (d == null || l == null) {
            return -1;
        }
        Integer deja = d.parcelles.get(u);
        if (deja != null) {
            d.poserMurs(l, deja);
            return deja;
        }
        BitSet prises = new BitSet(MAX);
        d.parcelles.values().forEach(prises::set);
        int n = prises.nextClearBit(0);
        if (n >= MAX) {
            Crac.LOGGER.error("[CRac] tutoriel : plus aucune parcelle libre ({} prises)", d.parcelles.size());
            return -1;
        }
        d.parcelles.put(u, n);
        d.setDirty();
        d.poserMurs(l, n);
        Crac.LOGGER.info("[CRac] tutoriel : parcelle {} ({} {}) donnée à {}", n, centreX(n), centreZ(n), u);
        return n;
    }

    /** Fin du tutoriel (cabane d'essai déjà effacée) : la parcelle redevient libre. */
    static void liberer(MinecraftServer server, UUID u) {
        PlotsTuto d = get(server);
        if (d != null && d.parcelles.remove(u) != null) {
            d.setDirty();
        }
    }

    /** Point d'arrivée de la parcelle du joueur (attribuée au besoin) : son centre, sur le sol. Null : monde absent ou plein. */
    @Nullable
    static Vec3 arrivee(ServerPlayer p) {
        int n = attribuer(p.server, p.getUUID());
        ServerLevel l = p.server.getLevel(MondeBases.TUTO);
        return n < 0 || l == null ? null : arrivee(l, n);
    }

    private static Vec3 arrivee(ServerLevel l, int n) {
        int x = centreX(n), z = centreZ(n);
        l.getChunk(x >> 4, z >> 4);
        int y = Math.max(l.getMinBuildHeight() + 1, l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    /** L'anneau de blocs barrière autour de la parcelle (une seule fois par parcelle). */
    private void poserMurs(ServerLevel l, int n) {
        if (this.murs.get(n)) {
            return;
        }
        int x0 = minX(n) - 1, z0 = minZ(n) - 1, x1 = minX(n) + TAILLE, z1 = minZ(n) + TAILLE;
        l.getChunk(centreX(n) >> 4, centreZ(n) >> 4);
        int sol = l.getHeight(Heightmap.Types.MOTION_BLOCKING, centreX(n), centreZ(n));
        int haut = Math.min(l.getMaxBuildHeight() - 1, sol + HAUT_MURS);
        BlockState barriere = Blocks.BARRIER.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = sol; y <= haut; y++) {
            for (int x = x0; x <= x1; x++) {
                l.setBlock(m.set(x, y, z0), barriere, Block.UPDATE_CLIENTS);
                l.setBlock(m.set(x, y, z1), barriere, Block.UPDATE_CLIENTS);
            }
            for (int z = z0 + 1; z < z1; z++) {
                l.setBlock(m.set(x0, y, z), barriere, Block.UPDATE_CLIENTS);
                l.setBlock(m.set(x1, y, z), barriere, Block.UPDATE_CLIENTS);
            }
        }
        this.murs.set(n);
        this.setDirty();
    }

    // ---------------------------------------------------------------------------------------------- murs invisibles

    /** Chaque tick : un joueur (survie, aventure) sorti de sa parcelle y est remis par une vraie téléportation. */
    @SubscribeEvent
    public static void tickJoueur(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !MondeBases.estTuto(p.level())
            || p.isCreative() || p.isSpectator()) {
            return;
        }
        int n = de(p.server, p.getUUID());
        if (n < 0) {
            return;   // sans parcelle (fini, staff…) : le tutoriel le ramène au spawn (Tutoriel.seconde)
        }
        double x0 = minX(n), z0 = minZ(n), x1 = x0 + TAILLE, z1 = z0 + TAILLE;
        double x = p.getX(), z = p.getZ();
        if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
            return;
        }
        ServerLevel l = p.serverLevel();
        p.stopRiding();
        double dehors = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
        if (dehors > 6.0) {
            Vec3 a = arrivee(l, n);
            p.teleportTo(l, a.x, a.y, a.z, p.getYRot(), p.getXRot());
        } else {
            double cx = Math.min(Math.max(x, x0 + 0.5), x1 - 0.5);
            double cz = Math.min(Math.max(z, z0 + 0.5), z1 - 0.5);
            p.teleportTo(l, cx, p.getY(), cz, p.getYRot(), p.getXRot());
        }
        p.resetFallDistance();
    }

    // ---------------------------------------------------------------------------------------------- bordure

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null || ++ticks % 20 != 0) {
            return;
        }
        ServerLevel l = e.getServer().getLevel(MondeBases.TUTO);
        if (l != null) {
            bordure(l);
        }
    }

    private static void bordure(ServerLevel l) {
        WorldBorder b = l.getWorldBorder();
        if (b.getCenterX() == 0.0 && b.getCenterZ() == 0.0 && Math.abs(b.getSize() - BORDURE) < 0.5) {
            return;
        }
        b.setCenter(0.0, 0.0);
        b.setSize(BORDURE);
        ClientboundInitializeBorderPacket pk = new ClientboundInitializeBorderPacket(b);
        for (ServerPlayer sp : l.players()) {
            sp.connection.send(pk);
        }
    }

    @SubscribeEvent
    public static void changement(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && MondeBases.estTuto(p.level())) {
            bordure(p.serverLevel());
            p.connection.send(new ClientboundInitializeBorderPacket(p.serverLevel().getWorldBorder()));
        }
    }
}

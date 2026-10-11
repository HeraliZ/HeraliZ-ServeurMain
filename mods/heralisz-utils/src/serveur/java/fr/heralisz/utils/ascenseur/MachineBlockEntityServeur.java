package fr.heralisz.utils.ascenseur;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Machine de l'ascenseur côté serveur (créée par Ascenseur.MACHINE_USINE dès que la partie serveur est dans le jar ; code
 * d'origine déplacé tel quel) : repère les paliers du puits, fait exister une cabine et une seule, répond aux appels,
 * sauvegarde. Avec le jar -complet, les machines du monde client sont aussi de cette classe : le ticker n'est donné
 * qu'au monde serveur (MachineBlock), comme à l'origine.
 */
public class MachineBlockEntityServeur extends MachineBlockEntity {
    public MachineBlockEntityServeur(BlockPos p, BlockState s) {
        super(p, s);
    }

    /** Sauvegarde complète : paliers (lus par MachineBlockEntity.load) puis la cabine. */
    @Override
    public void load(CompoundTag t) {
        super.load(t);
        cabineId = t.hasUUID("cabine") ? t.getUUID("cabine") : null;
    }

    /** Machine de chaque panneau d'appel déjà trouvé (serveur). */
    private static final Map<BlockPos, BlockPos> MACHINE_DU_PALIER = new ConcurrentHashMap<>();
    UUID cabineId;
    private int prochainScan;
    @Override
    void tickServeur(ServerLevel l) {
        if (--prochainScan <= 0) {
            prochainScan = 1200;
            scanner(l);
        }
        if (paliers.length > 0 && l.getGameTime() % 20 == 0) {
            assurerCabine(l);
        }
    }

    private void scanner(Level l) {
        List<Integer> trouves = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = worldPosition.getY() - 1; y >= l.getMinBuildHeight(); y--) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (l.getBlockState(m.set(worldPosition.getX() + dx, y, worldPosition.getZ() + dz)).getBlock() instanceof PalierBlock) {
                        if (!trouves.contains(y - 1)) {
                            trouves.add(y - 1);
                        }
                        MACHINE_DU_PALIER.put(m.immutable(), worldPosition);
                    }
                }
            }
        }
        int[] n = trouves.stream().mapToInt(Integer::intValue).sorted().toArray();
        if (!java.util.Arrays.equals(n, paliers)) {
            paliers = n;
            setChanged();
            l.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    CabineServeur cabineServeur(ServerLevel l) {
        if (cabineId != null) {
            Entity e = l.getEntity(cabineId);
            if (e instanceof CabineServeur c && c.isAlive()) {
                return c;
            }
        }
        List<CabineServeur> cs = l.getEntitiesOfClass(CabineServeur.class, puits());
        if (cs.isEmpty()) {
            return null;
        }
        for (int i = 1; i < cs.size(); i++) {                          // une seule cabine par puits
            cs.get(i).ejectPassengers();
            cs.get(i).discard();
        }
        cabineId = cs.get(0).getUUID();
        setChanged();
        return cs.get(0);
    }

    private void assurerCabine(ServerLevel l) {
        if (!l.areEntitiesLoaded(ChunkPos.asLong(worldPosition))) {
            return;                                                    // la cabine sauvegardée n'est peut-être pas encore là
        }
        if (cabineServeur(l) != null) {
            return;
        }
        if (!(Ascenseur.CABINE.get().create(l) instanceof CabineServeur c)) {   // créée par l'usine : version serveur
            return;
        }
        c.setPos(centreX(), paliers[paliers.length - 1] - Cabine.PONT, centreZ());
        c.facade(facade());
        l.addFreshEntity(c);
        cabineId = c.getUUID();
        setChanged();
    }

    /** Appel d'un panneau : la cabine vient au palier, ou part vers le palier suivant si elle y est déjà. */
    void appel(ServerLevel l, ServerPlayer p, int deck, boolean monter) {
        CabineServeur c = cabineServeur(l);
        if (c == null || paliers.length < 2) {
            p.displayClientMessage(Component.translatableWithFallback("heralisz_utils.ascenseur.demarrage",
                    "L'ascenseur se met en route… réessaie dans un instant"), true);
            return;
        }
        if (c.enMarche()) {
            p.displayClientMessage(Component.translatableWithFallback("heralisz_utils.ascenseur.en_marche", "L'ascenseur est déjà en marche"), true);
            return;
        }
        int ici = -1;
        for (int i = 0; i < paliers.length; i++) {
            if (paliers[i] == deck) {
                ici = i;
            }
        }
        if (ici < 0) {
            return;
        }
        if (Math.abs(c.plancher() - deck) < 0.5) {
            int suivant = monter ? ici + 1 : ici - 1;
            if (suivant < 0 || suivant >= paliers.length) {
                p.displayClientMessage(monter
                        ? Component.translatableWithFallback("heralisz_utils.ascenseur.tout_en_haut", "Tout en haut : on ne peut pas monter plus")
                        : Component.translatableWithFallback("heralisz_utils.ascenseur.tout_en_bas", "Tout en bas : on ne peut pas descendre plus"), true);
                return;
            }
            c.allerA(paliers[suivant]);
            p.displayClientMessage(monter
                    ? Component.translatableWithFallback("heralisz_utils.ascenseur.monte", "Ça monte… accroche-toi")
                    : Component.translatableWithFallback("heralisz_utils.ascenseur.descend", "Ça descend… accroche-toi"), true);
        } else {
            c.allerA(deck);
            p.displayClientMessage(Component.translatableWithFallback("heralisz_utils.ascenseur.arrive", "L'ascenseur arrive"), true);
        }
    }

    /** Machine d'un panneau d'appel (au-dessus de lui, à moins de 5 blocs de l'axe), ou null. */
    static MachineBlockEntityServeur trouver(Level l, BlockPos palier) {
        BlockPos connue = MACHINE_DU_PALIER.get(palier);
        if (connue != null && l.getBlockEntity(connue) instanceof MachineBlockEntityServeur m) {
            return m;
        }
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = palier.getY() + 1; y <= Math.min(l.getMaxBuildHeight() - 1, palier.getY() + 220); y++) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (l.getBlockEntity(q.set(palier.getX() + dx, y, palier.getZ() + dz)) instanceof MachineBlockEntityServeur m) {
                        m.scanner(l);
                        MACHINE_DU_PALIER.put(palier, m.worldPosition);
                        return m;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putIntArray("paliers", paliers);
        if (cabineId != null) {
            t.putUUID("cabine", cabineId);
        }
    }
}

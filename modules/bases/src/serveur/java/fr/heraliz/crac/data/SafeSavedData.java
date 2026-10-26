package fr.heraliz.crac.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import fr.heraliz.crac.Crac;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

/**
 * Saved data that is never lost to a bad file or a bad save.
 * <ul>
 *   <li>Saves are atomic (temporary file, then rename): a crash in the middle of a save cannot truncate the file.</li>
 *   <li>When the file exists but Minecraft fails to read it, Minecraft logs the error and starts from empty data that
 *   would overwrite the file at the next save. {@link #fresh} copies the file aside first (or, if even that fails,
 *   never writes over it).</li>
 * </ul>
 * Loaders still have to read each entry on its own, so that one bad entry never costs the others.
 */
public abstract class SafeSavedData extends SavedData {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** The file on disk could not be read nor copied aside: it must not be overwritten. */
    private boolean writeProtected;
    /** Started empty in place of a lost file: see {@link #isStandIn}. */
    private boolean standIn;
    /**
     * Écritures en fond (25/10, perf) : une seule file, dans l'ordre d'arrivée, attendue à l'arrêt du serveur
     * ({@link #attendreEcritures}). Seulement pour les données qui le demandent ({@link #ecritureEnFond}).
     */
    private static final java.util.concurrent.ExecutorService FOND = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CRac-sauvegarde");
        t.setDaemon(true);
        return t;
    });
    /** Une écriture en fond a échoué : la donnée redevient « à sauver » à la sauvegarde suivante. */
    private final java.util.concurrent.atomic.AtomicBoolean echecFond = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * For the factory given to {@code computeIfAbsent}, which Minecraft only calls when there is no file or when it
     * failed to read the file. Returns {@code data}, after protecting the unreadable file if there is one.
     */
    public static <T extends SafeSavedData> T fresh(ServerLevel level, String id, T data) {
        guardUnreadable(level, id, data);
        return data;
    }

    /** Same as {@link #fresh}; returns true when an unreadable file was found (and copied aside or protected). */
    protected static boolean guardUnreadable(ServerLevel level, String id, SafeSavedData data) {
        Path file = dataFile(level, id);
        if (!Files.exists(file)) {
            return false;
        }
        data.standIn = true;
        Path copy = keepAside(file);
        if (copy != null) {
            Crac.LOGGER.error("[CRac] {} could not be read (see the error above) and starts empty. The unreadable file was copied to {}:"
                + " stop the server and put it back if it can be repaired.", file, copy.getFileName());
        } else {
            data.writeProtected = true;
            Crac.LOGGER.error("[CRac] {} could not be read (see the error above) nor copied aside: it starts empty and will NOT be saved"
                + " until the server restarts, so the file stays as it is.", file);
        }
        return true;
    }

    /** Copies {@code file} to {@code <name>.unreadable-<time>} next to it; null when the copy failed. */
    public static Path keepAside(Path file) {
        return copyAside(file, "unreadable");
    }

    /** Copies {@code file} to {@code <name>.<label>-<time>} next to it; null when the copy failed. */
    public static Path copyAside(Path file, String label) {
        Path copy = file.resolveSibling(file.getFileName() + "." + label + "-" + LocalDateTime.now().format(STAMP));
        try {
            return Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Crac.LOGGER.error("[CRac] Could not copy {} to {}", file, copy, e);
            return null;
        }
    }

    /** Where {@code DimensionDataStorage} keeps {@code <id>.dat} for this level. */
    public static Path dataFile(ServerLevel level, String id) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT).normalize();
        return DimensionType.getStorageFolder(level.dimension(), root).resolve("data").resolve(id + ".dat");
    }

    /** Writes the tag to a temporary file next to {@code file}, then renames it over {@code file}. */
    public static void writeAtomically(CompoundTag root, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        NbtIo.writeCompressed(root, tmp.toFile());
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean isWriteProtected() {
        return this.writeProtected;
    }

    /** Never write this data over its file (until the server restarts): the file holds data this version must keep. */
    protected void protectFile() {
        this.writeProtected = true;
    }

    /**
     * Whether this data started empty in place of a lost file: one that could not be read, or that is missing while other
     * data refers to it. What it lacks may still exist, so nothing may be deleted for being absent from it. Lasts until
     * the server stops (the file is put back with the server stopped).
     */
    public boolean isStandIn() {
        return this.standIn;
    }

    /** Marks this data as a stand-in for a lost file (see {@link #isStandIn}); tests clear it again. */
    public void setStandIn(boolean standIn) {
        this.standIn = standIn;
    }

    /**
     * Vrai pour une grosse donnée dont l'écriture (compression comprise) part sur le fil de sauvegarde de CRac : le contenu
     * est relevé sur le fil du serveur, seul le disque est fait en fond. Faux par défaut (écriture tout de suite).
     */
    protected boolean ecritureEnFond() {
        return false;
    }

    /** Attend la fin des écritures en fond (arrêt du serveur), 60 s au plus. */
    public static void attendreEcritures() {
        try {
            FOND.submit(() -> { }).get(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            Crac.LOGGER.error("[CRac] Sauvegardes en fond pas terminées à l'arrêt", e);
        }
    }

    @Override
    public void save(@NotNull File file) {
        if (this.echecFond.getAndSet(false)) {
            this.setDirty(true);
        }
        if (!this.isDirty() || this.writeProtected) {
            return;
        }
        CompoundTag root = new CompoundTag();
        try {
            root.put("data", this.save(new CompoundTag()));
        } catch (RuntimeException e) {
            // relevé impossible (donnée abîmée) : le fichier d'avant est gardé tel quel et le serveur ne plante pas
            Crac.LOGGER.error("[CRac] relevé de {} impossible, fichier précédent gardé", file, e);
            return;
        }
        NbtUtils.addCurrentDataVersion(root);
        if (this.ecritureEnFond()) {
            // 25/10 (perf) : 12 000 pièces = ~5 Mo de NBT, ~140 ms de compression et d'écriture mesurés sur un PC, sur le
            // fil du serveur à chaque sauvegarde automatique ; seul le relevé (ci-dessus) reste sur le fil du serveur
            this.setDirty(false);
            Path path = file.toPath();
            FOND.execute(() -> {
                try {
                    writeAtomically(root, path);
                } catch (IOException | RuntimeException e) {
                    this.echecFond.set(true);
                    Crac.LOGGER.error("[CRac] Could not save {}", file, e);
                }
            });
            return;
        }
        try {
            writeAtomically(root, file.toPath());
            this.setDirty(false);
        } catch (IOException e) {
            // stays dirty: the next save tries again
            Crac.LOGGER.error("[CRac] Could not save {}", file, e);
        }
    }
}

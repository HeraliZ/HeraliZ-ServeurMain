package fr.heraliz.crac;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * crac-server.toml used to keep its claim, upkeep and placable settings at the root of the file. Forge drops the keys
 * its spec does not know and resets them to their default, so the file is registered with a reader that first moves
 * such old keys to their section: a server keeps its values when it updates.
 */
public final class ConfigMigration {
    public static final String FILE_NAME = "crac-server.toml";
    /** Root key of the old layout -> dotted path of the same setting now. */
    static final Map<String, String> MOVED;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        for (String key : List.of("viewRadius", "protectOutsideClaims", "allowBreakInClaim", "radiusL1", "radiusL2", "radiusL3",
            "yRadiusL1", "yRadiusL2", "yRadiusL3", "upgradeItemL1L2", "upgradeItemL2L3", "upgradeCostL1L2", "upgradeCostL2L3",
            "spacingMargin", "minY", "maxY")) {
            m.put(key, "claims." + key);
        }
        m.put("upkeepHours", "upkeep.hours");
        for (String res : List.of("Wood", "Stone", "Metal", "Armored")) {
            m.put("upkeepItem" + res, "upkeep.item" + res);
            m.put("upkeepPerPiece" + res, "upkeep.perPiece" + res);
        }
        m.put("upkeepSlotCapacity", "upkeep.slotCapacity");
        m.put("adventureReach", "placables.reach");
        m.put("adventureCooldownMs", "placables.cooldownMs");
        MOVED = Collections.unmodifiableMap(m);
    }

    private ConfigMigration() {
    }

    /** Registers crac-server.toml for {@code container}, in place of {@code ModLoadingContext.registerConfig}. */
    public static void register(ModContainer container) {
        container.addConfig(new MigratingConfig(container));
    }

    /**
     * Moves the old root keys of {@code data} into their section. A value already at the new path wins and the old key
     * is only dropped. Returns how many old keys were found.
     */
    public static int migrate(CommentedConfig data) {
        int found = 0;
        for (Map.Entry<String, String> e : MOVED.entrySet()) {
            List<String> from = List.of(e.getKey());
            Object value = data.get(from);
            if (value == null || value instanceof UnmodifiableConfig) {
                continue;
            }
            List<String> to = List.of(e.getValue().split("\\."));
            if (data.get(to) == null) {
                data.set(to, value);
            }
            data.remove(from);
            found++;
        }
        return found;
    }

    /**
     * Migrates the file on disk before Forge opens it: once open, Forge watches the file and would reload and correct
     * it on its watcher thread, at the same time as it checks it on the server thread. A world without the file yet
     * starts from the copy of defaultconfigs/ that Forge would make.
     */
    static void migrateFile(Path file) {
        try {
            if (!Files.exists(file)) {
                Path defaults = FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()).resolve(file.getFileName());
                if (!Files.exists(defaults)) {
                    return;
                }
                Files.createDirectories(file.getParent());
                Files.copy(defaults, file);
            }
            try (CommentedFileConfig data = CommentedFileConfig.builder(file).sync().preserveInsertionOrder().build()) {
                data.load();
                int moved = migrate(data);
                if (moved > 0) {
                    data.save();
                    Crac.LOGGER.info("[CRac] {}: moved {} settings from the root of the file into their section", file, moved);
                }
            }
        } catch (IOException | RuntimeException e) {
            // an unreadable file is reported by Forge when it opens it
            Crac.LOGGER.warn("[CRac] Could not check {} for settings of the old layout", file, e);
        }
    }

    /** crac-server.toml with a reader that migrates the old layout before Forge opens the file. */
    private static final class MigratingConfig extends ModConfig {
        private static final ConfigFileTypeHandler HANDLER = new ConfigFileTypeHandler() {
            @Override
            public Function<ModConfig, CommentedFileConfig> reader(Path configBasePath) {
                Function<ModConfig, CommentedFileConfig> read = super.reader(configBasePath);
                return config -> {
                    migrateFile(configBasePath.resolve(config.getFileName()));
                    return read.apply(config);
                };
            }
        };

        MigratingConfig(ModContainer container) {
            super(ModConfig.Type.SERVER, Config.SPEC, container, FILE_NAME);
        }

        @Override
        public ConfigFileTypeHandler getHandler() {
            return HANDLER;
        }
    }
}

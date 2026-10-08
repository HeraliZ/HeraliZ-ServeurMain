package com.Spartan.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Écrit build/config-modele/spartan-common.toml : la config par défaut, au format exact de Forge (même spécification
 * que le mod). Sert à refaire serveur/config/spartan-common.toml quand des réglages sont ajoutés.
 */
class ConfigModeleTest {
    @Test
    void writesTheDefaultConfig() throws Exception {
        CommentedConfig c = TomlFormat.newConfig();
        SpartanConfig.SPEC.correct(c);
        Path out = Path.of("build/config-modele/spartan-common.toml");
        Files.createDirectories(out.getParent());
        TomlFormat.instance().createWriter().write(c, out, WritingMode.REPLACE);
        String s = Files.readString(out);
        assertTrue(s.contains("discordSanctionsWebhookUrl") && s.contains("alertOnly"), s);
    }
}

package com.Spartan.scan.collect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.Mixin;

class MixinTargetsTest {
    /** Mixin factice : deux façons de désigner une cible. */
    @Mixin(value = String.class, targets = "net.minecraft.client.renderer.LevelRenderer")
    abstract static class SampleMixin {
    }

    @Test
    void targetsAreReadFromTheMixinAnnotation() throws Exception {
        byte[] bytes;
        try (InputStream in = MixinTargetsTest.class.getResourceAsStream("MixinTargetsTest$SampleMixin.class")) {
            bytes = in.readAllBytes();
        }
        Set<String> targets = MixinTargets.targetsOf(bytes);
        assertTrue(targets.contains("java.lang.String"), targets.toString());
        assertTrue(targets.contains("net.minecraft.client.renderer.LevelRenderer"), targets.toString());
        assertEquals(2, targets.size());
    }

    @Test
    void classWithoutMixinHasNoTarget() throws Exception {
        try (InputStream in = MixinTargetsTest.class.getResourceAsStream("MixinTargetsTest.class")) {
            assertTrue(MixinTargets.targetsOf(in.readAllBytes()).isEmpty());
        }
    }

    @Test
    void mixinClassesOfAConfig() {
        var cfg = JsonParser.parseString("{\"package\": \"dev.cheat.mixin\", \"mixins\": [\"A\"],"
                + " \"client\": [\"render.B\"], \"server\": [\"C\"], \"injectors\": {}}").getAsJsonObject();
        assertEquals(List.of("dev.cheat.mixin.A", "dev.cheat.mixin.render.B", "dev.cheat.mixin.C"), MixinTargets.mixinClasses(cfg));
    }
}

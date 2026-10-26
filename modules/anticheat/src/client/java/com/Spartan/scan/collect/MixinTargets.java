package com.Spartan.scan.collect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Classes du jeu modifiées par une config Mixin, lues directement dans le jar :
 * la config JSON liste ses classes Mixin, et chaque classe Mixin porte
 * l'annotation @Mixin(cible). On ne dépend pas de l'état interne de Mixin (qui
 * ne garde pas ses configs une fois appliquées).
 */
public final class MixinTargets {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

    private MixinTargets() {
    }

    /** Noms complets des classes Mixin déclarées par une config (communes, client et serveur). */
    public static List<String> mixinClasses(JsonObject config) {
        List<String> out = new ArrayList<>();
        String pkg = config.has("package") && config.get("package").isJsonPrimitive()
                ? config.get("package").getAsString() : "";
        for (String key : List.of("mixins", "client", "server")) {
            if (!config.has(key) || !config.get(key).isJsonArray()) {
                continue;
            }
            for (JsonElement e : config.getAsJsonArray(key)) {
                if (e.isJsonPrimitive() && out.size() < 512) {
                    out.add(pkg.isEmpty() ? e.getAsString() : pkg + "." + e.getAsString());
                }
            }
        }
        return out;
    }

    /** Cibles d'une classe Mixin (@Mixin(value = X.class) ou @Mixin(targets = "x.Y")). */
    public static Set<String> targetsOf(byte[] mixinClass) {
        Set<String> out = new TreeSet<>();
        new ClassReader(mixinClass).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MIXIN.equals(descriptor)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!"value".equals(name) && !"targets".equals(name)) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String n, Object value) {
                                if (value instanceof Type t) {
                                    out.add(t.getClassName());
                                } else if (value instanceof String s) {
                                    out.add(s.replace('/', '.'));
                                }
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return out;
    }
}

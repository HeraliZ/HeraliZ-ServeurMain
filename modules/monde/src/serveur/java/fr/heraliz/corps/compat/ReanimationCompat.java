package fr.heraliz.corps.compat;

import fr.heraliz.corps.HeralizCorps;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Lien souple avec Reanimation (par reflexion, aucune dependance) : finition utilisee par un bourreau. Au moment de la
 * mort par execution ({@code reanimation:execution}), l'execution du bourreau est encore enregistree
 * ({@code Executions.of(bourreau)}, retiree au tick 118 de la scene, la mort arrive au tick 96) : on y lit
 * {@code type.id} (« karambit », « coup_de_grace »...), traduit chez le client par {@code execution.reanimation.<id>}.
 */
public final class ReanimationCompat {
    private ReanimationCompat() {
    }

    /** Ticks entre le coup fatal d'une execution et la fin de sa scene (118 + 98 - 96) : la victime y est encore montree. */
    public static final int SCENE_APRES_COUP = 120;

    private static boolean pret;
    @Nullable
    private static Method of;
    @Nullable
    private static Field type, id;

    private static void resoudre() {
        if (pret) return;
        pret = true;
        if (ModList.get() == null || !ModList.get().isLoaded("reanimation")) return;
        try {
            Class<?> executions = Class.forName("fr.heraliz.reanimation.execution.Executions");
            of = executions.getMethod("of", Entity.class);
            type = Class.forName("fr.heraliz.reanimation.execution.Executions$Execution").getField("type");
            id = Class.forName("fr.heraliz.reanimation.execution.ExecutionType").getField("id");
        } catch (ReflectiveOperationException | LinkageError e) {
            of = null;
            HeralizCorps.LOG.warn("[corps] Reanimation present mais API inattendue : finition des executions inconnue ({})", e.toString());
        }
    }

    /** Identifiant de la finition en cours du bourreau, ou null. */
    @Nullable
    public static String finition(@Nullable Entity bourreau) {
        if (bourreau == null) return null;
        resoudre();
        if (of == null) return null;
        try {
            Object x = of.invoke(null, bourreau);
            if (x == null) return null;
            Object t = type.get(x);
            return t == null ? null : (String) id.get(t);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}

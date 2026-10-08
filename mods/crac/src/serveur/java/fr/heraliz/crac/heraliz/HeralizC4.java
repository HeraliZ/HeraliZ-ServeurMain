package fr.heraliz.crac.heraliz;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import fr.heraliz.crac.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * The C4 of the server: a {@code heralisz_tactical} sticky explosive whose item carries {@code ThrowableId}
 * {@code heralisz_grenade:c4} (list {@code heraliz.c4ThrowableIds}). Its blast is a {@code CustomExplosion} of radius 7.5
 * whose direct source is the stuck entity; read here without depending on that mod (reflection on its public methods,
 * NBT as a fallback, the way HeraliZ Bases reads it for its armour plating).
 */
public final class HeralizC4 {
    private static final String TACTICAL = "heralisz_tactical";
    private static final Map<Class<?>, Method> GET_ITEM = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> GET_STUCK = new ConcurrentHashMap<>();
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private HeralizC4() {
    }

    /** The stuck C4 entity behind this blast, or null when the blast is something else. */
    @Nullable
    public static Entity c4Of(Explosion explosion) {
        Entity e = explosion.getDirectSourceEntity();
        if (e == null) {
            return null;
        }
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (type == null || !TACTICAL.equals(type.getNamespace())) {
            return null;
        }
        String id = throwableId(e);
        if (id == null) {
            return null;
        }
        List<? extends String> ids = Config.heralizC4Ids.get();
        for (String s : ids) {
            if (s.trim().equalsIgnoreCase(id)) {
                return e;
            }
        }
        return null;
    }

    /** {@code ThrowableId} of the item a heralisz_tactical throwable entity carries, or null. */
    @Nullable
    static String throwableId(Entity e) {
        Method m = GET_ITEM.computeIfAbsent(e.getClass(), c -> find(c, "getItem"));
        if (m != NONE) {
            try {
                if (m.invoke(e) instanceof ItemStack stack && stack.getTag() != null) {
                    String id = stack.getTag().getString("ThrowableId");
                    return id.isEmpty() ? null : id;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall back to the saved data
            }
        }
        try {
            CompoundTag t = new CompoundTag();
            e.saveWithoutId(t);
            String id = t.getCompound("Item").getCompound("tag").getString("ThrowableId");
            return id.isEmpty() ? null : id;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** The block the C4 is stuck to ({@code StickyGrenadeEntity.getStuckBlockPos()}), or null (stuck to an entity, unknown). */
    @Nullable
    public static BlockPos stuckBlock(Entity c4) {
        Method m = GET_STUCK.computeIfAbsent(c4.getClass(), c -> find(c, "getStuckBlockPos"));
        if (m == NONE) {
            return null;
        }
        try {
            return m.invoke(c4) instanceof BlockPos p ? p.immutable() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method find(Class<?> c, String name) {
        try {
            return c.getMethod(name);
        } catch (NoSuchMethodException | SecurityException e) {
            return NONE;
        }
    }
}

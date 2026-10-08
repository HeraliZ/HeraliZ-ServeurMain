package fr.heraliz.crac.admin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.network.S2CAdminData;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import net.minecraftforge.common.ForgeConfigSpec.ValueSpec;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and edits the values of crac-server.toml and crac-building.toml for the admin panel, validated against the
 * spec of their file. Paths of crac-building.toml values start with {@link #BUILDING_PREFIX}.
 */
public final class AdminConfigAccess {
    public static final int TYPE_BOOL = 0;
    public static final int TYPE_INT = 1;
    public static final int TYPE_DOUBLE = 2;
    public static final int TYPE_STRING = 3;
    public static final int TYPE_LIST = 4;
    /** Most entries a list value may get from the panel. */
    public static final int MAX_LIST_ENTRIES = 256;
    /** Panel path prefix of crac-building.toml values: they get their own tab and cannot clash with crac-server.toml. */
    public static final String BUILDING_PREFIX = "building.";
    /** Sections never shown or edited from the panel (secrets belong in AdminConfig, but keep a guard). */
    private static final String HIDDEN_SECTION = "admin.";
    private static Map<String, ConfigValue<?>> cache;
    /** Spec (file) of each panel path. */
    private static Map<String, ForgeConfigSpec> specs;
    /** Specs edited since the last {@link #save()}; server thread only. By identity: a spec's equals() is its content's. */
    private static final Set<ForgeConfigSpec> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());

    private AdminConfigAccess() {
    }

    public static synchronized Map<String, ConfigValue<?>> values() {
        if (cache == null) {
            Map<String, ConfigValue<?>> out = new LinkedHashMap<>();
            Map<String, ForgeConfigSpec> owners = new HashMap<>();
            collect(Config.class, "", Config.SPEC, out, owners);
            collect(BuildConfig.class, BUILDING_PREFIX, BuildConfig.SPEC, out, owners);
            specs = owners;
            cache = out;
        }
        return cache;
    }

    /** The spec a panel path belongs to, or null for an unknown path. */
    @Nullable
    public static synchronized ForgeConfigSpec specOf(String path) {
        values();
        return specs.get(path);
    }

    /** Every static ConfigValue of {@code holder}, also inside static maps (per tier, per piece kind...). */
    private static void collect(Class<?> holder, String prefix, ForgeConfigSpec spec, Map<String, ConfigValue<?>> out, Map<String, ForgeConfigSpec> owners) {
        for (Field f : holder.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object o = f.get(null);
                if (o instanceof ConfigValue<?> cv) {
                    put(out, owners, prefix, spec, cv);
                } else if (o instanceof Map<?, ?> map) {
                    for (Object v : map.values()) {
                        if (v instanceof ConfigValue<?> cv) {
                            put(out, owners, prefix, spec, cv);
                        }
                    }
                }
            } catch (Exception e) {
                Crac.LOGGER.warn("Admin config: cannot read field {}", f.getName(), e);
            }
        }
    }

    private static void put(Map<String, ConfigValue<?>> out, Map<String, ForgeConfigSpec> owners, String prefix, ForgeConfigSpec spec, ConfigValue<?> cv) {
        String path = prefix + path(cv);
        if (!path.startsWith(HIDDEN_SECTION)) {
            out.put(path, cv);
            owners.put(path, spec);
        }
    }

    private static String path(ConfigValue<?> cv) {
        return String.join(".", cv.getPath());
    }

    public static int typeOf(ConfigValue<?> cv) {
        if (cv instanceof BooleanValue) {
            return TYPE_BOOL;
        } else if (cv instanceof IntValue) {
            return TYPE_INT;
        } else if (cv instanceof DoubleValue) {
            return TYPE_DOUBLE;
        } else {
            return cv.getDefault() instanceof List ? TYPE_LIST : TYPE_STRING;
        }
    }

    public static String asString(ConfigValue<?> cv) {
        Object v = cv.get();
        if (!(v instanceof List<?> list)) {
            return String.valueOf(v);
        } else {
            List<String> parts = new ArrayList<>();

            for (Object o : list) {
                parts.add(String.valueOf(o));
            }

            return String.join(", ", parts);
        }
    }

    /**
     * Parses {@code raw} for {@code cv} and checks it against {@code spec} (ranges, list element validators). Returns the
     * value to store, or null when it is not valid. Booleans must read "true" or "false": a typo is refused, not false.
     */
    @Nullable
    public static Object parse(ForgeConfigSpec spec, ConfigValue<?> cv, String raw) {
        if (!(spec.getSpec().get(cv.getPath()) instanceof ValueSpec vs)) {
            return null;
        }
        String s = raw.trim();
        Object value;
        try {
            switch (typeOf(cv)) {
                case TYPE_BOOL -> {
                    String l = s.toLowerCase(Locale.ROOT);
                    if (!l.equals("true") && !l.equals("false")) {
                        return null;
                    }
                    value = Boolean.parseBoolean(l);
                }
                case TYPE_INT -> value = Integer.parseInt(s);
                case TYPE_DOUBLE -> {
                    double d = Double.parseDouble(s);
                    if (!Double.isFinite(d)) {
                        return null;
                    }
                    value = d;
                }
                case TYPE_LIST -> {
                    List<String> list = new ArrayList<>();
                    for (String part : s.split(",")) {
                        String t = part.trim();
                        if (!t.isEmpty()) {
                            list.add(t);
                        }
                    }
                    if (list.size() > MAX_LIST_ENTRIES) {
                        return null;
                    }
                    value = list;
                }
                default -> {
                    if (!(cv.getDefault() instanceof String)) {
                        return null;
                    }
                    value = s;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        // same check Forge runs when it reloads the file (an empty list passes it and is kept)
        if (!vs.test(value)) {
            return null;
        }
        // the panel must still be able to show the value afterwards
        if (value instanceof List<?> list && String.join(", ", list.stream().map(String::valueOf).toList()).length() > S2CAdminData.MAX_VALUE_CHARS) {
            return null;
        }
        return value;
    }

    /** Applies one panel edit. Returns false (and changes nothing) for an unknown path or an invalid value. */
    @SuppressWarnings("unchecked")
    public static boolean apply(String path, String raw) {
        ConfigValue<?> cv = values().get(path);
        ForgeConfigSpec spec = specOf(path);
        if (cv == null || spec == null) {
            return false;
        }
        Object value = parse(spec, cv, raw);
        if (value == null) {
            Crac.LOGGER.warn("Admin config: refused value '{}' for {}", raw, path);
            return false;
        }
        ((ConfigValue<Object>) cv).set(value);
        DIRTY.add(spec);
        return true;
    }

    /** Writes the files edited since the last save. */
    public static void save() {
        for (ForgeConfigSpec spec : DIRTY) {
            try {
                spec.save();
            } catch (Exception e) {
                Crac.LOGGER.error("Admin config: save failed", e);
            }
        }
        DIRTY.clear();
    }
}

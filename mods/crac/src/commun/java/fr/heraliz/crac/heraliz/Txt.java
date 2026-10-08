package fr.heraliz.crac.heraliz;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Player texts of CRac, translated on the client (assets/crac/lang: fr_fr, en_us). Many rules hand their refusal back
 * as a plain {@code String} (placement rules, claim checks, shared by the client ghost and the server): such a string is
 * either a translation key starting with {@code crac.} with its arguments joined by {@link #SEP}, built by
 * {@link #k}, or (anything else) shown as it is.
 */
public final class Txt {
    /** Separates a key from its arguments inside an encoded message (ASCII unit separator: never typed by anyone). */
    public static final char SEP = '\u001F';

    private Txt() {
    }

    /** An encoded message: {@code key} and its arguments, for code that passes messages around as strings. */
    public static String k(String key, Object... args) {
        if (args.length == 0) {
            return key;
        }
        StringBuilder sb = new StringBuilder(key);
        for (Object a : args) {
            sb.append(SEP).append(a instanceof Component c ? c.getString() : String.valueOf(a));
        }
        return sb.toString();
    }

    /** The component of an encoded message (see {@link #k}); any other text is shown as it is. */
    public static MutableComponent c(@Nullable String msg) {
        if (msg == null || msg.isEmpty()) {
            return Component.empty();
        }
        if (!msg.startsWith("crac.") || msg.indexOf(' ') >= 0) {
            return Component.literal(msg);
        }
        int sep = msg.indexOf(SEP);
        if (sep < 0) {
            return Component.translatable(msg);
        }
        String[] parts = msg.split(String.valueOf(SEP), -1);
        Object[] args = new Object[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return Component.translatable(parts[0], args);
    }

    public static MutableComponent red(@Nullable String msg) {
        return c(msg).withStyle(ChatFormatting.RED);
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static MutableComponent red(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.RED);
    }

    public static MutableComponent green(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GREEN);
    }

    public static MutableComponent yellow(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.YELLOW);
    }

    public static MutableComponent gray(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }
}

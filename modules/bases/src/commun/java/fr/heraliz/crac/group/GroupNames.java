package fr.heraliz.crac.group;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Rules for group names and tags, shared by the create screen and the server. Names end up in chat lines and screens,
 * so formatting codes (§), control characters and invisible or bidirectional characters are refused: they could fake
 * a server message or another player's text. Tags are 3-4 ASCII letters so look-alike letters cannot copy a tag.
 */
public final class GroupNames {
    /** Longest name any packet carries; {@code group.groupNameMaxLength} (3..64) is the real limit. */
    public static final int WIRE_MAX_NAME = 64;
    public static final int WIRE_MAX_TAG = 8;
    public static final int TAG_MIN = 3;
    public static final int TAG_MAX = 4;
    private static final String NAME_PUNCTUATION = " -_'.,!?&()#+*:";

    private GroupNames() {
    }

    /** Whether {@code c} may appear in a group name: letters and digits of any script, space and a little punctuation. */
    public static boolean isNameChar(char c) {
        if (c == '§' || Character.isISOControl(c) || Character.isSurrogate(c) || isDefaultIgnorable(c)) {
            return false;
        }
        return Character.isLetterOrDigit(c) || NAME_PUNCTUATION.indexOf(c) >= 0;
    }

    /**
     * Unicode's default-ignorable code points (in the BMP; the others are surrogate pairs, refused anyway): format
     * characters, variation selectors and the Hangul fillers, which count as letters yet draw nothing.
     */
    static boolean isDefaultIgnorable(char c) {
        if (Character.getType(c) == Character.FORMAT || Character.isIdentifierIgnorable(c)) {
            return true;
        }
        return c == 0x00AD || c == 0x034F || c == 0x061C || c == 0x115F || c == 0x1160 || c == 0x17B4 || c == 0x17B5
            || c >= 0x180B && c <= 0x180F || c >= 0x200B && c <= 0x200F || c >= 0x202A && c <= 0x202E
            || c >= 0x2060 && c <= 0x206F || c == 0x3164 || c >= 0xFE00 && c <= 0xFE0F || c == 0xFEFF || c == 0xFFA0
            || c >= 0xFFF0 && c <= 0xFFF8;
    }

    public static boolean isTagChar(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    /**
     * The trimmed name with inner runs of spaces collapsed, or null when it is too long, holds a refused character or
     * has no letter or digit (blank or punctuation only).
     */
    @Nullable
    public static String cleanName(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().replaceAll(" {2,}", " ");
        if (name.isEmpty() || name.length() > maxLength) {
            return null;
        }
        boolean visible = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isNameChar(c)) {
                return null;
            }
            visible |= Character.isLetterOrDigit(c);
        }
        return visible ? name : null;
    }

    /** The upper-cased tag, or null unless it is 3-4 ASCII letters. */
    @Nullable
    public static String cleanTag(String raw) {
        if (raw == null) {
            return null;
        }
        String tag = raw.trim().toUpperCase(Locale.ROOT);
        if (tag.length() < TAG_MIN || tag.length() > TAG_MAX) {
            return null;
        }
        for (int i = 0; i < tag.length(); i++) {
            if (!isTagChar(tag.charAt(i))) {
                return null;
            }
        }
        return tag;
    }

    /** Drops refused characters from text saved before these rules existed, so old names cannot inject formatting. */
    public static String sanitize(String text, int maxLength) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length() && sb.length() < maxLength; i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++;
            } else if (isNameChar(c)) {
                sb.append(c);
            }
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "?" : out;
    }
}

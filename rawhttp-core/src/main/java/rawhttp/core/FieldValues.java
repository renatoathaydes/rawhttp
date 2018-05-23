package rawhttp.core;

import java.util.OptionalInt;

/**
 * A utility class containing static methods which can check what kind of field-value a String may have.
 */
public final class FieldValues {

    // character as int can be used as index in this table to check if the character may appear in a token
    private static final boolean[] TOKEN_CHARS = new boolean[]{
            false, false, false, false, false, false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, true, false, true, true, true, true, true,
            false, false, true, true, false, true, true, false, true, true, true, true, true, true, true,
            true, true, true, false, false, false, false, false, false, false, true, true, true, true, true,
            true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true,
            true, true, true, true, true, false, false, false, true, true, true, true, true, true, true, true,
            true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true,
            true, true, true, true, true, false, true, false, true, false, false
    };

    /**
     * Check if the given value is allowed in a token field-value.
     * <p>
     * If it is, an empty value is returned, otherwise, the index of the first character that is not allowed in a token
     * is returned.
     *
     * @param value to check
     * @return the index of the first character in the given value which is not allowed in a token field-value, if any.
     */
    public static OptionalInt indexOfNotAllowedInTokens(String value) {
        final char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!isAllowedInTokens(c)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * @param c character
     * @return true if the given char is allowed in token field-values, false otherwise.
     */
    public static boolean isAllowedInTokens(char c) {
        return c < TOKEN_CHARS.length && TOKEN_CHARS[c];
    }

    /**
     * Check if the given value is allowed in a VCHAR field-value.
     * <p>
     * If it is, an empty value is returned, otherwise, the index of the first character that is not allowed in a VCHAR
     * is returned.
     *
     * @param value to check
     * @return the index of the first character in the given value which is not allowed in a VCHAR field-value, if any.
     */
    public static OptionalInt indexOfNotAllowedInVCHARs(String value) {
        final char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!isAllowedInVCHARs(c)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * @param c character
     * @return true if the given char is allowed in VCHAR field-values, false otherwise.
     */
    public static boolean isAllowedInVCHARs(char c) {
        return c > 31 && c < 127;
    }

}

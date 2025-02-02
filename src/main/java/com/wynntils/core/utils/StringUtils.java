package com.wynntils.core.utils;

import com.wynntils.core.framework.rendering.ScreenRenderer;
import com.wynntils.core.framework.rendering.SmartFontRenderer;
import com.wynntils.core.framework.rendering.colors.CustomColor;
import com.wynntils.core.utils.helpers.MD5Verification;
import net.minecraft.util.text.TextFormatting;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class StringUtils {

    /**
     * Removes the characters 'À' ('\u00c0') and '\u058e' that is sometimes added in Wynn APIs and
     * replaces '\u2019' (RIGHT SINGLE QUOTATION MARK) with '\'' (And trims)
     *
     * @param input string
     * @return the string without these two chars
     */
    public static String normalizeBadString(String input) {
        if (input == null) return "";
        return input
            .trim()
            .replace("À", "").replace("\u058e", "")
            .replace('\u2019', '\'')
            .trim();
    }

    public static String firstCharToUpper(String[] array) {
        StringBuilder result = new StringBuilder();

        result.append(array[0].toLowerCase());

        for (int i = 1; i < array.length; i++) {
            result.append(capitalizeFirst(array[i]));
        }

        return result.toString();
    }

    public static String capitalizeFirst(String input) {
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    // ported from a really really old C# code because im lazy, dont judge -SHCM
    public static String getCutString(String inputIN, String startIN, String endIN, boolean keepStartAndEndIN) {
        StringBuilder returning = new StringBuilder();
        StringBuilder read = new StringBuilder();
        boolean collecting = false;

        for (char chr : inputIN.toCharArray())
            if (collecting) {
                returning.append(chr);
                if (returning.toString().endsWith(endIN)) {
                    return (keepStartAndEndIN ? (startIN + returning) : returning.toString().replace(endIN, ""));
                }
            }
            else
            {
                read.append(chr);
                if (read.toString().endsWith(startIN))
                    collecting = true;
            }
        return "";
    }

    public static String toMD5(String msg) {
        return new MD5Verification(msg.getBytes(StandardCharsets.UTF_8)).getMd5();
    }


    public static String[] wrapText(String s, int max) {
        String[] stringArray = s.split(" ");
        StringBuilder result = new StringBuilder();
        int length = 0;

        for (String string: stringArray) {
            if (length + string.length() >= max) {
                result.append('|');
                length = 0;
            }
            result.append(string).append(' ');
            length += string.length() + 1;  // +1 for the space following
        }

        return result.toString().split("\\|");
    }

    public static String[] wrapTextBySize(String s, int maxPixels) {
        SmartFontRenderer renderer = ScreenRenderer.fontRenderer;
        int spaceSize = renderer.getStringWidth(" ");

        String[] stringArray = s.split(" ");
        StringBuilder result = new StringBuilder();
        int length = 0;

        for (String string : stringArray) {
            if (length + renderer.getStringWidth(string) >= maxPixels) {
                result.append('|');
                length = 0;
            }
            result.append(string).append(' ');
            length += renderer.getStringWidth(string) + spaceSize;
        }

        return result.toString().split("\\|");
    }

    private static HashMap<String, CustomColor> registeredColors = new HashMap<>();
    private static HashMap<Integer, CustomColor> registeredHexColors = new HashMap<>();

    /**
     * Generates a Color based in the input string
     * The color will be always the same if the string is the same
     * (The alpha value will not be stable, so remember to setA())
     *
     * @param input the input stream
     * @return the color
     */
    public static CustomColor colorFromString(String input) {
        if (registeredColors.containsKey(input)) return registeredColors.get(input);

        CRC32 crc32 = new CRC32();
        crc32.update(input.getBytes(StandardCharsets.UTF_8));

        CustomColor color = CustomColor.fromInt(((int) crc32.getValue()) & 0xFFFFFF, 1);
        registeredColors.put(input, color);

        return color;
    }

    public static CustomColor colorFromHex(String hex) {
        int rgb = Integer.parseInt(hex.substring(1, 7), 16);
        if (registeredHexColors.containsKey(rgb)) return registeredHexColors.get(rgb);

        CustomColor color = CustomColor.fromInt(rgb, 1);
        registeredHexColors.put(rgb, color);

        return color;
    }

    public static String millisToString(long duration) {
        long millis = duration % 1000,
            second = (duration / 1000) % 60,
            minute = (duration / (1000 * 60)) % 60,
            hour = (duration / (1000 * 60 * 60));

        return String.format("%02d:%02d:%02d.%03d", hour, minute, second, millis);
    }


    /**
     * @return `true` if `c` is a valid Unicode code point (in [0, 0x10FFFF] and not a surrogate)
     */
    public static boolean isValidCodePoint(int c) {
                                            /* low surrogates */             /* high surrogates */
        return 0 <= c && c <= 0x10FFFF && !(0xD800 <= c && c <= 0xDBFF) && !(0xDC00 <= c && c <= 0xDFFF);
    }

    private static final Pattern numberRegex = Pattern.compile("0|-?[1-9][0-9]*");

    /**
     * @return `true` if `s` is an integer string and can fit in an `int`
     */
    public static boolean isValidInteger(String s) {
        if (s == null || s.length() > 11 || !numberRegex.matcher(s).matches()) return false;
        if (s.length() < 10) return true;
        long parsed = Long.parseLong(s);
        return (int) parsed == parsed;
    }

    /**
     * @return `true` if `s` is an integer string and can fit in a `long`
     */
    public static boolean isValidLong(String s) {
        if (s == null || s.length() > 20 || !numberRegex.matcher(s).matches()) return false;
        if (s.length() < 19) return true;
        try {
            Long.parseLong(s);  // Could overflow
        } catch (NumberFormatException ignored) {
            return false;
        }
        return true;
    }

    /**
     * @return `s.getBytes("UTF-8").length`, but without encoding the string
     */
    public static int utf8Length(String s) {
        if (s == null) return 0;
        return s.codePoints().map(c -> c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4).sum();
    }


    public static UUID uuidFromString(String s) {
        if (s.contains("-")) return UUID.fromString(s);
        if (s.length() != 32) throw new IllegalArgumentException("Invalid UUID string: " + s);

        try {
            return new UUID(Long.parseUnsignedLong(s.substring(0, 16), 16), Long.parseUnsignedLong(s.substring(16, 32), 16));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid UUID string: " + s);
        }
    }

    public static boolean isWynnic(int c) {
        return (
            (0x249C <= c && c <= 0x24B5) ||
            (0x2474 <= c && c <= 0x2476) ||
            (0x247D <= c && c <= 0x247F) ||
            (0xFF10 <= c && c <= 0xFF12)
        );
    }

    public static boolean hasWynnic(String text) {
        return text.chars().anyMatch(StringUtils::isWynnic);
    }

    public static String translateCharacterFromWynnic(char wynnic) {
        return translateCharacterFromWynnic((int) wynnic);  // convert to code point
    }

    public static String translateCharacterFromWynnic(int wynnic) {
        if (0x249C <= wynnic && wynnic <= 0x24B5) {
            return Character.toString((char) ((wynnic) - 9275));
        } else if (0x2474 <= wynnic && wynnic <= 0x2476) {
            return Character.toString((char) ((wynnic) - 9283));
        }

        switch (wynnic) {
            case 0x247D: return "10";
            case 0x247E: return "50";
            case 0x247F: return "100";
            case 0xFF10: return ".";
            case 0xFF11: return "!";
            case 0xFF12: return "?";
            default: return "";
        }
    }

}

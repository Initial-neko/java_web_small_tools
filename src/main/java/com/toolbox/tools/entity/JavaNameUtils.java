package com.toolbox.tools.entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class JavaNameUtils {

    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "true", "false", "null"
    ));

    private JavaNameUtils() {
    }

    public static String toFieldName(String source) {
        String value = toCamel(source, false);
        if (value.isEmpty()) {
            value = "field";
        }
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            value = "field" + Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }
        if (KEYWORDS.contains(value)) {
            value = value + "Value";
        }
        return value;
    }

    public static String toClassName(String source) {
        String value = toCamel(source, true);
        if (value.isEmpty()) {
            value = "GeneratedEntity";
        }
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            value = "Entity" + value;
        }
        return value;
    }

    public static String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String toCamel(String source, boolean upperFirst) {
        if (source == null) return "";
        StringBuilder out = new StringBuilder();
        StringBuilder token = new StringBuilder();

        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                token.append(ch);
            } else {
                appendToken(out, token, out.length() == 0 ? upperFirst : true);
                token.setLength(0);
            }
        }
        appendToken(out, token, out.length() == 0 ? upperFirst : true);

        if (out.length() > 0 && !upperFirst) {
            out.setCharAt(0, Character.toLowerCase(out.charAt(0)));
        }
        return out.toString();
    }

    private static void appendToken(StringBuilder out, StringBuilder token, boolean upperFirst) {
        if (token.length() == 0) return;

        String raw = token.toString();
        String normalized = allUpper(raw) ? raw.toLowerCase(Locale.ROOT) : raw;
        if (upperFirst) {
            out.append(Character.toUpperCase(normalized.charAt(0)));
        } else {
            out.append(Character.toLowerCase(normalized.charAt(0)));
        }
        if (normalized.length() > 1) {
            out.append(normalized.substring(1));
        }
    }

    private static boolean allUpper(String value) {
        boolean hasLetter = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
                if (Character.isLowerCase(ch)) return false;
            }
        }
        return hasLetter;
    }
}

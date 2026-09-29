package com.toolbox.tools.entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class JavaNameUtils {

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

    static String toFieldName(String source) {
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

    static String toClassName(String source) {
        String value = toCamel(source, true);
        if (value.isEmpty()) {
            value = "GeneratedEntity";
        }
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            value = "Entity" + value;
        }
        return value;
    }

    static String capitalize(String value) {
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
        if (upperFirst) {
            out.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) out.append(token.substring(1));
        } else {
            out.append(Character.toLowerCase(token.charAt(0)));
            if (token.length() > 1) out.append(token.substring(1));
        }
    }
}

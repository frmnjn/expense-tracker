package com.expensetracker.service;

import java.util.Map;

/**
 * Mengubah HTML email menjadi teks berbaris agar field tabel (label & value)
 * bisa diekstrak dengan regex. Sel tabel dipisah spasi, baris tabel dipisah
 * newline, tag lain dibuang. Cukup untuk email notifikasi bank, tanpa
 * menambah dependency parser HTML.
 */
public final class HtmlText {

    private static final Map<String, String> ENTITIES = Map.of(
            "nbsp", " ",
            "amp", "&",
            "lt", "<",
            "gt", ">",
            "quot", "\"",
            "apos", "'",
            "middot", "\u00b7",
            "bull", "\u2022",
            "hellip", "\u2026");

    private HtmlText() {
    }

    public static String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String s = html;
        s = s.replaceAll("(?is)<!--.*?-->", " ");
        s = s.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(td|th)>", " ");
        s = s.replaceAll("(?i)</(tr)>", "\n");
        s = s.replaceAll("(?i)</(p|div|li|h[1-6]|table)>", "\n");
        s = s.replaceAll("(?s)<[^>]+>", " ");
        s = decodeEntities(s);

        StringBuilder out = new StringBuilder();
        for (String rawLine : s.split("\n")) {
            String line = rawLine.replaceAll("[\\t\\x0B\\f\\r]+", " ")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            if (!line.isEmpty()) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String decodeEntities(String s) {
        String result = s;
        for (Map.Entry<String, String> e : ENTITIES.entrySet()) {
            result = result.replace("&" + e.getKey() + ";", e.getValue());
        }
        StringBuilder sb = new StringBuilder(result.length());
        int i = 0;
        while (i < result.length()) {
            char c = result.charAt(i);
            if (c == '&') {
                int semi = result.indexOf(';', i + 1);
                if (semi > i && semi - i <= 8) {
                    String code = result.substring(i + 1, semi);
                    Integer cp = codePointOf(code);
                    if (cp != null) {
                        sb.appendCodePoint(cp);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static Integer codePointOf(String code) {
        try {
            if (code.startsWith("#x") || code.startsWith("#X")) {
                return Integer.parseInt(code.substring(2), 16);
            }
            if (code.startsWith("#")) {
                return Integer.parseInt(code.substring(1));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }
}

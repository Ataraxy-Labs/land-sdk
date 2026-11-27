package dev.useland;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader, so the SDK needs no library. An object is a {@code Map<String, Object>}, an array a
 * {@code List<Object>}, a number a {@code Long} or a {@code Double}, and the rest String, Boolean and null.
 */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        Object v = p.value();
        p.space();
        if (p.i != text.length()) throw p.bad();
        return v;
    }

    private IllegalArgumentException bad() {
        return new IllegalArgumentException("not JSON at " + i);
    }

    private void space() {
        while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
    }

    private Object value() {
        space();
        if (i >= s.length()) throw bad();
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        for (String word : new String[] {"true", "false", "null"}) {
            if (s.startsWith(word, i)) {
                i += word.length();
                return word.equals("null") ? null : Boolean.valueOf(word);
            }
        }
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(start, i);
        try {
            return n.matches("-?\\d{1,18}") ? (Object) Long.valueOf(n) : (Object) Double.valueOf(n);
        } catch (NumberFormatException e) {
            throw bad();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        space();
        if (peek('}')) return m;
        while (true) {
            space();
            if (i >= s.length() || s.charAt(i) != '"') throw bad();
            String k = string();
            space();
            expect(':');
            m.put(k, value());
            space();
            if (peek('}')) return m;
            expect(',');
        }
    }

    private List<Object> array() {
        List<Object> a = new ArrayList<>();
        i++;
        space();
        if (peek(']')) return a;
        while (true) {
            a.add(value());
            space();
            if (peek(']')) return a;
            expect(',');
        }
    }

    private boolean peek(char c) {
        if (i < s.length() && s.charAt(i) == c) {
            i++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!peek(c)) throw bad();
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw bad();
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= s.length()) throw bad();
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'u' -> {
                    if (i + 4 > s.length()) throw bad();
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> b.append(e);
            }
        }
    }
}

package com.jihoumod.text;

import com.jihoumod.config.ConfigException;
import com.jihoumod.text.FormattedText.Decoration;
import com.jihoumod.text.FormattedText.Span;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;

public final class TextParser {
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA),
            Map.entry("dark_green", 0x00AA00), Map.entry("dark_aqua", 0x00AAAA),
            Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA),
            Map.entry("dark_gray", 0x555555), Map.entry("blue", 0x5555FF),
            Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF),
            Map.entry("red", 0xFF5555), Map.entry("light_purple", 0xFF55FF),
            Map.entry("yellow", 0xFFFF55), Map.entry("white", 0xFFFFFF));

    private record OpenTag(String name, Decoration previous) {}

    private TextParser() {}

    public static FormattedText parse(String input, int line) throws ConfigException {
        var spans = new ArrayList<Span>();
        Deque<OpenTag> stack = new ArrayDeque<>();
        Decoration style = Decoration.EMPTY;
        var literal = new StringBuilder();
        for (int i = 0; i < input.length();) {
            char c = input.charAt(i++);
            if (c == '\\' && i < input.length() && "<>\\".indexOf(input.charAt(i)) >= 0) {
                literal.append(input.charAt(i++));
            } else if (c == '<') {
                int end = input.indexOf('>', i);
                if (end < 0) throw new ConfigException(line, "Unclosed decoration tag");
                if (!literal.isEmpty()) {
                    spans.add(new Span(literal.toString(), style));
                    literal.setLength(0);
                }
                String tag = input.substring(i, end);
                i = end + 1;
                if (tag.startsWith("/")) {
                    String name = tag.substring(1);
                    if (stack.isEmpty() || !stack.peek().name().equals(name)) {
                        throw new ConfigException(line, "Mismatched closing tag <" + tag + ">");
                    }
                    style = stack.pop().previous();
                } else if (tag.equals("reset")) {
                    style = Decoration.EMPTY;
                    stack.clear();
                } else {
                    Decoration next = apply(style, tag, line);
                    stack.push(new OpenTag(tag, style));
                    style = next;
                }
            } else if (c == '>') {
                throw new ConfigException(line, "Unexpected '>' outside a decoration tag");
            } else {
                literal.append(c);
            }
        }
        if (!literal.isEmpty()) spans.add(new Span(literal.toString(), style));
        // Opening tags may intentionally run to the end of the message, like <red>Hello.
        return new FormattedText(spans);
    }

    private static Decoration apply(Decoration s, String tag, int line) throws ConfigException {
        Integer color = COLORS.get(tag);
        if (tag.startsWith("#")) {
            if (!tag.matches("#[0-9a-fA-F]{6}")) throw new ConfigException(line, "Invalid HEX color <" + tag + ">");
            color = Integer.parseInt(tag.substring(1), 16);
        }
        if (color != null) return new Decoration(color, s.bold(), s.italic(), s.underlined(), s.strikethrough(), s.obfuscated());
        return switch (tag) {
            case "bold" -> new Decoration(s.color(), true, s.italic(), s.underlined(), s.strikethrough(), s.obfuscated());
            case "italic" -> new Decoration(s.color(), s.bold(), true, s.underlined(), s.strikethrough(), s.obfuscated());
            case "underlined" -> new Decoration(s.color(), s.bold(), s.italic(), true, s.strikethrough(), s.obfuscated());
            case "strikethrough" -> new Decoration(s.color(), s.bold(), s.italic(), s.underlined(), true, s.obfuscated());
            case "obfuscated" -> new Decoration(s.color(), s.bold(), s.italic(), s.underlined(), s.strikethrough(), true);
            default -> throw new ConfigException(line, "Unknown decoration tag <" + tag + ">");
        };
    }
}

package com.jihoumod.text;

import java.util.List;

/** Validated, Minecraft-independent text model. */
public record FormattedText(List<Span> spans) {
    public FormattedText { spans = List.copyOf(spans); }
    public record Span(String text, Decoration decoration) {}
    public record Decoration(Integer color, boolean bold, boolean italic, boolean underlined,
                             boolean strikethrough, boolean obfuscated) {
        public static final Decoration EMPTY = new Decoration(null, false, false, false, false, false);
    }
}

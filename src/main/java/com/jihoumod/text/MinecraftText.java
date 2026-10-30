package com.jihoumod.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class MinecraftText {
    private MinecraftText() {}
    public static Component create(FormattedText text) {
        var result = Component.empty();
        for (var span : text.spans()) {
            var d = span.decoration();
            Style style = Style.EMPTY.withBold(d.bold()).withItalic(d.italic())
                    .withUnderlined(d.underlined()).withStrikethrough(d.strikethrough()).withObfuscated(d.obfuscated());
            if (d.color() != null) style = style.withColor(d.color());
            result.append(Component.literal(span.text()).setStyle(style));
        }
        return result;
    }
}

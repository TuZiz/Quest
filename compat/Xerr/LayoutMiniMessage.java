package com.hiusers.xerr.compat;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Xerr 2.1 font wrapper transport bridge; configuration remains MiniMessage RGB.
 * Convert before width measurement and bracket escaping, on both sync/async paths.
 * Paper provides Adventure; this helper bundles no library and performs no Bukkit access.
 */
public final class LayoutMiniMessage {
    private static final MiniMessage TEXT = MiniMessage.builder().tags(
        TagResolver.resolver(StandardTags.color(), StandardTags.gradient())).build();
    private static final LegacyComponentSerializer TRANSPORT = LegacyComponentSerializer.builder()
        .hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private LayoutMiniMessage() {}
    public static String prepare(String value) {
        // Plain and existing legacy layouts retain their original semantics.
        if (!value.contains("<#") && !value.contains("<gradient:")) return value;
        return TRANSPORT.serialize(TEXT.deserialize(value));
    }
}

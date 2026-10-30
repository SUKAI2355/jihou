package com.jihoumod.notification;

import com.jihoumod.Jihou;
import com.jihoumod.text.MinecraftText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;

public final class NotificationSender {
    private NotificationSender() {}
    /** Recipients are resolved at actual send time on the server thread. */
    public static void send(MinecraftServer server, Notification notification) {
        Component message = MinecraftText.create(notification.message());
        for (var player : server.getPlayerList().getPlayers()) {
            switch (notification.type()) {
                case CHAT -> player.sendSystemMessage(message);
                case ACTIONBAR -> player.sendSystemMessage(message, true);
                case TITLE, SUBTITLE -> {
                    Component title = notification.type() == NotificationType.SUBTITLE ? Component.empty() : message;
                    Component subtitle = notification.type() == NotificationType.SUBTITLE ? message
                            : notification.subtitle() == null ? Component.empty() : MinecraftText.create(notification.subtitle());
                    // Reset vanilla times and stale subtitles left by other title commands.
                    player.connection.send(new ClientboundClearTitlesPacket(true));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                    player.connection.send(new ClientboundSetTitleTextPacket(title));
                }
            }
        }
        Jihou.LOGGER.info("[Jihou] {} - Notification executed (ID {})", notification.time(), notification.id());
    }
}

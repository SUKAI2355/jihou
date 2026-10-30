package com.jihoumod;

import com.jihoumod.config.ConfigException;
import com.jihoumod.config.ConfigLoader;
import com.jihoumod.notification.NotificationSender;
import com.jihoumod.notification.TitleQueue;
import com.jihoumod.scheduler.JihouScheduler;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/** All mutable state belongs to the server thread, including reload and the queue. */
public final class JihouRuntime {
    private final ConfigLoader loader;
    private final JihouScheduler scheduler = new JihouScheduler();
    private final TitleQueue titles = new TitleQueue();

    public JihouRuntime(Path config) { loader = new ConfigLoader(config); }
    public void start() {
        try {
            if (loader.createIfMissing()) Jihou.LOGGER.info("[Jihou] Created config/jihou.conf");
            reload();
        } catch (IOException | ConfigException ex) {
            Jihou.LOGGER.error("[Jihou] {}. Notifications disabled; server startup will continue.", ex.getMessage());
        }
    }
    public void reload() throws IOException, ConfigException {
        var candidate = loader.reload();
        scheduler.install(candidate, Instant.now());
        titles.clear();
        Jihou.LOGGER.info("[Jihou] Configuration loaded/reloaded: {} notifications, timezone {}",
                candidate.notifications().size(), candidate.timezone());
    }
    public void tick(MinecraftServer server) {
        var due = scheduler.poll(Instant.now());
        if (server.getPlayerList().getPlayers().isEmpty()) {
            // Never retain notifications for offline players or a later login.
            titles.clear();
            return;
        }
        for (var notification : due) {
            if (notification.type().isTitle()) titles.add(notification);
            else NotificationSender.send(server, notification);
        }
        titles.tick(notification -> NotificationSender.send(server, notification));
    }
}

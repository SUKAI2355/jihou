package com.jihoumod.notification;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

/** Standard Minecraft title times: 10 fade-in + 70 stay + 20 fade-out ticks. */
public final class TitleQueue {
    public static final int DISPLAY_TICKS = 10 + 70 + 20;
    private final Queue<Notification> pending = new ArrayDeque<>();
    private int remainingTicks;

    public void add(Notification notification) { pending.add(notification); }
    public void tick(Consumer<Notification> send) {
        if (remainingTicks > 0) remainingTicks--;
        if (remainingTicks == 0 && !pending.isEmpty()) {
            send.accept(pending.remove());
            remainingTicks = DISPLAY_TICKS;
        }
    }
    public void clear() { pending.clear(); remainingTicks = 0; }
}

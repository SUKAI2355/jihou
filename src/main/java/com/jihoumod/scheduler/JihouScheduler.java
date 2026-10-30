package com.jihoumod.scheduler;

import com.jihoumod.config.JihouConfig;
import com.jihoumod.notification.Notification;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Current-minute polling only: missed minutes are never replayed. No Minecraft dependency. */
public final class JihouScheduler {
    private record Execution(LocalDate scheduleDate, int notificationId) {}
    private final Set<Execution> executed = new HashSet<>();
    private JihouConfig config;
    private long latestMinute = Long.MIN_VALUE;
    private LocalDate latestDate;

    public void install(JihouConfig candidate, Instant installedAt) {
        config = candidate;
        executed.clear();
        // The boundary of the installation minute already passed. Never catch it up,
        // including on startup, recovery from a bad config, or repeated reloads.
        latestMinute = Math.max(latestMinute, Math.floorDiv(installedAt.getEpochSecond(), 60));
    }

    public List<Notification> poll(Instant instant) {
        if (config == null) return List.of();
        long minute = Math.floorDiv(instant.getEpochSecond(), 60);
        if (minute <= latestMinute) return List.of();
        latestMinute = minute;
        var now = instant.atZone(config.timezone());
        LocalDate today = now.toLocalDate();
        if (latestDate == null || today.isAfter(latestDate)) {
            latestDate = today;
            executed.removeIf(key -> key.scheduleDate().isBefore(today.minusDays(2)));
        }
        int minuteOfDay = now.getHour() * 60 + now.getMinute();
        var due = new ArrayList<Notification>();
        // Source order applies across everyday, weekday and previous-day 24:00 occurrences.
        for (Notification notification : config.notifications()) {
            boolean endOfDay = notification.time().minuteOfDay() == 1440;
            if (endOfDay ? minuteOfDay != 0 : notification.time().minuteOfDay() != minuteOfDay) continue;
            LocalDate scheduleDate = endOfDay ? today.minusDays(1) : today;
            if (notification.appliesTo(scheduleDate.getDayOfWeek())
                    && executed.add(new Execution(scheduleDate, notification.id()))) due.add(notification);
        }
        return List.copyOf(due);
    }
}

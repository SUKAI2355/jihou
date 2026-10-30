package com.jihoumod.notification;

import com.jihoumod.text.FormattedText;
import java.time.DayOfWeek;

/** A null day means everyday. IDs follow source order, including repeated day blocks. */
public record Notification(int id, DayOfWeek day, ScheduleTime time, NotificationType type,
                           FormattedText message, FormattedText subtitle) {
    public boolean appliesTo(DayOfWeek dayOfWeek) { return day == null || day == dayOfWeek; }
}

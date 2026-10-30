package com.jihoumod.notification;

import com.jihoumod.config.ConfigException;

/** 1440 preserves 24:00 as an occurrence belonging to the preceding schedule day. */
public record ScheduleTime(int minuteOfDay) {
    public ScheduleTime {
        if (minuteOfDay < 0 || minuteOfDay > 1440) throw new IllegalArgumentException("Invalid minute");
    }

    public static ScheduleTime parse(String value, int line) throws ConfigException {
        if (!value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]|24:00")) {
            throw new ConfigException(line, "Invalid time '" + value + "' (expected 00:00–23:59 or 24:00)");
        }
        return new ScheduleTime(Integer.parseInt(value.substring(0, 2)) * 60
                + Integer.parseInt(value.substring(3, 5)));
    }

    @Override
    public String toString() { return "%02d:%02d".formatted(minuteOfDay / 60, minuteOfDay % 60); }
}

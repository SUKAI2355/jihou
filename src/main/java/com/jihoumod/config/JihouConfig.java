package com.jihoumod.config;

import com.jihoumod.notification.Notification;
import java.time.ZoneId;
import java.util.List;

public record JihouConfig(ZoneId timezone, List<Notification> notifications) {
    public JihouConfig { notifications = List.copyOf(notifications); }
}

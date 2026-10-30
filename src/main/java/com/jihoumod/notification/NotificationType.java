package com.jihoumod.notification;

public enum NotificationType {
    CHAT, TITLE, SUBTITLE, ACTIONBAR;

    public boolean isTitle() { return this == TITLE || this == SUBTITLE; }
}

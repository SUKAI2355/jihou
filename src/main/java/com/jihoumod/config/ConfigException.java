package com.jihoumod.config;

public final class ConfigException extends Exception {
    private final int line;

    public ConfigException(int line, String cause) {
        super("Config error at line " + line + ": " + cause);
        this.line = line;
    }

    public int line() { return line; }
}

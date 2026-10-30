package com.jihoumod.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Server-thread confined: a failed load never changes the last valid snapshot. */
public final class ConfigLoader {
    private final Path path;
    private JihouConfig current;

    public ConfigLoader(Path path) { this.path = path; }
    public JihouConfig current() { return current; }

    public boolean createIfMissing() throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (var source = ConfigLoader.class.getResourceAsStream("/jihou-default.conf")) {
            if (source == null) throw new IOException("Missing bundled jihou-default.conf");
            try {
                Files.write(path, source.readAllBytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return true;
            } catch (FileAlreadyExistsException ex) { return false; }
        }
    }

    public JihouConfig reload() throws IOException, ConfigException {
        JihouConfig candidate = ConfigParser.parse(Files.readString(path, StandardCharsets.UTF_8));
        current = candidate;
        return candidate;
    }
}

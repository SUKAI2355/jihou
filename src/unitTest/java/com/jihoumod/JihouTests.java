package com.jihoumod;

import com.jihoumod.config.ConfigException;
import com.jihoumod.config.ConfigLoader;
import com.jihoumod.config.ConfigParser;
import com.jihoumod.config.JihouConfig;
import com.jihoumod.notification.Notification;
import com.jihoumod.notification.NotificationType;
import com.jihoumod.notification.ScheduleTime;
import com.jihoumod.notification.TitleQueue;
import com.jihoumod.scheduler.JihouScheduler;
import com.jihoumod.text.FormattedText.Decoration;
import com.jihoumod.text.TextParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free checks in a separate source set; no Minecraft runtime is required. */
public final class JihouTests {
    private static int passed;
    @FunctionalInterface private interface Test { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        test("UTF-8 default generation and no overwrite", JihouTests::generation);
        test("atomic reload, failure preserves A, recovery applies B", JihouTests::reload);
        test("invalid first load remains disabled", () -> {
            Path file = Files.createTempFile("jihou-bad", ".conf");
            try {
                Files.writeString(file, "broken");
                var loader = new ConfigLoader(file);
                try { loader.reload(); throw new AssertionError("Accepted invalid file"); }
                catch (ConfigException expected) { check(loader.current() == null, "Must remain disabled"); }
            } finally { Files.delete(file); }
        });
        test("all weekdays, everyday, merged blocks and ordered IDs", JihouTests::weekdays);
        test("all notification types and title+subtitle", () -> {
            var config = parse("everyday {" + entry("20:00", "chat", "chat")
                    + entry("20:00", "actionbar", "action") + entry("20:00", "subtitle", "sub")
                    + "notification { time: \"20:00\" type: title message: \"main\" subtitle: \"sub\" }}");
            equal(config.notifications().stream().map(Notification::type).toList(),
                    List.of(NotificationType.CHAT, NotificationType.ACTIONBAR, NotificationType.SUBTITLE, NotificationType.TITLE));
            check(config.notifications().get(3).subtitle() != null, "Title subtitle lost");
        });
        test("00:00, 23:59, 24:00 and distinct midnight occurrences", JihouTests::midnight);
        test("timezone conversion", () -> {
            var scheduler = installed(parse("timezone: \"Asia/Tokyo\" everyday {" + entry("09:00", "chat", "x") + "}"), "2026-10-26T23:59:00Z");
            equal(ids(scheduler.poll(Instant.parse("2026-10-27T00:00:10Z"))), List.of(1));
        });
        test("duplicate ticks, clock rollback, skipped minutes, restart", JihouTests::duplicates);
        test("DST repeated hour does not repeat occurrence", () -> {
            var scheduler = installed(parse("timezone: \"America/New_York\" everyday {" + entry("01:30", "chat", "x") + "}"), "2026-11-01T05:29:00Z");
            equal(ids(scheduler.poll(Instant.parse("2026-11-01T05:30:00Z"))), List.of(1));
            equal(scheduler.poll(Instant.parse("2026-11-01T06:30:00Z")).size(), 0);
        });
        test("DST nonexistent minute is skipped", () -> {
            var scheduler = installed(parse("timezone: \"America/New_York\" everyday {" + entry("02:30", "chat", "x") + "}"), "2026-03-08T06:59:00Z");
            equal(scheduler.poll(Instant.parse("2026-03-08T07:30:00Z")).size(), 0);
        });
        test("two title FIFO", () -> queue(2));
        test("four title/subtitle FIFO; chat/actionbar bypass", () -> queue(4));
        test("successful reload clears pending title queue", () -> {
            var queue = new TitleQueue();
            queue.add(parse("everyday {" + entry("20:00", "title", "x") + "}").notifications().getFirst());
            queue.clear();
            queue.tick(n -> { throw new AssertionError("Stale title"); });
        });
        test("comments, # HEX, quotes, escapes, BOM and CRLF", () -> {
            var config = parse("\uFEFF# comment\r\neveryday { notification {\r\n time: \"20:00\" type: chat # comment\r\n"
                    + "message: \"<#FF5500># 日本語 \\\"quoted\\\" \\n \\t \\\\\"\r\n} } # end");
            equal(config.notifications().size(), 1);
            check(config.notifications().getFirst().message().spans().getFirst().text().contains("# 日本語 \"quoted\""), "String/comment parse");
        });
        test("named red and gold, HEX", () -> {
            equal(TextParser.parse("<red>r<gold>g<#FF5500>h", 1).spans().stream().map(s -> s.decoration().color()).toList(),
                    List.of(0xFF5555, 0xFFAA00, 0xFF5500));
        });
        test("all decorations and reset", () -> {
            var spans = TextParser.parse("<bold><italic><underlined><strikethrough><obfuscated>x<reset>y", 1).spans();
            equal(spans.getFirst().decoration(), new Decoration(null, true, true, true, true, true));
            equal(spans.getLast().decoration(), Decoration.EMPTY);
        });
        test("nested closing tags restore prior style", () -> {
            var spans = TextParser.parse("<red><bold>x</bold>y</red>z", 1).spans();
            check(spans.get(0).decoration().bold(), "Missing bold");
            check(!spans.get(1).decoration().bold(), "Bold not restored");
            equal(spans.get(2).decoration(), Decoration.EMPTY);
        });
        for (String tag : List.of("<#GG5500>", "<#FFF>", "<unknown>", "<bold></italic>", "</red>", "<red", "oops>")) {
            test("reject decoration " + tag, () -> invalid("everyday {\nnotification { time: \"20:00\" type: chat\nmessage: \"" + tag + "\"\n}}", 3, ""));
        }
        for (String time : List.of("25:00", "23:60", "24:01", "1:00", "00:0", "-1:00")) {
            test("reject time " + time, () -> invalid("everyday {\nnotification {\ntime: \"" + time + "\"\ntype: chat message: \"x\" }}", 3, "Invalid time"));
        }
        for (String key : List.of("time", "type", "message")) {
            test("missing required " + key, () -> {
                String fields = "time: \"20:00\"\ntype: chat\nmessage: \"x\"\n";
                fields = fields.replaceAll("(?m)^" + key + ":.*\\n", "");
                invalid("everyday {\nnotification {\n" + fields + "}}", 2, "Missing required key '" + key + "'");
            });
        }
        test("invalid timezone", () -> invalid("# comment\ntimezone: \"Invalid/Zone\"", 2, "Invalid timezone"));
        test("unknown type", () -> invalid("everyday {\nnotification { time: \"20:00\" type: sound message: \"x\" }}", 2, "Unknown type"));
        test("unknown block", () -> invalid("holiday {}", 1, "Unknown day block"));
        test("unknown nested block", () -> invalid("monday { wrong {} }", 1, "Expected notification"));
        test("unknown key", () -> invalid("monday { notification { wrong: \"x\" }}", 1, "Unknown notification key"));
        test("duplicate key", () -> invalid("everyday { notification { time: \"20:00\" time: \"21:00\" }}", 1, "Duplicate key"));
        test("duplicate timezone", () -> invalid("timezone: \"UTC\"\ntimezone: \"Asia/Tokyo\"", 2, "Duplicate timezone"));
        test("missing colon", () -> invalid("timezone \"UTC\"", 1, "Expected ':'"));
        test("comma rejected", () -> invalid("timezone: \"UTC\",", 1, "Unexpected character"));
        test("unclosed day block", () -> invalid("monday {\n", 2, "Unclosed 'monday'"));
        test("unclosed notification", () -> invalid("monday {\nnotification {\n", 3, "Unclosed notification"));
        test("extra closing brace", () -> invalid("monday {}\n}", 2, "Expected timezone"));
        test("unclosed string", () -> invalid("timezone: \"UTC\n", 1, "Unclosed string"));
        test("invalid escape", () -> invalid("timezone: \"UT\\qC\"", 1, "Unknown string escape"));
        test("invalid optional subtitle rejects whole file", () -> invalid("everyday { notification { time: \"20:00\" type: chat message: \"ok\" subtitle: \"<wrong>\" }}", 1, "Unknown decoration"));
        System.out.println("Jihou tests: " + passed + " passed");
    }

    private static void generation() throws Exception {
        Path directory = Files.createTempDirectory("jihou-config-test");
        Path path = directory.resolve("config/jihou.conf");
        try {
            var loader = new ConfigLoader(path);
            check(loader.createIfMissing(), "Must generate default");
            String contents = Files.readString(path, StandardCharsets.UTF_8);
            equal(loader.reload().timezone(), ZoneId.of("Asia/Tokyo"));
            for (String block : List.of("everyday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")) {
                check(contents.contains(block + " {"), "Missing sample " + block);
            }
            Files.writeString(path, "broken original 日本語");
            check(!loader.createIfMissing(), "Must not overwrite");
            equal(Files.readString(path), "broken original 日本語");
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(path.getParent()); Files.delete(directory); }
    }

    private static void reload() throws Exception {
        Path path = Files.createTempFile("jihou-reload", ".conf");
        try {
            var loader = new ConfigLoader(path);
            Files.writeString(path, "timezone: \"UTC\" everyday {" + entry("20:00", "chat", "A") + "}");
            JihouConfig a = loader.reload();
            var scheduler = installed(a, "2026-10-26T19:59:00Z");
            equal(ids(scheduler.poll(Instant.parse("2026-10-26T20:00:00Z"))), List.of(1));
            Files.writeString(path, "everyday {" + entry("21:00", "chat", "partial B") + entry("25:00", "chat", "bad B") + "}");
            try { loader.reload(); throw new AssertionError("Invalid B accepted"); }
            catch (ConfigException expected) { check(loader.current() == a, "A snapshot was lost"); }
            // Scheduler is untouched on failure; A still fires on the next day.
            equal(ids(scheduler.poll(Instant.parse("2026-10-27T20:00:00Z"))), List.of(1));
            Files.delete(path);
            try { loader.reload(); throw new AssertionError("Missing file accepted"); }
            catch (IOException expected) { check(loader.current() == a, "I/O failure lost A"); }
            Files.writeString(path, "timezone: \"UTC\" everyday {" + entry("21:00", "chat", "B") + "}");
            var b = loader.reload();
            check(b != a, "New snapshot not installed");
            scheduler.install(b, Instant.parse("2026-10-27T20:00:30Z"));
            equal(ids(scheduler.poll(Instant.parse("2026-10-27T21:00:00Z"))), List.of(1));
            equal(b.notifications().getFirst().message().spans().getFirst().text(), "B");
        } finally { Files.deleteIfExists(path); }
    }

    private static void weekdays() throws Exception {
        var text = new StringBuilder("timezone: \"UTC\" everyday {" + entry("20:00", "chat", "daily") + "}");
        for (var day : DayOfWeek.values()) text.append(day.name().toLowerCase(java.util.Locale.ROOT)).append(" {")
                .append(entry("20:00", "chat", day.name())).append("}\n");
        text.append("monday {").append(entry("20:00", "chat", "second monday")).append("}");
        var config = parse(text.toString());
        equal(config.notifications().size(), 9);
        equal(ids(config.notifications()), List.of(1, 2, 3, 4, 5, 6, 7, 8, 9));
        var scheduler = installed(config, "2026-10-25T20:00:00Z");
        for (int i = 0; i < 7; i++) {
            Instant now = LocalDate.of(2026, 10, 26).plusDays(i).atTime(20, 0).atZone(ZoneId.of("UTC")).toInstant();
            equal(ids(scheduler.poll(now)), i == 0 ? List.of(1, 2, 9) : List.of(1, i + 2));
        }
    }

    private static void midnight() throws Exception {
        var config = parse("timezone: \"UTC\" monday {" + entry("24:00", "title", "Monday end")
                + entry("23:59", "chat", "before") + "} tuesday {" + entry("00:00", "subtitle", "Tuesday")
                + "} everyday {" + entry("24:00", "title", "daily end") + entry("00:00", "chat", "daily start") + "}");
        var scheduler = installed(config, "2026-10-26T23:58:00Z");
        equal(ids(scheduler.poll(Instant.parse("2026-10-26T23:59:02Z"))), List.of(2));
        equal(ids(scheduler.poll(Instant.parse("2026-10-27T00:00:02Z"))), List.of(1, 3, 4, 5));
        equal(scheduler.poll(Instant.parse("2026-10-27T00:00:59Z")).size(), 0);
        equal(ids(scheduler.poll(Instant.parse("2026-10-28T00:00:00Z"))), List.of(4, 5));
        equal(ScheduleTime.parse("24:00", 1).minuteOfDay(), 1440);
        equal(ScheduleTime.parse("00:00", 1).minuteOfDay(), 0);
    }

    private static void duplicates() throws Exception {
        var config = parse("timezone: \"UTC\" everyday {" + entry("20:00", "chat", "x") + entry("20:01", "chat", "skip") + "}");
        var scheduler = installed(config, "2026-10-26T19:59:30Z");
        equal(scheduler.poll(Instant.parse("2026-10-26T20:00:02Z")).size(), 1);
        for (int i = 3; i < 60; i++) equal(scheduler.poll(Instant.parse("2026-10-26T20:00:%02dZ".formatted(i))).size(), 0);
        scheduler.install(config, Instant.parse("2026-10-26T20:00:59Z"));
        equal(scheduler.poll(Instant.parse("2026-10-26T20:00:59Z")).size(), 0);
        equal(scheduler.poll(Instant.parse("2026-10-26T19:59:00Z")).size(), 0);
        equal(scheduler.poll(Instant.parse("2026-10-26T20:00:00Z")).size(), 0);
        equal(scheduler.poll(Instant.parse("2026-10-26T20:02:00Z")).size(), 0);
        var restarted = installed(config, "2026-10-26T20:00:30Z");
        equal(restarted.poll(Instant.parse("2026-10-26T20:00:31Z")).size(), 0);
        equal(restarted.poll(Instant.parse("2026-10-26T20:02:00Z")).size(), 0);
        equal(restarted.poll(Instant.parse("2026-10-27T20:00:00Z")).size(), 1);
    }

    private static void queue(int count) throws Exception {
        var source = new StringBuilder("timezone: \"UTC\" everyday {");
        for (int i = 0; i < count; i++) source.append(entry("20:00", i % 2 == 0 ? "title" : "subtitle", "n" + i));
        source.append(entry("20:00", "chat", "immediate")).append(entry("20:00", "actionbar", "immediate")).append("}");
        var scheduler = installed(parse(source.toString()), "2026-10-26T19:59:00Z");
        var queue = new TitleQueue();
        List<Integer> immediate = new ArrayList<>();
        for (var n : scheduler.poll(Instant.parse("2026-10-26T20:00:00Z"))) {
            if (n.type().isTitle()) queue.add(n); else immediate.add(n.id());
        }
        equal(immediate, List.of(count + 1, count + 2));
        List<Integer> sent = new ArrayList<>();
        queue.tick(n -> sent.add(n.id()));
        equal(sent, List.of(1));
        for (int tick = 1; tick <= (count - 1) * TitleQueue.DISPLAY_TICKS; tick++) {
            queue.tick(n -> sent.add(n.id()));
            equal(sent.size(), Math.min(count, 1 + tick / TitleQueue.DISPLAY_TICKS));
        }
        for (int i = 0; i < count; i++) equal(sent.get(i), i + 1);
    }

    private static JihouConfig parse(String text) throws ConfigException { return ConfigParser.parse(text); }
    private static String entry(String time, String type, String message) {
        return "notification { time: \"" + time + "\" type: " + type + " message: \"" + message + "\" }\n";
    }
    private static JihouScheduler installed(JihouConfig config, String before) {
        var scheduler = new JihouScheduler();
        scheduler.install(config, Instant.parse(before));
        return scheduler;
    }
    private static List<Integer> ids(List<Notification> notifications) { return notifications.stream().map(Notification::id).toList(); }
    private static void invalid(String text, int line, String reason) throws Exception {
        try { parse(text); throw new AssertionError("Accepted invalid config: " + text); }
        catch (ConfigException ex) { equal(ex.line(), line); check(ex.getMessage().contains(reason), ex.getMessage()); }
    }
    private static void test(String name, Test test) throws Exception {
        try { test.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable failure) { throw new AssertionError("FAIL " + name, failure); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void equal(Object actual, Object expected) {
        if (!java.util.Objects.equals(actual, expected)) throw new AssertionError("Expected " + expected + "; got " + actual);
    }
}

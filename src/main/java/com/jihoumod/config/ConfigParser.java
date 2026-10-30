package com.jihoumod.config;

import com.jihoumod.notification.Notification;
import com.jihoumod.notification.NotificationType;
import com.jihoumod.notification.ScheduleTime;
import com.jihoumod.text.TextParser;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small strict lexer and recursive block parser; # inside quoted text is not a comment. */
public final class ConfigParser {
    private enum Kind { WORD, STRING, COLON, OPEN, CLOSE, EOF }
    private record Token(Kind kind, String value, int line) {}
    private final String input;
    private int offset;
    private int line = 1;
    private Token token;

    private ConfigParser(String input) throws ConfigException {
        this.input = input.startsWith("\uFEFF") ? input.substring(1) : input;
        token = next();
    }

    public static JihouConfig parse(String input) throws ConfigException { return new ConfigParser(input).parse(); }

    private JihouConfig parse() throws ConfigException {
        ZoneId zone = ZoneId.of("Asia/Tokyo");
        boolean zoneSeen = false;
        var notifications = new ArrayList<Notification>();
        while (token.kind != Kind.EOF) {
            Token block = take(Kind.WORD, "Expected timezone or a day block");
            if (block.value.equals("timezone")) {
                if (zoneSeen) throw error(block, "Duplicate timezone");
                zoneSeen = true;
                take(Kind.COLON, "Expected ':' after timezone");
                Token value = take(Kind.STRING, "Timezone must be quoted");
                try { zone = ZoneId.of(value.value); }
                catch (DateTimeException ex) { throw error(value, "Invalid timezone '" + value.value + "'"); }
                continue;
            }
            DayOfWeek day = null;
            if (!block.value.equals("everyday")) {
                try { day = DayOfWeek.valueOf(block.value.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ex) { throw error(block, "Unknown day block '" + block.value + "'"); }
                if (!block.value.equals(block.value.toLowerCase(Locale.ROOT))) throw error(block, "Day names must be lowercase");
            }
            take(Kind.OPEN, "Expected '{' after day block");
            while (token.kind != Kind.CLOSE) {
                if (token.kind == Kind.EOF) throw error(token, "Unclosed '" + block.value + "' block opened at line " + block.line);
                Token start = take(Kind.WORD, "Expected notification block");
                if (!start.value.equals("notification")) throw error(start, "Expected notification, got '" + start.value + "'");
                notifications.add(notification(notifications.size() + 1, day, start));
            }
            take(Kind.CLOSE, "Expected '}'");
        }
        return new JihouConfig(zone, notifications);
    }

    private Notification notification(int id, DayOfWeek day, Token start) throws ConfigException {
        take(Kind.OPEN, "Expected '{' after notification");
        Map<String, Token> fields = new HashMap<>();
        while (token.kind != Kind.CLOSE) {
            if (token.kind == Kind.EOF) throw error(token, "Unclosed notification opened at line " + start.line);
            Token key = take(Kind.WORD, "Expected notification key");
            if (!key.value.matches("time|type|message|subtitle")) throw error(key, "Unknown notification key '" + key.value + "'");
            if (fields.containsKey(key.value)) throw error(key, "Duplicate key '" + key.value + "'");
            take(Kind.COLON, "Expected ':' after " + key.value);
            fields.put(key.value, take(key.value.equals("type") ? Kind.WORD : Kind.STRING,
                    "Expected " + (key.value.equals("type") ? "unquoted type" : "quoted " + key.value)));
        }
        take(Kind.CLOSE, "Expected '}'");
        for (String key : new String[]{"time", "type", "message"}) {
            if (!fields.containsKey(key)) throw error(start, "Missing required key '" + key + "'");
        }
        Token time = fields.get("time");
        Token type = fields.get("type");
        NotificationType notificationType;
        try {
            if (!type.value.equals(type.value.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
            notificationType = NotificationType.valueOf(type.value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) { throw error(type, "Unknown type '" + type.value + "'"); }
        Token message = fields.get("message");
        Token subtitle = fields.get("subtitle");
        return new Notification(id, day, ScheduleTime.parse(time.value, time.line), notificationType,
                TextParser.parse(message.value, message.line),
                subtitle == null ? null : TextParser.parse(subtitle.value, subtitle.line));
    }

    private Token take(Kind kind, String reason) throws ConfigException {
        if (token.kind != kind) throw error(token, reason + ", got '" + token.value + "'");
        Token result = token;
        token = next();
        return result;
    }

    private ConfigException error(Token at, String reason) { return new ConfigException(at.line, reason); }

    private Token next() throws ConfigException {
        while (offset < input.length()) {
            char c = input.charAt(offset);
            if (Character.isWhitespace(c)) { if (c == '\n') line++; offset++; }
            else if (c == '#') { while (offset < input.length() && input.charAt(offset) != '\n') offset++; }
            else break;
        }
        if (offset == input.length()) return new Token(Kind.EOF, "end of file", line);
        int startLine = line;
        char c = input.charAt(offset++);
        Kind symbol = switch (c) { case ':' -> Kind.COLON; case '{' -> Kind.OPEN; case '}' -> Kind.CLOSE; default -> null; };
        if (symbol != null) return new Token(symbol, String.valueOf(c), startLine);
        if (c == '"') {
            var value = new StringBuilder();
            while (offset < input.length()) {
                c = input.charAt(offset++);
                if (c == '"') return new Token(Kind.STRING, value.toString(), startLine);
                if (c == '\n' || c == '\r') throw new ConfigException(startLine, "Unclosed string (use \\n for a newline)");
                if (c == '\\') {
                    if (offset == input.length()) break;
                    char escaped = input.charAt(offset++);
                    value.append(switch (escaped) {
                        case '"' -> '"'; case '\\' -> '\\'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                        default -> throw new ConfigException(startLine, "Unknown string escape \\" + escaped);
                    });
                } else value.append(c);
            }
            throw new ConfigException(startLine, "Unclosed quoted string");
        }
        if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_') {
            int start = offset - 1;
            while (offset < input.length() && (Character.isLetterOrDigit(input.charAt(offset)) || input.charAt(offset) == '_')) offset++;
            return new Token(Kind.WORD, input.substring(start, offset), startLine);
        }
        throw new ConfigException(startLine, "Unexpected character '" + c + "'");
    }
}

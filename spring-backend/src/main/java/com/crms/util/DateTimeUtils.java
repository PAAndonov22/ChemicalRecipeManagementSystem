package com.crms.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtils {
    private static final DateTimeFormatter SQLITE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private DateTimeUtils() {
    }

    public static String currentUtcTimestamp() {
        return SQLITE_TIMESTAMP.format(Instant.now());
    }

    public static String futureUtcTimestampMinutes(long minutes) {
        return SQLITE_TIMESTAMP.format(Instant.now().plusSeconds(minutes * 60));
    }

    public static String futureUtcTimestampHours(long hours) {
        return SQLITE_TIMESTAMP.format(Instant.now().plusSeconds(hours * 3600));
    }
}

package com.fitcore.gym.service;

import com.fitcore.gym.exception.BadRequestException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class TrainerAvailability {
    private static final Pattern WINDOW = Pattern.compile(
            "^(?:([A-Za-z]+)(?:\\s*:\\s*|\\s+))?([0-9]{2}:[0-9]{2})\\s*-\\s*([0-9]{2}:[0-9]{2})$");

    private TrainerAvailability() { }

    /**
     * Empty slots mean no daily restrictions have been published. Nonempty schedules contain
     * UTC windows such as 09:00-12:00, Tue 09:00-12:00 or Tuesday: 09:00-12:00.
     * Full weekday names and three-letter abbreviations are case-insensitive.
     */
    static void requireAvailable(List<String> slots, LocalDate date, LocalTime start, LocalTime end) {
        if (slots == null || slots.isEmpty()) {
            return;
        }

        boolean available = false;
        for (String slot : slots) {
            if (slot == null) {
                throw malformedSchedule();
            }
            var match = WINDOW.matcher(slot.trim());
            if (!match.matches()) {
                throw malformedSchedule();
            }
            DayOfWeek day = match.group(1) == null ? null : weekday(match.group(1));
            LocalTime windowStart;
            LocalTime windowEnd;
            try {
                windowStart = LocalTime.parse(match.group(2));
                windowEnd = LocalTime.parse(match.group(3));
            } catch (DateTimeParseException exception) {
                throw malformedSchedule();
            }
            if (!windowEnd.isAfter(windowStart)) {
                throw malformedSchedule();
            }
            if ((day == null || day == date.getDayOfWeek()) && !start.isBefore(windowStart) && !end.isAfter(windowEnd)) {
                available = true;
            }
        }
        // Parse every entry before accepting: an invalid entry must not make the schedule permissive.
        if (!available) {
            throw new BadRequestException("Requested time is outside the trainer's published availability (UTC)");
        }
    }

    private static DayOfWeek weekday(String label) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "mon", "monday" -> DayOfWeek.MONDAY;
            case "tue", "tuesday" -> DayOfWeek.TUESDAY;
            case "wed", "wednesday" -> DayOfWeek.WEDNESDAY;
            case "thu", "thursday" -> DayOfWeek.THURSDAY;
            case "fri", "friday" -> DayOfWeek.FRIDAY;
            case "sat", "saturday" -> DayOfWeek.SATURDAY;
            case "sun", "sunday" -> DayOfWeek.SUNDAY;
            default -> throw malformedSchedule();
        };
    }

    private static BadRequestException malformedSchedule() {
        return new BadRequestException("Trainer availability is invalid; use HH:mm-HH:mm or a weekday followed by HH:mm-HH:mm (UTC)");
    }
}

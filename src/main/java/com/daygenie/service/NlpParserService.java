package com.daygenie.service;

import com.daygenie.model.TaskCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * Rule-based NLP parser.
 * Extracts date, time, location and category from free-text like:
 *   "Go to college on Sunday at 9 AM"
 *   "Doctor appointment tomorrow at 3:30 PM near Park Street"
 */
@Service
@Slf4j
public class NlpParserService {

    public static class ParsedTask {
        public String title;
        public LocalDateTime scheduledTime;
        public String location;
        public TaskCategory category;
        public String description;
    }

    // ── Patterns ─────────────────────────────────────────────────────────────

    private static final Pattern TIME_PATTERN =
        Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM|am|pm)\\b");

    private static final Pattern HOUR24_PATTERN =
        Pattern.compile("\\bat\\s+(\\d{1,2}):(\\d{2})\\b");

    private static final Pattern DATE_EXPLICIT =
        Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\b",
                Pattern.CASE_INSENSITIVE);

    private static final Map<String, Integer> DAY_OFFSETS = new LinkedHashMap<>();
    static {
        DAY_OFFSETS.put("today",       0);
        DAY_OFFSETS.put("tomorrow",    1);
        DAY_OFFSETS.put("day after",   2);
        DAY_OFFSETS.put("monday",    -1); // resolved dynamically
        DAY_OFFSETS.put("tuesday",   -1);
        DAY_OFFSETS.put("wednesday", -1);
        DAY_OFFSETS.put("thursday",  -1);
        DAY_OFFSETS.put("friday",    -1);
        DAY_OFFSETS.put("saturday",  -1);
        DAY_OFFSETS.put("sunday",    -1);
    }

    private static final Map<String, DayOfWeek> WEEKDAY_MAP = Map.of(
        "monday", DayOfWeek.MONDAY,
        "tuesday", DayOfWeek.TUESDAY,
        "wednesday", DayOfWeek.WEDNESDAY,
        "thursday", DayOfWeek.THURSDAY,
        "friday", DayOfWeek.FRIDAY,
        "saturday", DayOfWeek.SATURDAY,
        "sunday", DayOfWeek.SUNDAY
    );

    private static final Map<String, TaskCategory> CATEGORY_KEYWORDS = Map.of(
        "college|university|school|class|lecture|exam|study", TaskCategory.STUDY,
        "meeting|office|work|client|conference|interview",    TaskCategory.MEETING,
        "travel|trip|bus|train|airport|flight|drive|commute", TaskCategory.TRAVEL,
        "doctor|hospital|clinic|medicine|appointment|dentist", TaskCategory.HEALTH,
        "gym|exercise|workout|run|yoga",                       TaskCategory.HEALTH
    );

    private static final Pattern LOCATION_PATTERN =
        Pattern.compile("\\b(?:at|to|in|near|@)\\s+([A-Z][a-zA-Z\\s]{2,30}?)(?:\\s+(?:on|at|by|for)|$)",
                Pattern.CASE_INSENSITIVE);

    // ── Public API ────────────────────────────────────────────────────────────

    public ParsedTask parse(String rawInput) {
        log.debug("NLP parsing: {}", rawInput);
        ParsedTask result = new ParsedTask();
        result.title = cleanTitle(rawInput);
        result.description = rawInput;
        result.scheduledTime = extractDateTime(rawInput);
        result.location = extractLocation(rawInput);
        result.category = inferCategory(rawInput);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String cleanTitle(String input) {
        // Remove time / date phrases, keep core intent
        String t = input.replaceAll("(?i)\\bon\\s+\\w+day\\b", "")
                        .replaceAll("(?i)\\btomorrow\\b|\\btoday\\b", "")
                        .replaceAll("(?i)\\bat\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?", "")
                        .replaceAll("(?i)\\bnear\\s+\\w+", "")
                        .trim();
        return t.isEmpty() ? input : capitalize(t);
    }

    private LocalDateTime extractDateTime(String input) {
        LocalDate date = extractDate(input.toLowerCase());
        LocalTime time = extractTime(input);
        if (date == null) date = LocalDate.now().plusDays(1); // default tomorrow
        if (time == null) time = LocalTime.of(9, 0);          // default 9 AM
        return LocalDateTime.of(date, time);
    }

    private LocalDate extractDate(String lower) {
        // Relative keywords
        if (lower.contains("today"))    return LocalDate.now();
        if (lower.contains("tomorrow")) return LocalDate.now().plusDays(1);
        if (lower.contains("day after"))return LocalDate.now().plusDays(2);

        // Named weekday
        for (Map.Entry<String, DayOfWeek> e : WEEKDAY_MAP.entrySet()) {
            if (lower.contains(e.getKey())) {
                return nextWeekday(e.getValue());
            }
        }

        // Explicit date like "15 June"
        Matcher m = DATE_EXPLICIT.matcher(lower);
        if (m.find()) {
            try {
                int day = Integer.parseInt(m.group(1));
                String monthStr = m.group(2).substring(0, 3).toLowerCase();
                Month month = Month.valueOf(monthStr.toUpperCase());
                int year = LocalDate.now().getYear();
                LocalDate d = LocalDate.of(year, month, day);
                if (d.isBefore(LocalDate.now())) d = d.plusYears(1);
                return d;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private LocalDate nextWeekday(DayOfWeek target) {
        LocalDate today = LocalDate.now();
        int diff = target.getValue() - today.getDayOfWeek().getValue();
        if (diff <= 0) diff += 7;
        return today.plusDays(diff);
    }

    private LocalTime extractTime(String input) {
        // 12-hour format: 9 AM, 3:30 PM
        Matcher m = TIME_PATTERN.matcher(input);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3).toUpperCase();
            if (ampm.equals("PM") && hour != 12) hour += 12;
            if (ampm.equals("AM") && hour == 12) hour = 0;
            return LocalTime.of(hour, minute);
        }
        // 24-hour format: at 14:30
        Matcher m24 = HOUR24_PATTERN.matcher(input);
        if (m24.find()) {
            return LocalTime.of(Integer.parseInt(m24.group(1)), Integer.parseInt(m24.group(2)));
        }
        return null;
    }

    private String extractLocation(String input) {
        Matcher m = LOCATION_PATTERN.matcher(input);
        if (m.find()) {
            String loc = m.group(1).trim();
            if (loc.length() > 2) return capitalize(loc);
        }
        return null;
    }

    private TaskCategory inferCategory(String input) {
        String lower = input.toLowerCase();
        for (Map.Entry<String, TaskCategory> e : CATEGORY_KEYWORDS.entrySet()) {
            if (lower.matches(".*(" + e.getKey() + ").*")) return e.getValue();
        }
        return TaskCategory.OTHER;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

package com.daygenie.service;

import com.daygenie.model.TaskCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.joestelmach.natty.*;

import java.time.*;
import java.util.*;
import java.util.regex.*;

@Service
@Slf4j
public class NlpParserService {

    public static class ParsedTask {
        public String title;
        public LocalDateTime scheduledTime;
        public String location;
        public String originLocation;
        public TaskCategory category;
        public String description;
    }

    // ── Singleton Natty parser ────────────────────────────────────────────────
    private final Parser nattyParser = new Parser();

    // ── Patterns ─────────────────────────────────────────────────────────────

    private static final Pattern TIME_PATTERN =
            Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM|am|pm)\\b");

    private static final Pattern HOUR24_PATTERN =
            Pattern.compile("\\bat\\s+(\\d{1,2}):(\\d{2})\\b");

    private static final Pattern DATE_EXPLICIT =
            Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_NUMERIC =
            Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b");

    private static final Pattern IN_N_DAYS =
            Pattern.compile("\\bin\\s+(\\d+)\\s+days?\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern IN_N_HOURS =
            Pattern.compile("\\bin\\s+(\\d+)\\s+hours?\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern IN_N_MINUTES =
            Pattern.compile("\\bin\\s+(\\d+)\\s+min(?:utes?)?\\b", Pattern.CASE_INSENSITIVE);

    // FIX: relative expression pattern for Natty guard
    private static final Pattern RELATIVE_EXPR =
            Pattern.compile("\\bin\\s+\\d+\\s+(hours?|minutes?|mins?|days?)\\b", Pattern.CASE_INSENSITIVE);

    private static final Map<String, DayOfWeek> WEEKDAY_MAP = Map.of(
            "monday",    DayOfWeek.MONDAY,
            "tuesday",   DayOfWeek.TUESDAY,
            "wednesday", DayOfWeek.WEDNESDAY,
            "thursday",  DayOfWeek.THURSDAY,
            "friday",    DayOfWeek.FRIDAY,
            "saturday",  DayOfWeek.SATURDAY,
            "sunday",    DayOfWeek.SUNDAY
    );

    private static final Map<String, LocalTime> TIME_OF_DAY = new LinkedHashMap<>();
    static {
        TIME_OF_DAY.put("midnight",      LocalTime.of(0,  0));
        TIME_OF_DAY.put("early morning", LocalTime.of(6,  0));
        TIME_OF_DAY.put("morning",       LocalTime.of(8,  0));
        TIME_OF_DAY.put("noon",          LocalTime.of(12, 0));
        TIME_OF_DAY.put("midday",        LocalTime.of(12, 0));
        TIME_OF_DAY.put("afternoon",     LocalTime.of(14, 0));
        TIME_OF_DAY.put("evening",       LocalTime.of(18, 0));
        TIME_OF_DAY.put("night",         LocalTime.of(20, 0));
        TIME_OF_DAY.put("late night",    LocalTime.of(22, 0));
    }

    private static final Map<String, TaskCategory> CATEGORY_KEYWORDS = Map.of(
            "college|university|school|class|lecture|exam|study", TaskCategory.STUDY,
            "meeting|office|work|client|conference|interview",    TaskCategory.MEETING,
            "travel|trip|bus|train|airport|flight|drive|commute", TaskCategory.TRAVEL,
            "doctor|hospital|clinic|medicine|appointment|dentist", TaskCategory.HEALTH,
            "gym|exercise|workout|run|yoga",                       TaskCategory.HEALTH
    );

    private static final Pattern FROM_TO_PATTERN =
            Pattern.compile("(?:^|\\s)from\\s+([a-zA-Z\\s]{2,30}?)\\s+to\\s+([a-zA-Z\\s]{2,30}?)(?:\\s+(?:on|at|by|for|tomorrow|today|in)|$)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern TO_FROM_PATTERN =
            Pattern.compile("(?:go|travel|head|commute)\\s+to\\s+([a-zA-Z\\s]{2,30}?)\\s+from\\s+([a-zA-Z\\s]{2,30}?)(?:\\s+(?:on|at|by|for|tomorrow|today|in)|$)",
                    Pattern.CASE_INSENSITIVE);

    // ── Public API ────────────────────────────────────────────────────────────

    public ParsedTask parse(String rawInput) {
        log.debug("NLP parsing: {}", rawInput);
        ParsedTask result = new ParsedTask();
        result.description = rawInput;

        Matcher toFrom = TO_FROM_PATTERN.matcher(rawInput);
        Matcher fromTo = FROM_TO_PATTERN.matcher(rawInput);

        if (toFrom.find()) {
            result.location       = capitalize(toFrom.group(1).trim());
            result.originLocation = capitalize(toFrom.group(2).trim());
            log.info("To/From extracted: {} → {}", result.originLocation, result.location);
            String stripped = rawInput
                    .replaceAll("(?i)to\\s+" + Pattern.quote(toFrom.group(1).trim())
                            + "\\s+from\\s+" + Pattern.quote(toFrom.group(2).trim()), "")
                    .trim();
            result.title = cleanTitle(stripped.isEmpty() ? rawInput : stripped);
        } else if (fromTo.find()) {
            result.originLocation = capitalize(fromTo.group(1).trim());
            result.location       = capitalize(fromTo.group(2).trim());
            log.info("From/To extracted: {} → {}", result.originLocation, result.location);
            String stripped = rawInput
                    .replaceAll("(?i)from\\s+" + Pattern.quote(fromTo.group(1).trim())
                            + "\\s+to\\s+" + Pattern.quote(fromTo.group(2).trim()), "")
                    .trim();
            result.title = cleanTitle(stripped.isEmpty() ? rawInput : stripped);
        } else {
            result.location = extractLocation(rawInput);
            result.title    = cleanTitle(rawInput);
        }

        result.scheduledTime = extractDateTime(rawInput);
        log.info("Extracted location = {}", result.location);
        result.category = inferCategory(rawInput);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String cleanTitle(String input) {
        String t = input
                .replaceAll("(?i)\\bon\\s+\\w+day\\b", "")
                .replaceAll("(?i)\\btomorrow\\b|\\btoday\\b", "")
                .replaceAll("(?i)\\bnext\\s+week\\b", "")
                .replaceAll("(?i)\\bin\\s+\\d+\\s+days?\\b", "")
                .replaceAll("(?i)\\bin\\s+\\d+\\s+hours?\\b", "")
                .replaceAll("(?i)\\bin\\s+\\d+\\s+min(?:utes?)?\\b", "")
                .replaceAll("(?i)\\bat\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?", "")
                .replaceAll("(?i)\\b(morning|afternoon|evening|night|noon|midday|midnight|early morning|late night)\\b", "")
                .replaceAll("(?i)\\bnear\\s+\\S+", "")
                .replaceAll("(?i)\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return t.isEmpty() ? input : capitalize(t);
    }

    private LocalDateTime extractDateTime(String input) {
        try {
            List<DateGroup> groups = nattyParser.parse(input);
            if (!groups.isEmpty() && !groups.get(0).getDates().isEmpty()) {
                DateGroup group = groups.get(0);
                // FIX: use Natty if explicit date OR relative expression like "in 3 hours"
                boolean isRelative = RELATIVE_EXPR.matcher(input).find();
                if (!group.isDateInferred() || isRelative) {
                    Date date = group.getDates().get(0);
                    return date.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                }
            }
        } catch (Exception e) {
            log.warn("Natty parse failed, falling back to regex: {}", e.getMessage());
        }
        // fallback to regex
        LocalDate date = extractDate(input.toLowerCase());
        LocalTime time = extractTime(input);
        if (date == null) date = LocalDate.now().plusDays(1);
        if (time == null) time = LocalTime.of(9, 0);
        return LocalDateTime.of(date, time);
    }

    private LocalDate extractDate(String lower) {
        if (lower.contains("today"))      return LocalDate.now();
        if (lower.contains("tomorrow"))   return LocalDate.now().plusDays(1);
        if (lower.contains("day after"))  return LocalDate.now().plusDays(2);
        if (lower.contains("next week"))  return LocalDate.now().plusWeeks(1);

        Matcher inDays = IN_N_DAYS.matcher(lower);
        if (inDays.find()) {
            return LocalDate.now().plusDays(Long.parseLong(inDays.group(1)));
        }

        for (Map.Entry<String, DayOfWeek> e : WEEKDAY_MAP.entrySet()) {
            if (lower.contains(e.getKey())) {
                return nextWeekday(e.getValue());
            }
        }

        Matcher m = DATE_EXPLICIT.matcher(lower);
        if (m.find()) {
            try {
                int day = Integer.parseInt(m.group(1));
                String monthStr = m.group(2).substring(0, 3).toUpperCase();
                Month month = Month.valueOf(monthStr);
                int year = LocalDate.now().getYear();
                LocalDate d = LocalDate.of(year, month, day);
                if (d.isBefore(LocalDate.now())) d = d.plusYears(1);
                return d;
            } catch (Exception ignored) {}
        }

        Matcher num = DATE_NUMERIC.matcher(lower);
        if (num.find()) {
            try {
                int day   = Integer.parseInt(num.group(1));
                int month = Integer.parseInt(num.group(2));
                int year  = num.group(3) != null
                        ? Integer.parseInt(num.group(3).length() == 2
                        ? "20" + num.group(3) : num.group(3))
                        : LocalDate.now().getYear();
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
        Matcher m = TIME_PATTERN.matcher(input);
        if (m.find()) {
            int hour   = Integer.parseInt(m.group(1));
            int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3).toUpperCase();
            if (ampm.equals("PM") && hour != 12) hour += 12;
            if (ampm.equals("AM") && hour == 12) hour = 0;
            return LocalTime.of(hour, minute);
        }
        Matcher m24 = HOUR24_PATTERN.matcher(input);
        if (m24.find()) {
            return LocalTime.of(
                    Integer.parseInt(m24.group(1)),
                    Integer.parseInt(m24.group(2))
            );
        }
        String lower = input.toLowerCase();
        for (Map.Entry<String, LocalTime> e : TIME_OF_DAY.entrySet()) {
            if (lower.contains(e.getKey())) {
                log.info("Time-of-day matched: '{}'", e.getKey());
                return e.getValue();
            }
        }
        return null;
    }

    private String extractLocation(String input) {
        Pattern p = Pattern.compile(
                "(?:near|at|in|go to|travel to|area is|location is)\\s+([a-zA-Z\\s]+?)(?:\\s+and|\\s+on|\\s+at|\\s+in\\s+\\d|\\s+tomorrow|\\s+today|$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(input);
        if (m.find()) {
            String loc = m.group(1).trim();
            loc = loc.replaceFirst("(?i)^go\\s+to\\s+", "").trim();
            loc = loc.replaceFirst("(?i)^travel\\s+to\\s+", "").trim();
            loc = loc.replaceFirst("(?i)^visit\\s+", "").trim();
            loc = loc.replaceAll("(?i)\\s+by\\s+\\w+.*$", "").trim();
            log.info("Location extracted = {}", loc);
            return capitalize(loc);
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
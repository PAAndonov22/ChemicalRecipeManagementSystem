package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.models.Event;
import org.codingburgas.ChemicalRecipeSystem.models.EventType;
import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.codingburgas.ChemicalRecipeSystem.models.PeriodReport;
import org.codingburgas.ChemicalRecipeSystem.models.ReportRow;
import org.codingburgas.ChemicalRecipeSystem.repositories.EventRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    public static final List<String> GROUPS = List.of("day", "week", "month", "year");
    public static final List<Integer> MONTH_OPTIONS = List.of(1, 6, 12);

    private final OrderRepository orders;
    private final EventRepository events;

    public ReportService(OrderRepository orders, EventRepository events) {
        this.orders = orders;
        this.events = events;
    }

    /** Statistics for a period (from - to), grouped by day, week, month or year. */
    public PeriodReport build(LocalDate from, LocalDate to, String groupBy) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'To' date must be after 'from' date.");
        }
        if (!GROUPS.contains(groupBy)) {
            groupBy = "day";
        }

        List<Order> periodOrders = orders.findByOrderedAtBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<Event> periodEvents = events.findByDateBetweenOrderByDate(from, to);

        // TreeMap keeps the groups sorted by label (2026-01, 2026-02, ...)
        Map<String, ReportRow> rows = new TreeMap<>();
        if (groupBy.equals("month")) {
            // show every month, even the empty ones
            for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
                rows.put(m.toString(), new ReportRow(m.toString(), 0, 0, 0, 0));
            }
        }
        for (Order o : periodOrders) {
            add(rows, groupKey(o.getOrderedAt().toLocalDate(), groupBy), new ReportRow("", 1, o.getTotal(), 0, 0));
        }
        for (Event e : periodEvents) {
            add(rows, groupKey(e.getDate(), groupBy), new ReportRow("", 0, 0, 1, e.getRevenue()));
        }

        Map<EventType, Long> countByType = periodEvents.stream()
            .collect(Collectors.groupingBy(Event::getType, () -> new EnumMap<>(EventType.class), Collectors.counting()));
        Map<EventType, Double> revenueByType = periodEvents.stream()
            .collect(Collectors.groupingBy(Event::getType, () -> new EnumMap<>(EventType.class), Collectors.summingDouble(Event::getRevenue)));

        return new PeriodReport(from, to, groupBy, new ArrayList<>(rows.values()), periodEvents, countByType, revenueByType);
    }

    /**
     * Report for the last 1, 6 or 12 whole months, ending with the month of the chosen calendar date.
     * The chosen date cannot be in the future or more than 12 months back.
     */
    public PeriodReport monthly(int months, LocalDate date) {
        if (!MONTH_OPTIONS.contains(months)) {
            months = 1;
        }
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) {
            date = today;
        }
        if (date.isBefore(today.minusMonths(12))) {
            date = today.minusMonths(12);
        }
        YearMonth lastMonth = YearMonth.from(date);
        return build(lastMonth.minusMonths(months - 1).atDay(1), lastMonth.atEndOfMonth(), "month");
    }

    /** Weeks of a month for the calendar (Monday first). Empty cells are null. */
    public List<List<LocalDate>> calendar(YearMonth month) {
        List<List<LocalDate>> weeks = new ArrayList<>();
        List<LocalDate> week = new ArrayList<>();
        for (int i = 1; i < month.atDay(1).getDayOfWeek().getValue(); i++) {
            week.add(null);
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            week.add(month.atDay(day));
            if (week.size() == 7) {
                weeks.add(week);
                week = new ArrayList<>();
            }
        }
        if (!week.isEmpty()) {
            while (week.size() < 7) {
                week.add(null);
            }
            weeks.add(week);
        }
        return weeks;
    }

    private static String groupKey(LocalDate date, String groupBy) {
        return switch (groupBy) {
            case "week" -> Order.isoWeek(date);
            case "month" -> YearMonth.from(date).toString();
            case "year" -> String.valueOf(date.getYear());
            default -> date.toString();
        };
    }

    private static void add(Map<String, ReportRow> rows, String key, ReportRow value) {
        rows.merge(key, new ReportRow(key, value.orders(), value.orderRevenue(), value.events(), value.eventRevenue()),
            (a, b) -> new ReportRow(key, a.orders() + b.orders(), a.orderRevenue() + b.orderRevenue(),
                a.events() + b.events(), a.eventRevenue() + b.eventRevenue()));
    }
}
